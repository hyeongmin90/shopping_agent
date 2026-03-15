"""Embedding service using OpenAI text-embedding-3-small."""

import structlog
from openai import AsyncOpenAI

from app.config import settings

logger = structlog.get_logger()

_client: AsyncOpenAI | None = None


def get_openai_client() -> AsyncOpenAI:
    """Get or create the OpenAI async client."""
    global _client
    if _client is None:
        _client = AsyncOpenAI(api_key=settings.OPENAI_API_KEY)
    return _client


async def generate_embedding(text: str) -> list[float]:
    """Generate embedding vector for a single text.

    Args:
        text: Input text to embed.

    Returns:
        List of floats representing the embedding vector.
    """
    client = get_openai_client()
    response = await client.embeddings.create(
        model=settings.OPENAI_EMBEDDING_MODEL,
        input=text,
    )
    return response.data[0].embedding


async def generate_embeddings(texts: list[str]) -> list[list[float]]:
    """Generate embedding vectors for multiple texts in a single batch.

    Args:
        texts: List of input texts to embed.

    Returns:
        List of embedding vectors, one per input text.
    """
    if not texts:
        return []

    client = get_openai_client()
    response = await client.embeddings.create(
        model=settings.OPENAI_EMBEDDING_MODEL,
        input=texts,
    )
    # Sort by index to maintain order
    sorted_data = sorted(response.data, key=lambda x: x.index)
    return [item.embedding for item in sorted_data]
