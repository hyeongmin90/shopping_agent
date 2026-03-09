import asyncio
import asyncpg
from app.config import settings

async def init_db():
    print("Connecting to DB without pool to install extension...")
    conn = await asyncpg.connect(settings.POSTGRES_AGENT_URL)
    await conn.execute('CREATE EXTENSION IF NOT EXISTS vector;')
    await conn.close()
    print("Extension installed successfully. Ensuring tables...")

    from app.rag.pgvector_store import ensure_tables
    await ensure_tables()
    print('DB Intialized Successfully')

asyncio.run(init_db())
