"""Utility for mapping temporary indexing indices to real DB UUIDs and vice-versa."""

import re
from typing import Any
from app.memory.redis_store import RedisStore

class IdMapper:
    """Manages temporary indexing for entities like products, variants, and cart items.
    Prevents exposing actual database IDs (UUIDs) to the LLM by translating them
    into short session-based indices (e.g., 'p1', 'v2').
    """
    
    @staticmethod
    async def get_index(thread_id: str, real_id: str, prefix: str = "item") -> str:
        """Get or create a temporary index (e.g., 'p1') for a real UUID."""
        if not real_id or not thread_id:
            return str(real_id) if real_id else real_id
            
        # Do not mask if it's already masked (simple heuristic)
        if not re.match(r'^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$', str(real_id), re.IGNORECASE):
            return str(real_id)
        
        store = RedisStore()
        await store.initialize()
        
        try:
            context = await store.get_context(thread_id) or {}
            id_map = context.get("id_map", {})
            real_to_index = id_map.get("real_to_index", {})
            index_to_real = id_map.get("index_to_real", {})
            counters = id_map.get("counters", {})
            
            real_id_str = str(real_id)
            if real_id_str in real_to_index:
                return real_to_index[real_id_str]
                
            # Create new index
            count = counters.get(prefix, 0) + 1
            counters[prefix] = count
            index_str = f"{prefix}{count}"
            
            real_to_index[real_id_str] = index_str
            index_to_real[index_str] = real_id_str
            
            id_map["real_to_index"] = real_to_index
            id_map["index_to_real"] = index_to_real
            id_map["counters"] = counters
            
            await store.update_context(thread_id, {"id_map": id_map})
            return index_str
        finally:
            await store.close()
            
    @staticmethod
    async def get_real_id(thread_id: str, index_id: str) -> str:
        """Resolve a temporary index (e.g., 'p1') back to its real UUID."""
        if not index_id or not thread_id:
            return index_id if index_id else index_id
            
        index_id_str = str(index_id)
        # If it looks like a valid UUID, it wasn't mapped
        if re.match(r'^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$', index_id_str, re.IGNORECASE):
            return index_id_str
            
        store = RedisStore()
        await store.initialize()
        
        try:
            context = await store.get_context(thread_id) or {}
            id_map = context.get("id_map", {})
            index_to_real = id_map.get("index_to_real", {})
            
            # Return real ID if found, otherwise assume the index_id is the real ID or invalid
            return index_to_real.get(index_id_str, index_id_str)
        finally:
            await store.close()
            
    @staticmethod
    async def replace_dict_keys(thread_id: str, data: dict[str, Any], id_fields: dict[str, str]) -> dict[str, Any]:
        """Helper to replace ID fields in a JSON-like dictionary.
        id_fields is a mapping of key name to prefix. (e.g., {"id": "p", "variant_id": "v"})
        """
        if not data or not thread_id:
            return data
            
        result = data.copy()
        for key, prefix in id_fields.items():
            if key in result and result[key]:
                result[key] = await IdMapper.get_index(thread_id, result[key], prefix)
        return result
