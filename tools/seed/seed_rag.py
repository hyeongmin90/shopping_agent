"""Seed script for loading mock data into PostgreSQL (pgvector).

This script:
1. Creates PostgreSQL pgvector tables (with fts columns)
2. Generates embeddings for products, reviews, and policies
3. Loads mock data into PostgreSQL

Run with: python -m tools.seed.seed_rag
(from the agents/ directory, after the containers are up)
"""

import asyncio
import sys
from pathlib import Path

# Add agents directory to path for imports
sys.path.insert(0, str(Path(__file__).parent.parent.parent / "agents"))

from app.config import settings  # noqa: E402
from app.rag.embeddings import generate_embeddings  # noqa: E402
from app.rag.pgvector_store import ensure_tables, upsert_vectors  # noqa: E402

# ============================================================
# Product mock data (from product-init.sql)
# ============================================================
PRODUCTS = [
    {
        "product_id": "b1000000-0000-0000-0000-000000000001",
        "name": "오버핏 코튼 티셔츠",
        "description": "부드러운 순면 소재의 오버핏 반팔 티셔츠. 데일리 아이템으로 추천.",
        "brand": "무신사 스탠다드",
        "category": "상의",
        "category_name": "상의",
        "base_price": 19900,
        "tags": ["casual", "daily"],
    },
    {
        "product_id": "b1000000-0000-0000-0000-000000000002",
        "name": "슬림핏 옥스포드 셔츠",
        "description": "비즈니스 캐주얼에 적합한 옥스포드 셔츠. 구김이 적은 소재.",
        "brand": "폴로",
        "category": "상의",
        "category_name": "상의",
        "base_price": 89000,
        "tags": ["business", "formal"],
    },
    {
        "product_id": "b1000000-0000-0000-0000-000000000003",
        "name": "캐시미어 블렌드 니트",
        "description": "캐시미어 30% 혼방 니트. 보풀이 적고 부드러운 촉감.",
        "brand": "유니클로",
        "category": "상의",
        "category_name": "상의",
        "base_price": 49900,
        "tags": ["casual", "warm"],
    },
    {
        "product_id": "b1000000-0000-0000-0000-000000000004",
        "name": "스트레이트 데님 팬츠",
        "description": "클래식한 스트레이트 핏 데님. 중간 두께로 사계절 착용 가능.",
        "brand": "리바이스",
        "category": "하의",
        "category_name": "하의",
        "base_price": 79000,
        "tags": ["casual", "daily"],
    },
    {
        "product_id": "b1000000-0000-0000-0000-000000000005",
        "name": "와이드 슬랙스",
        "description": "편안한 와이드 핏 슬랙스. 구김 방지 가공.",
        "brand": "무신사 스탠다드",
        "category": "하의",
        "category_name": "하의",
        "base_price": 39900,
        "tags": ["business", "casual"],
    },
    {
        "product_id": "b1000000-0000-0000-0000-000000000006",
        "name": "경량 패딩 자켓",
        "description": "초경량 다운 패딩. 휴대가 편한 파우치 포함.",
        "brand": "노스페이스",
        "category": "아우터",
        "category_name": "아우터",
        "base_price": 159000,
        "tags": ["outdoor", "warm"],
    },
    {
        "product_id": "b1000000-0000-0000-0000-000000000007",
        "name": "울 블렌드 코트",
        "description": "울 70% 혼방 싱글 코트. 세미 오버핏.",
        "brand": "코스",
        "category": "아우터",
        "category_name": "아우터",
        "base_price": 289000,
        "tags": ["formal", "warm"],
    },
    {
        "product_id": "b1000000-0000-0000-0000-000000000008",
        "name": "갤럭시 S24 Ultra",
        "description": "최신 AI 기능 탑재. 6.8인치 다이나믹 AMOLED 디스플레이.",
        "brand": "삼성",
        "category": "스마트폰",
        "category_name": "스마트폰",
        "base_price": 1599000,
        "tags": ["samsung", "android", "flagship"],
    },
    {
        "product_id": "b1000000-0000-0000-0000-000000000009",
        "name": "아이폰 15 Pro",
        "description": "A17 Pro 칩 탑재. 티타늄 디자인.",
        "brand": "애플",
        "category": "스마트폰",
        "category_name": "스마트폰",
        "base_price": 1550000,
        "tags": ["apple", "ios", "flagship"],
    },
    {
        "product_id": "b1000000-0000-0000-0000-000000000010",
        "name": "맥북 에어 M3",
        "description": "M3 칩, 15인치 Liquid Retina 디스플레이. 18시간 배터리.",
        "brand": "애플",
        "category": "노트북",
        "category_name": "노트북",
        "base_price": 1890000,
        "tags": ["apple", "laptop", "portable"],
    },
    {
        "product_id": "b1000000-0000-0000-0000-000000000011",
        "name": "갤럭시북4 프로",
        "description": "인텔 Ultra 프로세서, 16인치 AMOLED. 경량 설계.",
        "brand": "삼성",
        "category": "노트북",
        "category_name": "노트북",
        "base_price": 1790000,
        "tags": ["samsung", "laptop", "portable"],
    },
    {
        "product_id": "b1000000-0000-0000-0000-000000000012",
        "name": "에어팟 프로 2",
        "description": "적응형 노이즈 캔슬링, USB-C 충전. H2 칩.",
        "brand": "애플",
        "category": "이어폰/헤드폰",
        "category_name": "이어폰/헤드폰",
        "base_price": 359000,
        "tags": ["apple", "audio", "wireless"],
    },
    {
        "product_id": "b1000000-0000-0000-0000-000000000013",
        "name": "갤럭시 버즈3 프로",
        "description": "360 오디오, 지능형 ANC. 방수 IPX7.",
        "brand": "삼성",
        "category": "이어폰/헤드폰",
        "category_name": "이어폰/헤드폰",
        "base_price": 299000,
        "tags": ["samsung", "audio", "wireless"],
    },
    {
        "product_id": "b1000000-0000-0000-0000-000000000014",
        "name": "WH-1000XM5",
        "description": "업계 최고의 노이즈 캔슬링 헤드폰. 30시간 배터리.",
        "brand": "소니",
        "category": "이어폰/헤드폰",
        "category_name": "이어폰/헤드폰",
        "base_price": 399000,
        "tags": ["sony", "audio", "wireless", "headphone"],
    },
    {
        "product_id": "b1000000-0000-0000-0000-000000000015",
        "name": "에어포스 1 07",
        "description": "클래식 화이트 스니커즈. 에어 유닛 쿠셔닝.",
        "brand": "나이키",
        "category": "신발",
        "category_name": "신발",
        "base_price": 139000,
        "tags": ["casual", "sneaker"],
    },
    {
        "product_id": "b1000000-0000-0000-0000-000000000016",
        "name": "뉴발란스 530",
        "description": "레트로 러닝 스니커즈. ABZORB 쿠셔닝.",
        "brand": "뉴발란스",
        "category": "신발",
        "category_name": "신발",
        "base_price": 139000,
        "tags": ["casual", "sneaker", "retro"],
    },
    {
        "product_id": "b1000000-0000-0000-0000-000000000017",
        "name": "클래식 가죽 벨트",
        "description": "이탈리안 소가죽 벨트. 자동 버클.",
        "brand": "몽블랑",
        "category": "액세서리",
        "category_name": "액세서리",
        "base_price": 250000,
        "tags": ["formal", "leather"],
    },
    {
        "product_id": "b1000000-0000-0000-0000-000000000018",
        "name": "캔버스 토트백",
        "description": "대용량 캔버스 토트백. A4 수납 가능.",
        "brand": "무신사 스탠다드",
        "category": "액세서리",
        "category_name": "액세서리",
        "base_price": 29900,
        "tags": ["casual", "bag"],
    },
]

