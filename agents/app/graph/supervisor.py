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
from app.memory.pg_user_store import PgUserStore

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
3. cart_management_agent_tool: 장바구니에 특정 상품 추가/삭제, 예산 관리를 수행합니다. 장바구니에 상품을 추가하기 위해서는 먼저 제품을 검색해야 합니다.
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

_MEMORY_EXTRACTION_PROMPT = """다음 대화에서 사용자에 대해 새롭게 알게 된 정보를 JSON으로 추출하세요.
추출할 정보가 없으면 빈 값을 반환하세요.

반환 형식 (JSON만 반환, 설명 없이):
{
  "preferences": {
    "brands": ["삼성", "애플"],         // 언급된 선호 브랜드 (없으면 빈 배열)
    "categories": ["노트북"],           // 관심 카테고리 (없으면 빈 배열)
    "price_range": {"min": 0, "max": 1500000},  // 예산 범위 (언급 없으면 null)
    "priorities": ["가성비", "경량"]    // 구매 우선순위 키워드 (없으면 빈 배열)
  },
  "facts": [],   // 사용자가 명시적으로 언급한 사실 (직업, 보유 기기, 상황 등). 예: ["대학원생", "맥북 에어 M3 보유 중"]
  "summary": "" // 이번 대화로 파악된 사용자 특성 1~2문장 요약. 변화가 없으면 빈 문자열.
}

대화:
{conversation}"""


async def _extract_memories(
    user_message: str,
    ai_response: str,
    pg_store: PgUserStore,
    user_id: str,
) -> None:
    """대화에서 사용자 메모리를 추출하여 PostgreSQL에 저장합니다."""
    conversation = f"사용자: {user_message}\n에이전트: {ai_response}"
    prompt = _MEMORY_EXTRACTION_PROMPT.format(conversation=conversation)

    try:
        llm = ChatOpenAI(model=settings.OPENAI_SUB_AGENT_MODEL, temperature=0)
        result = await llm.ainvoke([HumanMessage(content=prompt)])
        raw = result.content.strip()

        # JSON 파싱
        if raw.startswith("```"):
            raw = raw.split("```")[1]
            if raw.startswith("json"):
                raw = raw[4:]
        extracted = json.loads(raw.strip())

        prefs = extracted.get("preferences", {})
        # 빈 값 정리
        if prefs.get("price_range") is None:
            prefs.pop("price_range", None)
        prefs = {k: v for k, v in prefs.items() if v}

        new_facts = [f for f in extracted.get("facts", []) if f]
        summary = extracted.get("summary", "").strip()

        if prefs or new_facts or summary:
            await pg_store.upsert_profile(
                user_id=user_id,
                preferences=prefs or None,
                new_facts=new_facts or None,
                summary=summary or None,
            )
            logger.info("memory_extracted_and_saved", user_id=user_id)

    except Exception as e:
        # 메모리 추출 실패는 전체 대화에 영향 없이 무시
        logger.warning("memory_extraction_failed", user_id=user_id, error=str(e))


async def run_agent(
    agent,
    message: str,
    thread_id: str,
    user_id: str,
    context: dict,
    redis_store: RedisStore,
    pg_store: PgUserStore | None = None,
) -> dict:
    """Invoke the supervisor agent.

    체크포인터가 thread_id 기준으로 대화 이력을 자동으로 저장/복원합니다.
    pg_store가 있으면 user_id 기준 장기 메모리를 프롬프트에 주입하고,
    대화 후 새 메모리를 추출하여 저장합니다.
    """
    input_messages: list = []

    # 장기 메모리 로드 → 시스템 메시지로 주입
    if pg_store and user_id:
        try:
            profile = await pg_store.get_profile(user_id)
            if profile:
                memory_text = pg_store.build_memory_prompt(profile)
                input_messages.append(SystemMessage(content=memory_text))
                logger.debug("long_term_memory_injected", user_id=user_id)
        except Exception as e:
            logger.warning("long_term_memory_load_failed", user_id=user_id, error=str(e))

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
        )

        # 최종 AI 응답 추출
        response = "죄송합니다, 요청을 처리하지 못했습니다."
        for msg in reversed(final_state.get("messages", [])):
            if isinstance(msg, AIMessage) and msg.content:
                response = msg.content
                break

        # 장기 메모리 추출 및 저장 (비동기, 응답에 영향 없음)
        if pg_store and user_id and response:
            try:
                await _extract_memories(message, response, pg_store, user_id)
            except Exception as e:
                logger.warning("memory_extraction_error", error=str(e))

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