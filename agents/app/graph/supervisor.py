"""Supervisor agent - langchain.agents.create_agent + Redis checkpointer for persistent memory."""

from contextlib import asynccontextmanager
from typing import Optional

from langchain.agents import create_agent, AgentState
from langchain_core.messages import AIMessage, HumanMessage
from langchain_openai import ChatOpenAI
from langgraph.checkpoint.redis import AsyncRedisSaver
from langgraph.store.postgres import PostgresStore

from app.agents.workflows import AGENT_TOOLS
from app.config import settings
from app.memory.redis_store import RedisStore
from app.memory.pg_user_store import Context, USER_PROFILE_TOOLS, inject_user_preferences

import structlog

logger = structlog.get_logger()


# 대화 이력이 마지막으로 사용된 후 유지되는 시간 (분)
# 이 시간이 지나면 Redis TTL에 의해 체크포인트가 자동 삭제되어 대화가 초기화됩니다.
CONVERSATION_TTL_MINUTES = int(getattr(settings, "CONVERSATION_TTL", 30))

SUPERVISOR_PROMPT = """당신은 지능형 쇼핑 에이전트 시스템의 메인 슈퍼바이저입니다.

당신은 사용자 요청을 해결하기 위해 여러 전문화된 하위 에이전트를 도구(Tool)로 사용할 수 있습니다.

사용할 수 있는 전문 에이전트 도구:
1. product_search_agent_tool: 상품 검색, 가격 비교, 카테고리 탐색을 수행합니다.
2. review_analysis_agent_tool: 상품 리뷰 요약, 사이즈/품질 의견 분석을 수행합니다.
3. cart_management_agent_tool: 장바구니에 특정 상품 추가/삭제, 예산 관리를 수행합니다. 장바구니에 상품을 추가하기 위해서는 먼저 제품을 검색해야 합니다.
4. customer_service_agent_tool: 기존 주문 내역 조회, 반품/환불 절차 안내, 쇼핑몰 정책(배송, 약관) 안내를 수행합니다.
5. user_profile_tool: 사용자 프로필 조회, 사용자 관심사 저장, 사용자 관심사 업데이트를 수행합니다.

규칙 (쇼핑):
- 사용자의 요청이 모호할경우 다시 질문하기보단 가장 적절해보이는 제품을 추천하거나 기본값(예: 1개, 기본옵션 등)을 사용해서 도구를 호출하세요.
- 동일한 하위 에이전트를 반복적으로 호출하지 마세요. (예: 상품을 찾고싶은데 product_search_agent_tool을 여러번 호출하지 마세요.)
- 하위 에이전트에게 전달할 query는 필요한 핵심 정보만 포함하여 간결하게 전달하세요.
- 제품을 추천할때는 최대 3개를 제품의 리뷰와 함께 추천해주세요.
- 사용자의 프로필을 확인하여 제품을 추천하거나 다음 행동을 결정할때 참고하세요.
- 여러 단계의 분석이나 조치가 끝나면 최종적으로 수집된 정보를 바탕으로 사용자에게 한국어로 친절하고 완성된 형태의 최종 답변을 제공하세요.

규칙 (사용자 프로필):
- 사용자 프로필을 관리하는 user_profile_tool을 사용할 수 있습니다.
- 사용자의 성격, 선호 브랜드, 선호 색상등의 사용자 프로필을 조회, 저장, 업데이트 할 수 있습니다.
- 사용자에게 프로필을 저장한다거나 저장된 프로필을 절대로 노출하면 안됩니다.
- 사용자의 대화 내역을 바탕으로 최대한 사용자의 성향이나 취향등을 기록하세요.

규칙 (공통):
- 주어진 역할 이외의 요청은 거절하세요.
- 도구를 호출해야 하는 상황이면, 사용자에게 답변을 생성하지 말고 바로 도구를 호출하세요 (Tool Call).
- 사용자에게 내부의 오류나 에러, 내부 로직, 도구에 대해 절대 언급하지 마세요.   
"""

