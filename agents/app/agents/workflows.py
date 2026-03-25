"""Specialized agent sub-graphs packaged as tools."""

import json
from typing import Annotated, Sequence, Any
from typing_extensions import TypedDict
from langchain_core.messages import SystemMessage, HumanMessage, BaseMessage, AIMessage
from langchain_core.tools import tool
from langgraph.graph import StateGraph, END
from langgraph.graph.message import add_messages
from langgraph.prebuilt import ToolNode, InjectedState
from langchain_core.runnables.config import RunnableConfig

from app.config import settings
from app.graph.tools import (
    SEARCH_AGENT_TOOLS, REVIEW_AGENT_TOOLS, CART_AGENT_TOOLS, CUSTOMER_SERVICE_AGENT_TOOLS
)
from app.agents.specialized import get_agent_prompt, _get_llm

import structlog

logger = structlog.get_logger()

# -------------------------------------------------------------
# Common Sub-Graph State Schema
# -------------------------------------------------------------
class SubAgentState(TypedDict):
    messages: Annotated[Sequence[BaseMessage], add_messages]
    context: dict
    user_id: str
    thread_id: str

# -------------------------------------------------------------
# Generic Sub-Graph Builder
# -------------------------------------------------------------
def build_sub_agent_graph(name: str, tools: list, system_prompt: str):
    """Builds a generic sub-agent workflow using ToolNode."""
    llm = _get_llm(model=settings.OPENAI_SUB_AGENT_MODEL)
    if tools:
        llm_with_tools = llm.bind_tools(tools)
    else:
        llm_with_tools = llm

    async def call_model(state: SubAgentState):
        messages = state["messages"]
        context_msg = f"사용자 컨텍스트: {json.dumps(state.get('context', {}), ensure_ascii=False)}"
        sys_msg = SystemMessage(content=system_prompt + f"\n\n{context_msg}")
        
        response = await llm_with_tools.ainvoke([sys_msg] + list(messages))
        return {"messages": [response]}

    def should_continue(state: SubAgentState):
        messages = state["messages"]
        last_message = messages[-1]
        if last_message.tool_calls:
            return "tools"
        return END

    workflow = StateGraph(SubAgentState)
    workflow.add_node("agent", call_model)
    
    if tools:
        workflow.add_node("tools", ToolNode(tools))
        workflow.add_conditional_edges("agent", should_continue, {"tools": "tools", END: END})
        workflow.add_edge("tools", "agent")
    
    workflow.set_entry_point("agent")
    return workflow.compile()

# -------------------------------------------------------------
# Compiled Sub-Graphs
# -------------------------------------------------------------
product_search_graph = build_sub_agent_graph(
    "product_search", 
    SEARCH_AGENT_TOOLS, 
    get_agent_prompt("product_search")
)

review_analysis_graph = build_sub_agent_graph(
    "review_analysis", 
    REVIEW_AGENT_TOOLS, 
    get_agent_prompt("review_analysis")
)

# Inject Recent Products handling into Cart prompt dynamically
cart_base_prompt = get_agent_prompt("cart_management")
cart_enhanced_prompt = cart_base_prompt + """

주의: 사용자가 "첫 번째 거 담아줘", "아까 찾은거" 처럼 ID 없이 상품을 지칭하면, 제공된 사용자 컨텍스트의 `recent_products` 목록을 확인하여 정확한 product_id를 매핑하여 장바구니에 추가하세요."""

cart_management_graph = build_sub_agent_graph(
    "cart_management", 
    CART_AGENT_TOOLS, 
    cart_enhanced_prompt
)

customer_service_graph = build_sub_agent_graph(
    "customer_service", 
    CUSTOMER_SERVICE_AGENT_TOOLS, 
    get_agent_prompt("customer_service")
)


# -------------------------------------------------------------
# Sub-Graphs Exported as Outer Tools
# -------------------------------------------------------------
def _extract_final_response(state: SubAgentState) -> str:
    for msg in reversed(state["messages"]):
        if isinstance(msg, AIMessage) and msg.content:
            return msg.content
    return "요청을 처리할 수 없습니다."

@tool
async def product_search_agent_tool(
    query: str, 
    user_id: Annotated[str, InjectedState("user_id")], 
    thread_id: Annotated[str, InjectedState("thread_id")],
    ctx: Annotated[dict, InjectedState("context")],
    config: RunnableConfig
) -> str:
    """상품을 검색하는 에이전트를 호출합니다. 사용자가 상품 추천, 검색, 조회를 원할 때 이 도구를 사용합니다."""
    # load context
    logger.info("Product search Agent called")
    filtered_ctx = {"recent_products": ctx.get("recent_products", [])}
    logger.info(f"Product search Agent context: {filtered_ctx}")
    
    initial_state = {"messages": [HumanMessage(content=query)], "user_id": user_id, "thread_id": thread_id, "context": filtered_ctx}
    final_state = await product_search_graph.ainvoke(initial_state, config)
    return _extract_final_response(final_state)

@tool
async def review_analysis_agent_tool(
    query: str, 
    user_id: Annotated[str, InjectedState("user_id")], 
    thread_id: Annotated[str, InjectedState("thread_id")],
    ctx: Annotated[dict, InjectedState("context")],
    config: RunnableConfig
) -> str:
    """상품의 리뷰, 평점, 사이즈 의견 등을 분석하는 에이전트를 호출합니다."""
    logger.info("Review analysis Agent called")
    filtered_ctx = {"recent_products": ctx.get("recent_products", [])}
    
    initial_state = {"messages": [HumanMessage(content=query)], "user_id": user_id, "thread_id": thread_id, "context": filtered_ctx}
    final_state = await review_analysis_graph.ainvoke(initial_state, config)
    return _extract_final_response(final_state)

@tool
async def cart_management_agent_tool(
    query: str, 
    user_id: Annotated[str, InjectedState("user_id")], 
    thread_id: Annotated[str, InjectedState("thread_id")],
    ctx: Annotated[dict, InjectedState("context")],
    config: RunnableConfig
) -> str:
    """사용자의 장바구니에 상품을 추가/삭제하거나 예산을 관리하는 에이전트를 호출합니다. 주문, 결제 기능은 지원하지 않습니다."""
    logger.info("Cart management Agent called")
    filtered_ctx = {"recent_products": ctx.get("recent_products", [])}
    logger.info(f"Cart management Agent context: {filtered_ctx}")
    
    initial_state = {"messages": [HumanMessage(content=query)], "user_id": user_id, "thread_id": thread_id, "context": filtered_ctx}
    final_state = await cart_management_graph.ainvoke(initial_state, config)
    return _extract_final_response(final_state)

@tool
async def customer_service_agent_tool(
    query: str, 
    user_id: Annotated[str, InjectedState("user_id")], 
    thread_id: Annotated[str, InjectedState("thread_id")],
    ctx: Annotated[dict, InjectedState("context")],
    config: RunnableConfig
) -> str:
    """주문 상태 조회, 환불/취소 절차 문의, 각종 쇼핑몰 정책 정보를 답변하는 CS 에이전트를 호출합니다."""
    logger.info("Customer service Agent called")
    filtered_ctx = {"recent_products": ctx.get("recent_products", [])}
    
    initial_state = {"messages": [HumanMessage(content=query)], "user_id": user_id, "thread_id": thread_id, "context": filtered_ctx}
    final_state = await customer_service_graph.ainvoke(initial_state, config)
    return _extract_final_response(final_state)

AGENT_TOOLS = [
    product_search_agent_tool,
    review_analysis_agent_tool,
    cart_management_agent_tool,
    customer_service_agent_tool
]
