"""Redis store for agent state and conversation memory."""

import json
from typing import Any, Optional

import redis.asyncio as aioredis

from app.config import settings

import structlog

logger = structlog.get_logger()


class RedisStore:
    """Redis-backed store for agent checkpoints and conversation context."""

    def __init__(self):
        self._redis: Optional[aioredis.Redis] = None

    async def initialize(self):
        """Initialize Redis connection."""
        self._redis = aioredis.from_url(
            settings.REDIS_URL,
            encoding="utf-8",
            decode_responses=True,
        )
        try:
            await self._redis.ping()
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

    async def save_context(self, thread_id: str, context: dict, ttl: int = 3600):
        """Save conversation context."""
        key = f"agent:context:{thread_id}"
        await self._redis.set(key, json.dumps(context, ensure_ascii=False), ex=ttl)

    async def get_context(self, thread_id: str) -> Optional[dict]:
        """Get conversation context."""
        key = f"agent:context:{thread_id}"
        data = await self._redis.get(key)
        if data:
            return json.loads(data)
        return None

    async def update_context(self, thread_id: str, updates: dict, ttl: int = 3600):
        """Merge updates into existing context."""
        context = await self.get_context(thread_id) or {}
        context.update(updates)
        await self.save_context(thread_id, context, ttl)

    # ---- Cart State ----

    async def save_cart(self, user_id: str, cart: dict, ttl: int = 7200):
        """Save cart state."""
        key = f"agent:cart:{user_id}"
        await self._redis.set(key, json.dumps(cart, ensure_ascii=False), ex=ttl)

    async def get_cart(self, user_id: str) -> Optional[dict]:
        """Get cart state."""
        key = f"agent:cart:{user_id}"
        data = await self._redis.get(key)
        if data:
            return json.loads(data)
        return None

    # ---- Approval Tokens ----

    async def save_approval_token(
        self, token: str, order_data: dict, ttl: int = 300
    ):
        """Save pending approval token."""
        key = f"agent:approval:{token}"
        await self._redis.set(key, json.dumps(order_data, ensure_ascii=False), ex=ttl)

    async def get_approval_token(self, token: str) -> Optional[dict]:
        """Get and consume approval token."""
        key = f"agent:approval:{token}"
        data = await self._redis.get(key)
        if data:
            await self._redis.delete(key)
            return json.loads(data)
        return None

    # ---- Generic KV ----

    async def set(self, key: str, value: Any, ttl: Optional[int] = None):
        """Set a key-value pair."""
        serialized = json.dumps(value, ensure_ascii=False) if not isinstance(value, str) else value
        if ttl:
            await self._redis.set(key, serialized, ex=ttl)
        else:
            await self._redis.set(key, serialized)

    async def get(self, key: str) -> Optional[str]:
        """Get a value by key."""
        return await self._redis.get(key)

    async def delete(self, key: str):
        """Delete a key."""
        await self._redis.delete(key)
