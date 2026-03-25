"""LangGraph tool definitions wrapping backend service calls and RAG searches."""

import json
from typing import Optional, Annotated

from langchain_core.tools import tool
from langgraph.prebuilt import InjectedState

from app.tools import service_clients as sc
from app.graph.id_mapper import IdMapper

import structlog

logger = structlog.get_logger()


# ============================================================
# Product Tools
# ============================================================

@tool
async def search_products(
    search: Optional[str] = None,
    category: Optional[str] = None,
    brand: Optional[str] = None,
    min_price: Optional[int] = None,
    max_price: Optional[int] = None,
    user_id: Annotated[str, InjectedState("user_id")] = None,
    thread_id: Annotated[str, InjectedState("thread_id")] = None,
) -> str:
    """
    상품을 검색합니다. 
    
    Args:
        search: 검색어 (정확한 상품명이 아니면 결과가 나오지 않으니, 되도록 카테고리나 브랜드로 검색하세요)
        category: 카테고리 (get_categories()로 확인하세요)
        brand: 브랜드
        min_price: 최소 가격
        max_price: 최대 가격
    
    Returns:
        JSON 형식의 검색 결과
    """
    try:
        result = await sc.search_products(
            search=search,
            category=category,
            brand=brand,
            min_price=min_price,
            max_price=max_price,
        )
        
        if user_id and thread_id and isinstance(result, dict) and "content" in result:
            recent_products = []
            for p in result["content"]:
                real_id = p.get("id")
                if real_id:
                    masked_id = await IdMapper.get_index(thread_id, real_id, "p")
                    p["id"] = masked_id
                    recent_products.append({"id": masked_id, "name": p.get("name", "Unknown")})
            
            if recent_products:
                from app.memory.redis_store import RedisStore
                redis_store = RedisStore()
                await redis_store.initialize()
                await redis_store.update_context(thread_id, {"recent_products": recent_products})
                await redis_store.close()

        return json.dumps(result, ensure_ascii=False)
    except Exception as e:
        return json.dumps({"error": str(e)})


@tool
async def get_product_details(
    product_id: str,
    thread_id: Annotated[str, InjectedState("thread_id")] = None,
) -> str:
    """Get detailed information about a specific product including variants."""
    try:
        real_product_id = await IdMapper.get_real_id(thread_id, product_id)
        product = await sc.get_product(real_product_id)
        variants = await sc.get_product_variants(real_product_id)
        
        product = await IdMapper.replace_dict_keys(thread_id, product, {"id": "p"})
        for i, v in enumerate(variants):
            variants[i] = await IdMapper.replace_dict_keys(thread_id, v, {"id": "v", "product_id": "p"})
            
        product["variants"] = variants
        return json.dumps(product, ensure_ascii=False)
    except Exception as e:
        return json.dumps({"error": str(e)})


@tool
async def get_categories() -> str:
    """Get all available product categories."""
    try:
        result = await sc.get_categories()
        return json.dumps(result, ensure_ascii=False)
    except Exception as e:
        return json.dumps({"error": str(e)})


# ============================================================
# Review Tools
# ============================================================

@tool
async def get_product_reviews(
    product_id: str, 
    sort: str = "helpful",
    thread_id: Annotated[str, InjectedState("thread_id")] = None,
) -> str:
    """Get reviews for a specific product."""
    try:
        real_product_id = await IdMapper.get_real_id(thread_id, product_id)
        result = await sc.get_product_reviews(real_product_id, sort=sort)
        
        if isinstance(result, dict) and "content" in result:
            for r in result["content"]:
                await IdMapper.replace_dict_keys(thread_id, r, {"id": "r", "product_id": "p"})
                
        return json.dumps(result, ensure_ascii=False)
    except Exception as e:
        return json.dumps({"error": str(e)})


@tool
async def get_review_summary(
    product_id: str,
    thread_id: Annotated[str, InjectedState("thread_id")] = None,
) -> str:
    """Get review summary including average rating and quality rating."""
    try:
        real_product_id = await IdMapper.get_real_id(thread_id, product_id)
        result = await sc.get_review_summary(real_product_id)
        return json.dumps(result, ensure_ascii=False)
    except Exception as e:
        return json.dumps({"error": str(e)})


@tool
async def search_reviews(
    product_id: str, 
    keyword: str,
    thread_id: Annotated[str, InjectedState("thread_id")] = None,
) -> str:
    """Search reviews for specific topics like sizing, quality, material, etc."""
    try:
        real_product_id = await IdMapper.get_real_id(thread_id, product_id)
        result = await sc.search_reviews(real_product_id, keyword)
        
        if isinstance(result, dict) and "content" in result:
            for r in result["content"]:
                await IdMapper.replace_dict_keys(thread_id, r, {"id": "r", "product_id": "p"})
                
        return json.dumps(result, ensure_ascii=False)
    except Exception as e:
        return json.dumps({"error": str(e)})


