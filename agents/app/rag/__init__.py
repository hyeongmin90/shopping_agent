"""RAG (Retrieval-Augmented Generation) module.

Provides three RAG systems for the shopping agent:
- Product RAG: Hybrid search (keyword-first via OpenSearch, vector via Qdrant)
- Review RAG: Vector-first search via Qdrant
- Policy RAG: Balanced hybrid search (both OpenSearch + Qdrant)
"""
