"""
POST /ai/time/predict
현재 주문 수 / 평균 조리 시간 / 동시 처리 가능 수를 기반으로
예상 대기 시간을 계산하고, AI가 자연어로 설명을 덧붙인다.
"""

import math
from fastapi import APIRouter, Request
from pydantic import BaseModel, Field
from slowapi import Limiter
from slowapi.util import get_remote_address
from utils.openai_client import chat

router  = APIRouter()
limiter = Limiter(key_func=get_remote_address)


class TimePredictRequest(BaseModel):
    activeOrderCount: int = Field(ge=0)
    avgCookTime: int      = Field(ge=1, description="평균 조리 시간(분)")
    capacity: int         = Field(ge=1, description="동시 조리 가능 수")


class TimePredictResponse(BaseModel):
    estimatedTime: int   # 분
    explanation: str


@router.post(
    "/time/predict",
    response_model=TimePredictResponse,
    tags=["운영"],
    summary="예상 대기 시간 계산",
    description="""
현재 주문 수와 조리 능력을 기반으로 예상 대기 시간을 계산합니다.

**계산 공식**

```
estimatedTime = max(⌈(activeOrderCount × avgCookTime) / capacity⌉, avgCookTime)
```

계산 결과와 함께 AI가 생성한 고객 안내 문구도 반환합니다.
""",
)
@limiter.limit("60/minute")
async def predict_time(request: Request, body: TimePredictRequest):
    # 공식 계산 (명세서 5.3)
    raw_time = math.ceil(
        (body.activeOrderCount * body.avgCookTime) / body.capacity
    )
    estimated = max(raw_time, body.avgCookTime)  # 최소 1회 조리 시간 보장

    # AI 자연어 설명
    user_prompt = (
        f"현재 대기 주문 수: {body.activeOrderCount}건, "
        f"평균 조리 시간: {body.avgCookTime}분, "
        f"동시 조리 가능: {body.capacity}개, "
        f"예상 대기 시간: 약 {estimated}분. "
        "고객에게 자연스럽고 친절하게 한 문장으로 안내 멘트를 작성해주세요."
    )
    system = "당신은 친절한 식당 안내 AI입니다. 요청된 정보를 바탕으로 한 문장짜리 안내 멘트만 반환하세요."

    try:
        explanation = await chat(system, user_prompt)
    except Exception:
        explanation = f"현재 약 {estimated}분 후에 준비될 예정입니다."

    return TimePredictResponse(estimatedTime=estimated, explanation=explanation)
