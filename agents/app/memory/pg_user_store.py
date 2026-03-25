from dataclasses import dataclass
from typing import Any, cast
from typing_extensions import TypedDict

from langchain.agents.middleware import wrap_model_call, ModelRequest, ModelResponse
from langchain.tools import tool, ToolRuntime

@dataclass
class Context:
    user_id: str

class UserProfile(TypedDict):
    preferences: dict[str, Any]
    facts: list[str]
    summary: str

@tool
async def get_user_profile(runtime: ToolRuntime[Context]) -> str:
    """현재 저장된 사용자 프로필 정보를 조회한다."""
    assert runtime.store is not None
    user_id = runtime.context.user_id
    item = await runtime.store.aget(("users", user_id), "profile")
    if item:
        return f"저장된 프로필: {item.value}"
    return "저장된 프로필 정보가 없습니다."


@tool
async def save_user_profile(profile: UserProfile, runtime: ToolRuntime[Context]) -> str:
    """사용자 프로필을 저장한다."""
    assert runtime.store is not None
    user_id = runtime.context.user_id
    await runtime.store.aput(
        ("users", user_id),          # namespace: 유저별 격리
        "profile",                   # key
        cast("dict[str, Any]", profile)
    )
    return f"프로필이 저장되었습니다: {profile}"

@tool
async def update_user_interests(topics: list[str], runtime: ToolRuntime[Context]) -> str:
    """관심 주제 목록을 업데이트한다."""
    assert runtime.store is not None
    user_id = runtime.context.user_id
    item = await runtime.store.aget(("users", user_id), "profile")
    existing = dict(item.value) if item else {}
    existing["topics_of_interest"] = topics
    await runtime.store.aput(("users", user_id), "profile", existing)
    return f"관심 주제 업데이트 완료: {topics}"

@wrap_model_call
async def inject_user_preferences(
    request: ModelRequest, handler
) -> ModelResponse:
    """Store에서 유저 프로필을 읽어 system prompt에 개인화 정보를 주입."""
    store = request.runtime.store
    user_id = request.runtime.context.user_id

    if store:
        item = await store.aget(("users", user_id), "profile")
        if item:
            profile = item.value
            preferences = profile.get("preferences", {})
            facts = profile.get("facts", [])
            summary = profile.get("summary", "")

            personalized_prompt = f"""
현재 대화 중인 사용자 정보:
- 선호도: {preferences}
- 사실: {facts}
- 요약: {summary}

위 정보를 반영하여 사용자에게 맞춤화된 응답을 제공하세요.
"""
            # 기존 system prompt에 개인화 정보 추가
            current_prompt = request.system_prompt or ""
            request = request.override(
                system_prompt=current_prompt + personalized_prompt
            )

    return await handler(request)


USER_PROFILE_TOOLS = [get_user_profile, save_user_profile, update_user_interests]
