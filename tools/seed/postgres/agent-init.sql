-- Agent DB Schema
-- postgres-agent (pgvector/pgvector:pg16)
-- RAG 서비스와 에이전트 서비스가 공유하는 DB입니다.

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

-- ============================================================
-- 사용자 장기 메모리 테이블 (agent-service에서 사용)
-- ============================================================
CREATE TABLE IF NOT EXISTS user_profiles (
    user_id     VARCHAR(255) PRIMARY KEY,
    preferences JSONB        NOT NULL DEFAULT '{}',
    facts       TEXT[]       NOT NULL DEFAULT '{}',
    summary     TEXT         NOT NULL DEFAULT '',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
