"""LangGraph Supervisor - Multi-agent orchestration graph."""

import json
from typing import Any, Optional

from langchain_core.messages import SystemMessage, HumanMessage, AIMessage, ToolMessage
from langchain_openai import ChatOpenAI
from langgraph.graph import StateGraph, END
from langgraph.prebuilt import ToolNode

from app.config import settings
from app.graph.state import AgentState
from app.graph.tools import ALL_TOOLS
from app.agents.specialized import (
    create_agent_runnable,
    get_agent_tools,
    get_agent_prompt,
)
from app.memory.redis_store import RedisStore

import structlog

logger = structlog.get_logger()

SUPERVISOR_PROMPT = """당신은 지능형 쇼핑 에이전트 시스템의 슈퍼바이저입니다.

사용자의 메시지를 분석하여 적절한 전문 에이전트에게 작업을 위임합니다.

사용 가능한 에이전트:
1. product_search: 상품 검색, 카테고리 탐색, 상품 비교
2. review_analysis: 리뷰 분석, 사이즈/품질 의견 종합
3. cart_management: 장바구니 관리, 상품 추가/삭제, 예산 검증
4. order_management: 주문 체크아웃, 결제, 주문 상태 확인
5. customer_service: 주문 취소, 환불, 반품, 일반 문의

라우팅 규칙:
- 상품 찾기, 검색, 추천 → product_search
- 리뷰 확인, 사이즈 문의, 품질 문의 → review_analysis
- 장바구니 담기, 수량 변경, 장바구니 확인 → cart_management
- 결제, 주문, 구매 → order_management
- 취소, 환불, 반품, 배송 문의, 고객센터 → customer_service
- 복합 요청: 가장 먼저 필요한 에이전트를 선택하세요

대화 맥락을 고려하여 이전 작업의 연속인 경우 같은 에이전트를 유지하세요.

반드시 다음 JSON 형식으로만 응답하세요:
{"next_agent": "에이전트이름", "reasoning": "선택 이유"}

직접 사용자에게 응답하지 마세요. 에이전트 라우팅만 수행하세요."""


def _create_supervisor_node():
    """Create the supervisor routing node."""
    llm = ChatOpenAI(
        model=settings.OPENAI_MODEL,
        temperature=0,
        api_key=settings.OPENAI_API_KEY,
    )

    def supervisor(state: AgentState) -> dict:
        messages = [SystemMessage(content=SUPERVISOR_PROMPT)]

        # Add context about current state
        context_info = ""
        if state.get("current_order_id"):
            context_info += f"\n현재 주문 ID: {state['current_order_id']}"
        if state.get("cart_items"):
            context_info += f"\n장바구니 상품 수: {len(state['cart_items'])}"
        if state.get("context"):
            ctx = state["context"]
            if ctx.get("last_search"):
                context_info += f"\n이전 검색: {ctx['last_search']}"
            if ctx.get("budget"):
                context_info += f"\n예산: {ctx['budget']}원"

        if context_info:
            messages.append(SystemMessage(content=f"현재 상태:{context_info}"))

        # Add recent conversation messages (last 10)
        recent = state.get("messages", [])[-10:]
        messages.extend(recent)

        response = llm.invoke(messages)

        try:
            result = json.loads(response.content)
            next_agent = result.get("next_agent", "product_search")
            reasoning = result.get("reasoning", "")
        except (json.JSONDecodeError, AttributeError):
            # Fallback: try to extract agent name from text
            content = response.content.lower()
            if "review" in content:
                next_agent = "review_analysis"
            elif "cart" in content or "장바구니" in content:
                next_agent = "cart_management"
            elif "order" in content or "주문" in content or "결제" in content:
                next_agent = "order_management"
            elif "cancel" in content or "refund" in content or "취소" in content or "환불" in content:
                next_agent = "customer_service"
            else:
                next_agent = "product_search"
            reasoning = "Fallback routing"

        logger.info("Supervisor routing", next_agent=next_agent, reasoning=reasoning)

        return {
            "next_agent": next_agent,
            "current_agent": next_agent,
        }

    return supervisor