# ============================================================
# Review mock data (from review-init.sql)
# ============================================================
REVIEWS = [
    {
        "review_id": "rev-001",
        "product_id": "b1000000-0000-0000-0000-000000000001",
        "product_name": "오버핏 코튼 티셔츠",
        "rating": 5,
        "title": "가성비 최고",
        "content": "이 가격에 이 퀄리티는 진짜 말이 안 됩니다. 세탁 3번 했는데 늘어남 없고 색빠짐도 없어요. 오버핏이라 M 사이즈 추천합니다. 175/65 기준 M 딱 좋아요.",
        "quality_rating": 5,
        "verified_purchase": True,
        "helpful_count": 42,
    },
    {
        "review_id": "rev-002",
        "product_id": "b1000000-0000-0000-0000-000000000001",
        "product_name": "오버핏 코튼 티셔츠",
        "rating": 4,
        "title": "괜찮은 티셔츠",
        "content": "무난하게 입기 좋아요. 다만 화이트 색상은 약간 비침이 있어서 이너 필요합니다. 사이즈는 넉넉한 편.",
        "quality_rating": 4,
        "verified_purchase": True,
        "helpful_count": 18,
    },
    {
        "review_id": "rev-003",
        "product_id": "b1000000-0000-0000-0000-000000000001",
        "product_name": "오버핏 코튼 티셔츠",
        "rating": 3,
        "title": "보통이에요",
        "content": "가격 대비 적당해요. 근데 목 부분이 좀 늘어나는 감이 있습니다. 보풀도 조금 생기네요.",
        "quality_rating": 3,
        "verified_purchase": True,
        "helpful_count": 8,
    },
    {
        "review_id": "rev-004",
        "product_id": "b1000000-0000-0000-0000-000000000002",
        "product_name": "슬림핏 옥스포드 셔츠",
        "rating": 5,
        "title": "출근용으로 완벽",
        "content": "비즈니스 캐주얼에 딱이에요. 구김도 적고 핏도 예쁩니다. 180/72 L 입었는데 팔 길이도 딱 맞아요.",
        "quality_rating": 5,
        "verified_purchase": True,
        "helpful_count": 35,
    },
    {
        "review_id": "rev-005",
        "product_id": "b1000000-0000-0000-0000-000000000002",
        "product_name": "슬림핏 옥스포드 셔츠",
        "rating": 4,
        "title": "핏이 예뻐요",
        "content": "슬림핏이라 몸에 붙는 느낌인데 불편하지 않아요. 다만 팔이 긴 분들은 한 사이즈 업 추천.",
        "quality_rating": 4,
        "verified_purchase": True,
        "helpful_count": 22,
    },
    {
        "review_id": "rev-006",
        "product_id": "b1000000-0000-0000-0000-000000000003",
        "product_name": "캐시미어 블렌드 니트",
        "rating": 5,
        "title": "부드럽고 따뜻해요",
        "content": "캐시미어 비율이 높아서 촉감이 좋아요. 보풀도 거의 안 생기고 세탁도 편합니다. 170/60 M 착용.",
        "quality_rating": 5,
        "verified_purchase": True,
        "helpful_count": 29,
    },
    {
        "review_id": "rev-007",
        "product_id": "b1000000-0000-0000-0000-000000000003",
        "product_name": "캐시미어 블렌드 니트",
        "rating": 4,
        "title": "색감이 좋아요",
        "content": "사진과 실물 색감 차이 없고 고급스러워요. 약간 짧은 느낌이 있어서 키 큰 분은 사이즈 업.",
        "quality_rating": 4,
        "verified_purchase": True,
        "helpful_count": 15,
    },
    {
        "review_id": "rev-008",
        "product_id": "b1000000-0000-0000-0000-000000000004",
        "product_name": "스트레이트 데님 팬츠",
        "rating": 5,
        "title": "리바이스는 역시",
        "content": "핏이 진짜 예쁩니다. 원단도 두껍지 않아 사계절 가능. 30 사이즈 허리 딱 맞아요. 175/68 기준.",
        "quality_rating": 5,
        "verified_purchase": True,
        "helpful_count": 48,
    },
    {
        "review_id": "rev-009",
        "product_id": "b1000000-0000-0000-0000-000000000004",
        "product_name": "스트레이트 데님 팬츠",
        "rating": 3,
        "title": "허리가 좀 큽니다",
        "content": "길이는 좋은데 허리가 넉넉해요. 한 사이즈 다운 추천. 원단 질감은 좋습니다.",
        "quality_rating": 3,
        "verified_purchase": True,
        "helpful_count": 12,
    },
    {
        "review_id": "rev-010",
        "product_id": "b1000000-0000-0000-0000-000000000006",
        "product_name": "경량 패딩 자켓",
        "rating": 5,
        "title": "가볍고 따뜻해요",
        "content": "무게가 거의 안 느껴지는데 보온성은 확실해요. 파우치에 넣으면 가방에 쏙 들어가서 좋습니다.",
        "quality_rating": 5,
        "verified_purchase": True,
        "helpful_count": 55,
    },
    {
        "review_id": "rev-011",
        "product_id": "b1000000-0000-0000-0000-000000000006",
        "product_name": "경량 패딩 자켓",
        "rating": 4,
        "title": "등산할 때 좋아요",
        "content": "아우터 안에 레이어드하기 좋은 두께입니다. 바람이 좀 들어오긴 하지만 무게 대비 훌륭합니다.",
        "quality_rating": 4,
        "verified_purchase": True,
        "helpful_count": 23,
    },
    {
        "review_id": "rev-012",
        "product_id": "b1000000-0000-0000-0000-000000000008",
        "product_name": "갤럭시 S24 Ultra",
        "rating": 5,
        "title": "AI 기능이 미쳤어요",
        "content": "통역 기능이랑 사진 편집 AI가 진짜 편합니다. 배터리도 하루 종일 쓰고도 남아요. 카메라 성능은 말할 것도 없고요.",
        "quality_rating": 5,
        "verified_purchase": True,
        "helpful_count": 88,
    },
    {
        "review_id": "rev-013",
        "product_id": "b1000000-0000-0000-0000-000000000008",
        "product_name": "갤럭시 S24 Ultra",
        "rating": 4,
        "title": "무겁지만 좋아요",
        "content": "234g이라 좀 무겁긴 한데 성능은 압도적이에요. S펜도 유용합니다. 가격이 좀 부담되지만 후회 없어요.",
        "quality_rating": 4,
        "verified_purchase": True,
        "helpful_count": 45,
    },
    {
        "review_id": "rev-014",
        "product_id": "b1000000-0000-0000-0000-000000000009",
        "product_name": "아이폰 15 Pro",
        "rating": 5,
        "title": "티타늄 디자인 최고",
        "content": "가볍고 고급스러워요. 카메라 특히 동영상 촬영이 좋습니다. 에어팟이랑 맥북이랑 연동 완벽.",
        "quality_rating": 5,
        "verified_purchase": True,
        "helpful_count": 72,
    },
    {
        "review_id": "rev-015",
        "product_id": "b1000000-0000-0000-0000-000000000009",
        "product_name": "아이폰 15 Pro",
        "rating": 4,
        "title": "만족합니다",
        "content": "안드로이드에서 넘어왔는데 적응하면 편해요. 다만 충전 속도가 아쉽고 가격 대비 용량이 적습니다.",
        "quality_rating": 4,
        "verified_purchase": True,
        "helpful_count": 33,
    },
    {
        "review_id": "rev-016",
        "product_id": "b1000000-0000-0000-0000-000000000012",
        "product_name": "에어팟 프로 2",
        "rating": 5,
        "title": "노이즈 캔슬링 최강",
        "content": "지하철에서 완전 조용해집니다. 통화 품질도 좋고 음질은 이어폰 중에서 최고급은 아니지만 충분해요.",
        "quality_rating": 5,
        "verified_purchase": True,
        "helpful_count": 61,
    },
    {
        "review_id": "rev-017",
        "product_id": "b1000000-0000-0000-0000-000000000012",
        "product_name": "에어팟 프로 2",
        "rating": 3,
        "title": "귀가 좀 아파요",
        "content": "2시간 이상 착용하면 귀가 아프네요. 이어팁 바꾸면 나아질 수도 있지만 개인차가 있을 것 같아요.",
        "quality_rating": 3,
        "verified_purchase": True,
        "helpful_count": 28,
    },
    {
        "review_id": "rev-018",
        "product_id": "b1000000-0000-0000-0000-000000000015",
        "product_name": "에어포스 1 07",
        "rating": 5,
        "title": "클래식은 이유가 있다",
        "content": "어떤 옷에도 다 어울려요. 발볼 넓은 편이라 평소 사이즈 추천. 270 신었는데 딱 맞아요.",
        "quality_rating": 5,
        "verified_purchase": True,
        "helpful_count": 39,
    },
    {
        "review_id": "rev-019",
        "product_id": "b1000000-0000-0000-0000-000000000015",
        "product_name": "에어포스 1 07",
        "rating": 4,
        "title": "무거운 게 단점",
        "content": "디자인은 완벽한데 좀 무거워요. 오래 걸으면 발이 피로해질 수 있어요. 반사이즈 업 추천합니다.",
        "quality_rating": 4,
        "verified_purchase": True,
        "helpful_count": 21,
    },
    {
        "review_id": "rev-020",
        "product_id": "b1000000-0000-0000-0000-000000000016",
        "product_name": "뉴발란스 530",
        "rating": 5,
        "title": "쿠셔닝이 좋아요",
        "content": "오래 걸어도 발이 편합니다. 디자인도 레트로해서 예쁘고 활용도 높아요. 정사이즈 추천.",
        "quality_rating": 5,
        "verified_purchase": True,
        "helpful_count": 44,
    },
    {
        "review_id": "rev-021",
        "product_id": "b1000000-0000-0000-0000-000000000014",
        "product_name": "WH-1000XM5",
        "rating": 5,
        "title": "헤드폰의 끝판왕",
        "content": "노이즈 캔슬링이 미쳤어요. 비행기에서 엔진소리가 안 들려요. 음질도 하이레졸 지원하고 배터리도 30시간.",
        "quality_rating": 5,
        "verified_purchase": True,
        "helpful_count": 92,
    },
    {
        "review_id": "rev-022",
        "product_id": "b1000000-0000-0000-0000-000000000014",
        "product_name": "WH-1000XM5",
        "rating": 4,
        "title": "가격 빼면 완벽",
        "content": "성능은 인정하는데 40만원이 좀 부담됩니다. 그래도 쓰면 쓸수록 만족도 올라가요. 접히지 않는 건 아쉬워요.",
        "quality_rating": 4,
        "verified_purchase": True,
        "helpful_count": 37,
    },
]

