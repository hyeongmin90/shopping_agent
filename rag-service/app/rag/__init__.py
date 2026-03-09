"""RAG (Retrieval-Augmented Generation) module.

Provides three RAG systems for the shopping agent:
- Product RAG: Hybrid search (keyword-first via OpenSearch, vector via PostgreSQL pgvector)
- Review RAG: Vector-first search via PostgreSQL pgvector
- Policy RAG: Balanced hybrid search (both OpenSearch + PostgreSQL pgvector)
"""
