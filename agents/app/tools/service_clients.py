"""REST client tools for backend service communication."""

from typing import Any, Optional

import httpx
from tenacity import retry, stop_after_attempt, wait_exponential, retry_if_exception_type

from app.config import settings

import structlog

logger = structlog.get_logger()

# Shared HTTP client with connection pooling
_client: Optional[httpx.AsyncClient] = None


def _get_client() -> httpx.AsyncClient:
    global _client
    if _client is None or _client.is_closed:
        _client = httpx.AsyncClient(timeout=httpx.Timeout(30.0, connect=10.0))
    return _client


class ServiceError(Exception):
    """Error from backend service."""

    def __init__(self, service: str, status_code: int, detail: str):
        self.service = service
        self.status_code = status_code
        self.detail = detail
        super().__init__(f"{service} error ({status_code}): {detail}")


@retry(
    stop=stop_after_attempt(3),
    wait=wait_exponential(multiplier=1, min=1, max=10),
    retry=retry_if_exception_type((httpx.ConnectError, httpx.ReadTimeout)),
)
async def _request(
    method: str,
    url: str,
    service_name: str,
    params: Optional[dict] = None,
    json_data: Optional[dict] = None,
) -> dict | list:
    """Make HTTP request with retry and error handling."""
    client = _get_client()
    try:
        response = await client.request(method, url, params=params, json=json_data)
        if response.status_code >= 400:
            detail = response.text
            try:
                detail = response.json().get("message", response.text)
            except Exception:
                pass
            raise ServiceError(service_name, response.status_code, detail)
        return response.json()
    except httpx.HTTPError as e:
        logger.error("HTTP request failed", service=service_name, url=url, error=str(e))
        raise


# ============================================================
# Product Service Tools
# ============================================================

async def search_products(
    keyword: Optional[str] = None,
    category: Optional[str] = None,
    brand: Optional[str] = None,
    min_price: Optional[int] = None,
    max_price: Optional[int] = None,
    page: int = 0,
    size: int = 10,
) -> dict:
    """Search products with filters."""
    params = {"page": page, "size": size}
    if keyword:
        params["keyword"] = keyword
    if category:
        params["category"] = category
    if brand:
        params["brand"] = brand
    if min_price is not None:
        params["minPrice"] = min_price
    if max_price is not None:
        params["maxPrice"] = max_price

    return await _request(
        "GET",
        f"{settings.PRODUCT_SERVICE_URL}/api/products",
        "product-service",
        params=params,
    )


async def get_product(product_id: str) -> dict:
    """Get product details by ID."""
    return await _request(
        "GET",
        f"{settings.PRODUCT_SERVICE_URL}/api/products/{product_id}",
        "product-service",
    )


async def get_product_variants(product_id: str) -> list:
    """Get variants for a product."""
    return await _request(
        "GET",
        f"{settings.PRODUCT_SERVICE_URL}/api/products/{product_id}/variants",
        "product-service",
    )


async def get_categories() -> list:
    """Get all categories."""
    return await _request(
        "GET",
        f"{settings.PRODUCT_SERVICE_URL}/api/categories",
        "product-service",
    )


# ============================================================
# Review Service Tools
# ============================================================

async def get_product_reviews(
    product_id: str,
    page: int = 0,
    size: int = 10,
    sort: str = "helpful",
) -> dict:
    """Get reviews for a product."""
    return await _request(
        "GET",
        f"{settings.REVIEW_SERVICE_URL}/api/reviews/product/{product_id}",
        "review-service",
        params={"page": page, "size": size, "sort": sort},
    )


async def get_review_summary(product_id: str) -> dict:
    """Get review summary for a product."""
    return await _request(
        "GET",
        f"{settings.REVIEW_SERVICE_URL}/api/reviews/product/{product_id}/summary",
        "review-service",
    )


