"""
POST /ai/cook/sequence
주문된 메뉴 목록을 분석해 최적 조리 순서를 반환한다.
기본 알고리즘: 조리 시간이 긴 메뉴부터 시작 (LPT: Longest Processing Time).
AI가 이유를 자연어로 설명한다.
"""

import json
import re
from fastapi import APIRouter, Request
from pydantic import BaseModel
from slowapi import Limiter
from slowapi.util import get_remote_address
from utils.openai_client import chat

router  = APIRouter()
limiter = Limiter(key_func=get_remote_address)


class CookItem(BaseModel):
    menuId: int
    cookTime: int        # 분
    menuName: str = ""   # 메뉴 이름 (설명에 활용)


class CookSequenceRequest(BaseModel):
    items: list[CookItem]


class CookSequenceResponse(BaseModel):
    order: list[int]   # menuId 순서
    reason: str


SYSTEM_PROMPT = """
당신은 주방 효율 최적화 AI입니다.
주어진 메뉴 목록에서 조리 순서를 결정하고 JSON으로만 응답하세요.
형식: {"order": [menuId, ...], "reason": "한국어 설명"}
조리 시간이 긴 메뉴를 먼저 시작하는 LPT 전략을 기본으로 사용하고,
같은 카테고리 재료를 묶어 준비 효율을 높이는 방법도 고려하세요.
reason에는 메뉴 이름을 사용해 설명하세요. menuId 숫자는 언급하지 마세요.
"""


@router.post(
    "/cook/sequence",
    response_model=CookSequenceResponse,
    tags=["운영"],
    summary="조리 순서 최적화",
    description="""
주문된 메뉴 목록을 받아 주방 효율을 극대화하는 조리 순서를 반환합니다.

**기본 전략**: LPT(Longest Processing Time) — 조리 시간이 긴 메뉴부터 시작
AI가 같은 카테고리 재료를 묶는 추가 최적화를 수행합니다.
""",
)
@limiter.limit("30/minute")
async def optimize_cook_sequence(request: Request, body: CookSequenceRequest):
    if not body.items:
        return CookSequenceResponse(order=[], reason="조리할 메뉴가 없습니다.")

    # 기본 LPT 정렬 (fallback용)
    lpt_order = [
        item.menuId
        for item in sorted(body.items, key=lambda x: x.cookTime, reverse=True)
    ]

    items_desc = "\n".join(
        f"- menuId={i.menuId}, 메뉴명={i.menuName or str(i.menuId)}, 조리시간={i.cookTime}분"
        for i in body.items
    )
    user_prompt = f"메뉴 목록:\n{items_desc}\n\n최적 조리 순서를 결정해주세요."

    raw = await chat(SYSTEM_PROMPT, user_prompt)
    cleaned = re.sub(r"```(?:json)?|```", "", raw).strip()

    try:
        data   = json.loads(cleaned)
        order  = [int(x) for x in data.get("order", lpt_order)]
        reason = str(data.get("reason", ""))
        # 유효하지 않은 menuId 필터링
        valid_ids = {i.menuId for i in body.items}
        order = [mid for mid in order if mid in valid_ids]
        if not order:
            order = lpt_order
        return CookSequenceResponse(order=order, reason=reason)
    except (json.JSONDecodeError, ValueError):
        return CookSequenceResponse(
            order=lpt_order,
            reason="조리 시간이 긴 메뉴부터 시작해야 동시에 완료됩니다.",
        )