def _create_agent_node(agent_type: str):
    """Create an agent execution node."""
    llm_with_tools, tools, system_prompt = create_agent_runnable(agent_type)

    async def agent_node(state: AgentState) -> dict:
        messages = [SystemMessage(content=system_prompt)]

        # Add context
        context = state.get("context", {})
        if context:
            context_msg = f"사용자 컨텍스트: {json.dumps(context, ensure_ascii=False)}"
            messages.append(SystemMessage(content=context_msg))

        if state.get("current_order_id"):
            messages.append(
                SystemMessage(content=f"현재 주문 ID: {state['current_order_id']}")
            )

        # Add conversation history
        messages.extend(state.get("messages", [])[-15:])

        # Add self-reflection from previous iteration if any
        if state.get("reflection"):
            messages.append(
                SystemMessage(
                    content=f"이전 시도 반성: {state['reflection']}. 다른 접근을 시도하세요."
                )
            )

        response = await llm_with_tools.ainvoke(messages)

        # Check if the agent wants to request approval
        requires_approval = False
        approval_data = None

        if agent_type == "order_management" and response.content:
            content_lower = response.content.lower() if isinstance(response.content, str) else ""
            if "승인" in content_lower or "확인" in content_lower:
                if state.get("current_order_id"):
                    requires_approval = True
                    approval_data = {
                        "order_id": state.get("current_order_id"),
                        "action": "purchase_approval",
                    }

        result = {
            "messages": [response],
            "iteration_count": state.get("iteration_count", 0) + 1,
        }

        if requires_approval:
            result["requires_approval"] = True
            result["approval_data"] = approval_data

        return result

    return agent_node


def _should_continue(state: AgentState) -> str:
    """Determine if the agent should continue, use tools, or finish."""
    messages = state.get("messages", [])
    if not messages:
        return "end"

    last_message = messages[-1]

    # Check iteration limit
    if state.get("iteration_count", 0) >= settings.MAX_AGENT_ITERATIONS:
        logger.warning("Agent hit iteration limit")
        return "end"

    # If approval required, end and return to user
    if state.get("requires_approval"):
        return "end"

    # If last message has tool calls, execute tools
    if hasattr(last_message, "tool_calls") and last_message.tool_calls:
        return "tools"

    return "end"


def _self_reflect(state: AgentState) -> dict:
    """Self-reflection node — analyze if results are satisfactory."""
    messages = state.get("messages", [])
    if not messages:
        return {"should_retry": False}

    last_messages = messages[-3:]
    has_error = any(
        "error" in (getattr(m, "content", "") or "").lower()
        for m in last_messages
        if isinstance(m, ToolMessage)
    )

    if has_error and state.get("iteration_count", 0) < settings.MAX_AGENT_ITERATIONS - 2:
        return {
            "should_retry": True,
            "reflection": "이전 도구 호출에서 오류가 발생했습니다. 다른 방법을 시도해야 합니다.",
        }

    return {"should_retry": False, "reflection": None}


def _route_after_reflection(state: AgentState) -> str:
    """Route based on self-reflection result."""
    if state.get("should_retry"):
        return state.get("current_agent", "product_search")
    return "end"


