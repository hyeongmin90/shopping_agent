"""Chat API endpoint - main entry point for user interactions."""

import uuid
from typing import Optional

from fastapi import APIRouter, HTTPException, Request
from pydantic import BaseModel, Field

from app.graph.supervisor import run_agent
from app.memory.redis_store import RedisStore
from app.memory.pg_user_store import Context

import structlog

logger = structlog.get_logger()

router = APIRouter()


class ChatRequest(BaseModel):
    """Chat request from user."""

    message: str = Field(..., description="User message", min_length=1)
    user_id: str = Field(default_factory=lambda: str(uuid.uuid4()), description="User ID")
    thread_id: Optional[str] = Field(None, description="Conversation thread ID for context continuity")


class ChatResponse(BaseModel):
    """Chat response to user."""

    message: str = Field(..., description="Agent response")
    thread_id: str = Field(..., description="Thread ID for continuing conversation")
    agent_used: Optional[str] = Field(None, description="Which agent handled the request")
    metadata: Optional[dict] = Field(None, description="Additional metadata")


@router.post("/chat", response_model=ChatResponse)
async def chat(request: Request, body: ChatRequest):
    """Process a user chat message through the agent system."""
    thread_id = body.thread_id or str(uuid.uuid4())

    logger.info(
        "Chat request received",
        user_id=body.user_id,
        thread_id=thread_id,
        message_preview=body.message[:100],
    )

    try:
        redis_store: RedisStore = request.app.state.redis

        # Load existing context (product memory, budget hints, etc.)
        state_context = await redis_store.get_context(thread_id) or {}
        state_context["user_id"] = body.user_id
        user_ctx = Context(user_id=body.user_id)

        # Run agent - conversation history is managed inside run_agent via Redis
        result = await run_agent(
            agent=request.app.state.graph,
            message=body.message,
            thread_id=thread_id,
            user_id=body.user_id,
            context=state_context,
            user_ctx=user_ctx,
            redis_store=redis_store,
        )

        # Persist any context updates (e.g. recent_products from search)
        if result.get("updated_context"):
            await redis_store.update_context(thread_id, result["updated_context"])

        return ChatResponse(
            message=result["response"],
            thread_id=thread_id,
            agent_used=result.get("agent_used"),
            metadata=result.get("metadata"),
        )
    except Exception as e:
        logger.error("Chat processing failed", error=str(e), thread_id=thread_id)
        raise HTTPException(status_code=500, detail=f"Agent processing failed: {str(e)}")
