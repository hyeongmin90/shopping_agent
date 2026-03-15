"""Review RAG — Vector-first semantic search.

Search priority:
1. Semantic search via Qdrant (primary) — understands nuance in review queries
2. Results enriched with payload metadata
"""

import structlog

from app.config import settings
from app.rag.embeddings import generate_embedding
from app.rag.pgvector_store import search_vectors

logger = structlog.get_logger()


async def search_reviews_rag(
    query: str,
    product_id: str | None = None,
    min_rating: int | None = None,
    verified_only: bool = False,
    limit: int = 10,
) -> list[dict[str, object]]:
    """Semantic review search — vector-first strategy.

    Uses embedding similarity to find reviews matching the user's intent,
    e.g., "여름에 입기 시원한가요?" will find reviews mentioning breathability,
    fabric thickness, and summer comfort even without exact keyword matches.

    Args:
        query: User search query (natural language).
        product_id: Optional product ID filter.
        min_rating: Optional minimum rating filter (1-5).
        verified_only: If True, only return verified purchase reviews.
        limit: Maximum number of results.

    Returns:
        List of review results with metadata.
    """
    vector_results = []
    try:
        query_vector = await generate_embedding(query)

        # Build pgvector filter conditions
        vector_filter = {}
        if product_id:
            vector_filter["product_id"] = product_id
        if verified_only:
            vector_filter["verified_purchase"] = True

        raw_vectors = await search_vectors(
            table_name=settings.POSTGRES_REVIEW_TABLE,
            query_vector=query_vector,
            limit=limit,
            score_threshold=0.4,
            filter_conditions=vector_filter if vector_filter else None,
        )

        for hit in raw_vectors:
            payload = hit["payload"]
            rating = payload.get("rating", 0)

            # Apply min_rating filter post-search
            if min_rating is not None and rating < min_rating:
                continue

            vector_results.append(
                {
                    "review_id": payload.get("review_id", ""),
                    "product_id": payload.get("product_id", ""),
                    "product_name": payload.get("product_name", ""),
                    "rating": rating,
                    "title": payload.get("title", ""),
                    "content": payload.get("content", ""),
                    "quality_rating": payload.get("quality_rating", 0),
                    "verified_purchase": payload.get("verified_purchase", False),
                    "helpful_count": payload.get("helpful_count", 0),
                    "score": hit["score"],
                    "source": "vector",
                }
            )

        logger.info(
            "review_rag_vector_results",
            query=query,
            product_id=product_id,
            count=len(vector_results),
        )
    except Exception as e:
        logger.error("review_rag_vector_error", error=str(e))

    return vector_results[:limit]