def create_supervisor_graph():
    """Create the multi-agent supervisor graph."""
    workflow = StateGraph(AgentState)

    # Add nodes
    workflow.add_node("supervisor", _create_supervisor_node())
    workflow.add_node("product_search", _create_agent_node("product_search"))
    workflow.add_node("review_analysis", _create_agent_node("review_analysis"))
    workflow.add_node("cart_management", _create_agent_node("cart_management"))
    workflow.add_node("order_management", _create_agent_node("order_management"))
    workflow.add_node("customer_service", _create_agent_node("customer_service"))
    workflow.add_node("tools", ToolNode(ALL_TOOLS))
    workflow.add_node("reflect", _self_reflect)

    # Set entry point
    workflow.set_entry_point("supervisor")

    # Supervisor routes to specific agent
    workflow.add_conditional_edges(
        "supervisor",
        lambda state: state.get("next_agent", "product_search"),
        {
            "product_search": "product_search",
            "review_analysis": "review_analysis",
            "cart_management": "cart_management",
            "order_management": "order_management",
            "customer_service": "customer_service",
        },
    )

    # Each agent either calls tools or finishes
    for agent in [
        "product_search",
        "review_analysis",
        "cart_management",
        "order_management",
        "customer_service",
    ]:
        workflow.add_conditional_edges(
            agent,
            _should_continue,
            {"tools": "tools", "end": "reflect"},
        )

    # Tools return to the current agent
    workflow.add_conditional_edges(
        "tools",
        lambda state: state.get("current_agent", "product_search"),
        {
            "product_search": "product_search",
            "review_analysis": "review_analysis",
            "cart_management": "cart_management",
            "order_management": "order_management",
            "customer_service": "customer_service",
        },
    )

    # Reflection either retries or ends
    workflow.add_conditional_edges(
        "reflect",
        _route_after_reflection,
        {
            "product_search": "product_search",
            "review_analysis": "review_analysis",
            "cart_management": "cart_management",
            "order_management": "order_management",
            "customer_service": "customer_service",
            "end": END,
        },
    )

    graph = workflow.compile()
    logger.info("Supervisor graph compiled successfully")
    return graph


async def run_agent(
    graph,
    message: str,
    thread_id: str,
    user_id: str,
    context: dict,
    redis_store: RedisStore,
    is_approval: bool = False,
    approved: Optional[bool] = None,
    order_id: Optional[str] = None,
) -> dict:
    """Run the agent graph with a user message."""
    initial_state: AgentState = {
        "messages": [HumanMessage(content=message)],
        "user_id": user_id,
        "thread_id": thread_id,
        "next_agent": None,
        "current_agent": None,
        "context": context,
        "current_order_id": context.get("current_order_id") or order_id,
        "cart_items": context.get("cart_items", []),
        "search_results": None,
        "review_analysis": None,
        "inventory_status": None,
        "requires_approval": False,
        "approval_data": None,
        "error": None,
        "iteration_count": 0,
        "reflection": None,
        "should_retry": False,
    }

    # Handle approval flow
    if is_approval and approved and order_id:
        from app.tools.service_clients import approve_order
        try:
            result = await approve_order(order_id)
            return {
                "response": f"주문이 승인되어 처리를 시작합니다. 주문 ID: {order_id}\n결제 및 재고 확인이 진행됩니다.",
                "agent_used": "order_management",
                "updated_context": {"current_order_id": order_id},
            }
        except Exception as e:
            return {
                "response": f"주문 승인 중 오류가 발생했습니다: {str(e)}",
                "agent_used": "order_management",
            }
    elif is_approval and not approved:
        if order_id:
            from app.tools.service_clients import cancel_order
            try:
                await cancel_order(order_id, "사용자 거부")
            except Exception:
                pass
        return {
            "response": "주문이 취소되었습니다. 다른 도움이 필요하시면 말씀해주세요.",
            "agent_used": "order_management",
        }

    # Run the graph
    try:
        final_state = await graph.ainvoke(initial_state)

        # Extract response from the last AI message
        response = "죄송합니다, 요청을 처리하지 못했습니다. 다시 시도해 주세요."
        agent_used = final_state.get("current_agent")

        for msg in reversed(final_state.get("messages", [])):
            if isinstance(msg, AIMessage) and msg.content:
                response = msg.content
                break

        # Build updated context
        updated_context = {}
        if final_state.get("current_order_id"):
            updated_context["current_order_id"] = final_state["current_order_id"]
        if final_state.get("cart_items"):
            updated_context["cart_items"] = final_state["cart_items"]

        return {
            "response": response,
            "thread_id": thread_id,
            "requires_approval": final_state.get("requires_approval", False),
            "approval_data": final_state.get("approval_data"),
            "agent_used": agent_used,
            "updated_context": updated_context,
        }
    except Exception as e:
        logger.error("Agent graph execution failed", error=str(e))
        return {
            "response": f"처리 중 오류가 발생했습니다: {str(e)}\n다시 시도해 주세요.",
            "agent_used": None,
            "error": str(e),
        }
