"""Application configuration."""

from pydantic_settings import BaseSettings

class Settings(BaseSettings):
    """Application settings from environment variables."""

    # OpenAI

    OPENAI_API_KEY: str = "sk-mock-your-api-key"
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

    # OpenTelemetry
    OTEL_EXPORTER_OTLP_ENDPOINT: str = "http://localhost:4317"
    OTEL_SERVICE_NAME: str = "agent-service"

    # Langfuse (Optional)
    LANGFUSE_SECRET_KEY: str | None = None
    LANGFUSE_PUBLIC_KEY: str | None = None
    LANGFUSE_HOST: str = "https://cloud.langfuse.com"
    LANGFUSE_BASE_URL: str | None = None

    # RAG Service
    RAG_SERVICE_URL: str = "http://localhost:8002"

    # Agent settings
    MAX_AGENT_ITERATIONS: int = 15
    APPROVAL_TIMEOUT_SECONDS: int = 300  # 5 minutes

    class Config:
        env_file = ".env"
        case_sensitive = True


settings = Settings()
