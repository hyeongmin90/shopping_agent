"""Product RAG — Hybrid search with keyword-first strategy.

Search priority:
1. Keyword search via OpenSearch (primary)
2. Semantic search via Qdrant (supplementary / fallback)
Results are merged and deduplicated.
"""

import structlog

from app.config import settings
from app.rag.embeddings import generate_embedding
from app.rag.opensearch_store import keyword_search
from app.rag.qdrant_store import search_vectors

logger = structlog.get_logger()


async def search_products_rag(
    query: str,
    category: str | None = None,
    brand: str | None = None,
    min_price: int | None = None,
    max_price: int | None = None,
    limit: int = 10,
) -> list[dict]:
    """Hybrid product search: keyword-first with vector supplement.

    Args:
        query: User search query.
        category: Optional category filter.
        brand: Optional brand filter.
        min_price: Optional minimum price filter.
        max_price: Optional maximum price filter.
        limit: Maximum number of results.

    Returns:
        Merged, deduplicated list of product results.
    """
    keyword_limit = min(limit, 8)
    vector_limit = min(limit, 5)

    # --- 1. Keyword search (primary) ---
    os_filters = {}
    if category:
        os_filters["category"] = category
    if brand:
        os_filters["brand"] = brand

    keyword_results = []
    try:
        raw_hits = await keyword_search(
            index_name=settings.OPENSEARCH_PRODUCT_INDEX,
            query=query,
            fields=["name^3", "description^2", "brand", "category_name", "tags"],
            size=keyword_limit,
            filters=os_filters if os_filters else None,
        )
        keyword_results = [
            {
                "product_id": hit["_source"].get("product_id", hit["_id"]),
                "name": hit["_source"].get("name", ""),
                "description": hit["_source"].get("description", ""),
                "brand": hit["_source"].get("brand", ""),
                "category": hit["_source"].get("category_name", ""),
                "base_price": hit["_source"].get("base_price", 0),
                "score": hit["_score"],
                "source": "keyword",
            }
            for hit in raw_hits
        ]
        logger.info(
            "product_rag_keyword_results",
            query=query,
            count=len(keyword_results),
        )
    except Exception as e:
        logger.error("product_rag_keyword_error", error=str(e))

    # --- 2. Vector search (supplementary) ---
    vector_results = []
    try:
        query_vector = await generate_embedding(query)

        qdrant_filter = {}
        if category:
            qdrant_filter["category"] = category
        if brand:
            qdrant_filter["brand"] = brand

        raw_vectors = await search_vectors(
            collection_name=settings.QDRANT_PRODUCT_COLLECTION,
            query_vector=query_vector,
            limit=vector_limit,
            score_threshold=0.5,
            filter_conditions=qdrant_filter if qdrant_filter else None,
        )
        vector_results = [
            {
                "product_id": hit["payload"].get("product_id", ""),
                "name": hit["payload"].get("name", ""),
                "description": hit["payload"].get("description", ""),
                "brand": hit["payload"].get("brand", ""),
                "category": hit["payload"].get("category_name", ""),
                "base_price": hit["payload"].get("base_price", 0),
                "score": hit["score"],
                "source": "vector",
            }
            for hit in raw_vectors
        ]
        logger.info(
            "product_rag_vector_results",
            query=query,
            count=len(vector_results),
        )
    except Exception as e:
        logger.error("product_rag_vector_error", error=str(e))

    # --- 3. Merge & deduplicate (keyword results first) ---
    merged = _merge_results(keyword_results, vector_results, limit)

    # --- 4. Apply price filters post-merge ---
    if min_price is not None:
        merged = [r for r in merged if r.get("base_price", 0) >= min_price]
    if max_price is not None:
        merged = [r for r in merged if r.get("base_price", 0) <= max_price]

    return merged


def _merge_results(
    primary: list[dict],
    secondary: list[dict],
    limit: int,
) -> list[dict]:
    """Merge two result lists, deduplicating by product_id.

    Primary results take precedence and appear first.
    """
    seen_ids: set[str] = set()
    merged: list[dict] = []

    for result in primary:
        pid = result.get("product_id", "")
        if pid and pid not in seen_ids:
            seen_ids.add(pid)
            merged.append(result)

    for result in secondary:
        pid = result.get("product_id", "")
        if pid and pid not in seen_ids:
            seen_ids.add(pid)
            merged.append(result)

    return merged[:limit]
