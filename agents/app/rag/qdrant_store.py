"""Qdrant vector store client for RAG collections."""

import structlog
from qdrant_client import QdrantClient, models

from app.config import settings

logger = structlog.get_logger()

_client: QdrantClient | None = None


def get_qdrant_client() -> QdrantClient:
    """Get or create the Qdrant client (sync, thread-safe)."""
    global _client
    if _client is None:
        _client = QdrantClient(
            host=settings.QDRANT_HOST,
            port=settings.QDRANT_PORT,
            timeout=30,
        )
        logger.info(
            "qdrant_client_initialized",
            host=settings.QDRANT_HOST,
            port=settings.QDRANT_PORT,
        )
    return _client


async def ensure_collections() -> None:
    """Create Qdrant collections if they don't already exist."""
    client = get_qdrant_client()
    dimension = settings.OPENAI_EMBEDDING_DIMENSION

    collections_config = {
        settings.QDRANT_PRODUCT_COLLECTION: {
            "description": "Product descriptions and details for semantic search",
        },
        settings.QDRANT_REVIEW_COLLECTION: {
            "description": "Product reviews for semantic search",
        },
        settings.QDRANT_POLICY_COLLECTION: {
            "description": "Shopping policies (refund, terms, shipping, etc.)",
        },
    }

    existing = {c.name for c in client.get_collections().collections}

    for name, meta in collections_config.items():
        if name not in existing:
            client.create_collection(
                collection_name=name,
                vectors_config=models.VectorParams(
                    size=dimension,
                    distance=models.Distance.COSINE,
                ),
            )
            logger.info("qdrant_collection_created", collection=name, **meta)
        else:
            logger.info("qdrant_collection_exists", collection=name)


async def upsert_vectors(
    collection_name: str,
    ids: list[str],
    vectors: list[list[float]],
    payloads: list[dict],
) -> None:
    """Upsert vectors with payloads into a Qdrant collection.

    Args:
        collection_name: Target collection name.
        ids: List of point IDs (will be hashed to int).
        vectors: List of embedding vectors.
        payloads: List of metadata dicts.
    """
    client = get_qdrant_client()
    points = [
        models.PointStruct(
            id=_str_to_int_id(point_id),
            vector=vector,
            payload=payload,
        )
        for point_id, vector, payload in zip(ids, vectors, payloads)
    ]
    client.upsert(collection_name=collection_name, points=points)
    logger.info(
        "qdrant_vectors_upserted",
        collection=collection_name,
        count=len(points),
    )


async def search_vectors(
    collection_name: str,
    query_vector: list[float],
    limit: int = 5,
    score_threshold: float | None = None,
    filter_conditions: dict | None = None,
) -> list[dict]:
    """Search for similar vectors in a Qdrant collection.

    Args:
        collection_name: Collection to search.
        query_vector: Query embedding vector.
        limit: Maximum number of results.
        score_threshold: Minimum similarity score (0-1 for cosine).
        filter_conditions: Optional Qdrant filter dict.

    Returns:
        List of dicts with 'id', 'score', and 'payload' keys.
    """
    client = get_qdrant_client()

    query_filter = None
    if filter_conditions:
        must_conditions = []
        for key, value in filter_conditions.items():
            if isinstance(value, list):
                must_conditions.append(
                    models.FieldCondition(
                        key=key,
                        match=models.MatchAny(any=value),
                    )
                )
            else:
                must_conditions.append(
                    models.FieldCondition(
                        key=key,
                        match=models.MatchValue(value=value),
                    )
                )
        query_filter = models.Filter(must=must_conditions)

    results = client.search(
        collection_name=collection_name,
        query_vector=query_vector,
        limit=limit,
        score_threshold=score_threshold,
        query_filter=query_filter,
    )

    return [
        {
            "id": str(hit.id),
            "score": hit.score,
            "payload": hit.payload,
        }
        for hit in results
    ]


def _str_to_int_id(string_id: str) -> int:
    """Convert a string ID to a positive integer hash for Qdrant."""
    return abs(hash(string_id)) % (2**63)
