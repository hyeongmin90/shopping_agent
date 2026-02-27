"""LangGraph tool definitions wrapping backend service calls."""

import json
from typing import Optional

from langchain_core.tools import tool

from app.tools import service_clients as sc

import structlog

logger = structlog.get_logger()


# ============================================================
# Product Tools
# ============================================================

@tool
async def search_products(
    keyword: Optional[str] = None,
    category: Optional[str] = None,
    brand: Optional[str] = None,
    min_price: Optional[int] = None,
    max_price: Optional[int] = None,
) -> str:
    """Search products in the store. Prices are in KRW (Korean Won).

    Args:
        keyword: Search keyword (product name, description)
        category: Category name to filter
        brand: Brand name to filter
        min_price: Minimum price in KRW
        max_price: Maximum price in KRW

    Returns:
        JSON string of matching products with their details
    """
    try:
        result = await sc.search_products(
            keyword=keyword,
            category=category,
            brand=brand,
            min_price=min_price,
            max_price=max_price,
        )
        return json.dumps(result, ensure_ascii=False)
    except Exception as e:
        return json.dumps({"error": str(e)})


@tool
async def get_product_details(product_id: str) -> str:
    """Get detailed information about a specific product including variants.

    Args:
        product_id: UUID of the product

    Returns:
        JSON string with product details and available variants
    """
    try:
        product = await sc.get_product(product_id)
        variants = await sc.get_product_variants(product_id)
        product["variants"] = variants
        return json.dumps(product, ensure_ascii=False)
    except Exception as e:
        return json.dumps({"error": str(e)})


@tool
async def get_categories() -> str:
    """Get all available product categories.

    Returns:
        JSON string of category tree
    """
    try:
        result = await sc.get_categories()
        return json.dumps(result, ensure_ascii=False)
    except Exception as e:
        return json.dumps({"error": str(e)})


# ============================================================
# Review Tools
# ============================================================

@tool
async def get_product_reviews(product_id: str, sort: str = "helpful") -> str:
    """Get reviews for a specific product.

    Args:
        product_id: UUID of the product
        sort: Sort order - 'helpful' (most helpful first), 'rating', 'date' (newest first)

    Returns:
        JSON string with reviews and pagination info
    """
    try:
        result = await sc.get_product_reviews(product_id, sort=sort)
        return json.dumps(result, ensure_ascii=False)
    except Exception as e:
        return json.dumps({"error": str(e)})


@tool
async def get_review_summary(product_id: str) -> str:
    """Get review summary including average rating, size feedback distribution, and quality rating.

    Args:
        product_id: UUID of the product

    Returns:
        JSON with averageRating, totalReviews, ratingDistribution, sizeFeedbackDistribution, averageQualityRating
    """
    try:
        result = await sc.get_review_summary(product_id)
        return json.dumps(result, ensure_ascii=False)
    except Exception as e:
        return json.dumps({"error": str(e)})


@tool
async def search_reviews(product_id: str, keyword: str) -> str:
    """Search reviews for specific topics like sizing, quality, material, etc.

    Args:
        product_id: UUID of the product
        keyword: Search keyword (e.g., '사이즈', '품질', '배송', '소재')

    Returns:
        JSON with matching reviews
    """
    try:
        result = await sc.search_reviews(product_id, keyword)
        return json.dumps(result, ensure_ascii=False)
    except Exception as e:
        return json.dumps({"error": str(e)})


# ============================================================
# Cart / Order Tools
# ============================================================

@tool
async def create_cart(user_id: str) -> str:
    """Create a new shopping cart (draft order) for a user.

    Args:
        user_id: User's UUID

    Returns:
        JSON with the created order (cart) details including order ID
    """
    try:
        result = await sc.create_order(user_id)
        return json.dumps(result, ensure_ascii=False)
    except Exception as e:
        return json.dumps({"error": str(e)})


@tool
async def add_to_cart(
    order_id: str,
    product_id: str,
    product_name: str,
    quantity: int,
    unit_price: int,
    variant_id: Optional[str] = None,
) -> str:
    """Add a product to the shopping cart.

    Args:
        order_id: The cart/draft order UUID
        product_id: Product UUID to add
        product_name: Name of the product
        quantity: Number of items
        unit_price: Price per unit in KRW
        variant_id: Optional variant UUID (for specific size/color)

    Returns:
        JSON with updated order details
    """
    try:
        result = await sc.add_order_item(
            order_id, product_id, variant_id, product_name, quantity, unit_price
        )
        return json.dumps(result, ensure_ascii=False)
    except Exception as e:
        return json.dumps({"error": str(e)})


@tool
async def remove_from_cart(order_id: str, item_id: str) -> str:
    """Remove an item from the shopping cart.

    Args:
        order_id: The cart/draft order UUID
        item_id: The order item UUID to remove

    Returns:
        JSON with updated order details
    """
    try:
        result = await sc.remove_order_item(order_id, item_id)
        return json.dumps(result, ensure_ascii=False)
    except Exception as e:
        return json.dumps({"error": str(e)})


