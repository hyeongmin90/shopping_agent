import asyncio
from contextlib import asynccontextmanager
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
import structlog

from app.api import rag_router
from app.rag.pipeline import EmbeddingPipeline
from app.rag.pgvector_store import ensure_tables

logger = structlog.get_logger()
pipeline = EmbeddingPipeline()

@asynccontextmanager
async def lifespan(app: FastAPI):
    logger.info("rag_service_starting")
    # Initialize pgvector tables first 
    await ensure_tables()
    
    # Start Kafka pipeline
    pipeline.initialize()
    await pipeline.start()
    
    yield
    
    # Shutdown pipeline
    logger.info("rag_service_stopping")
    await pipeline.stop()

app = FastAPI(
    title="Shopping RAG Service",
    description="Vector DB and Kafka embedding pipeline separated from agent-service",
    version="1.0.0",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(rag_router.router)

@app.get("/health")
async def health_check():
    return {"status": "ok", "service": "rag-service"}
