"""PostgreSQL vector store client for RAG collections using pgvector."""

import json
import structlog
import asyncpg
from pgvector.asyncpg import register_vector

from app.config import settings

logger = structlog.get_logger()

_pool: asyncpg.Pool | None = None


async def get_pool() -> asyncpg.Pool:
    """Get or create the asyncpg connection pool."""
    global _pool
    if _pool is None:
        _pool = await asyncpg.create_pool(
            settings.POSTGRES_AGENT_URL,
            min_size=1,
            max_size=10,
        )

        async with _pool.acquire() as conn:
            await register_vector(conn)
            
        logger.info(
            "pgvector_pool_initialized",
            url=settings.POSTGRES_AGENT_URL.split("@")[1] if "@" in settings.POSTGRES_AGENT_URL else "localhost",
        )
    return _pool



async def upsert_vectors(
    table_name: str,
    ids: list[str],
    vectors: list[list[float]],
    payloads: list[dict],
    search_texts: list[str] | None = None,
) -> None:
    """Upsert vectors with payloads into a pgvector table.

    Args:
        table_name: Target table name.
        ids: List of point IDs (string).
        vectors: List of embedding vectors.
        payloads: List of metadata dicts.
        search_texts: Optional List of text strings for keyword search (combined into tsvector).
    """
    if not ids:
        return

    pool = await get_pool()
    
    # Prepare list of tuples for executemany
    records = []
    for i, (point_id, vector, payload) in enumerate(zip(ids, vectors, payloads)):
        search_text = search_texts[i] if search_texts else ""
        records.append((point_id, vector, json.dumps(payload), search_text))
        
    query = f"""
        INSERT INTO {table_name} (id, embedding, metadata, fts)
        VALUES ($1, $2, $3::jsonb, to_tsvector('simple', $4))
        ON CONFLICT (id) DO UPDATE SET
            embedding = EXCLUDED.embedding,
            metadata = EXCLUDED.metadata,
            fts = EXCLUDED.fts;
    """

    async with pool.acquire() as conn:
        await conn.executemany(query, records)
        
    logger.info(
        "pgvector_vectors_upserted",
        table=table_name,
        count=len(ids),
    )


async def search_vectors(
    table_name: str,
    query_vector: list[float],
    limit: int = 5,
    score_threshold: float | None = None,
    filter_conditions: dict | None = None,
) -> list[dict]:
    """Search for similar vectors in a pgvector table.

    Args:
        table_name: Table to search.
        query_vector: Query embedding vector.
        limit: Maximum number of results.
        score_threshold: Minimum similarity score (0-1 for cosine).
        filter_conditions: Optional metadata filter dict.

    Returns:
        List of dicts with 'id', 'score', and 'payload' keys.
    """
    pool = await get_pool()
    
    where_clauses = []
    args = [query_vector]
    arg_idx = 2
    
    if filter_conditions:
        for key, value in filter_conditions.items():
            if isinstance(value, list):
                where_clauses.append(f"metadata->>'{key}' = ANY(${arg_idx}::text[])")
                args.append([str(v) for v in value])
                arg_idx += 1
            else:
                where_clauses.append(f"metadata->>'{key}' = ${arg_idx}")
                args.append(str(value))
                arg_idx += 1
                
    if score_threshold is not None:
        where_clauses.append(f"1 - (embedding <=> $1) >= ${arg_idx}")
        args.append(score_threshold)
        arg_idx += 1
        
    where_sql = ""
    if where_clauses:
        where_sql = "WHERE " + " AND ".join(where_clauses)
        
    query = f"""
        SELECT id, metadata, 1 - (embedding <=> $1) AS score
        FROM {table_name}
        {where_sql}
        ORDER BY embedding <=> $1
        LIMIT ${arg_idx}
    """
    args.append(limit)
    
    async with pool.acquire() as conn:
        records = await conn.fetch(query, *args)
        
    return [
        {
            "id": record["id"],
            "score": record["score"],
            "payload": json.loads(record["metadata"]) if isinstance(record["metadata"], str) else record["metadata"],
        }
        for record in records
    ]


async def keyword_search(
    table_name: str,
    query: str,
    limit: int = 5,
    filter_conditions: dict | None = None,
) -> list[dict]:
    """Perform keyword search using PostgreSQL full-text search.

    Args:
        table_name: Table to search.
        query: Search query string.
        limit: Maximum number of results.
        filter_conditions: Optional metadata filter dict.

    Returns:
        List of dicts with 'id', 'score', and 'payload' keys.
    """
    pool = await get_pool()
    
    where_clauses = ["fts @@ websearch_to_tsquery('simple', $1)"]
    args = [query]
    arg_idx = 2
    
    if filter_conditions:
        for key, value in filter_conditions.items():
            if isinstance(value, list):
                where_clauses.append(f"metadata->>'{key}' = ANY(${arg_idx}::text[])")
                args.append([str(v) for v in value])
                arg_idx += 1
            else:
                where_clauses.append(f"metadata->>'{key}' = ${arg_idx}")
                args.append(str(value))
                arg_idx += 1
                
    where_sql = "WHERE " + " AND ".join(where_clauses)
        
    sql_query = f"""
        SELECT id, metadata, ts_rank(fts, websearch_to_tsquery('simple', $1)) AS score
        FROM {table_name}
        {where_sql}
        ORDER BY score DESC
        LIMIT ${arg_idx}
    """
    args.append(limit)
    
    async with pool.acquire() as conn:
        records = await conn.fetch(sql_query, *args)
        
    return [
        {
            "id": record["id"],
            "score": record["score"],
            "payload": json.loads(record["metadata"]) if isinstance(record["metadata"], str) else record["metadata"],
        }
        for record in records
    ]

