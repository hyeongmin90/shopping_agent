"""기존 상품 데이터를 pgvector에 인덱싱하는 스크립트.

실행 방법 (프로젝트 루트에서):
    python tools/seed/seed_products_rag.py
"""

import asyncio
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent.parent / "rag-service"))

from app.config import settings  # noqa: E402
from app.rag.embeddings import generate_embeddings  # noqa: E402
from app.rag.pgvector_store import upsert_vectors  # noqa: E402

PRODUCTS = [
    {
        "product_id": "b1000000-0000-0000-0000-000000000001",
        "name": "오버핏 코튼 티셔츠",
        "description": "부드러운 순면 소재의 오버핏 반팔 티셔츠. 데일리 아이템으로 추천.",
        "brand": "무신사 스탠다드",
        "category_name": "상의",
        "base_price": 19900,
        "currency": "KRW",
        "status": "ACTIVE",
    },
    {
        "product_id": "b1000000-0000-0000-0000-000000000002",
        "name": "슬림핏 옥스포드 셔츠",
        "description": "비즈니스 캐주얼에 적합한 옥스포드 셔츠. 구김이 적은 소재.",
        "brand": "폴로",
        "category_name": "상의",
        "base_price": 89000,
        "currency": "KRW",
        "status": "ACTIVE",
    },
    {
        "product_id": "b1000000-0000-0000-0000-000000000003",
        "name": "캐시미어 블렌드 니트",
        "description": "캐시미어 30% 혼방 니트. 보풀이 적고 부드러운 촉감.",
        "brand": "유니클로",
        "category_name": "상의",
        "base_price": 49900,
        "currency": "KRW",
        "status": "ACTIVE",
    },
    {
        "product_id": "b1000000-0000-0000-0000-000000000004",
        "name": "스트레이트 데님 팬츠",
        "description": "클래식한 스트레이트 핏 데님. 중간 두께로 사계절 착용 가능.",
        "brand": "리바이스",
        "category_name": "하의",
        "base_price": 79000,
        "currency": "KRW",
        "status": "ACTIVE",
    },
    {
        "product_id": "b1000000-0000-0000-0000-000000000005",
        "name": "와이드 슬랙스",
        "description": "편안한 와이드 핏 슬랙스. 구김 방지 가공.",
        "brand": "무신사 스탠다드",
        "category_name": "하의",
        "base_price": 39900,
        "currency": "KRW",
        "status": "ACTIVE",
    },
    {
        "product_id": "b1000000-0000-0000-0000-000000000006",
        "name": "경량 패딩 자켓",
        "description": "초경량 다운 패딩. 휴대가 편한 파우치 포함.",
        "brand": "노스페이스",
        "category_name": "아우터",
        "base_price": 159000,
        "currency": "KRW",
        "status": "ACTIVE",
    },
    {
        "product_id": "b1000000-0000-0000-0000-000000000007",
        "name": "울 블렌드 코트",
        "description": "울 70% 혼방 싱글 코트. 세미 오버핏.",
        "brand": "코스",
        "category_name": "아우터",
        "base_price": 289000,
        "currency": "KRW",
        "status": "ACTIVE",
    },
    {
        "product_id": "b1000000-0000-0000-0000-000000000008",
        "name": "갤럭시 S24 Ultra",
        "description": "최신 AI 기능 탑재. 6.8인치 다이나믹 AMOLED 디스플레이.",
        "brand": "삼성",
        "category_name": "스마트폰",
        "base_price": 1599000,
        "currency": "KRW",
        "status": "ACTIVE",
    },
    {
        "product_id": "b1000000-0000-0000-0000-000000000009",
        "name": "아이폰 15 Pro",
        "description": "A17 Pro 칩 탑재. 티타늄 디자인.",
        "brand": "애플",
        "category_name": "스마트폰",
        "base_price": 1550000,
        "currency": "KRW",
        "status": "ACTIVE",
    },
    {
        "product_id": "b1000000-0000-0000-0000-000000000010",
        "name": "맥북 에어 M3",
        "description": "M3 칩, 15인치 Liquid Retina 디스플레이. 18시간 배터리.",
        "brand": "애플",
        "category_name": "노트북",
        "base_price": 1890000,
        "currency": "KRW",
        "status": "ACTIVE",
    },
    {
        "product_id": "b1000000-0000-0000-0000-000000000011",
        "name": "갤럭시북4 프로",
        "description": "인텔 Ultra 프로세서, 16인치 AMOLED. 경량 설계.",
        "brand": "삼성",
        "category_name": "노트북",
        "base_price": 1790000,
        "currency": "KRW",
        "status": "ACTIVE",
    },
    {
        "product_id": "b1000000-0000-0000-0000-000000000012",
        "name": "에어팟 프로 2",
        "description": "적응형 노이즈 캔슬링, USB-C 충전. H2 칩.",
        "brand": "애플",
        "category_name": "이어폰/헤드폰",
        "base_price": 359000,
        "currency": "KRW",
        "status": "ACTIVE",
    },
    {
        "product_id": "b1000000-0000-0000-0000-000000000013",
        "name": "갤럭시 버즈3 프로",
        "description": "360 오디오, 지능형 ANC. 방수 IPX7.",
        "brand": "삼성",
        "category_name": "이어폰/헤드폰",
        "base_price": 299000,
        "currency": "KRW",
        "status": "ACTIVE",
    },
    {
        "product_id": "b1000000-0000-0000-0000-000000000014",
        "name": "WH-1000XM5",
        "description": "업계 최고의 노이즈 캔슬링 헤드폰. 30시간 배터리.",
        "brand": "소니",
        "category_name": "이어폰/헤드폰",
        "base_price": 399000,
        "currency": "KRW",
        "status": "ACTIVE",
    },
    {
        "product_id": "b1000000-0000-0000-0000-000000000015",
        "name": "에어포스 1 07",
        "description": "클래식 화이트 스니커즈. 에어 유닛 쿠셔닝.",
        "brand": "나이키",
        "category_name": "신발",
        "base_price": 139000,
        "currency": "KRW",
        "status": "ACTIVE",
    },
    {
        "product_id": "b1000000-0000-0000-0000-000000000016",
        "name": "뉴발란스 530",
        "description": "레트로 러닝 스니커즈. ABZORB 쿠셔닝.",
        "brand": "뉴발란스",
        "category_name": "신발",
        "base_price": 139000,
        "currency": "KRW",
        "status": "ACTIVE",
    },
    {
        "product_id": "b1000000-0000-0000-0000-000000000017",
        "name": "클래식 가죽 벨트",
        "description": "이탈리안 소가죽 벨트. 자동 버클.",
        "brand": "몽블랑",
        "category_name": "액세서리",
        "base_price": 250000,
        "currency": "KRW",
        "status": "ACTIVE",
    },
    {
        "product_id": "b1000000-0000-0000-0000-000000000018",
        "name": "캔버스 토트백",
        "description": "대용량 캔버스 토트백. A4 수납 가능.",
        "brand": "무신사 스탠다드",
        "category_name": "액세서리",
        "base_price": 29900,
        "currency": "KRW",
        "status": "ACTIVE",
    },
]


