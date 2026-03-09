import json
import unittest
from unittest.mock import AsyncMock, patch

from app.graph.tools import (
    search_products,
    get_cart,
    add_to_cart,
    checkout_cart,
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
        # The tool function itself needs to be called. In LangChain, the actual async function is usually .invoke or the raw function can be called. 
        # But wait, `@tool` wraps the function into a BaseTool. The original coroutine is accessible or the tool can be `.ainvoke()`.
        # Let's use the standard `invoke` or `ainvoke` of Langchain's Tool.
        result_str = await search_products.ainvoke({
            "keyword": "Shirt"
        })

        # Assert
        mock_search.assert_awaited_once_with(
            keyword="Shirt",
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
        
        result_str = await search_products.ainvoke({"keyword": "Shirt"})
        
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

    @patch("app.graph.tools.sc.checkout_cart", new_callable=AsyncMock)
    async def test_checkout_cart_success(self, mock_checkout):
        mock_response = {
            "orderId": "o1",
            "status": "PENDING_APPROVAL",
            "totalAmount": 50000
        }
        mock_checkout.return_value = mock_response

        result_str = await checkout_cart.ainvoke({"user_id": "u1"})

        mock_checkout.assert_awaited_once_with("u1")
        result_json = json.loads(result_str)
        self.assertEqual(result_json["orderId"], "o1")
        self.assertEqual(result_json["status"], "PENDING_APPROVAL")

if __name__ == '__main__':
    unittest.main()
