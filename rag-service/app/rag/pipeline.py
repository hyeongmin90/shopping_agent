"""Automated embedding pipeline — Kafka consumer for review events.

Listens to 'review.events' topic.
On new review creation, generates embeddings and stores them in PostgreSQL (vector search).
"""

import asyncio
import json
from typing import Any, Optional

from confluent_kafka import Consumer, KafkaError  # pyright: ignore[reportMissingImports]

import structlog

from app.config import settings
from app.rag.embeddings import generate_embedding
from app.rag.pgvector_store import upsert_vectors

logger = structlog.get_logger()

REVIEW_CREATED_EVENT = "ReviewCreatedEvent"


class EmbeddingPipeline:
    """Kafka consumer that automatically embeds new reviews."""

    def __init__(self):
        self._consumer: Optional[Consumer] = None
        self._running = False
        self._task: Optional[asyncio.Task[None]] = None

    def initialize(self) -> None:
        """Initialize the Kafka consumer for embedding pipeline."""
        consumer_config = {
            "bootstrap.servers": settings.KAFKA_BOOTSTRAP_SERVERS,
            "group.id": f"{settings.OTEL_SERVICE_NAME}-embedding-pipeline",
            "auto.offset.reset": "earliest",
            "enable.auto.commit": False,
            "max.poll.interval.ms": 300000,
        }
        consumer = Consumer(consumer_config)
        consumer.subscribe(["review.events"])
        self._consumer = consumer
        logger.info(
            "embedding_pipeline_initialized",
            topics=["review.events"],
        )

    async def start(self) -> None:
        """Start the pipeline as an asyncio background task."""
        self._running = True
        self._task = asyncio.create_task(self._consume_loop())
        logger.info("embedding_pipeline_started")

    async def stop(self) -> None:
        """Stop the pipeline gracefully."""
        self._running = False
        if self._task:
            self._task.cancel()
            try:
                await self._task
            except asyncio.CancelledError:
                pass
        if self._consumer:
            self._consumer.close()
        logger.info("embedding_pipeline_stopped")

    async def _consume_loop(self) -> None:
        """Main consumption loop — polls Kafka and processes events."""
        while self._running:
            try:
                consumer = self._consumer
                if consumer is None:
                    logger.error("embedding_pipeline_consumer_not_initialized")
                    await asyncio.sleep(1)
                    continue

                msg = consumer.poll(timeout=1.0)
                if msg is None:
                    await asyncio.sleep(0.1)
                    continue
                if msg.error():
                    if msg.error().code() == KafkaError._PARTITION_EOF:
                        continue
                    logger.error(
                        "embedding_pipeline_kafka_error",
                        error=str(msg.error()),
                    )
                    continue

                try:
                    envelope = json.loads(msg.value().decode("utf-8"))
                    await self._process_event(envelope, msg.topic())
                    consumer.commit(msg)
                except json.JSONDecodeError as e:
                    logger.error(
                        "embedding_pipeline_parse_error",
                        error=str(e),
                        topic=msg.topic(),
                    )
                    consumer.commit(msg)  # Skip malformed messages
                except Exception as e:
                    logger.error(
                        "embedding_pipeline_process_error",
                        error=str(e),
                        topic=msg.topic(),
                    )
                    # Don't commit — will retry on next poll

            except asyncio.CancelledError:
                raise
            except Exception as e:
                logger.error("embedding_pipeline_loop_error", error=str(e))
                await asyncio.sleep(5)  # Back off on unexpected errors

    async def _process_event(self, envelope: dict[str, Any], topic: str) -> None:
        """Route event to appropriate handler based on event type."""
        # Support both flat and nested envelope formats
        meta = envelope.get("meta", envelope)
        data = envelope.get("data", envelope.get("payload", {}))
        event_type = meta.get("eventType", "")

        logger.info(
            "embedding_pipeline_event_received",
            event_type=event_type,
            topic=topic,
        )

        if event_type == REVIEW_CREATED_EVENT:
            await self._handle_review_event(data)
        else:
            logger.debug(
                "embedding_pipeline_event_skipped",
                event_type=event_type,
            )

    async def _handle_review_event(self, data: dict[str, Any]) -> None:
        """Embed and store a review in PostgreSQL."""
        review_id = data.get("reviewId", data.get("review_id", ""))
        product_id = data.get("productId", data.get("product_id", ""))
        title = data.get("title", "")
        content = data.get("content", "")
        rating = data.get("rating", 0)
        product_name = data.get("productName", data.get("product_name", ""))
        quality_rating = data.get("qualityRating", data.get("quality_rating", 0))
        verified_purchase = data.get("verifiedPurchase", data.get("verified_purchase", False))
        helpful_count = data.get("helpfulCount", data.get("helpful_count", 0))

        if not review_id or not content:
            logger.warning("embedding_pipeline_review_missing_fields", data=data)
            return

        # Build embedding text: title + content
        embed_text = f"{title} {content}".strip()

        try:
            vector = await generate_embedding(embed_text)

            # Upsert to PostgreSQL (reviews are vector-only, no OpenSearch)
            payload = {
                "review_id": review_id,
                "product_id": product_id,
                "product_name": product_name,
                "title": title,
                "content": content,
                "rating": rating,
                "quality_rating": quality_rating,
                "verified_purchase": verified_purchase,
                "helpful_count": helpful_count,
            }
            await upsert_vectors(
                table_name=settings.POSTGRES_REVIEW_TABLE,
                ids=[review_id],
                vectors=[vector],
                payloads=[payload],
            )

            logger.info(
                "embedding_pipeline_review_indexed",
                review_id=review_id,
                product_id=product_id,
            )
        except Exception as e:
            logger.error(
                "embedding_pipeline_review_error",
                review_id=review_id,
                error=str(e),
            )
            raise
