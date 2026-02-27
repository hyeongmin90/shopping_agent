"""OpenTelemetry tracing setup."""

from opentelemetry import trace
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import BatchSpanProcessor
from opentelemetry.exporter.otlp.proto.grpc.trace_exporter import OTLPSpanExporter
from opentelemetry.sdk.resources import Resource
from opentelemetry.instrumentation.fastapi import FastAPIInstrumentor
from opentelemetry.instrumentation.httpx import HTTPXClientInstrumentor

from app.config import settings

import structlog

logger = structlog.get_logger()


def setup_tracing():
    """Configure OpenTelemetry distributed tracing."""
    resource = Resource.create(
        {
            "service.name": settings.OTEL_SERVICE_NAME,
            "service.version": "1.0.0",
        }
    )

    provider = TracerProvider(resource=resource)

    try:
        otlp_exporter = OTLPSpanExporter(
            endpoint=settings.OTEL_EXPORTER_OTLP_ENDPOINT,
            insecure=True,
        )
        processor = BatchSpanProcessor(otlp_exporter)
        provider.add_span_processor(processor)
        logger.info("OTLP trace exporter configured", endpoint=settings.OTEL_EXPORTER_OTLP_ENDPOINT)
    except Exception as e:
        logger.warning("Failed to configure OTLP exporter, tracing disabled", error=str(e))

    trace.set_tracer_provider(provider)

    # Auto-instrument FastAPI and HTTPX
    FastAPIInstrumentor.instrument()
    HTTPXClientInstrumentor.instrument()

    logger.info("OpenTelemetry tracing initialized")


def get_tracer(name: str = "shopping-agent"):
    """Get a tracer instance."""
    return trace.get_tracer(name)
