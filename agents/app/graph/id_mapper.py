"""Utility for mapping temporary indexing indices to real DB UUIDs and vice-versa."""

import re
from typing import Any
from app.memory.redis_store import RedisStore

_UUID_RE = re.compile(
    r'^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$',
    re.IGNORECASE,
)


class IdMapper:
    """Manages temporary indexing for entities like products, variants, and cart items.
    Prevents exposing actual database IDs (UUIDs) to the LLM by translating them
    into short session-based indices (e.g., 'p1', 'v2').
    """

    @staticmethod
    async def get_real_id(thread_id: str, index_id: str) -> str:
        """Resolve a temporary index (e.g., 'p1') back to its real UUID."""
        if not index_id or not thread_id:
            return index_id

        index_id_str = str(index_id)
        if _UUID_RE.match(index_id_str):
            return index_id_str

        store = RedisStore()
        await store.initialize()
        try:
            context = await store.get_context(thread_id) or {}
            index_to_real = context.get("id_map", {}).get("index_to_real", {})
            return index_to_real.get(index_id_str, index_id_str)
        finally:
            await store.close()

    @staticmethod
    async def replace_many(
        thread_id: str,
        items: list[dict[str, Any]],
        id_fields: dict[str, str],
    ) -> list[dict[str, Any]]:
        """Batch-mask ID fields across multiple items with a single Redis read+write.

        id_fields maps field name → prefix  (e.g., {"id": "p", "variant_id": "v"})
        """
        if not items or not thread_id:
            return items

        store = RedisStore()
        await store.initialize()
        try:
            context = await store.get_context(thread_id) or {}
            id_map = context.get("id_map", {})
            real_to_index: dict[str, str] = id_map.get("real_to_index", {})
            index_to_real: dict[str, str] = id_map.get("index_to_real", {})
            counters: dict[str, int] = id_map.get("counters", {})

            results: list[dict[str, Any]] = []
            for item in items:
                row = item.copy()
                for field, prefix in id_fields.items():
                    raw = row.get(field)
                    if not raw:
                        continue
                    real_id = str(raw)
                    if not _UUID_RE.match(real_id):
                        continue
                    if real_id not in real_to_index:
                        count = counters.get(prefix, 0) + 1
                        counters[prefix] = count
                        index_str = f"{prefix}{count}"
                        real_to_index[real_id] = index_str
                        index_to_real[index_str] = real_id
                    row[field] = real_to_index[real_id]
                results.append(row)

            id_map.update(
                real_to_index=real_to_index,
                index_to_real=index_to_real,
                counters=counters,
            )
            await store.update_context(thread_id, {"id_map": id_map})
            return results
        finally:
            await store.close()