class ShoppingAgentState(AgentState):
    """Extended agent state with user/session context."""
    user_id: str = ""
    thread_id: str = ""
    context: dict = {}

# ---------------------------------------------------------------------------
# Agent factory
# ---------------------------------------------------------------------------

def supervisor_agent(checkpointer: AsyncRedisSaver, store: PostgresStore):
    """Create the supervisor agent with a Redis checkpointer and PostgresStore."""
    llm = ChatOpenAI(model=settings.OPENAI_SUPERVISOR_MODEL)

    agent = create_agent(
        model=llm,
        tools=AGENT_TOOLS + USER_PROFILE_TOOLS,
        system_prompt=SUPERVISOR_PROMPT,
        state_schema=ShoppingAgentState,
        checkpointer=checkpointer,
        store=store,
        context_schema=Context,
        middleware=[inject_user_preferences],
    )
    logger.info("Supervisor agent created with AsyncRedisSaver checkpointer and PostgresStore")
    return agent


# ---------------------------------------------------------------------------
# Checkpointer lifecycle helper (주입용)
# ---------------------------------------------------------------------------

@asynccontextmanager
async def create_redis_checkpointer():
    """AsyncRedisSaver를 생성하고 필요한 인덱스를 세팅한 뒤 반환합니다."""
    ttl_config = {
        "default_ttl": CONVERSATION_TTL_MINUTES,
        "refresh_on_read": True,
    }
    async with AsyncRedisSaver.from_conn_string(
        settings.REDIS_URL, ttl=ttl_config
    ) as saver:
        await saver.asetup()
        logger.info(f"Redis checkpointer initialized with TTL: {CONVERSATION_TTL_MINUTES}min")
        yield saver


async def run_agent(
    agent,
    message: str,
    thread_id: str,
    user_id: str,
    context: dict,
    user_ctx: Context,
    redis_store: RedisStore,
) -> dict:
    """Invoke the supervisor agent.

    체크포인터가 thread_id 기준으로 대화 이력을 자동으로 저장/복원합니다.
    """
    input_messages: list = []

    input_messages.append(HumanMessage(content=message))

    # Langfuse 콜백 설정
    callbacks = []
    if getattr(settings, "LANGFUSE_SECRET_KEY", None) and getattr(settings, "LANGFUSE_PUBLIC_KEY", None):
        import os
        os.environ["LANGFUSE_SECRET_KEY"] = settings.LANGFUSE_SECRET_KEY
        os.environ["LANGFUSE_PUBLIC_KEY"] = settings.LANGFUSE_PUBLIC_KEY
        if getattr(settings, "LANGFUSE_HOST", None):
            os.environ["LANGFUSE_HOST"] = settings.LANGFUSE_HOST

        try:
            from langfuse.langchain import CallbackHandler
            langfuse_handler = CallbackHandler()
            callbacks.append(langfuse_handler)
        except Exception as e:
            logger.warning("Failed to initialize Langfuse callback", error=str(e))

    config = {
        "configurable": {"thread_id": thread_id},
        "callbacks": callbacks,
    }

    try:
        final_state = await agent.ainvoke(
            {
                "messages": input_messages,
                "user_id": user_id,
                "thread_id": thread_id,
                "context": context,
                "recursion_limit": 50,
            },
            config,
            context=user_ctx,
        )

        # 최종 AI 응답 추출
        response = "죄송합니다, 요청을 처리하지 못했습니다."
        for msg in reversed(final_state.get("messages", [])):
            if isinstance(msg, AIMessage) and msg.content:
                response = msg.content
                break

        return {
            "response": response,
            "thread_id": thread_id,
            "agent_used": "supervisor",
            "updated_context": final_state.get("context", context),
        }

    except Exception as e:
        logger.error("Supervisor agent execution failed", error=str(e), thread_id=thread_id, exc_info=True)
        return {
            "response": f"처리 중 내부 오류가 발생했습니다: {str(e)}",
            "agent_used": None,
            "error": str(e),
        }