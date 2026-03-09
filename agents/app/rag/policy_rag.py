"""Policy RAG — Balanced hybrid search.

Search strategy:
1. Both keyword (OpenSearch) and semantic (Qdrant) searches run in parallel
2. Results are scored and merged with equal weight
3. Best of both worlds: exact term matches + semantic understanding
"""

import asyncio

import structlog

from app.config import settings
from app.rag.embeddings import generate_embedding
from app.rag.pgvector_store import keyword_search, search_vectors

logger = structlog.get_logger()


async def search_policies_rag(
    query: str,
    category: str | None = None,
    limit: int = 5,
) -> list[dict]:
    """Balanced hybrid policy search.

    Runs keyword and vector search in parallel, then merges results
    with normalized scoring to combine exact-match and semantic relevance.

    Args:
        query: User query about policies (e.g., "환불 기한이 어떻게 되나요?").
        category: Optional policy category filter (e.g., "refund", "shipping").
        limit: Maximum number of results.

    Returns:
        Merged, ranked list of policy documents.
    """
    # Run both searches concurrently
    keyword_task = _keyword_search(query, category, limit)
    vector_task = _vector_search(query, category, limit)

    keyword_results, vector_results = await asyncio.gather(
        keyword_task, vector_task, return_exceptions=True
    )

    # Handle exceptions gracefully
    if isinstance(keyword_results, Exception):
        logger.error("policy_rag_keyword_error", error=str(keyword_results))
        keyword_results = []
    if isinstance(vector_results, Exception):
        logger.error("policy_rag_vector_error", error=str(vector_results))
        vector_results = []

    # Merge with balanced scoring
    merged = _balanced_merge(keyword_results, vector_results, limit)

    logger.info(
        "policy_rag_results",
        query=query,
        keyword_count=len(keyword_results) if isinstance(keyword_results, list) else 0,
        vector_count=len(vector_results) if isinstance(vector_results, list) else 0,
        merged_count=len(merged),
    )

    return merged


async def _keyword_search(
    query: str,
    category: str | None,
    limit: int,
) -> list[dict]:
    """Keyword search for policy documents."""
    os_filters = {}
    if category:
        os_filters["category"] = category

    raw_hits = await keyword_search(
        table_name=settings.POSTGRES_POLICY_TABLE,
        query=query,
        limit=limit,
        filter_conditions=os_filters if os_filters else None,
    )

    return [
        {
            "policy_id": hit["payload"].get("policy_id", ""),
            "title": hit["payload"].get("title", ""),
            "content": hit["payload"].get("content", ""),
            "category": hit["payload"].get("category", ""),
            "score": hit["score"],
            "source": "keyword",
        }
        for hit in raw_hits
    ]


async def _vector_search(
    query: str,
    category: str | None,
    limit: int,
) -> list[dict]:
    """Semantic vector search for policy documents."""
    query_vector = await generate_embedding(query)

    vector_filter = {}
    if category:
        vector_filter["category"] = category

    raw_vectors = await search_vectors(
        table_name=settings.POSTGRES_POLICY_TABLE,
        query_vector=query_vector,
        limit=limit,
        score_threshold=0.4,
        filter_conditions=vector_filter if vector_filter else None,
    )

    return [
        {
            "policy_id": hit["payload"].get("policy_id", ""),
            "title": hit["payload"].get("title", ""),
            "content": hit["payload"].get("content", ""),
            "category": hit["payload"].get("category", ""),
            "score": hit["score"],
            "source": "vector",
        }
        for hit in raw_vectors
    ]


def _balanced_merge(
    keyword_results: list[dict],
    vector_results: list[dict],
    limit: int,
) -> list[dict]:
    """Merge keyword and vector results with balanced scoring.

    Each result gets a normalized score (0-1) within its source,
    then a combined score = 0.5 * keyword_norm + 0.5 * vector_norm.
    Deduplicates by policy_id.
    """
    scored: dict[str, dict] = {}

    # Normalize keyword scores
    if keyword_results:
        max_kw = max(r["score"] for r in keyword_results)
        for r in keyword_results:
            pid = r["policy_id"]
            norm_score = r["score"] / max_kw if max_kw > 0 else 0
            scored[pid] = {
                **r,
                "keyword_score": norm_score,
                "vector_score": 0.0,
            }

    # Normalize vector scores
    if vector_results:
        max_vec = max(r["score"] for r in vector_results)
        for r in vector_results:
            pid = r["policy_id"]
            norm_score = r["score"] / max_vec if max_vec > 0 else 0
            if pid in scored:
                scored[pid]["vector_score"] = norm_score
                scored[pid]["source"] = "hybrid"
            else:
                scored[pid] = {
                    **r,
                    "keyword_score": 0.0,
                    "vector_score": norm_score,
                }

    # Calculate combined score and sort
    for entry in scored.values():
        entry["combined_score"] = (
            0.5 * entry["keyword_score"] + 0.5 * entry["vector_score"]
        )
        # Clean up internal scoring fields for output
        entry["score"] = entry["combined_score"]

    results = sorted(scored.values(), key=lambda x: x["combined_score"], reverse=True)

    # Remove internal scoring fields
    for r in results:
        r.pop("keyword_score", None)
        r.pop("vector_score", None)
        r.pop("combined_score", None)

    return results[:limit]