# Import policy documents
from tools.seed.qdrant.mock_policies import POLICY_DOCUMENTS  # noqa: E402


async def seed_products() -> None:
    """Seed product data into PostgreSQL (vector + fts)."""
    print("📦 Seeding products...")

    # Prepare texts for embedding: name + description + brand
    texts = [
        f"{p['name']} {p['description']} {p['brand']}" for p in PRODUCTS
    ]
    embeddings = await generate_embeddings(texts)

    # PostgreSQL: upsert product vectors
    ids = [p["product_id"] for p in PRODUCTS]
    payloads = [
        {
            "product_id": p["product_id"],
            "name": p["name"],
            "description": p["description"],
            "brand": p["brand"],
            "category": p["category"],
            "category_name": p["category_name"],
            "base_price": p["base_price"],
            "tags": p["tags"],
        }
        for p in PRODUCTS
    ]
    await upsert_vectors(
        table_name=settings.POSTGRES_PRODUCT_TABLE,
        ids=ids,
        vectors=embeddings,
        payloads=payloads,
        search_texts=texts,
    )
    print(f"  ✅ PostgreSQL: {len(PRODUCTS)} product vectors & fts indexed")


async def seed_reviews() -> None:
    """Seed review data into PostgreSQL."""
    print("📝 Seeding reviews...")

    # Prepare texts for embedding: title + content
    texts = [
        f"{r['title']} {r['content']}" for r in REVIEWS
    ]
    embeddings = await generate_embeddings(texts)

    # PostgreSQL: upsert review vectors
    ids = [r["review_id"] for r in REVIEWS]
    payloads = [
        {
            "review_id": r["review_id"],
            "product_id": r["product_id"],
            "product_name": r["product_name"],
            "rating": r["rating"],
            "title": r["title"],
            "content": r["content"],
            "quality_rating": r["quality_rating"],
            "verified_purchase": r["verified_purchase"],
            "helpful_count": r["helpful_count"],
        }
        for r in REVIEWS
    ]
    await upsert_vectors(
        table_name=settings.POSTGRES_REVIEW_TABLE,
        ids=ids,
        vectors=embeddings,
        payloads=payloads,
        search_texts=texts,
    )
    print(f"  ✅ PostgreSQL: {len(REVIEWS)} review vectors & fts indexed")


