"""LangGraph agent state definition."""

from typing import Annotated, Any, Optional, Sequence
from typing_extensions import TypedDict

from langchain_core.messages import BaseMessage
from langgraph.graph.message import add_messages


class AgentState(TypedDict):
    """Shared state across the supervisor agent in the graph."""

    # Conversation messages
    messages: Annotated[Sequence[BaseMessage], add_messages]

    # User context
    user_id: str
    thread_id: str

    # Conversation context (persisted in Redis)
    context: dict

