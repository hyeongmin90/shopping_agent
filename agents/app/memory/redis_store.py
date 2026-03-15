"""Redis store for agent state and conversation memory."""

from inspect import isawaitable
import json
from typing import Any, Awaitable, cast

import redis.asyncio as aioredis

from app.config import settings

import structlog

logger = structlog.get_logger()


class RedisStore:
    """Redis-backed store for agent checkpoints and conversation context."""

    def __init__(self):
        self._redis: aioredis.Redis | None = None

    @property
    def _redis_client(self) -> aioredis.Redis:
        if self._redis is None:
            raise RuntimeError("Redis client is not initialized")
        return self._redis

    async def initialize(self):
        """Initialize Redis connection."""
        self._redis = aioredis.from_url(
            settings.REDIS_URL,
            encoding="utf-8",
            decode_responses=True,
        )
        try:
            ping_result = self._redis.ping()
            if isawaitable(ping_result):
                await cast(Awaitable[bool], ping_result)
            logger.info("Redis connection established", url=settings.REDIS_URL)
        except Exception as e:
            logger.error("Failed to connect to Redis", error=str(e))
            raise

    async def close(self):
        """Close Redis connection."""
        if self._redis:
            await self._redis.close()
            logger.info("Redis connection closed")

    # ---- Conversation Context ----

    async def save_context(self, thread_id: str, context: dict[str, Any], ttl: int = 3600):
        """Save conversation context."""
        redis = self._redis_client
        key = f"agent:context:{thread_id}"
        await redis.set(key, json.dumps(context, ensure_ascii=False), ex=ttl)

    async def get_context(self, thread_id: str) -> dict[str, Any] | None:
        """Get conversation context."""
        redis = self._redis_client
        key = f"agent:context:{thread_id}"
        data = await redis.get(key)
        if data:
            return json.loads(data)
        return None

    async def update_context(self, thread_id: str, updates: dict[str, Any], ttl: int = 3600):
        """Merge updates into existing context."""
        context = await self.get_context(thread_id) or {}
        context.update(updates)
        await self.save_context(thread_id, context, ttl)

    # ---- Conversation History (Messages) ----

    async def load_messages(self, thread_id: str) -> list[dict[str, Any]]:
        """Load serialized message history for a thread."""
        redis = self._redis_client
        key = f"agent:messages:{thread_id}"
        data = await redis.get(key)
        if data:
            return json.loads(data)
        return []

    async def save_messages(self, thread_id: str, messages: list[dict[str, Any]], ttl: int = 3600):
        """Persist serialized message history with TTL (auto-expires conversation)."""
        redis = self._redis_client
        key = f"agent:messages:{thread_id}"
        await redis.set(key, json.dumps(messages, ensure_ascii=False), ex=ttl)

    async def clear_messages(self, thread_id: str):
        """Delete the conversation history for a thread."""
        redis = self._redis_client
        key = f"agent:messages:{thread_id}"
        await redis.delete(key)
        logger.info("Conversation history cleared", thread_id=thread_id)

    # ---- Cart State ----

    async def save_cart(self, user_id: str, cart: dict[str, Any], ttl: int = 7200):
        """Save cart state."""
        redis = self._redis_client
        key = f"agent:cart:{user_id}"
        await redis.set(key, json.dumps(cart, ensure_ascii=False), ex=ttl)

    async def get_cart(self, user_id: str) -> dict[str, Any] | None:
        """Get cart state."""
        redis = self._redis_client
        key = f"agent:cart:{user_id}"
        data = await redis.get(key)
        if data:
            return json.loads(data)
        return None

    # ---- Generic KV ----

    async def set(self, key: str, value: Any, ttl: int | None = None):
        """Set a key-value pair."""
        redis = self._redis_client
        serialized = json.dumps(value, ensure_ascii=False) if not isinstance(value, str) else value
        if ttl:
            await redis.set(key, serialized, ex=ttl)
        else:
            await redis.set(key, serialized)

    async def get(self, key: str) -> str | None:
        """Get a value by key."""
        redis = self._redis_client
        return await redis.get(key)

    async def delete(self, key: str):
        """Delete a key."""
        redis = self._redis_client
        await redis.delete(key)
