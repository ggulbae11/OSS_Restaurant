"""
POST /ai/recommend
현재 메뉴판에서 사용자 요청/시간대/이전 주문 이력을 분석해 실제 메뉴를 추천한다.
"""

import json
import os
import re

import httpx
from fastapi import APIRouter, Request
from pydantic import BaseModel
from slowapi import Limiter
from slowapi.util import get_remote_address

from utils.openai_client import chat

router  = APIRouter()
limiter = Limiter(key_func=get_remote_address)

CORE_SERVICE_URL = os.getenv("CORE_SERVICE_URL", "http://core-service:8082")


class RecommendRequest(BaseModel):
    message: str
    time: str | None = None            # "breakfast" | "lunch" | "dinner" | "snack"
    userHistory: list[int] | None = None   # 이전에 주문한 menuId 목록


class RecommendResponse(BaseModel):
    recommendedMenuIds: list[int]
    reason: str


async def fetch_menus() -> list[dict]:
    """core-service에서 현재 판매 중인 메뉴 목록을 가져온다."""
    try:
        async with httpx.AsyncClient(timeout=5.0) as client:
            resp = await client.get(f"{CORE_SERVICE_URL}/menus")
            resp.raise_for_status()
            return resp.json()
    except Exception:
        return []


def build_menu_summary(menus: list[dict]) -> str:
    """메뉴 목록을 AI 프롬프트용 텍스트로 변환한다."""
    lines = []
    for m in menus:
        spicy = f" 🌶️{'x' * m.get('spicyLevel', 0)}" if m.get('spicyLevel', 0) > 0 else ""
        lines.append(
            f"- ID {m['id']}: {m['name']} ({m['price']:,}원, 조리 {m.get('cookTime', '?')}분{spicy})"
        )
    return "\n".join(lines)


@router.post(
    "/recommend",
    response_model=RecommendResponse,
    tags=["추천"],
    summary="메뉴 추천",
    description="""
현재 판매 중인 메뉴를 core-service에서 실시간으로 조회한 뒤,
사용자 요청 메시지·시간대·이전 주문 이력을 분석해 최대 3개 메뉴를 추천합니다.

**time 값**: `breakfast` · `lunch` · `dinner` · `snack`
""",
)
@limiter.limit("30/minute")
async def recommend_menu(request: Request, body: RecommendRequest):
    menus = await fetch_menus()

    if not menus:
        return RecommendResponse(
            recommendedMenuIds=[],
            reason="메뉴 정보를 불러올 수 없어 추천이 어렵습니다. 잠시 후 다시 시도해주세요.",
        )

    menu_summary = build_menu_summary(menus)
    valid_ids = [m["id"] for m in menus]

    system_prompt = f"""당신은 한국 식당의 AI 메뉴 추천 어시스턴트입니다.
아래는 현재 판매 중인 메뉴 목록입니다. 이 목록에 있는 메뉴 중에서만 추천하세요.

[메뉴 목록]
{menu_summary}

규칙:
1. 반드시 JSON 형식으로만 응답하세요.
2. 형식: {{"recommendedMenuIds": [숫자, ...], "reason": "추천 이유"}}
3. recommendedMenuIds에는 위 메뉴 목록의 ID 값만 사용하고 최대 3개 추천하세요.
4. reason은 추천한 메뉴명을 언급하며 한국어 2문장 이내로 작성하세요.
5. 욕설, 비하 표현 금지."""

    time_label = {
        "breakfast": "아침", "lunch": "점심",
        "dinner": "저녁", "snack": "간식",
    }.get(body.time or "", "")

    user_prompt = f"""사용자 요청: "{body.message}"
시간대: {time_label or "정보 없음"}
이전 주문 메뉴 ID: {body.userHistory or []}

위 조건에 가장 잘 맞는 메뉴를 추천해주세요."""

    raw = await chat(system_prompt, user_prompt)

    cleaned = re.sub(r"```(?:json)?|```", "", raw).strip()
    try:
        data = json.loads(cleaned)
        recommended = [
            mid for mid in data.get("recommendedMenuIds", [])
            if mid in valid_ids
        ]
        if not recommended:
            recommended = valid_ids[:3]
        return RecommendResponse(
            recommendedMenuIds=recommended,
            reason=data.get("reason", ""),
        )
    except (json.JSONDecodeError, KeyError):
        return RecommendResponse(
            recommendedMenuIds=valid_ids[:3],
            reason="추천 처리 중 오류가 발생했습니다. 인기 메뉴를 대신 추천드립니다.",
        )
