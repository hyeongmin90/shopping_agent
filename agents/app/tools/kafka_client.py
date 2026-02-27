"""Kafka producer/consumer manager for agent service."""

import json
import uuid
from datetime import datetime, timezone
from typing import Any, Callable, Optional

from confluent_kafka import Consumer, Producer, KafkaError, KafkaException

from app.config import settings

import structlog

logger = structlog.get_logger()


class KafkaManager:
    """Manages Kafka producer and consumer connections."""

    def __init__(self):
        self._producer: Optional[Producer] = None
        self._consumer: Optional[Consumer] = None
        self._running = False

    def initialize(self):
        """Initialize Kafka connections."""
        producer_config = {
            "bootstrap.servers": settings.KAFKA_BOOTSTRAP_SERVERS,
            "client.id": f"{settings.OTEL_SERVICE_NAME}-producer",
            "acks": "all",
            "retries": 3,
            "retry.backoff.ms": 1000,
            "enable.idempotence": True,
        }
        self._producer = Producer(producer_config)

        consumer_config = {
            "bootstrap.servers": settings.KAFKA_BOOTSTRAP_SERVERS,
            "group.id": settings.KAFKA_GROUP_ID,
            "auto.offset.reset": "latest",
            "enable.auto.commit": False,
            "max.poll.interval.ms": 300000,
        }
        self._consumer = Consumer(consumer_config)

        logger.info(
            "Kafka connections initialized",
            bootstrap=settings.KAFKA_BOOTSTRAP_SERVERS,
        )

    def close(self):
        """Close Kafka connections."""
        if self._producer:
            self._producer.flush(timeout=5)
        if self._consumer:
            self._consumer.close()
        logger.info("Kafka connections closed")

    def produce_event(
        self,
        topic: str,
        event_type: str,
        data: dict,
        key: Optional[str] = None,
        correlation_id: Optional[str] = None,
        causation_id: Optional[str] = None,
    ):
        """Produce an event to Kafka with standard envelope."""
        event_id = str(uuid.uuid4())
        envelope = {
            "meta": {
                "eventId": event_id,
                "eventType": event_type,
                "schemaVersion": 1,
                "occurredAt": datetime.now(timezone.utc).isoformat(),
                "producer": settings.OTEL_SERVICE_NAME,
                "correlationId": correlation_id or str(uuid.uuid4()),
                "causationId": causation_id,
                "idempotencyKey": f"{event_type}:{event_id}",
            },
            "data": data,
        }

        def delivery_callback(err, msg):
            if err:
                logger.error(
                    "Kafka delivery failed",
                    topic=topic,
                    event_type=event_type,
                    error=str(err),
                )
            else:
                logger.info(
                    "Kafka event delivered",
                    topic=msg.topic(),
                    partition=msg.partition(),
                    offset=msg.offset(),
                    event_type=event_type,
                )

        self._producer.produce(
            topic=topic,
            key=key or event_id,
            value=json.dumps(envelope, ensure_ascii=False).encode("utf-8"),
            callback=delivery_callback,
        )
        self._producer.poll(0)  # Trigger delivery callbacks

        return event_id

    def subscribe(self, topics: list[str]):
        """Subscribe to Kafka topics."""
        self._consumer.subscribe(topics)
        logger.info("Subscribed to Kafka topics", topics=topics)

    def poll_message(self, timeout: float = 1.0) -> Optional[dict]:
        """Poll a single message from subscribed topics."""
        msg = self._consumer.poll(timeout=timeout)
        if msg is None:
            return None
        if msg.error():
            if msg.error().code() == KafkaError._PARTITION_EOF:
                return None
            logger.error("Kafka consumer error", error=msg.error())
            return None

        try:
            value = json.loads(msg.value().decode("utf-8"))
            self._consumer.commit(msg)
            return value
        except Exception as e:
            logger.error("Failed to parse Kafka message", error=str(e))
            return None
