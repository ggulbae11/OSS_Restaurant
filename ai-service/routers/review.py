"""
POST /ai/review/generate  — AI 리뷰 초안 생성
POST /ai/review/moderate  — 리뷰 텍스트 검열
"""

import json
import re
from fastapi import APIRouter, Request
from pydantic import BaseModel, Field
from slowapi import Limiter
from slowapi.util import get_remote_address
from utils.openai_client import chat

router  = APIRouter()
limiter = Limiter(key_func=get_remote_address)


# ══════════════════════════════════════════════════════════════
# 1. 리뷰 생성
# ══════════════════════════════════════════════════════════════

class GenerateReviewRequest(BaseModel):
    menuId: int
    keywords: list[str] = Field(default_factory=list)
    tone: str = "casual"   # "casual" | "formal"


class GenerateReviewResponse(BaseModel):
    rating: int
    content: str


GENERATE_SYSTEM = """
당신은 한국 식당의 리뷰 작성을 도와주는 AI입니다.
규칙:
1. 반드시 JSON 형식으로만 응답: {"rating": 별점(1~5), "content": "리뷰 내용"}
2. 자연스럽고 실제 경험처럼 작성하세요.
3. 욕설, 과장, 허위 사실 금지.
4. content는 50자 이상 200자 이하로 작성하세요.
5. 한국어로 작성하세요.
"""


@router.post(
    "/review/generate",
    response_model=GenerateReviewResponse,
    tags=["리뷰"],
    summary="리뷰 초안 생성",
    description="메뉴 ID와 키워드를 바탕으로 자연스러운 리뷰 초안을 생성합니다. tone은 `casual`(친근) 또는 `formal`(격식).",
)
@limiter.limit("20/minute")
async def generate_review(request: Request, body: GenerateReviewRequest):
    tone_label = "친근하고 편안한 말투" if body.tone == "casual" else "정중하고 격식 있는 말투"
    user_prompt = f"""
메뉴 ID: {body.menuId}
키워드: {', '.join(body.keywords) if body.keywords else '없음'}
말투: {tone_label}

위 정보를 바탕으로 자연스러운 리뷰를 작성해주세요.
"""
    raw = await chat(GENERATE_SYSTEM, user_prompt)
    cleaned = re.sub(r"```(?:json)?|```", "", raw).strip()

    try:
        data = json.loads(cleaned)
        rating  = max(1, min(5, int(data.get("rating", 4))))
        content = str(data.get("content", "")).strip()
        return GenerateReviewResponse(rating=rating, content=content)
    except (json.JSONDecodeError, ValueError):
        return GenerateReviewResponse(
            rating=4,
            content="맛있게 잘 먹었습니다. 다음에 또 방문하고 싶네요.",
        )


# ══════════════════════════════════════════════════════════════
# 2. 리뷰 검열
# ══════════════════════════════════════════════════════════════

class ModerateRequest(BaseModel):
    content: str


class ModerateResponse(BaseModel):
    status: str    # "NORMAL" | "SUSPICIOUS" | "BLOCKED"
    reason: str
    score: float   # 0.0 ~ 1.0 (높을수록 문제 있음)


MODERATE_SYSTEM = """
당신은 식당 리뷰 검열 AI입니다.
주어진 리뷰 텍스트를 분석하여 아래 기준으로 분류하세요.

분류 기준:
- NORMAL   : 일반적인 리뷰. 욕설/비방/허위 없음.
- SUSPICIOUS: 경계선 상의 표현. 약한 비방, 과도한 과장.
- BLOCKED  : 명확한 욕설, 심각한 비방, 허위 사실 유포.

반드시 JSON으로만 응답:
{"status": "NORMAL|SUSPICIOUS|BLOCKED", "reason": "판단 이유", "score": 0.0~1.0}
"""


@router.post(
    "/review/moderate",
    response_model=ModerateResponse,
    tags=["리뷰"],
    summary="리뷰 검열",
    description="""
리뷰 텍스트를 분석해 3단계로 분류합니다.

- **NORMAL** (score 0.0~0.3): 일반 리뷰
- **SUSPICIOUS** (score 0.3~0.7): 경계선 표현 — 관리자 검토 권장
- **BLOCKED** (score 0.7~1.0): 욕설·허위사실 — 노출 차단
""",
)
@limiter.limit("60/minute")
async def moderate_review(request: Request, body: ModerateRequest):
    if not body.content or not body.content.strip():
        return ModerateResponse(status="BLOCKED", reason="빈 내용", score=1.0)

    raw = await chat(MODERATE_SYSTEM, f'리뷰 내용: "{body.content}"')
    cleaned = re.sub(r"```(?:json)?|```", "", raw).strip()

    try:
        data   = json.loads(cleaned)
        status = data.get("status", "NORMAL")
        if status not in ("NORMAL", "SUSPICIOUS", "BLOCKED"):
            status = "NORMAL"
        return ModerateResponse(
            status=status,
            reason=str(data.get("reason", "")),
            score=float(data.get("score", 0.0)),
        )
    except (json.JSONDecodeError, ValueError):
        return ModerateResponse(status="NORMAL", reason="검열 처리 오류", score=0.0)
