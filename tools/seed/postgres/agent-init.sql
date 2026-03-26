-- Agent DB Schema
-- postgres-agent (pgvector/pgvector:pg16)

-- ============================================================
-- pgvector extension (RAG 서비스용)
-- ============================================================
CREATE EXTENSION IF NOT EXISTS vector;

-- ============================================================
-- RAG 벡터 테이블 (rag-service에서 사용)
-- ============================================================
CREATE TABLE IF NOT EXISTS product_vectors (
    id       VARCHAR PRIMARY KEY,
    embedding vector(1536),
    metadata JSONB,
    fts      tsvector
);
CREATE INDEX IF NOT EXISTS product_vectors_embedding_idx ON product_vectors USING hnsw (embedding vector_cosine_ops);
CREATE INDEX IF NOT EXISTS product_vectors_metadata_idx  ON product_vectors USING gin (metadata);
CREATE INDEX IF NOT EXISTS product_vectors_fts_idx       ON product_vectors USING gin (fts);

CREATE TABLE IF NOT EXISTS review_vectors (
    id       VARCHAR PRIMARY KEY,
    embedding vector(1536),
    metadata JSONB,
    fts      tsvector
);
CREATE INDEX IF NOT EXISTS review_vectors_embedding_idx ON review_vectors USING hnsw (embedding vector_cosine_ops);
CREATE INDEX IF NOT EXISTS review_vectors_metadata_idx  ON review_vectors USING gin (metadata);
CREATE INDEX IF NOT EXISTS review_vectors_fts_idx       ON review_vectors USING gin (fts);

CREATE TABLE IF NOT EXISTS policy_vectors (
    id       VARCHAR PRIMARY KEY,
    embedding vector(1536),
    metadata JSONB,
    fts      tsvector
);
CREATE INDEX IF NOT EXISTS policy_vectors_embedding_idx ON policy_vectors USING hnsw (embedding vector_cosine_ops);
CREATE INDEX IF NOT EXISTS policy_vectors_metadata_idx  ON policy_vectors USING gin (metadata);
CREATE INDEX IF NOT EXISTS policy_vectors_fts_idx       ON policy_vectors USING gin (fts);
