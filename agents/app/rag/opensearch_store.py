"""OpenSearch client for keyword-based search."""

import structlog
from opensearchpy import OpenSearch

from app.config import settings

logger = structlog.get_logger()

_client: OpenSearch | None = None


def get_opensearch_client() -> OpenSearch:
    """Get or create the OpenSearch client."""
    global _client
    if _client is None:
        _client = OpenSearch(
            hosts=[
                {
                    "host": settings.OPENSEARCH_HOST,
                    "port": settings.OPENSEARCH_PORT,
                }
            ],
            http_compress=True,
            use_ssl=False,
            verify_certs=False,
            timeout=30,
        )
        logger.info(
            "opensearch_client_initialized",
            host=settings.OPENSEARCH_HOST,
            port=settings.OPENSEARCH_PORT,
        )
    return _client


# --- Index definitions ---

PRODUCT_INDEX_MAPPING = {
    "settings": {
        "analysis": {
            "analyzer": {
                "korean_analyzer": {
                    "type": "custom",
                    "tokenizer": "standard",
                    "filter": ["lowercase"],
                }
            }
        },
        "number_of_shards": 1,
        "number_of_replicas": 0,
    },
    "mappings": {
        "properties": {
            "product_id": {"type": "keyword"},
            "name": {"type": "text", "analyzer": "korean_analyzer"},
            "description": {"type": "text", "analyzer": "korean_analyzer"},
            "brand": {"type": "keyword"},
            "category": {"type": "keyword"},
            "category_name": {"type": "text", "analyzer": "korean_analyzer"},
            "base_price": {"type": "integer"},
            "tags": {"type": "keyword"},
        }
    },
}

POLICY_INDEX_MAPPING = {
    "settings": {
        "analysis": {
            "analyzer": {
                "korean_analyzer": {
                    "type": "custom",
                    "tokenizer": "standard",
                    "filter": ["lowercase"],
                }
            }
        },
        "number_of_shards": 1,
        "number_of_replicas": 0,
    },
    "mappings": {
        "properties": {
            "policy_id": {"type": "keyword"},
            "title": {"type": "text", "analyzer": "korean_analyzer"},
            "content": {"type": "text", "analyzer": "korean_analyzer"},
            "category": {"type": "keyword"},
            "effective_date": {"type": "date"},
        }
    },
}


async def ensure_indices() -> None:
    """Create OpenSearch indices if they don't already exist."""
    client = get_opensearch_client()

    index_configs = {
        settings.OPENSEARCH_PRODUCT_INDEX: PRODUCT_INDEX_MAPPING,
        settings.OPENSEARCH_POLICY_INDEX: POLICY_INDEX_MAPPING,
    }

    for index_name, mapping in index_configs.items():
        if not client.indices.exists(index=index_name):
            client.indices.create(index=index_name, body=mapping)
            logger.info("opensearch_index_created", index=index_name)
        else:
            logger.info("opensearch_index_exists", index=index_name)


async def index_document(
    index_name: str,
    doc_id: str,
    document: dict,
) -> None:
    """Index a single document.

    Args:
        index_name: Target index.
        doc_id: Document ID.
        document: Document body.
    """
    client = get_opensearch_client()
    client.index(index=index_name, id=doc_id, body=document, refresh="wait_for")
    logger.debug("opensearch_document_indexed", index=index_name, doc_id=doc_id)


async def bulk_index_documents(
    index_name: str,
    documents: list[dict],
) -> None:
    """Bulk index multiple documents.

    Args:
        index_name: Target index.
        documents: List of dicts, each must have an '_id' key.
    """
    client = get_opensearch_client()
    actions = []
    for doc in documents:
        doc_id = doc.pop("_id")
        actions.append({"index": {"_index": index_name, "_id": doc_id}})
        actions.append(doc)

    if actions:
        response = client.bulk(body=actions, refresh="wait_for")
        if response.get("errors"):
            logger.error("opensearch_bulk_errors", response=response)
        else:
            logger.info(
                "opensearch_bulk_indexed",
                index=index_name,
                count=len(documents),
            )


async def keyword_search(
    index_name: str,
    query: str,
    fields: list[str] | None = None,
    size: int = 5,
    filters: dict | None = None,
) -> list[dict]:
    """Perform keyword search using multi_match query.

    Args:
        index_name: Index to search.
        query: Search query string.
        fields: Fields to search across (defaults to all text fields).
        size: Maximum number of results.
        filters: Optional field-value filters (term queries).

    Returns:
        List of dicts with '_id', '_score', and '_source' keys.
    """
    client = get_opensearch_client()

    must_clause = {
        "multi_match": {
            "query": query,
            "fields": fields or ["*"],
            "type": "best_fields",
            "fuzziness": "AUTO",
        }
    }

    filter_clauses = []
    if filters:
        for key, value in filters.items():
            if isinstance(value, list):
                filter_clauses.append({"terms": {key: value}})
            else:
                filter_clauses.append({"term": {key: value}})

    body = {
        "query": {
            "bool": {
                "must": [must_clause],
                "filter": filter_clauses,
            }
        },
        "size": size,
    }

    response = client.search(index=index_name, body=body)
    hits = response.get("hits", {}).get("hits", [])

    return [
        {
            "_id": hit["_id"],
            "_score": hit["_score"],
            "_source": hit["_source"],
        }
        for hit in hits
    ]