# ============================================================
# Cart / Order Tools
# ============================================================

@tool
async def get_cart(
    user_id: Annotated[str, InjectedState("user_id")],
    thread_id: Annotated[str, InjectedState("thread_id")] = None,
) -> str:
    """Get current shopping cart details."""
    try:
        result = await sc.get_cart(user_id)
        
        if isinstance(result, dict) and "items" in result:
            for item in result["items"]:
                await IdMapper.replace_dict_keys(thread_id, item, {
                    "id": "c",
                    "product_id": "p",
                    "variant_id": "v"
                })
        
        return json.dumps(result, ensure_ascii=False)
    except Exception as e:
        return json.dumps({"error": str(e)})


@tool
async def add_to_cart(
    product_id: str,
    quantity: int,
    user_id: Annotated[str, InjectedState("user_id")],
    variant_id: Optional[str] = None,
    thread_id: Annotated[str, InjectedState("thread_id")] = None,
) -> str:
    """Add a product to the shopping cart."""
    try:
        real_product_id = await IdMapper.get_real_id(thread_id, product_id)
        real_variant_id = await IdMapper.get_real_id(thread_id, variant_id) if variant_id else None
        
        result = await sc.add_cart_item(
            user_id, real_product_id, real_variant_id, quantity
        )

        logger.info("ID Mapping")
        logger.info(f"id: {product_id}, real_id: {real_product_id}")
        logger.info(f"variant_id: {variant_id}, real_variant_id: {real_variant_id}")
        logger.info(f"Cart item added: {result}")
        
        # Usually returns updated cart
        if isinstance(result, dict) and "items" in result:
            for item in result["items"]:
                await IdMapper.replace_dict_keys(thread_id, item, {
                    "id": "c",
                    "product_id": "p",
                    "variant_id": "v"
                })
                
        return json.dumps(result, ensure_ascii=False)
    except Exception as e:
        return json.dumps({"error": str(e)})


@tool
async def remove_from_cart(
    item_id: str, 
    user_id: Annotated[str, InjectedState("user_id")],
    thread_id: Annotated[str, InjectedState("thread_id")] = None,
) -> str:
    """Remove an item from the shopping cart."""
    try:
        real_item_id = await IdMapper.get_real_id(thread_id, item_id)
        result = await sc.remove_cart_item(user_id, real_item_id)
        
        if isinstance(result, dict) and "items" in result:
            for item in result["items"]:
                await IdMapper.replace_dict_keys(thread_id, item, {
                    "id": "c",
                    "product_id": "p",
                    "variant_id": "v"
                })
                
        return json.dumps(result, ensure_ascii=False)
    except Exception as e:
        return json.dumps({"error": str(e)})


@tool
async def update_cart_item_quantity(
    item_id: str, 
    product_id: str, 
    quantity: int, 
    user_id: Annotated[str, InjectedState("user_id")],
    variant_id: Optional[str] = None,
    thread_id: Annotated[str, InjectedState("thread_id")] = None,
) -> str:
    """Update quantity of an item in the cart."""
    try:
        real_item_id = await IdMapper.get_real_id(thread_id, item_id)
        real_product_id = await IdMapper.get_real_id(thread_id, product_id)
        real_variant_id = await IdMapper.get_real_id(thread_id, variant_id) if variant_id else None
        
        result = await sc.update_cart_item(user_id, real_item_id, real_product_id, real_variant_id, quantity)
        
        if isinstance(result, dict) and "items" in result:
            for item in result["items"]:
                await IdMapper.replace_dict_keys(thread_id, item, {
                    "id": "c",
                    "product_id": "p",
                    "variant_id": "v"
                })
                
        return json.dumps(result, ensure_ascii=False)
    except Exception as e:
        return json.dumps({"error": str(e)})


@tool
async def get_order_details(
    order_id: str,
    thread_id: Annotated[str, InjectedState("thread_id")] = None,
) -> str:
    """Get current order/cart details."""
    try:
        real_order_id = await IdMapper.get_real_id(thread_id, order_id)
        result = await sc.get_order(real_order_id)
        
        if isinstance(result, dict):
            await IdMapper.replace_dict_keys(thread_id, result, {"id": "o"})
            if "items" in result:
                for item in result["items"]:
                    await IdMapper.replace_dict_keys(thread_id, item, {
                        "id": "i",
                        "product_id": "p",
                        "variant_id": "v",
                        "order_id": "o"
                    })
                    
        return json.dumps(result, ensure_ascii=False)
    except Exception as e:
        return json.dumps({"error": str(e)})


