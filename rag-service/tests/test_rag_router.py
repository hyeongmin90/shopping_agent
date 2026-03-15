"""Tests for RAG router — policy ingestion endpoints."""

from unittest.mock import AsyncMock, patch

import pytest
from httpx import ASGITransport, AsyncClient

from app.main import app


@pytest.fixture
def mock_generate_embedding():
    """Mock single embedding generation."""
    with patch("app.api.rag_router.generate_embedding", new_callable=AsyncMock) as mock:
        mock.return_value = [0.1] * 1536
        yield mock


@pytest.fixture
def mock_generate_embeddings():
    """Mock batch embedding generation."""
    with patch("app.api.rag_router.generate_embeddings", new_callable=AsyncMock) as mock:
        mock.side_effect = lambda texts: [[0.1] * 1536 for _ in texts]
        yield mock


@pytest.fixture
def mock_upsert_vectors():
    """Mock vector upsert."""
    with patch("app.api.rag_router.upsert_vectors", new_callable=AsyncMock) as mock:
        yield mock


@pytest.fixture
def mock_search_reviews_rag():
    """Mock review RAG search."""
    with patch("app.api.rag_router.search_reviews_rag", new_callable=AsyncMock) as mock:
        mock.return_value = [
            {
                "review_id": "rev-001",
                "product_id": "prod-001",
                "product_name": "Test Product",
                "rating": 5,
                "title": "Great",
                "content": "Excellent product",
                "quality_rating": 5,
                "verified_purchase": True,
                "helpful_count": 10,
                "score": 0.95,
                "source": "vector",
            }
        ]
        yield mock


@pytest.fixture
def mock_search_policies_rag():
    """Mock policy RAG search."""
    with patch("app.api.rag_router.search_policies_rag", new_callable=AsyncMock) as mock:
        mock.return_value = [
            {
                "policy_id": "policy-refund-001",
                "title": "환불 정책",
                "content": "7일 이내 환불 가능",
                "category": "refund",
                "score": 0.9,
                "source": "hybrid",
            }
        ]
        yield mock


SAMPLE_POLICY = {
    "policy_id": "policy-test-001",
    "title": "테스트 정책",
    "content": "이것은 테스트 정책입니다.",
    "category": "test",
    "effective_date": "2024-01-01",
}


@pytest.mark.asyncio
async def test_ingest_single_policy(
    mock_generate_embedding,
    mock_upsert_vectors,
):
    """POST /api/rag/policies — single policy ingestion."""
    transport = ASGITransport(app=app, raise_app_exceptions=False)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        resp = await client.post("/api/rag/policies", json=SAMPLE_POLICY)

    assert resp.status_code == 200
    data = resp.json()
    assert data["status"] == "ok"
    assert data["policy_id"] == "policy-test-001"
    assert data["message"] == "Policy indexed successfully"

    mock_generate_embedding.assert_awaited_once()
    mock_upsert_vectors.assert_awaited_once()

    # Verify upsert was called with correct args
    call_kwargs = mock_upsert_vectors.call_args
    assert call_kwargs.kwargs["table_name"] == "policies"
    assert call_kwargs.kwargs["ids"] == ["policy-test-001"]
    assert call_kwargs.kwargs["search_texts"] == ["테스트 정책 이것은 테스트 정책입니다."]


@pytest.mark.asyncio
async def test_ingest_single_policy_without_effective_date(
    mock_generate_embedding,
    mock_upsert_vectors,
):
    """POST /api/rag/policies — effective_date is optional."""
    policy = {
        "policy_id": "policy-test-002",
        "title": "날짜 없는 정책",
        "content": "날짜 없이도 등록 가능합니다.",
        "category": "test",
    }
    transport = ASGITransport(app=app, raise_app_exceptions=False)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        resp = await client.post("/api/rag/policies", json=policy)

    assert resp.status_code == 200
    data = resp.json()
    assert data["status"] == "ok"

    # Verify payload includes effective_date as None
    call_kwargs = mock_upsert_vectors.call_args
    payload = call_kwargs.kwargs["payloads"][0]
    assert payload["effective_date"] is None


@pytest.mark.asyncio
async def test_ingest_single_policy_missing_required_field():
    """POST /api/rag/policies — missing required field returns 422."""
    policy = {
        "policy_id": "policy-test-003",
        "title": "불완전한 정책",
        # missing content and category
    }
    transport = ASGITransport(app=app, raise_app_exceptions=False)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        resp = await client.post("/api/rag/policies", json=policy)

    assert resp.status_code == 422


