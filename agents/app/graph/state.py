"""LangGraph agent state definition."""

from typing import Annotated, Any, Optional, Sequence
from typing_extensions import TypedDict

from langchain_core.messages import BaseMessage
from langgraph.graph.message import add_messages


class AgentState(TypedDict):
    """Shared state across all agents in the graph."""

    # Conversation messages
    messages: Annotated[Sequence[BaseMessage], add_messages]

    # User context
    user_id: str
    thread_id: str

    # Routing
    next_agent: Optional[str]
    current_agent: Optional[str]

    # Conversation context (persisted in Redis)
    context: dict

    # Cart/Order state
    current_order_id: Optional[str]
    cart_items: list

    # Agent working memory
    search_results: Optional[list]
    review_analysis: Optional[dict]
    inventory_status: Optional[dict]

    # Control flow
    requires_approval: bool
    approval_data: Optional[dict]
    error: Optional[str]
    iteration_count: int

    # Self-reflection
    reflection: Optional[str]
    should_retry: bool
