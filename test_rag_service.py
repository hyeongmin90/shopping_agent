import asyncio
import httpx
import json

async def wait_for_health():
    print("Waiting for rag-service to be healthy on port 8002...")
    async with httpx.AsyncClient() as client:
        for _ in range(30):
            try:
                res = await client.get("http://localhost:8002/health")
                if res.status_code == 200:
                    print("rag-service IS UP!")
                    return True
            except Exception as e:
                pass
            await asyncio.sleep(2)
    print("rag-service failed to start or is not responding.")
    return False

async def main():
    if not await wait_for_health():
        return
    
    print("\n--- Testing RAG Products Search ---")
    async with httpx.AsyncClient() as client:
        res = await client.get("http://localhost:8002/api/rag/products", params={"query": "셔츠"})
        print(f"Status: {res.status_code}")
        print("Response:", json.dumps(res.json(), indent=2, ensure_ascii=False)[:300], "...")

    print("\n--- Testing RAG Reviews Search ---")
    async with httpx.AsyncClient() as client:
        res = await client.get("http://localhost:8002/api/rag/reviews", params={"query": "사이즈가"})
        print(f"Status: {res.status_code}")
        print("Response:", json.dumps(res.json(), indent=2, ensure_ascii=False)[:300], "...")

if __name__ == "__main__":
    asyncio.run(main())
