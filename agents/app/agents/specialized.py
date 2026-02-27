"""Specialized agent node implementations."""

from langchain_core.messages import SystemMessage, HumanMessage, AIMessage
from langchain_openai import ChatOpenAI

from app.config import settings
from app.graph.tools import (
    PRODUCT_TOOLS, REVIEW_TOOLS, CART_TOOLS,
    ORDER_TOOLS, INVENTORY_TOOLS,
)

import structlog

logger = structlog.get_logger()


def _get_llm(temperature: float = 0.1):
    """Get LLM instance."""
    return ChatOpenAI(
        model=settings.OPENAI_MODEL,
        temperature=temperature,
        api_key=settings.OPENAI_API_KEY,
    )


# ============================================================
# Agent System Prompts
# ============================================================

PRODUCT_SEARCH_PROMPT = """당신은 쇼핑몰의 상품 검색 전문 에이전트입니다.

역할:
- 사용자의 요구사항을 분석하여 적합한 상품을 검색합니다
- 예산, 배송일, 브랜드, 카테고리 등 제약 조건을 고려합니다
- 상품 간 호환성(compatibility_tags)을 교차 검증합니다
- 검색 결과가 부족하면 조건을 완화하여 재검색합니다

규칙:
- 검색 결과를 사용자에게 명확하게 정리하여 보여주세요
- 가격은 KRW(원) 단위입니다
- 재고가 없는 상품은 알려주고 대안을 제시하세요
- 한국어로 응답하세요

자기반성:
- 검색 결과가 사용자 의도와 맞는지 확인하세요
- 결과가 없거나 부적합하면 다른 키워드나 조건으로 재시도하세요"""

REVIEW_ANALYSIS_PROMPT = """당신은 쇼핑몰의 리뷰 분석 전문 에이전트입니다.

역할:
- 상품 리뷰를 분석하여 사이즈, 품질, 만족도에 대한 인사이트를 제공합니다
- 리뷰 요약 통계(평균 평점, 사이즈 피드백 분포)를 활용합니다
- 특정 키워드로 리뷰를 검색하여 관련 의견을 찾습니다
- 검증된 구매자 리뷰를 우선적으로 참고합니다

규칙:
- 사이즈 추천 시 리뷰의 사이즈 피드백(SMALL/TRUE_TO_SIZE/LARGE) 분포를 근거로 제시하세요
- 품질에 대한 의견은 여러 리뷰를 종합하여 결론을 도출하세요
- 부정적 의견도 공정하게 전달하세요
- 한국어로 응답하세요"""

CART_MANAGEMENT_PROMPT = """당신은 쇼핑몰의 장바구니 관리 전문 에이전트입니다.

역할:
- 사용자의 장바구니를 구성하고 관리합니다
- 예산 제약을 검증합니다 (총액이 예산 초과 시 알림)
- 상품 간 호환성을 확인합니다 (compatibility_tags)
- 재고를 확인하고 품절 시 대안을 제시합니다
- 최적의 장바구니를 구성합니다

규칙:
- 장바구니에 상품 추가 전 반드시 재고를 확인하세요
- 예산 초과 시 사용자에게 알리고 대안을 제시하세요
- 장바구니 총액을 항상 표시하세요
- 한국어로 응답하세요

자기반성:
- 장바구니 구성이 사용자의 모든 제약 조건을 만족하는지 확인하세요
- 더 나은 옵션이 있다면 제안하세요"""

ORDER_MANAGEMENT_PROMPT = """당신은 쇼핑몰의 주문 관리 전문 에이전트입니다.

역할:
- 주문 체크아웃 프로세스를 진행합니다
- 주문 상태를 확인하고 안내합니다
- 주문 취소 및 변경을 처리합니다
- 환불 프로세스를 안내합니다

규칙:
- 결제 전 반드시 사용자 승인을 받아야 합니다 (requires_approval=True)
- 주문 총액, 상품 목록을 명확하게 보여주고 승인을 요청하세요
- 주문 취소/변경이 불가한 경우 반품 프로세스를 안내하세요
- 주문 상태 변경 실패 시 원인을 분석하고 대안을 제시하세요
- 한국어로 응답하세요

중요:
- 구매 승인 없이 절대 주문을 확정하지 마세요
- checkout_order를 호출한 후 반드시 사용자에게 승인을 요청하세요"""

CUSTOMER_SERVICE_PROMPT = """당신은 쇼핑몰의 고객 서비스 전문 에이전트입니다.

역할:
- 주문 상태 조회 및 안내
- 반품/환불 프로세스 안내 및 처리
- 배송 관련 문의 응대
- 상품 관련 일반 문의 응대

규칙:
- 고객의 불편에 공감하며 응대하세요
- 가능한 해결 방안을 먼저 시도하세요
- 해결이 불가능한 경우 대안을 제시하세요
- 한국어로 응답하세요

프로세스:
- 주문 취소: 주문 상태 확인 → 취소 가능 여부 판단 → 취소 처리 또는 반품 안내
- 환불: 주문 상태 확인 → 환불 요청 → 처리 결과 안내
- 옵션 변경: 주문 상태 확인 → 변경 가능 여부 → 취소 후 재주문 안내"""


def get_agent_tools(agent_type: str) -> list:
    """Get tools for a specific agent type."""
    tool_map = {
        "product_search": PRODUCT_TOOLS + INVENTORY_TOOLS,
        "review_analysis": REVIEW_TOOLS + PRODUCT_TOOLS,
        "cart_management": CART_TOOLS + PRODUCT_TOOLS + INVENTORY_TOOLS,
        "order_management": ORDER_TOOLS,
        "customer_service": ORDER_TOOLS + PRODUCT_TOOLS,
    }
    return tool_map.get(agent_type, [])


def get_agent_prompt(agent_type: str) -> str:
    """Get system prompt for a specific agent type."""
    prompt_map = {
        "product_search": PRODUCT_SEARCH_PROMPT,
        "review_analysis": REVIEW_ANALYSIS_PROMPT,
        "cart_management": CART_MANAGEMENT_PROMPT,
        "order_management": ORDER_MANAGEMENT_PROMPT,
        "customer_service": CUSTOMER_SERVICE_PROMPT,
    }
    return prompt_map.get(agent_type, "")


def create_agent_runnable(agent_type: str):
    """Create a runnable agent with appropriate tools and prompt."""
    llm = _get_llm()
    tools = get_agent_tools(agent_type)
    prompt = get_agent_prompt(agent_type)

    if tools:
        llm_with_tools = llm.bind_tools(tools)
    else:
        llm_with_tools = llm

    return llm_with_tools, tools, prompt