@tool
async def update_cart_item_quantity(order_id: str, item_id: str, quantity: int) -> str:
    """Update quantity of an item in the cart.

    Args:
        order_id: The cart/draft order UUID
        item_id: The order item UUID
        quantity: New quantity

    Returns:
        JSON with updated order details
    """
    try:
        result = await sc.update_order_item(order_id, item_id, quantity)
        return json.dumps(result, ensure_ascii=False)
    except Exception as e:
        return json.dumps({"error": str(e)})


@tool
async def get_order_details(order_id: str) -> str:
    """Get current order/cart details.

    Args:
        order_id: Order UUID

    Returns:
        JSON with order details including items, status, total
    """
    try:
        result = await sc.get_order(order_id)
        return json.dumps(result, ensure_ascii=False)
    except Exception as e:
        return json.dumps({"error": str(e)})


@tool
async def get_user_orders(user_id: str) -> str:
    """Get all orders for a user (including past orders).

    Args:
        user_id: User UUID

    Returns:
        JSON list of orders
    """
    try:
        result = await sc.get_user_orders(user_id)
        return json.dumps(result, ensure_ascii=False)
    except Exception as e:
        return json.dumps({"error": str(e)})


@tool
async def checkout_order(order_id: str) -> str:
    """Submit order for checkout - moves to PENDING_APPROVAL status.
    The user must approve before the order is placed.

    Args:
        order_id: Order UUID

    Returns:
        JSON with checkout details including total amount
    """
    try:
        result = await sc.checkout_order(order_id)
        return json.dumps(result, ensure_ascii=False)
    except Exception as e:
        return json.dumps({"error": str(e)})


@tool
async def approve_order(order_id: str) -> str:
    """Approve and place the order after user confirmation.
    This triggers the payment and inventory reservation process.

    Args:
        order_id: Order UUID (must be in PENDING_APPROVAL status)

    Returns:
        JSON with order status
    """
    try:
        result = await sc.approve_order(order_id)
        return json.dumps(result, ensure_ascii=False)
    except Exception as e:
        return json.dumps({"error": str(e)})


@tool
async def cancel_order(order_id: str, reason: Optional[str] = None) -> str:
    """Cancel an order.

    Args:
        order_id: Order UUID
        reason: Optional cancellation reason

    Returns:
        JSON with cancellation result
    """
    try:
        result = await sc.cancel_order(order_id, reason)
        return json.dumps(result, ensure_ascii=False)
    except Exception as e:
        return json.dumps({"error": str(e)})


@tool
async def request_refund(order_id: str, reason: Optional[str] = None) -> str:
    """Request a refund for a confirmed order.

    Args:
        order_id: Order UUID
        reason: Reason for refund

    Returns:
        JSON with refund request result
    """
    try:
        result = await sc.request_refund(order_id, reason)
        return json.dumps(result, ensure_ascii=False)
    except Exception as e:
        return json.dumps({"error": str(e)})


# ============================================================
# Inventory Tools
# ============================================================

@tool
async def check_inventory(
    product_id: str,
    variant_id: Optional[str] = None,
    quantity: int = 1,
) -> str:
    """Check if a product variant is in stock.

    Args:
        product_id: Product UUID
        variant_id: Variant UUID (optional)
        quantity: Desired quantity

    Returns:
        JSON with availability info
    """
    try:
        result = await sc.check_inventory(product_id, variant_id, quantity)
        return json.dumps(result, ensure_ascii=False)
    except Exception as e:
        return json.dumps({"error": str(e)})


@tool
async def get_product_stock(product_id: str) -> str:
    """Get stock levels for all variants of a product.

    Args:
        product_id: Product UUID

    Returns:
        JSON list with stock info per variant
    """
    try:
        result = await sc.get_product_inventory(product_id)
        return json.dumps(result, ensure_ascii=False)
    except Exception as e:
        return json.dumps({"error": str(e)})


# ============================================================
# Tool Groups
# ============================================================

PRODUCT_TOOLS = [search_products, get_product_details, get_categories]
REVIEW_TOOLS = [get_product_reviews, get_review_summary, search_reviews]
CART_TOOLS = [
    create_cart, add_to_cart, remove_from_cart, update_cart_item_quantity,
    get_order_details, checkout_order,
]
ORDER_TOOLS = [
    get_order_details, get_user_orders, approve_order, cancel_order, request_refund,
]
INVENTORY_TOOLS = [check_inventory, get_product_stock]

ALL_TOOLS = PRODUCT_TOOLS + REVIEW_TOOLS + CART_TOOLS + ORDER_TOOLS + INVENTORY_TOOLS