@pytest.mark.asyncio
async def test_ingest_single_policy_embedding_failure(
    mock_upsert_vectors,
):
    """POST /api/rag/policies — embedding failure returns 500."""
    with patch(
        "app.api.rag_router.generate_embedding",
        new_callable=AsyncMock,
        side_effect=RuntimeError("OpenAI API error"),
    ):
        transport = ASGITransport(app=app, raise_app_exceptions=False)
        async with AsyncClient(transport=transport, base_url="http://test") as client:
            resp = await client.post("/api/rag/policies", json=SAMPLE_POLICY)

    assert resp.status_code == 500
    assert "Failed to generate embedding" in resp.json()["detail"]


@pytest.mark.asyncio
async def test_ingest_single_policy_upsert_failure(
    mock_generate_embedding,
):
    """POST /api/rag/policies — upsert failure returns 500."""
    with patch(
        "app.api.rag_router.upsert_vectors",
        new_callable=AsyncMock,
        side_effect=RuntimeError("DB connection failed"),
    ):
        transport = ASGITransport(app=app, raise_app_exceptions=False)
        async with AsyncClient(transport=transport, base_url="http://test") as client:
            resp = await client.post("/api/rag/policies", json=SAMPLE_POLICY)

    assert resp.status_code == 500
    assert "Failed to index policy" in resp.json()["detail"]


@pytest.mark.asyncio
async def test_bulk_ingest_policies(
    mock_generate_embeddings,
    mock_upsert_vectors,
):
    """POST /api/rag/policies/bulk — bulk ingestion."""
    policies = [
        {
            "policy_id": "policy-bulk-001",
            "title": "벌크 정책 1",
            "content": "첫 번째 벌크 정책",
            "category": "test",
        },
        {
            "policy_id": "policy-bulk-002",
            "title": "벌크 정책 2",
            "content": "두 번째 벌크 정책",
            "category": "test",
        },
    ]
    transport = ASGITransport(app=app, raise_app_exceptions=False)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        resp = await client.post("/api/rag/policies/bulk", json=policies)

    assert resp.status_code == 200
    data = resp.json()
    assert data["status"] == "ok"
    assert data["count"] == 2
    assert data["policy_ids"] == ["policy-bulk-001", "policy-bulk-002"]

    mock_generate_embeddings.assert_awaited_once()
    mock_upsert_vectors.assert_awaited_once()


@pytest.mark.asyncio
async def test_bulk_ingest_empty_list(
    mock_generate_embeddings,
    mock_upsert_vectors,
):
    """POST /api/rag/policies/bulk — empty list returns immediately."""
    transport = ASGITransport(app=app, raise_app_exceptions=False)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        resp = await client.post("/api/rag/policies/bulk", json=[])

    assert resp.status_code == 200
    data = resp.json()
    assert data["status"] == "ok"
    assert data["count"] == 0
    assert data["policy_ids"] == []

    mock_generate_embeddings.assert_not_awaited()
    mock_upsert_vectors.assert_not_awaited()


@pytest.mark.asyncio
async def test_bulk_ingest_embedding_failure(
    mock_upsert_vectors,
):
    """POST /api/rag/policies/bulk — embedding failure returns 500."""
    with patch(
        "app.api.rag_router.generate_embeddings",
        new_callable=AsyncMock,
        side_effect=RuntimeError("OpenAI API error"),
    ):
        policies = [SAMPLE_POLICY]
        transport = ASGITransport(app=app, raise_app_exceptions=False)
        async with AsyncClient(transport=transport, base_url="http://test") as client:
            resp = await client.post("/api/rag/policies/bulk", json=policies)

    assert resp.status_code == 500
    assert "Failed to generate embeddings" in resp.json()["detail"]


@pytest.mark.asyncio
async def test_search_reviews(mock_search_reviews_rag):
    """GET /api/rag/reviews — review search."""
    transport = ASGITransport(app=app, raise_app_exceptions=False)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        resp = await client.get(
            "/api/rag/reviews",
            params={"query": "품질 좋은 제품"},
        )

    assert resp.status_code == 200
    data = resp.json()
    assert len(data) == 1
    assert data[0]["review_id"] == "rev-001"

    mock_search_reviews_rag.assert_awaited_once()


@pytest.mark.asyncio
async def test_search_policies(mock_search_policies_rag):
    """GET /api/rag/policies — policy search."""
    transport = ASGITransport(app=app, raise_app_exceptions=False)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        resp = await client.get(
            "/api/rag/policies",
            params={"query": "환불 기한"},
        )

    assert resp.status_code == 200
    data = resp.json()
    assert len(data) == 1
    assert data[0]["policy_id"] == "policy-refund-001"

    mock_search_policies_rag.assert_awaited_once()
