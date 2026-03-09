from fastapi import APIRouter, Query
from typing import Optional, List, Dict, Any

from app.rag.review_rag import search_reviews_rag
from app.rag.policy_rag import search_policies_rag

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
