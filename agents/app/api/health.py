"""Health check endpoint."""

from fastapi import APIRouter, Request

router = APIRouter()


@router.get("/health")
async def health_check(request: Request):
    """Health check endpoint."""
    redis_ok = False
    try:
        redis_store = request.app.state.redis
        if redis_store._redis:
            await redis_store._redis.ping()
            redis_ok = True
    except Exception:
        pass

    return {
        "status": "UP",
        "components": {
            "redis": "UP" if redis_ok else "DOWN",
        },
    }