async def seed_policies() -> None:
    """Seed policy data into PostgreSQL (vector + fts)."""
    print("📋 Seeding policies...")

    # Prepare texts for embedding: title + content
    texts = [
        f"{p['title']} {p['content']}" for p in POLICY_DOCUMENTS
    ]
    embeddings = await generate_embeddings(texts)

    # PostgreSQL: upsert policy vectors
    ids = [p["policy_id"] for p in POLICY_DOCUMENTS]
    payloads = [
        {
            "policy_id": p["policy_id"],
            "title": p["title"],
            "content": p["content"],
            "category": p["category"],
        }
        for p in POLICY_DOCUMENTS
    ]
    await upsert_vectors(
        table_name=settings.POSTGRES_POLICY_TABLE,
        ids=ids,
        vectors=embeddings,
        payloads=payloads,
        search_texts=texts,
    )
    print(f"  ✅ PostgreSQL: {len(POLICY_DOCUMENTS)} policy vectors & fts indexed")


async def main() -> None:
    """Run all seed operations."""
    print("🚀 Starting RAG seed process...")
    host_part = settings.POSTGRES_AGENT_URL.split("@")[1] if "@" in settings.POSTGRES_AGENT_URL else "localhost"
    print(f"  PostgreSQL: {host_part}")
    print(f"  Embedding model: {settings.OPENAI_EMBEDDING_MODEL}")
    print()

    # 1. Create tables
    print("🏗️  Creating tables and indices...")
    await ensure_tables()
    print()

    # 2. Seed data
    await seed_products()
    print()
    await seed_reviews()
    print()
    await seed_policies()
    print()

    print("✅ All RAG seed data loaded successfully!")


if __name__ == "__main__":
    asyncio.run(main())
