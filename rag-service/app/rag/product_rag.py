"""Product RAG — Semantic product search using pgvector."""

import structlog

from app.config import settings
from app.rag.embeddings import generate_embedding
from app.rag.pgvector_store import search_vectors

logger = structlog.get_logger()


async def search_products_rag(
    query: str,
    limit: int = 10,
) -> list[dict]:
    """Semantic product search — finds products matching user intent.

    Understands natural language queries like "따뜻한 겨울 아우터" or
    "가성비 좋은 운동화" even without exact keyword matches.

    Args:
        query: User search query (natural language).
        limit: Maximum number of results.

    Returns:
        List of product results with metadata.
    """
    try:
        query_vector = await generate_embedding(query)

        raw_vectors = await search_vectors(
            table_name=settings.POSTGRES_PRODUCT_TABLE,
            query_vector=query_vector,
            limit=limit,
            score_threshold=0.4,
        )

        results = []
        for hit in raw_vectors:
            payload = hit["payload"]
            results.append(
                {
                    "id": payload.get("product_id", ""),
                    "name": payload.get("name", ""),
                    "description": payload.get("description", ""),
                    "brand": payload.get("brand", ""),
                    "category_name": payload.get("category_name", ""),
                    "base_price": payload.get("base_price"),
                    "currency": payload.get("currency", "KRW"),
                    "status": payload.get("status", ""),
                    "score": hit["score"],
                }
            )

        logger.info(
            "product_rag_search",
            query=query,
            count=len(results),
        )
        return results

    except Exception as e:
        logger.error("product_rag_search_error", error=str(e))
        return []