async def main() -> None:
    print(f"pgvector 대상: {settings.POSTGRES_AGENT_URL.split('@')[-1]}")
    print(f"테이블: {settings.POSTGRES_PRODUCT_TABLE}")
    print(f"임베딩 모델: {settings.OPENAI_EMBEDDING_MODEL}")
    print()

    texts = [
        " ".join(filter(None, [p["name"], p["brand"], p["category_name"], p["description"]]))
        for p in PRODUCTS
    ]

    print(f"임베딩 생성 중... ({len(PRODUCTS)}개)")
    vectors = await generate_embeddings(texts)

    ids = [p["product_id"] for p in PRODUCTS]
    payloads = [
        {
            "product_id": p["product_id"],
            "name": p["name"],
            "description": p["description"],
            "brand": p["brand"],
            "category_name": p["category_name"],
            "base_price": p["base_price"],
            "currency": p["currency"],
            "status": p["status"],
        }
        for p in PRODUCTS
    ]

    await upsert_vectors(
        table_name=settings.POSTGRES_PRODUCT_TABLE,
        ids=ids,
        vectors=vectors,
        payloads=payloads,
        search_texts=texts,
    )

    print(f"완료: {len(PRODUCTS)}개 상품 인덱싱됨")


if __name__ == "__main__":
    asyncio.run(main())
