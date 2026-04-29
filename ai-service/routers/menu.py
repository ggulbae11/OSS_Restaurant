"""
GET  /ai/menu/new       — 매출 데이터 기반 신메뉴 아이디어 제안
POST /ai/policy/check   — 메뉴 이름/가격/설명 정책 준수 여부 검사
POST /ai/quality/check  — 메뉴 품질 자가 점검
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


# ══════════════════════════════════════════════════════════════
# 1. 신메뉴 추천
# ══════════════════════════════════════════════════════════════

class NewMenuResponse(BaseModel):
    suggestion: str
    name: str | None = None
    estimatedPrice: int | None = None
    reason: str | None = None


NEW_MENU_SYSTEM = """
당신은 한국 식당의 메뉴 컨설턴트 AI입니다.
매출 데이터를 분석하여 신메뉴 아이디어를 제안하세요.
반드시 JSON으로만 응답:
{"name": "메뉴명", "estimatedPrice": 가격(정수), "reason": "추천 이유", "suggestion": "상세 설명"}
"""


@router.post(
    "/menu/new",
    response_model=NewMenuResponse,
    tags=["메뉴 관리"],
    summary="신메뉴 아이디어 제안",
    description="매출 데이터(salesData)를 전달하면 AI가 새로운 메뉴 1건을 추천합니다. salesData 생략 시 일반적인 제안을 반환합니다.",
)
@limiter.limit("10/minute")
async def suggest_new_menu(request: Request, body: dict = {}):
    sales_data = body.get("salesData", "데이터 없음")
    user_prompt = f"현재 매출 데이터: {sales_data}\n신메뉴를 1개 추천해주세요."

    raw = await chat(NEW_MENU_SYSTEM, user_prompt)
    cleaned = re.sub(r"```(?:json)?|```", "", raw).strip()

    try:
        data = json.loads(cleaned)
        return NewMenuResponse(
            suggestion=str(data.get("suggestion", "")),
            name=data.get("name"),
            estimatedPrice=data.get("estimatedPrice"),
            reason=data.get("reason"),
        )
    except (json.JSONDecodeError, KeyError):
        return NewMenuResponse(suggestion="데이터 부족으로 추천이 어렵습니다.")


# ══════════════════════════════════════════════════════════════
# 2. 정책 검사
# ══════════════════════════════════════════════════════════════

class PolicyCheckRequest(BaseModel):
    name: str
    price: int
    description: str | None = None


class PolicyCheckResponse(BaseModel):
    passed: bool
    issues: list[str]


POLICY_SYSTEM = """
당신은 식당 메뉴 정책 검사 AI입니다.
다음 정책을 기준으로 메뉴를 검사하세요:
1. 메뉴 이름에 욕설/비하 표현 금지
2. 가격은 100원 이상 100,000원 이하
3. 이름은 2글자 이상 30글자 이하
4. 설명에 허위·과장 표현 금지

반드시 JSON으로만 응답:
{"passed": true/false, "issues": ["문제점1", "문제점2"]}
"""


@router.post(
    "/policy/check",
    response_model=PolicyCheckResponse,
    tags=["메뉴 관리"],
    summary="메뉴 정책 검사",
    description="""
메뉴 등록 전 이름·가격·설명이 운영 정책을 준수하는지 검사합니다.

**검사 항목**
- 이름: 2~30자, 욕설 금지
- 가격: 100원 ~ 100,000원
- 설명: 허위·과장 표현 금지
""",
)
@limiter.limit("30/minute")
async def check_policy(request: Request, body: PolicyCheckRequest):
    user_prompt = (
        f"메뉴 이름: {body.name}\n"
        f"가격: {body.price}원\n"
        f"설명: {body.description or '없음'}"
    )
    raw = await chat(POLICY_SYSTEM, user_prompt)
    cleaned = re.sub(r"```(?:json)?|```", "", raw).strip()

    try:
        data = json.loads(cleaned)
        return PolicyCheckResponse(
            passed=bool(data.get("passed", True)),
            issues=list(data.get("issues", [])),
        )
    except (json.JSONDecodeError, KeyError):
        return PolicyCheckResponse(passed=True, issues=[])


# ══════════════════════════════════════════════════════════════
# 3. 품질 점검
# ══════════════════════════════════════════════════════════════

class QualityCheckRequest(BaseModel):
    menuId: int
    reviewSummary: str | None = None
    avgRating: float | None = None


class QualityCheckResponse(BaseModel):
    score: float      # 0.0 ~ 10.0
    feedback: str
    action: str       # "KEEP" | "IMPROVE" | "REMOVE"


QUALITY_SYSTEM = """
당신은 식당 메뉴 품질 관리 AI입니다.
리뷰 요약과 평균 별점을 기반으로 메뉴 품질을 평가하세요.
반드시 JSON으로만 응답:
{"score": 숫자(0~10), "feedback": "한국어 피드백", "action": "KEEP|IMPROVE|REMOVE"}
"""


@router.post(
    "/quality/check",
    response_model=QualityCheckResponse,
    tags=["메뉴 관리"],
    summary="메뉴 품질 점검",
    description="""
리뷰 요약과 평균 별점을 바탕으로 메뉴 품질을 0~10점으로 평가합니다.

**action 값**
- `KEEP`: 현 상태 유지
- `IMPROVE`: 개선 필요
- `REMOVE`: 메뉴 제거 권장
""",
)
@limiter.limit("20/minute")
async def check_quality(request: Request, body: QualityCheckRequest):
    user_prompt = (
        f"메뉴 ID: {body.menuId}\n"
        f"평균 별점: {body.avgRating or '없음'}\n"
        f"리뷰 요약: {body.reviewSummary or '없음'}"
    )
    raw = await chat(QUALITY_SYSTEM, user_prompt)
    cleaned = re.sub(r"```(?:json)?|```", "", raw).strip()

    try:
        data   = json.loads(cleaned)
        score  = float(data.get("score", 5.0))
        action = data.get("action", "KEEP")
        if action not in ("KEEP", "IMPROVE", "REMOVE"):
            action = "KEEP"
        return QualityCheckResponse(
            score=round(min(max(score, 0.0), 10.0), 1),
            feedback=str(data.get("feedback", "")),
            action=action,
        )
    except (json.JSONDecodeError, ValueError):
        return QualityCheckResponse(score=5.0, feedback="분석 실패", action="KEEP")