@tool
async def get_user_orders(
    user_id: Annotated[str, InjectedState("user_id")],
    thread_id: Annotated[str, InjectedState("thread_id")] = None,
) -> str:
    """Get all orders for a user (including past orders)."""
    try:
        result = await sc.get_user_orders(user_id)
        
        if isinstance(result, list):
            for order in result:
                await IdMapper.replace_dict_keys(thread_id, order, {"id": "o"})
                
        return json.dumps(result, ensure_ascii=False)
    except Exception as e:
        return json.dumps({"error": str(e)})


# ============================================================
# Inventory Tools
# ============================================================

@tool
async def check_inventory(
    product_id: str,
    variant_id: str,
    quantity: int = 1,
    thread_id: Annotated[str, InjectedState("thread_id")] = None,
) -> str:
    """Check if a product variant is in stock."""
    try:
        real_product_id = await IdMapper.get_real_id(thread_id, product_id)
        real_variant_id = await IdMapper.get_real_id(thread_id, variant_id)
        
        result = await sc.check_inventory(real_product_id, real_variant_id, quantity)
        if isinstance(result, dict):
            await IdMapper.replace_dict_keys(thread_id, result, {"product_id": "p", "variant_id": "v"})
            
        return json.dumps(result, ensure_ascii=False)
    except Exception as e:
        return json.dumps({"error": str(e)})


@tool
async def get_product_stock(
    product_id: str,
    thread_id: Annotated[str, InjectedState("thread_id")] = None,
) -> str:
    """Get stock levels for all variants of a product."""
    try:
        real_product_id = await IdMapper.get_real_id(thread_id, product_id)
        result = await sc.get_product_inventory(real_product_id)
        
        if isinstance(result, list):
            for item in result:
                await IdMapper.replace_dict_keys(thread_id, item, {"product_id": "p", "variant_id": "v"})
                
        return json.dumps(result, ensure_ascii=False)
    except Exception as e:
        return json.dumps({"error": str(e)})

# ============================================================
# RAG Tools (Hybrid Search)
# ============================================================

@tool
async def rag_search_reviews(
    query: str,
    product_id: Optional[str] = None,
    min_rating: Optional[int] = None,
    verified_only: bool = False,
    user_id: Annotated[str, InjectedState("user_id")] = None,
    thread_id: Annotated[str, InjectedState("thread_id")] = None,
) -> str:
    """Search product reviews using semantic vector search."""
    try:
        real_product_id = await IdMapper.get_real_id(thread_id, product_id) if product_id else None
        
        results = await sc.rag_search_reviews_api(
            query=query,
            product_id=real_product_id,
            min_rating=min_rating,
            verified_only=verified_only,
        )
        
        if user_id and thread_id and isinstance(results, dict) and "results" in results:
            recent_products = []
            seen = set()
            for r in results["results"]:
                pid = r.get("product_id")
                pname = r.get("product_name")
                
                # Replace product_id
                if pid:
                    masked_pid = await IdMapper.get_index(thread_id, pid, "p")
                    r["product_id"] = masked_pid
                    if masked_pid not in seen:
                        recent_products.append({"id": masked_pid, "name": pname or "Unknown"})
                        seen.add(masked_pid)
                        
                # Replace review_id
                if r.get("id"):
                    r["id"] = await IdMapper.get_index(thread_id, r["id"], "r")
            
            if recent_products:
                from app.memory.redis_store import RedisStore
                redis_store = RedisStore()
                await redis_store.initialize()
                await redis_store.update_context(thread_id, {"recent_products": recent_products})
                await redis_store.close()

        return json.dumps(results, ensure_ascii=False)
    except Exception as e:
        return json.dumps({"error": str(e)})


@tool
async def rag_search_policies(
    query: str,
    category: Optional[str] = None,
) -> str:
    """Search store policies using balanced hybrid search (keyword + semantic)."""
    try:
        results = await sc.rag_search_policies_api(
            query=query,
        )
        return json.dumps(results, ensure_ascii=False)
    except Exception as e:
        return json.dumps({"error": str(e)})

# ============================================================
# Tool Groups   
# ============================================================

SEARCH_AGENT_TOOLS = [search_products, get_product_details, get_categories]
REVIEW_AGENT_TOOLS = [get_product_reviews, get_review_summary, search_reviews, rag_search_reviews]
CART_AGENT_TOOLS = [get_cart, add_to_cart, remove_from_cart, update_cart_item_quantity]
CUSTOMER_SERVICE_AGENT_TOOLS = [get_order_details, get_user_orders, rag_search_policies]

ALL_TOOLS = (
    SEARCH_AGENT_TOOLS + REVIEW_AGENT_TOOLS + CART_AGENT_TOOLS + CUSTOMER_SERVICE_AGENT_TOOLS
)
