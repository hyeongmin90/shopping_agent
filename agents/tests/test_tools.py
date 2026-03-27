import json
import unittest
from unittest.mock import AsyncMock, patch

from app.graph.tools import (
    search_products,
    get_cart,
    add_to_cart,
    remove_from_cart,
    update_cart_item_quantity,
    get_order_details,
    get_user_orders,
    check_inventory,
    get_product_stock,
)


class TestAgentTools(unittest.IsolatedAsyncioTestCase):

    @patch("app.graph.tools.sc.search_products", new_callable=AsyncMock)
    async def test_search_products_success(self, mock_search):
        # Arrange
        mock_response = {
            "content": [
                {"id": "p1", "name": "Test Shirt", "price": 10000}
            ],
            "totalElements": 1
        }
        mock_search.return_value = mock_response

        # Act
        result_str = await search_products.ainvoke({
            "search": "Shirt"
        })

        # Assert
        mock_search.assert_awaited_once_with(
            search="Shirt",
            category=None,
            brand=None,
            min_price=None,
            max_price=None
        )
        
        result_json = json.loads(result_str)
        self.assertEqual(result_json["totalElements"], 1)
        self.assertEqual(result_json["content"][0]["name"], "Test Shirt")

    @patch("app.graph.tools.sc.search_products", new_callable=AsyncMock)
    async def test_search_products_exception(self, mock_search):
        mock_search.side_effect = Exception("Service unavailable")
        
        result_str = await search_products.ainvoke({"search": "Shirt"})
        
        result_json = json.loads(result_str)
        self.assertIn("error", result_json)
        self.assertEqual(result_json["error"], "Service unavailable")

    @patch("app.graph.tools.sc.get_cart", new_callable=AsyncMock)
    async def test_get_cart_success(self, mock_get_cart):
        mock_response = {
            "id": "c1",
            "userId": "u1",
            "items": [],
            "totalAmount": 0
        }
        mock_get_cart.return_value = mock_response

        result_str = await get_cart.ainvoke({"user_id": "u1"})

        mock_get_cart.assert_awaited_once_with("u1")
        result_json = json.loads(result_str)
        self.assertEqual(result_json["userId"], "u1")
        self.assertEqual(result_json["totalAmount"], 0)

    @patch("app.graph.tools.sc.add_cart_item", new_callable=AsyncMock)
    async def test_add_to_cart_success(self, mock_add_item):
        mock_response = {
            "id": "c1",
            "items": [{"productId": "p1", "quantity": 2}],
            "totalAmount": 20000
        }
        mock_add_item.return_value = mock_response

        result_str = await add_to_cart.ainvoke({
            "product_id": "p1",
            "quantity": 2,
            "user_id": "u1",
            "variant_id": "v1"
        })

        mock_add_item.assert_awaited_once_with("u1", "p1", "v1", 2)
        result_json = json.loads(result_str)
        self.assertEqual(len(result_json["items"]), 1)
        self.assertEqual(result_json["totalAmount"], 20000)

    @patch("app.graph.tools.sc.remove_cart_item", new_callable=AsyncMock)
    async def test_remove_from_cart_success(self, mock_remove):
        mock_response = {
            "id": "c1",
            "items": [],
            "totalAmount": 0
        }
        mock_remove.return_value = mock_response

        result_str = await remove_from_cart.ainvoke({
            "item_id": "item1",
            "user_id": "u1"
        })

        mock_remove.assert_awaited_once_with("u1", "item1")
        result_json = json.loads(result_str)
        self.assertEqual(result_json["items"], [])

    @patch("app.graph.tools.sc.update_cart_item", new_callable=AsyncMock)
    async def test_update_cart_item_quantity_success(self, mock_update):
        mock_response = {
            "id": "c1",
            "items": [{"productId": "p1", "quantity": 5}],
            "totalAmount": 50000
        }
        mock_update.return_value = mock_response

        result_str = await update_cart_item_quantity.ainvoke({
            "item_id": "item1",
            "product_id": "p1",
            "quantity": 5,
            "user_id": "u1",
            "variant_id": "v1"
        })

        mock_update.assert_awaited_once_with("u1", "item1", "p1", "v1", 5)
        result_json = json.loads(result_str)
        self.assertEqual(result_json["items"][0]["quantity"], 5)
        self.assertEqual(result_json["totalAmount"], 50000)

    @patch("app.graph.tools.sc.get_order", new_callable=AsyncMock)
    async def test_get_order_details_success(self, mock_get_order):
        mock_response = {
            "id": "o1",
            "status": "CONFIRMED",
            "totalAmount": 30000,
            "items": [{"productId": "p1", "quantity": 3}]
        }
        mock_get_order.return_value = mock_response

        result_str = await get_order_details.ainvoke({"order_id": "o1"})

        mock_get_order.assert_awaited_once_with("o1")
        result_json = json.loads(result_str)
        self.assertEqual(result_json["status"], "CONFIRMED")
        self.assertEqual(result_json["totalAmount"], 30000)

    @patch("app.graph.tools.sc.get_user_orders", new_callable=AsyncMock)
    async def test_get_user_orders_success(self, mock_get_orders):
        mock_response = [
            {"id": "o1", "status": "CONFIRMED", "totalAmount": 30000},
            {"id": "o2", "status": "DELIVERED", "totalAmount": 15000}
        ]
        mock_get_orders.return_value = mock_response

        result_str = await get_user_orders.ainvoke({"user_id": "u1"})

        mock_get_orders.assert_awaited_once_with("u1")
        result_json = json.loads(result_str)
        self.assertEqual(len(result_json), 2)
        self.assertEqual(result_json[0]["id"], "o1")

    @patch("app.graph.tools.sc.check_inventory", new_callable=AsyncMock)
    async def test_check_inventory_success(self, mock_check):
        mock_response = {
            "available": True,
            "quantity": 10,
            "productId": "p1",
            "variantId": "v1"
        }
        mock_check.return_value = mock_response

        result_str = await check_inventory.ainvoke({
            "product_id": "p1",
            "variant_id": "v1",
            "quantity": 2
        })

        mock_check.assert_awaited_once_with("p1", "v1", 2)
        result_json = json.loads(result_str)
        self.assertTrue(result_json["available"])
        self.assertEqual(result_json["quantity"], 10)

    @patch("app.graph.tools.sc.get_product_inventory", new_callable=AsyncMock)
    async def test_get_product_stock_success(self, mock_stock):
        mock_response = [
            {"variantId": "v1", "quantity": 10},
            {"variantId": "v2", "quantity": 0}
        ]
        mock_stock.return_value = mock_response

        result_str = await get_product_stock.ainvoke({"product_id": "p1"})

        mock_stock.assert_awaited_once_with("p1")
        result_json = json.loads(result_str)
        self.assertEqual(len(result_json), 2)
        self.assertEqual(result_json[0]["quantity"], 10)

    @patch("app.graph.tools.sc.get_order", new_callable=AsyncMock)
    async def test_get_order_details_exception(self, mock_get_order):
        mock_get_order.side_effect = Exception("Order not found")

        result_str = await get_order_details.ainvoke({"order_id": "invalid"})

        result_json = json.loads(result_str)
        self.assertIn("error", result_json)
        self.assertEqual(result_json["error"], "Order not found")


if __name__ == '__main__':
    unittest.main()
