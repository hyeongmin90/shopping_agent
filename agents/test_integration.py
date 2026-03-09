import asyncio
import json
import uuid
from termcolor import colored

from app.graph.tools import (
    search_products,
    get_categories,
    get_product_details,
    get_product_reviews,
    get_review_summary,
    search_reviews,
    get_cart,
    add_to_cart,
    update_cart_item_quantity,
    remove_from_cart,
    checkout_cart,
    get_user_orders,
    get_order_details,
    approve_order,
    cancel_order,
    request_refund,
    check_inventory,
    get_product_stock,
    rag_search_products,
    rag_search_reviews,
    rag_search_policies
)

async def run_tool(name, tool_func, kwargs):
    print(f"[{colored('TEST', 'cyan')}] Running {name}...")
    try:
        # LangChain tools use .ainvoke()
        result_str = await tool_func.ainvoke(kwargs)
        result = json.loads(result_str)
        
        if isinstance(result, dict) and "error" in result:
            print(f"  {colored('ERROR', 'red')}: {result['error']}")
            return None
            
        print(f"  {colored('SUCCESS', 'green')}")
        return result
    except Exception as e:
        print(f"  {colored('EXCEPTION', 'red')}: {str(e)}")
        return None

async def main():
    user_id = str(uuid.uuid4())
    print(f"Starting Integration Test Suite with User ID: {user_id}")
    print("-" * 50)

    # 1. Product Tools
    categories = await run_tool("get_categories", get_categories, {})
    
    products = await run_tool("search_products", search_products, {"keyword": "셔츠"})
    product_id = None
    variant_id = None
    if products and "content" in products and len(products["content"]) > 0:
        product_id = products["content"][0]["id"]
        
        # Get details to find variant
        details = await run_tool("get_product_details", get_product_details, {"product_id": product_id})
        if details and "variants" in details and len(details["variants"]) > 0:
            variant_id = details["variants"][0]["id"]

    if not product_id:
        print(colored("No product found to continue further tests. Make sure product-service and DB are populated.", "yellow"))
    else:
        # 2. Review Tools
        await run_tool("get_product_reviews", get_product_reviews, {"product_id": product_id})
        await run_tool("get_review_summary", get_review_summary, {"product_id": product_id})
        await run_tool("search_reviews", search_reviews, {"product_id": product_id, "keyword": "사이즈"})

        # 3. Inventory Tools
        test_variant_id = variant_id if variant_id else str(uuid.uuid4())
        await run_tool("check_inventory", check_inventory, {"product_id": product_id, "variant_id": test_variant_id, "quantity": 1})
        await run_tool("get_product_stock", get_product_stock, {"product_id": product_id})

        # 4. Cart & Order Tools (The core flow)
        await run_tool("get_cart (Initial)", get_cart, {"user_id": user_id})
        
        cart_after_add = await run_tool("add_to_cart", add_to_cart, {
            "product_id": product_id, 
            "variant_id": test_variant_id, 
            "quantity": 1, 
            "user_id": user_id
        })
        
        item_id = None
        if cart_after_add and "items" in cart_after_add and len(cart_after_add["items"]) > 0:
            item_id = cart_after_add["items"][0]["id"]
            
            # Update quantity
            await run_tool("update_cart_item_quantity", update_cart_item_quantity, {
                "item_id": item_id, 
                "product_id": product_id,
                "quantity": 2, 
                "user_id": user_id,
                "variant_id": test_variant_id
            })

            # Since we want to test checkout, we will NOT remove it.
        
        # Checkout
        checkout_res = await run_tool("checkout_cart", checkout_cart, {"user_id": user_id})
        order_id = None
        if checkout_res and "orderId" in checkout_res:
            order_id = checkout_res["orderId"]
            
            # Order details & user orders
            await run_tool("get_order_details", get_order_details, {"order_id": order_id})
            await run_tool("get_user_orders", get_user_orders, {"user_id": user_id})
            
            # Approve order
            await run_tool("approve_order", approve_order, {"order_id": order_id})
            
            # Note: Canceling/Refund might fail dependent on the strict state machine of the order, 
            # but we invoke it to test connectivity
            await run_tool("cancel_order", cancel_order, {"order_id": order_id, "reason": "test cancel"})

    print("-" * 50)
    # 5. RAG Tools (Optional, tests Vector DB)
    await run_tool("rag_search_products", rag_search_products, {"query": "시원한 여름 셔츠"})
    await run_tool("rag_search_policies", rag_search_policies, {"query": "환불 기한"})
    
    if product_id:
        await run_tool("rag_search_reviews", rag_search_reviews, {"query": "사이즈가 어떤가요", "product_id": product_id})

    print(colored("Integration Test Suite Finished.", "green"))

if __name__ == "__main__":
    asyncio.run(main())
