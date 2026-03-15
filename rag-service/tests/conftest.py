"""Pytest configuration for rag-service tests."""

import pytest


@pytest.fixture(autouse=True)
def _mock_startup(monkeypatch):
    """Prevent actual Kafka/DB initialization during tests.

    Replaces the app lifespan with a no-op to avoid connecting to
    external services during unit tests.
    """
    from contextlib import asynccontextmanager

    from fastapi import FastAPI

    @asynccontextmanager
    async def _noop_lifespan(app: FastAPI):
        yield

    # Patch before the app is used
    import app.main as main_module

    monkeypatch.setattr(main_module, "lifespan", _noop_lifespan)
    # Re-set the app's router lifespan
    main_module.app.router.lifespan_context = _noop_lifespan
