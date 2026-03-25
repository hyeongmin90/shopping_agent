"""Application configuration."""

from pydantic_settings import BaseSettings
import os

class Settings(BaseSettings):
    """Application settings from environment variables."""

    # OpenAI
    OPENAI_API_KEY: str = "sk-mork-your-api-key" or os.getenv("OPENAI_API_KEY")
    OPENAI_MODEL: str = "gpt-5-mini"
    OPENAI_EMBEDDING_MODEL: str = "text-embedding-3-small"
    OPENAI_EMBEDDING_DIMENSION: int = 1536

    # Kafka
    KAFKA_BOOTSTRAP_SERVERS: str = "localhost:9092"
    KAFKA_GROUP_ID: str = "rag-service"

    # OpenTelemetry
    OTEL_EXPORTER_OTLP_ENDPOINT: str = "http://localhost:4317"
    OTEL_SERVICE_NAME: str = "rag-service"

    # Vector DB (PostgreSQL pgvector)
    POSTGRES_AGENT_URL: str = "postgresql://agent_user:agent_pass@localhost:5437/agent_db"
    POSTGRES_PRODUCT_TABLE: str = "product_vectors"
    POSTGRES_REVIEW_TABLE: str = "review_vectors"
    POSTGRES_POLICY_TABLE: str = "policy_vectors"

    class Config:
        env_file = ".env"
        case_sensitive = True

settings = Settings()
