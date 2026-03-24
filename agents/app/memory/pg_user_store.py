"""PostgreSQL-backed long-term user memory store.

agent_db (postgres-agent)의 user_profiles 테이블에 사용자별 장기 메모리를 저장합니다.
RAG 서비스와 동일한 DB를 공유하며, 별도 테이블(user_profiles)을 사용합니다.

메모리 구조:
  - preferences: 선호 브랜드/카테고리/가격대/우선순위
  - facts:       사용자가 명시적으로 언급한 사실 (직업, 보유 기기 등)
  - summary:     LLM이 생성한 사용자 프로필 요약문
"""

import json
from typing import Any
from datetime import datetime, timezone

import asyncpg
import structlog

from app.config import settings

logger = structlog.get_logger()

_pool: asyncpg.Pool | None = None


# ---------------------------------------------------------------------------
# Pool & Schema
# ---------------------------------------------------------------------------

async def _get_pool() -> asyncpg.Pool:
    global _pool
    if _pool is None:
        _pool = await asyncpg.create_pool(
            settings.POSTGRES_AGENT_URL,
            min_size=1,
            max_size=5,
        )
        logger.info("pg_user_store_pool_initialized")
    return _pool


# ---------------------------------------------------------------------------
# Public API
# ---------------------------------------------------------------------------

class PgUserStore:
    """사용자 장기 메모리를 PostgreSQL에 저장·조회합니다.

    스키마는 tools/seed/postgres/agent-init.sql에서 정의되며
    Docker 최초 빌드 시 자동으로 생성됩니다.
    """

    async def initialize(self) -> None:
        await _get_pool()

    async def close(self) -> None:
        global _pool
        if _pool:
            await _pool.close()
            _pool = None
            logger.info("pg_user_store_pool_closed")

    # ── 조회 ──────────────────────────────────────────────────────────────

    async def get_profile(self, user_id: str) -> dict[str, Any] | None:
        """user_id에 해당하는 프로필을 반환합니다. 없으면 None."""
        pool = await _get_pool()
        async with pool.acquire() as conn:
            row = await conn.fetchrow(
                "SELECT preferences, facts, summary FROM user_profiles WHERE user_id = $1",
                user_id,
            )
        if row is None:
            return None
        return {
            "preferences": json.loads(row["preferences"]) if isinstance(row["preferences"], str) else row["preferences"],
            "facts":       list(row["facts"]),
            "summary":     row["summary"],
        }

    # ── 저장 ──────────────────────────────────────────────────────────────

    async def upsert_profile(
        self,
        user_id: str,
        preferences: dict[str, Any] | None = None,
        new_facts: list[str] | None = None,
        summary: str | None = None,
    ) -> None:
        """프로필을 생성하거나 기존 프로필에 병합합니다.

        - preferences: 전달된 키만 덮어씁니다 (deep merge).
        - new_facts:   기존 facts 배열에 추가합니다 (중복 제거, 최대 50개).
        - summary:     전달되면 전체 교체합니다.
        """
        pool = await _get_pool()
        async with pool.acquire() as conn:
            # 기존 프로필 로드
            row = await conn.fetchrow(
                "SELECT preferences, facts, summary FROM user_profiles WHERE user_id = $1",
                user_id,
            )

            if row:
                current_prefs = json.loads(row["preferences"]) if isinstance(row["preferences"], str) else dict(row["preferences"])
                current_facts = list(row["facts"])
                current_summary = row["summary"]
            else:
                current_prefs = {}
                current_facts = []
                current_summary = ""

            # preferences 병합 (최상위 키 단위 deep merge)
            if preferences:
                for key, val in preferences.items():
                    if isinstance(val, list) and isinstance(current_prefs.get(key), list):
                        # 리스트는 합집합으로 merge
                        merged = list(dict.fromkeys(current_prefs[key] + val))
                        current_prefs[key] = merged
                    elif isinstance(val, dict) and isinstance(current_prefs.get(key), dict):
                        current_prefs[key].update(val)
                    else:
                        current_prefs[key] = val

            # facts 추가 (중복 제거, 최대 50개)
            if new_facts:
                existing_set = set(current_facts)
                for fact in new_facts:
                    fact = fact.strip()
                    if fact and fact not in existing_set:
                        current_facts.append(fact)
                        existing_set.add(fact)
                current_facts = current_facts[-50:]  # 최근 50개만 유지

            # summary 교체
            if summary is not None:
                current_summary = summary

            await conn.execute(
                """
                INSERT INTO user_profiles (user_id, preferences, facts, summary, updated_at)
                VALUES ($1, $2::jsonb, $3, $4, NOW())
                ON CONFLICT (user_id) DO UPDATE SET
                    preferences = EXCLUDED.preferences,
                    facts       = EXCLUDED.facts,
                    summary     = EXCLUDED.summary,
                    updated_at  = EXCLUDED.updated_at
                """,
                user_id,
                json.dumps(current_prefs, ensure_ascii=False),
                current_facts,
                current_summary,
            )

        logger.info(
            "pg_user_profile_upserted",
            user_id=user_id,
            facts_count=len(current_facts),
        )

    def build_memory_prompt(self, profile: dict[str, Any]) -> str:
        """프로필을 supervisor 시스템 프롬프트에 삽입할 텍스트로 변환합니다."""
        lines = ["[사용자 장기 메모리 - 아래 정보를 참고해 개인화된 응답을 제공하세요]"]

        prefs = profile.get("preferences", {})
        if prefs.get("brands"):
            lines.append(f"- 선호 브랜드: {', '.join(prefs['brands'])}")
        if prefs.get("categories"):
            lines.append(f"- 관심 카테고리: {', '.join(prefs['categories'])}")
        if prefs.get("price_range"):
            pr = prefs["price_range"]
            lines.append(f"- 예산 범위: {pr.get('min', 0):,}원 ~ {pr.get('max', 0):,}원")
        if prefs.get("priorities"):
            lines.append(f"- 구매 우선순위: {', '.join(prefs['priorities'])}")

        facts = profile.get("facts", [])
        if facts:
            lines.append(f"- 알려진 사실: {'; '.join(facts[-10:])}")  # 최근 10개

        summary = profile.get("summary", "")
        if summary:
            lines.append(f"- 프로필 요약: {summary}")

        return "\n".join(lines)
