"""Shopping Agent Service - FastAPI Application."""

from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.config import settings
from app.observability.tracing import setup_tracing
from app.memory.redis_store import RedisStore
from app.tools.kafka_client import KafkaManager
from app.graph.supervisor import create_supervisor_graph
from app.rag.pgvector_store import ensure_tables
from app.rag.pipeline import EmbeddingPipeline

import structlog

logger = structlog.get_logger()


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Application lifespan manager."""
    logger.info("Starting Shopping Agent Service...")

    # Setup OpenTelemetry tracing
    setup_tracing()

    # Initialize Redis
    redis_store = RedisStore()
    await redis_store.initialize()
    app.state.redis = redis_store

    # Initialize Kafka
    kafka_manager = KafkaManager()
    kafka_manager.initialize()
    app.state.kafka = kafka_manager

    # Create supervisor graph
    graph = create_supervisor_graph()
    app.state.graph = graph

    # Initialize RAG stores (PostgreSQL pgvector tables)
    try:
        await ensure_tables()
        logger.info("RAG stores initialized (PostgreSQL pgvector)")
    except Exception as e:
        logger.error("rag_store_init_error", error=str(e))

    # Start embedding pipeline (Kafka consumer for auto-indexing)
    embedding_pipeline = EmbeddingPipeline()
    try:
        embedding_pipeline.initialize()
        await embedding_pipeline.start()
        app.state.embedding_pipeline = embedding_pipeline
        logger.info("Embedding pipeline started")
    except Exception as e:
        logger.error("embedding_pipeline_init_error", error=str(e))
    
    logger.info(
        "Shopping Agent Service started",
        product_service=settings.PRODUCT_SERVICE_URL,
        review_service=settings.REVIEW_SERVICE_URL,
        order_service=settings.ORDER_SERVICE_URL,
    )

    yield

    # Cleanup
    logger.info("Shutting down Shopping Agent Service...")

    # Stop embedding pipeline
    if hasattr(app.state, "embedding_pipeline"):
        await app.state.embedding_pipeline.stop()

    await redis_store.close()
    kafka_manager.close()
    logger.info("Shopping Agent Service stopped")


app = FastAPI(
    title="Shopping Agent Service",
    description="Intelligent autonomous shopping agent with multi-agent orchestration",
    version="1.0.0",
    lifespan=lifespan,
)

# CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


# Import and include routers
from app.api.chat import router as chat_router
from app.api.health import router as health_router

app.include_router(health_router, tags=["health"])
app.include_router(chat_router, prefix="/api", tags=["chat"])
