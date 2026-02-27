"""Application configuration."""

from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    """Application settings from environment variables."""

    # OpenAI
    OPENAI_API_KEY: str = "sk-mock-key-for-testing"
    OPENAI_MODEL: str = "gpt-4o-mini"

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

    # Agent settings
    MAX_AGENT_ITERATIONS: int = 15
    APPROVAL_TIMEOUT_SECONDS: int = 300  # 5 minutes

    class Config:
        env_file = ".env"
        case_sensitive = True


settings = Settings()
