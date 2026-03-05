"""Automated embedding pipeline — Kafka consumer for product/review events.

Listens to 'product.events' and 'review.events' topics.
On new product/review creation, generates embeddings and stores them in
Qdrant (vector search) and OpenSearch (keyword search).
"""

import asyncio
import json
from typing import Optional

from confluent_kafka import Consumer, KafkaError

import structlog

from app.config import settings
from app.rag.embeddings import generate_embedding
from app.rag.qdrant_store import upsert_vectors
from app.rag.opensearch_store import index_document

logger = structlog.get_logger()

# Event types that trigger embedding pipeline
PRODUCT_CREATED_EVENT = "ProductCreatedEvent"
PRODUCT_UPDATED_EVENT = "ProductUpdatedEvent"
REVIEW_CREATED_EVENT = "ReviewCreatedEvent"


class EmbeddingPipeline:
    """Kafka consumer that automatically embeds new products and reviews."""

    def __init__(self):
        self._consumer: Optional[Consumer] = None
        self._running = False
        self._task: Optional[asyncio.Task] = None

    def initialize(self) -> None:
        """Initialize the Kafka consumer for embedding pipeline."""
        consumer_config = {
            "bootstrap.servers": settings.KAFKA_BOOTSTRAP_SERVERS,
            "group.id": f"{settings.OTEL_SERVICE_NAME}-embedding-pipeline",
            "auto.offset.reset": "earliest",
            "enable.auto.commit": False,
            "max.poll.interval.ms": 300000,
        }
        self._consumer = Consumer(consumer_config)
        self._consumer.subscribe(["product.events", "review.events"])
        logger.info(
            "embedding_pipeline_initialized",
            topics=["product.events", "review.events"],
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
                msg = self._consumer.poll(timeout=1.0)
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
                    self._consumer.commit(msg)
                except json.JSONDecodeError as e:
                    logger.error(
                        "embedding_pipeline_parse_error",
                        error=str(e),
                        topic=msg.topic(),
                    )
                    self._consumer.commit(msg)  # Skip malformed messages
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

    async def _process_event(self, envelope: dict, topic: str) -> None:
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

        if event_type in (PRODUCT_CREATED_EVENT, PRODUCT_UPDATED_EVENT):
            await self._handle_product_event(data)
        elif event_type == REVIEW_CREATED_EVENT:
            await self._handle_review_event(data)
        else:
            logger.debug(
                "embedding_pipeline_event_skipped",
                event_type=event_type,
            )

    async def _handle_product_event(self, data: dict) -> None:
        """Embed and store a product in Qdrant + OpenSearch."""
        product_id = data.get("productId", data.get("product_id", ""))
        name = data.get("name", "")
        description = data.get("description", "")
        brand = data.get("brand", "")
        category_name = data.get("categoryName", data.get("category_name", ""))
        base_price = data.get("basePrice", data.get("base_price", 0))
        tags = data.get("tags", [])

        if not product_id or not name:
            logger.warning("embedding_pipeline_product_missing_fields", data=data)
            return

        # Build embedding text: name + description + brand
        embed_text = f"{name} {description} {brand}".strip()

        try:
            vector = await generate_embedding(embed_text)

            # Upsert to Qdrant
            payload = {
                "product_id": product_id,
                "name": name,
                "description": description,
                "brand": brand,
                "category_name": category_name,
                "base_price": base_price,
                "tags": tags,
            }
            await upsert_vectors(
                collection_name=settings.QDRANT_PRODUCT_COLLECTION,
                ids=[product_id],
                vectors=[vector],
                payloads=[payload],
            )

            # Index in OpenSearch
            os_doc = {
                "product_id": product_id,
                "name": name,
                "description": description,
                "brand": brand,
                "category_name": category_name,
                "base_price": base_price,
                "tags": tags,
            }
            await index_document(
                index_name=settings.OPENSEARCH_PRODUCT_INDEX,
                doc_id=product_id,
                document=os_doc,
            )

            logger.info(
                "embedding_pipeline_product_indexed",
                product_id=product_id,
                name=name,
            )
        except Exception as e:
            logger.error(
                "embedding_pipeline_product_error",
                product_id=product_id,
                error=str(e),
            )
            raise

    async def _handle_review_event(self, data: dict) -> None:
        """Embed and store a review in Qdrant."""
        review_id = data.get("reviewId", data.get("review_id", ""))
        product_id = data.get("productId", data.get("product_id", ""))
        title = data.get("title", "")
        content = data.get("content", "")
        rating = data.get("rating", 0)
        product_name = data.get("productName", data.get("product_name", ""))
        size_feedback = data.get("sizeFeedback", data.get("size_feedback", ""))
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

            # Upsert to Qdrant (reviews are vector-only, no OpenSearch)
            payload = {
                "review_id": review_id,
                "product_id": product_id,
                "product_name": product_name,
                "title": title,
                "content": content,
                "rating": rating,
                "size_feedback": size_feedback,
                "quality_rating": quality_rating,
                "verified_purchase": verified_purchase,
                "helpful_count": helpful_count,
            }
            await upsert_vectors(
                collection_name=settings.QDRANT_REVIEW_COLLECTION,
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
