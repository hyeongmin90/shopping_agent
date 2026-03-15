from typing import Optional, List, Dict, Any

import structlog
from fastapi import APIRouter, Body, HTTPException, Query
from pydantic import BaseModel

from app.config import settings
from app.rag.embeddings import generate_embedding, generate_embeddings
from app.rag.pgvector_store import upsert_vectors

from app.rag.review_rag import search_reviews_rag
from app.rag.policy_rag import search_policies_rag


logger = structlog.get_logger()


class PolicyIngestRequest(BaseModel):
    policy_id: str
    title: str
    content: str
    category: str
    effective_date: Optional[str] = None

router = APIRouter(prefix="/api/rag", tags=["rag"])

@router.get("/reviews", response_model=List[Dict[str, Any]])
async def api_search_reviews(
    query: str,
    product_id: Optional[str] = None,
    min_rating: Optional[int] = None,
    verified_only: bool = False,
    limit: int = Query(10, ge=1, le=50)
):
    return await search_reviews_rag(
        query=query,
        product_id=product_id,
        min_rating=min_rating,
        verified_only=verified_only,
        limit=limit
    )

@router.get("/policies", response_model=List[Dict[str, Any]])
async def api_search_policies(
    query: str,
    limit: int = Query(3, ge=1, le=10)
):
    return await search_policies_rag(
        query=query,
        limit=limit
    )


@router.post("/policies")
async def ingest_policy(
    request: PolicyIngestRequest = Body(...),
) -> Dict[str, str]:
    embed_text = f"{request.title} {request.content}".strip()
    payload = {
        "policy_id": request.policy_id,
        "title": request.title,
        "content": request.content,
        "category": request.category,
        "effective_date": request.effective_date,
    }

    try:
        vector = await generate_embedding(embed_text)
    except Exception as e:
        logger.error(
            "policy_ingest_embedding_error",
            policy_id=request.policy_id,
            error=str(e),
        )
        raise HTTPException(status_code=500, detail=f"Failed to generate embedding: {e}")

    try:
        await upsert_vectors(
            table_name=settings.POSTGRES_POLICY_TABLE,
            ids=[request.policy_id],
            vectors=[vector],
            payloads=[payload],
            search_texts=[embed_text],
        )
    except Exception as e:
        logger.error(
            "policy_ingest_upsert_error",
            policy_id=request.policy_id,
            error=str(e),
        )
        raise HTTPException(status_code=500, detail=f"Failed to index policy: {e}")

    logger.info("policy_ingested", policy_id=request.policy_id)
    return {
        "status": "ok",
        "policy_id": request.policy_id,
        "message": "Policy indexed successfully",
    }


@router.post("/policies/bulk")
async def ingest_policies_bulk(
    requests: List[PolicyIngestRequest] = Body(...),
) -> Dict[str, Any]:
    if not requests:
        return {"status": "ok", "count": 0, "policy_ids": []}

    embed_texts = [f"{item.title} {item.content}".strip() for item in requests]
    policy_ids = [item.policy_id for item in requests]
    payloads = [
        {
            "policy_id": item.policy_id,
            "title": item.title,
            "content": item.content,
            "category": item.category,
            "effective_date": item.effective_date,
        }
        for item in requests
    ]

    try:
        vectors = await generate_embeddings(embed_texts)
    except Exception as e:
        logger.error(
            "policy_bulk_ingest_embedding_error",
            count=len(requests),
            error=str(e),
        )
        raise HTTPException(status_code=500, detail=f"Failed to generate embeddings: {e}")

    try:
        await upsert_vectors(
            table_name=settings.POSTGRES_POLICY_TABLE,
            ids=policy_ids,
            vectors=vectors,
            payloads=payloads,
            search_texts=embed_texts,
        )
    except Exception as e:
        logger.error(
            "policy_bulk_ingest_upsert_error",
            count=len(requests),
            error=str(e),
        )
        raise HTTPException(status_code=500, detail=f"Failed to index policies: {e}")

    logger.info("policies_bulk_ingested", count=len(policy_ids))
    return {
        "status": "ok",
        "count": len(policy_ids),
        "policy_ids": policy_ids,
    }
