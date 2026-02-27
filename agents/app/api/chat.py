"""Chat API endpoint - main entry point for user interactions."""

import uuid
from typing import Optional

from fastapi import APIRouter, HTTPException, Request
from pydantic import BaseModel, Field

from app.graph.supervisor import run_agent
from app.memory.redis_store import RedisStore

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
    requires_approval: bool = Field(False, description="Whether user approval is needed")
    approval_data: Optional[dict] = Field(None, description="Data requiring approval")
    agent_used: Optional[str] = Field(None, description="Which agent handled the request")
    metadata: Optional[dict] = Field(None, description="Additional metadata")


class ApprovalRequest(BaseModel):
    """User approval for a purchase."""

    thread_id: str = Field(..., description="Thread ID")
    user_id: str = Field(..., description="User ID")
    approved: bool = Field(..., description="Whether user approves")
    order_id: Optional[str] = Field(None, description="Order ID to approve")


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
        graph = request.app.state.graph

        # Load existing context
        context = await redis_store.get_context(thread_id) or {}
        context["user_id"] = body.user_id

        # Run agent
        result = await run_agent(
            graph=graph,
            message=body.message,
            thread_id=thread_id,
            user_id=body.user_id,
            context=context,
            redis_store=redis_store,
        )

        # Save updated context
        if result.get("updated_context"):
            await redis_store.update_context(thread_id, result["updated_context"])

        return ChatResponse(
            message=result["response"],
            thread_id=thread_id,
            requires_approval=result.get("requires_approval", False),
            approval_data=result.get("approval_data"),
            agent_used=result.get("agent_used"),
            metadata=result.get("metadata"),
        )
    except Exception as e:
        logger.error("Chat processing failed", error=str(e), thread_id=thread_id)
        raise HTTPException(status_code=500, detail=f"Agent processing failed: {str(e)}")


@router.post("/approve", response_model=ChatResponse)
async def approve_purchase(request: Request, body: ApprovalRequest):
    """Handle user approval/rejection for a purchase."""
    logger.info(
        "Approval request",
        user_id=body.user_id,
        thread_id=body.thread_id,
        approved=body.approved,
    )

    try:
        redis_store: RedisStore = request.app.state.redis
        graph = request.app.state.graph

        context = await redis_store.get_context(body.thread_id) or {}
        context["user_id"] = body.user_id

        if body.approved:
            message = f"사용자가 주문을 승인했습니다. 주문 ID: {body.order_id}"
        else:
            message = "사용자가 주문을 거부했습니다. 주문을 취소해주세요."

        result = await run_agent(
            graph=graph,
            message=message,
            thread_id=body.thread_id,
            user_id=body.user_id,
            context=context,
            redis_store=redis_store,
            is_approval=True,
            approved=body.approved,
            order_id=body.order_id,
        )

        if result.get("updated_context"):
            await redis_store.update_context(body.thread_id, result["updated_context"])

        return ChatResponse(
            message=result["response"],
            thread_id=body.thread_id,
            agent_used=result.get("agent_used"),
        )
    except Exception as e:
        logger.error("Approval processing failed", error=str(e))
        raise HTTPException(status_code=500, detail=f"Approval processing failed: {str(e)}")
