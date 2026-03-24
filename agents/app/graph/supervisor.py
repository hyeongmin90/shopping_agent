"""Supervisor agent - langchain.agents.create_agent + Redis checkpointer for persistent memory."""

import json
from contextlib import asynccontextmanager
from typing import Optional

from langchain.agents import create_agent, AgentState
from langchain_core.messages import AIMessage, HumanMessage, SystemMessage
from langchain_openai import ChatOpenAI
from langgraph.checkpoint.redis import AsyncRedisSaver

from app.agents.workflows import AGENT_TOOLS
from app.config import settings
from app.memory.redis_store import RedisStore

import structlog

logger = structlog.get_logger()

# 대화 이력이 마지막으로 사용된 후 유지되는 시간 (초)
# 이 시간이 지나면 Redis TTL에 의해 체크포인트가 자동 삭제되어 대화가 초기화됩니다.
CONVERSATION_TTL = int(getattr(settings, "CONVERSATION_TTL", 3600))

SUPERVISOR_PROMPT = """당신은 지능형 쇼핑 에이전트 시스템의 메인 슈퍼바이저입니다.

당신은 사용자 요청을 해결하기 위해 여러 전문화된 하위 에이전트를 도구(Tool)로 사용할 수 있습니다.

사용할 수 있는 전문 에이전트 도구:
1. product_search_agent_tool: 상품 검색, 가격 비교, 카테고리 탐색을 수행합니다.
2. review_analysis_agent_tool: 상품 리뷰 요약, 사이즈/품질 의견 분석을 수행합니다.
3. cart_management_agent_tool: 장바구니에 특정 상품 추가/삭제, 예산 관리를 수행합니다.
4. customer_service_agent_tool: 기존 주문 내역 조회, 반품/환불 절차 안내, 쇼핑몰 정책(배송, 약관) 안내를 수행합니다.

- 사용자의 요청이 모호할경우 다시 질문하기보단 가장 적절해보이는 제품을 추천하거나 기본값(예: 1개, 기본옵션 등)을 사용해서 도구를 호출하세요.
- 하위 에이전트를 반복적으로 호출하지 마세요. 
- 제품을 추천할때는 최대 3개를 제품의 리뷰와 함께 추천해주세요.

규칙 (매우 중요):
- 사용자의 요청을 달성하기 위해 필요한 도구를 호출하세요.
- 만약 사용자가 결제나 주소 입력 등 **주문(주문 생성/승인/체크아웃)**을 요구하면, **직접 처리할 수 없다**고 안내하고 웹사이트 화면의 장바구니/결제하기 버튼을 이용해 달라고 정중히 답변하세요.
- 여러 단계의 분석이나 조치가 끝나면 최종적으로 수집된 정보를 바탕으로 사용자에게 한국어로 친절하고 완성된 형태의 최종 답변을 제공하세요.
- 도구를 호출해야 하는 상황이면, 사용자에게 답변을 생성하지 말고 바로 도구를 호출하세요 (Tool Call).
"""


class ShoppingAgentState(AgentState):
    """Extended agent state with user/session context."""
    user_id: str = ""
    thread_id: str = ""
    context: dict = {}


# ---------------------------------------------------------------------------
# Agent factory
# ---------------------------------------------------------------------------

def supervisor_agent(checkpointer: AsyncRedisSaver):
    """Create the supervisor agent with a Redis checkpointer.

    `checkpointer`는 애플리케이션 시작 시 lifespan 안에서 생성되어 주입됩니다.
    LangGraph가 thread_id 단위로 체크포인트를 자동 관리하므로,
    별도의 메시지 직렬화 코드 없이 대화 이력이 유지됩니다.
    """
    llm = ChatOpenAI(model=settings.OPENAI_SUPERVISOR_MODEL)

    agent = create_agent(
        model=llm,
        tools=AGENT_TOOLS,
        system_prompt=SUPERVISOR_PROMPT,
        state_schema=ShoppingAgentState,
        checkpointer=checkpointer,
    )
    logger.info("Supervisor agent created with AsyncRedisSaver checkpointer")
    return agent


# ---------------------------------------------------------------------------
# Checkpointer lifecycle helper (주입용)
# ---------------------------------------------------------------------------

@asynccontextmanager
async def create_redis_checkpointer():
    """AsyncRedisSaver를 생성하고 필요한 인덱스를 세팅한 뒤 반환합니다."""
    async with AsyncRedisSaver.from_conn_string(settings.REDIS_URL) as saver:
        saver.ttl = CONVERSATION_TTL
        await saver.asetup()
        logger.info(f"Redis checkpointer initialized with TTL: {CONVERSATION_TTL}s")
        yield saver


# ---------------------------------------------------------------------------
# Run helper
# ---------------------------------------------------------------------------

async def run_agent(
    agent,
    message: str,
    thread_id: str,
    user_id: str,
    context: dict,
    redis_store: RedisStore,
) -> dict:
    """Invoke the supervisor agent.

    체크포인터가 thread_id 기준으로 대화 이력을 자동으로 저장/복원합니다.
    `CONVERSATION_TTL` 초 이후 Redis TTL 만료 시 대화가 초기화됩니다.
    """

    # 세션 컨텍스트(최근 상품, 예산 등)를 시스템 보조 메시지로 삽입
    # ctx_str = json.dumps(context, ensure_ascii=False)
    # ctx_sys_msg = SystemMessage(
    #     content=f"[시스템] 현재 사용자 세션 상태 (사용자에게 노출하지 마세요):\n{ctx_str}"
    # )

    # 에이전트에 전달하는 새 메시지만 넣으면, 체크포인터가 자동으로 이전 이력을 붙여줍니다.
    # input_messages = [ctx_sys_msg, HumanMessage(content=message)]
    input_messages = [HumanMessage(content=message)]

    # thread_id 단위로 체크포인트 저장 / TTL은 saver.ttl로 제어할 수 있습니다.
    callbacks = []
    if getattr(settings, "LANGFUSE_SECRET_KEY", None) and getattr(settings, "LANGFUSE_PUBLIC_KEY", None):
        import os
        os.environ["LANGFUSE_SECRET_KEY"] = settings.LANGFUSE_SECRET_KEY
        os.environ["LANGFUSE_PUBLIC_KEY"] = settings.LANGFUSE_PUBLIC_KEY
        if getattr(settings, "LANGFUSE_HOST", None):
            os.environ["LANGFUSE_HOST"] = settings.LANGFUSE_HOST
            
        try:
            from langfuse.langchain import CallbackHandler

            # v3/v4+ 에서는 os.environ을 자동으로 읽으며, 키워드 인자가 필요 없습니다.
            langfuse_handler = CallbackHandler()
            callbacks.append(langfuse_handler)
        except Exception as e:
            logger.warning("Failed to initialize Langfuse callback", error=str(e))

    config = {
        "configurable": {
            "thread_id": thread_id,
        },
        "callbacks": callbacks
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
        logger.error("Supervisor agent execution failed", error=str(e), thread_id=thread_id)
        return {
            "response": f"처리 중 내부 오류가 발생했습니다: {str(e)}",
            "agent_used": None,
            "error": str(e),
        }