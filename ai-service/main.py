from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from slowapi import Limiter, _rate_limit_exceeded_handler
from slowapi.util import get_remote_address
from slowapi.errors import RateLimitExceeded

from routers import recommend, review, menu, time_predict, cook

# ── Rate Limiter ────────────────────────────────────────────────────────────
limiter = Limiter(key_func=get_remote_address)

app = FastAPI(
    title="AI Service API",
    description="""
GPT-4o-mini 기반 식당 운영 AI 마이크로서비스.

## 주요 기능

| 기능 | 엔드포인트 | Rate Limit |
|------|-----------|------------|
| 메뉴 추천 | `POST /ai/recommend` | 30회/분 |
| 리뷰 초안 생성 | `POST /ai/review/generate` | 20회/분 |
| 리뷰 검열 | `POST /ai/review/moderate` | 60회/분 |
| 신메뉴 아이디어 제안 | `POST /ai/menu/new` | 10회/분 |
| 메뉴 정책 검사 | `POST /ai/policy/check` | 30회/분 |
| 메뉴 품질 점검 | `POST /ai/quality/check` | 20회/분 |
| 예상 대기 시간 | `POST /ai/time/predict` | 60회/분 |
| 조리 순서 최적화 | `POST /ai/cook/sequence` | 30회/분 |

## Rate Limit 초과 시

HTTP `429 Too Many Requests` 응답이 반환됩니다.
""",
    version="1.0.0",
    contact={"name": "Restaurant OSS", "url": "http://localhost:80"},
    license_info={"name": "MIT"},
    openapi_tags=[
        {"name": "추천", "description": "사용자 요청 · 시간대 · 이력 기반 메뉴 추천"},
        {"name": "리뷰", "description": "리뷰 초안 생성 및 유해 콘텐츠 검열"},
        {"name": "메뉴 관리", "description": "신메뉴 제안, 정책 검사, 품질 점검"},
        {"name": "운영", "description": "대기 시간 예측 및 조리 순서 최적화"},
        {"name": "헬스체크", "description": "서비스 상태 확인"},
    ],
)

app.state.limiter = limiter
app.add_exception_handler(RateLimitExceeded, _rate_limit_exceeded_handler)

# ── CORS ────────────────────────────────────────────────────────────────────
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

# ── Routers ─────────────────────────────────────────────────────────────────
app.include_router(recommend.router,     prefix="/ai")
app.include_router(review.router,        prefix="/ai")
app.include_router(menu.router,          prefix="/ai")
app.include_router(time_predict.router,  prefix="/ai")
app.include_router(cook.router,          prefix="/ai")


@app.get("/health", tags=["헬스체크"], summary="서비스 상태 확인")
async def health():
    return {"status": "ok"}


@app.exception_handler(Exception)
async def global_exception_handler(request: Request, exc: Exception):
    return JSONResponse(
        status_code=500,
        content={"detail": f"AI 서비스 오류: {str(exc)}"},
    )
