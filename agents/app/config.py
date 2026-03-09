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

    # Service URLs
    PRODUCT_SERVICE_URL: str = "http://localhost:8081"
    REVIEW_SERVICE_URL: str = "http://localhost:8082"
    ORDER_SERVICE_URL: str = "http://localhost:8083"
    INVENTORY_SERVICE_URL: str = "http://localhost:8084"
    PAYMENT_SERVICE_URL: str = "http://localhost:8085"

    # Redis
    REDIS_URL: str = "redis://localhost:6379/0"

    # Kafka
    KAFKA_BOOTSTRAP_SERVERS: str = "localhost:9092"
    KAFKA_GROUP_ID: str = "agent-service"

    # OpenTelemetry
    OTEL_EXPORTER_OTLP_ENDPOINT: str = "http://localhost:4317"
    OTEL_SERVICE_NAME: str = "agent-service"

    # Vector DB (PostgreSQL pgvector)
    POSTGRES_AGENT_URL: str = "postgresql+asyncpg://agent_user:agent_pass@localhost:5437/agent_db"
    POSTGRES_PRODUCT_TABLE: str = "products"
    POSTGRES_REVIEW_TABLE: str = "reviews"
    POSTGRES_POLICY_TABLE: str = "policies"

    # Agent settings
    MAX_AGENT_ITERATIONS: int = 15
    APPROVAL_TIMEOUT_SECONDS: int = 300  # 5 minutes

    class Config:
        env_file = ".env"
        case_sensitive = True


settings = Settings()