async def search_reviews(product_id: str, keyword: str) -> dict:
    """Search reviews by keyword."""
    return await _request(
        "GET",
        f"{settings.REVIEW_SERVICE_URL}/api/reviews/search",
        "review-service",
        params={"productId": product_id, "keyword": keyword},
    )


# ============================================================
# Order Service Tools
# ============================================================

async def create_order(user_id: str) -> dict:
    """Create a draft order (cart)."""
    return await _request(
        "POST",
        f"{settings.ORDER_SERVICE_URL}/api/orders",
        "order-service",
        json_data={"userId": user_id},
    )


async def get_order(order_id: str) -> dict:
    """Get order details."""
    return await _request(
        "GET",
        f"{settings.ORDER_SERVICE_URL}/api/orders/{order_id}",
        "order-service",
    )


async def get_user_orders(user_id: str) -> list:
    """Get all orders for a user."""
    return await _request(
        "GET",
        f"{settings.ORDER_SERVICE_URL}/api/orders/user/{user_id}",
        "order-service",
    )


async def add_order_item(
    order_id: str,
    product_id: str,
    variant_id: Optional[str],
    product_name: str,
    quantity: int,
    unit_price: int,
) -> dict:
    """Add item to draft order."""
    return await _request(
        "POST",
        f"{settings.ORDER_SERVICE_URL}/api/orders/{order_id}/items",
        "order-service",
        json_data={
            "productId": product_id,
            "variantId": variant_id,
            "productName": product_name,
            "quantity": quantity,
            "unitPrice": unit_price,
        },
    )


async def remove_order_item(order_id: str, item_id: str) -> dict:
    """Remove item from draft order."""
    return await _request(
        "DELETE",
        f"{settings.ORDER_SERVICE_URL}/api/orders/{order_id}/items/{item_id}",
        "order-service",
    )


async def update_order_item(order_id: str, item_id: str, quantity: int) -> dict:
    """Update item quantity in draft order."""
    return await _request(
        "PUT",
        f"{settings.ORDER_SERVICE_URL}/api/orders/{order_id}/items/{item_id}",
        "order-service",
        json_data={"quantity": quantity},
    )


async def checkout_order(order_id: str) -> dict:
    """Move order to PENDING_APPROVAL."""
    return await _request(
        "POST",
        f"{settings.ORDER_SERVICE_URL}/api/orders/{order_id}/checkout",
        "order-service",
    )


async def approve_order(order_id: str) -> dict:
    """Approve order and start saga."""
    return await _request(
        "POST",
        f"{settings.ORDER_SERVICE_URL}/api/orders/{order_id}/approve",
        "order-service",
    )


async def cancel_order(order_id: str, reason: Optional[str] = None) -> dict:
    """Cancel an order."""
    return await _request(
        "POST",
        f"{settings.ORDER_SERVICE_URL}/api/orders/{order_id}/cancel",
        "order-service",
        json_data={"reason": reason} if reason else None,
    )


async def request_refund(order_id: str, reason: Optional[str] = None) -> dict:
    """Request refund for an order."""
    return await _request(
        "POST",
        f"{settings.ORDER_SERVICE_URL}/api/orders/{order_id}/refund",
        "order-service",
        json_data={"reason": reason} if reason else None,
    )


# ============================================================
# Inventory Service Tools
# ============================================================

async def check_inventory(
    product_id: str,
    variant_id: Optional[str] = None,
    quantity: int = 1,
) -> dict:
    """Check inventory availability."""
    params = {"productId": product_id, "quantity": quantity}
    if variant_id:
        params["variantId"] = variant_id
    return await _request(
        "GET",
        f"{settings.INVENTORY_SERVICE_URL}/api/inventory/check",
        "inventory-service",
        params=params,
    )


async def get_product_inventory(product_id: str) -> list:
    """Get stock levels for all variants of a product."""
    return await _request(
        "GET",
        f"{settings.INVENTORY_SERVICE_URL}/api/inventory/product/{product_id}",
        "inventory-service",
    )
