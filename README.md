# 🍽️ AI 기반 식당 운영 서비스

MSA(마이크로서비스 아키텍처) 구조로 구축된 식당 운영 풀스택 웹 서비스입니다.  
고객 주문·리뷰부터 관리자 메뉴 운영까지 GPT-4o-mini 기반 AI 기능을 통합한 플랫폼입니다.

---

## 기술 스택

| 영역 | 기술 |
|---|---|
| 고객 프론트엔드 | Vue 3, Pinia, Vue Router, Vite |
| 관리자 프론트엔드 | Vue 3, Vue Router, Vite |
| API 게이트웨이 | Nginx |
| 인증 서비스 | Spring Boot 3, Spring Security, JWT, Redis |
| 핵심 서비스 | Spring Boot 3, Spring Security, SQLite |
| AI 서비스 | FastAPI, OpenAI GPT-4o-mini, slowapi |
| 데이터베이스 | SQLite (auth.db, core.db) |
| 컨테이너 | Docker, Docker Compose |

---

## 서비스 구성

| 서비스 | 포트 | 설명 |
|---|---|---|
| frontend-customer | 3000 | 고객용 주문 웹 (Vue 3) |
| frontend-admin | 3001 | 관리자 대시보드 (Vue 3) |
| api-gateway | 80 | Nginx 리버스 프록시 |
| auth-service | 8081 | JWT 인증 / 로그아웃 블랙리스트 (Spring Boot) |
| core-service | 8082 | 메뉴·주문·리뷰 CRUD (Spring Boot) |
| ai-service | 8000 | AI 추론 전담 (FastAPI + GPT-4o-mini) |
| redis | 6379 | JWT 블랙리스트 캐시 |

---

## 아키텍처

```
[Browser]
   ├── :3000  → frontend-customer (Vue 3 + Nginx)
   └── :3001  → frontend-admin   (Vue 3 + Nginx)
                      │
                      ▼
              api-gateway (:80, Nginx)
            /auth/*   /ai/*   /admin/*   /*
               │        │        │        │
          auth-svc  ai-svc   core-svc  core-svc
          (:8081)  (:8000)  (:8082)   (:8082)
               │                │
             Redis            SQLite
            (JWT BL)       (core.db)
               │
             SQLite
           (auth.db)
```

**요청 흐름**

1. 프론트엔드 → API 게이트웨이(Nginx)로 요청
2. Nginx가 경로에 따라 auth-service / core-service / ai-service로 라우팅
3. core-service는 요청마다 auth-service `/verify` 엔드포인트로 JWT 검증
4. AI 기능이 필요한 경우 core-service 또는 클라이언트가 ai-service 호출

---

## 빠른 시작

### 사전 요구사항

- Docker Desktop (Docker Compose 포함)
- OpenAI API Key

### 1. 환경 변수 설정

```bash
cp .env.example .env
```

`.env` 파일을 열어 OpenAI API Key를 입력합니다:

```env
JWT_SECRET=restaurant-super-secret-key-min-32chars!!
JWT_EXPIRY_SECONDS=3600
OPENAI_API_KEY=sk-xxxxxxxxxxxxxxxxxxxxxxxx   # 여기에 입력
```

### 2. 실행

```bash
docker compose up --build
```

최초 실행 시 `db-init` 컨테이너가 자동으로 SQLite DB를 초기화하고 시드 데이터를 삽입합니다.

### 3. 접속

| URL | 설명 |
|---|---|
| http://localhost:3000 | 고객용 웹 |
| http://localhost:3001 | 관리자 웹 |
| http://localhost:8081/swagger-ui.html | Auth Service Swagger UI |
| http://localhost:8082/swagger-ui.html | Core Service Swagger UI |
| http://localhost:8000/docs | AI Service Swagger UI (FastAPI 내장) |
| http://localhost:8000/redoc | AI Service ReDoc |

### 4. 중지

```bash
docker compose down
```

---

## 기본 계정

시드 데이터(`data/seed.py`)에 포함된 계정입니다.

| 아이디 | 비밀번호 | 역할 |
|---|---|---|
| admin | admin1234 | 관리자 (ADMIN) |
| customer1 | password123 | 고객 (CUSTOMER) |
| customer2 | password123 | 고객 (CUSTOMER) |
| testuser | test1234 | 고객 (CUSTOMER) |
| rider1 | password123 | 라이더 (RIDER) |

---

## API 명세

### Auth Service (`/auth`)

| 메서드 | 경로 | 인증 | 설명 |
|---|---|---|---|
| POST | /auth/register | 불필요 | 회원가입 |
| POST | /auth/login | 불필요 | 로그인 → JWT 반환 |
| POST | /auth/logout | Bearer 토큰 | 로그아웃 (토큰 블랙리스트 등록) |
| GET | /auth/verify | Bearer 토큰 | 내부 서비스용 토큰 검증 |

### Core Service — 고객 API

| 메서드 | 경로 | 인증 | 설명 |
|---|---|---|---|
| GET | /categories | 불필요 | 카테고리 목록 조회 |
| GET | /menus | 불필요 | 메뉴 목록 조회 (카테고리 필터 가능) |
| GET | /menus/{id} | 불필요 | 메뉴 상세 조회 |
| POST | /orders | CUSTOMER | 주문 생성 |
| GET | /orders/my | CUSTOMER | 내 주문 목록 조회 |
| GET | /orders/{id} | CUSTOMER | 주문 상세 조회 |
| GET | /reviews?menuId= | 불필요 | 메뉴별 리뷰 조회 |
| POST | /reviews | CUSTOMER | 리뷰 작성 (AI 자동 검열) |

### Core Service — 관리자 API (`/admin`, ADMIN 전용)

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | /admin/menus | 전체 메뉴 조회 (비활성 포함) |
| POST | /admin/menus | 메뉴 등록 (AI 정책 검사 자동 실행) |
| PUT | /admin/menus/{id} | 메뉴 수정 |
| DELETE | /admin/menus/{id} | 메뉴 비활성화 |
| PATCH | /admin/menus/{id}/enable | 메뉴 활성화 |
| GET | /admin/orders | 전체 주문 조회 |
| PATCH | /admin/orders/{id}/status | 주문 상태 변경 |
| GET | /admin/reviews | 전체 리뷰 조회 |
| PATCH | /admin/reviews/{id}/status | 리뷰 상태 변경 |
| POST | /admin/ai/menu/suggest | AI 신메뉴 추천 |
| POST | /admin/ai/cook/sequence | AI 조리 순서 최적화 |

### AI Service (`/ai`)

| 메서드 | 경로 | Rate Limit | 설명 |
|---|---|---|---|
| POST | /ai/recommend | 30회/분 | 현재 메뉴판 기반 맞춤 메뉴 추천 |
| POST | /ai/review/generate | 20회/분 | 키워드·톤 기반 리뷰 초안 생성 |
| POST | /ai/review/moderate | 60회/분 | 리뷰 텍스트 검열 (NORMAL / SUSPICIOUS / BLOCKED) |
| POST | /ai/menu/new | 10회/분 | 매출 데이터 기반 신메뉴 아이디어 제안 |
| POST | /ai/policy/check | 30회/분 | 메뉴 이름·가격·설명 정책 준수 검사 |
| POST | /ai/quality/check | 20회/분 | 리뷰 요약·평점 기반 메뉴 품질 점검 |
| POST | /ai/time/predict | 60회/분 | 주문 수·조리 시간 기반 예상 대기 시간 계산 |
| POST | /ai/cook/sequence | 30회/분 | LPT 알고리즘 + AI 조리 순서 최적화 |

---

## AI 기능 상세

### 메뉴 추천 (`/ai/recommend`)

```json
// 요청
{
  "message": "맵지 않은 것 추천해줘",
  "time": "lunch",
  "userHistory": [1, 3]
}

// 응답
{
  "recommendedMenuIds": [2, 6, 11],
  "reason": "된장찌개와 비빔밥은 자극적이지 않아 점심에 잘 어울립니다."
}
```

### 대기 시간 예측 (`/ai/time/predict`)

공식: `⌈(activeOrderCount × avgCookTime) / capacity⌉` (최솟값: avgCookTime)

```json
// 요청
{ "activeOrderCount": 5, "avgCookTime": 15, "capacity": 2 }

// 응답
{ "estimatedTime": 38, "explanation": "현재 약 38분 후에 준비될 예정입니다." }
```

### 리뷰 검열 (`/ai/review/moderate`)

- `NORMAL`: 일반 리뷰
- `SUSPICIOUS`: 경계선 표현 (약한 비방, 과장)
- `BLOCKED`: 욕설·허위 사실 (0.0~1.0 score 포함)

---

## 주문 상태 흐름

```
CREATED → ACCEPTED → COOKING → READY → DELIVERING → COMPLETED
                  ↘                                  ↗
                              CANCELED
```

| 상태 | 설명 |
|---|---|
| CREATED | 주문 접수 완료 |
| ACCEPTED | 관리자 주문 수락 |
| COOKING | 조리 중 |
| READY | 조리 완료, 배달 대기 |
| DELIVERING | 배달 중 |
| COMPLETED | 배달 완료 |
| CANCELED | 취소 |

---

## 프로젝트 구조

```
OSS_Restaurant/
├── docker-compose.yml
├── .env.example
├── data/
│   ├── seed.py             # DB 초기화 & 시드 데이터
│   ├── auth/auth.db        # 인증 DB (자동 생성)
│   └── core/core.db        # 핵심 서비스 DB (자동 생성)
├── api-gateway/
│   └── nginx.conf          # 리버스 프록시 라우팅 설정
├── auth-service/           # Spring Boot — JWT 인증
│   └── src/main/java/com/restaurant/auth/
├── core-service/           # Spring Boot — 메뉴·주문·리뷰
│   └── src/main/java/com/restaurant/core/
├── ai-service/             # FastAPI — GPT-4o-mini 연동
│   ├── main.py
│   ├── routers/            # recommend, review, menu, cook, time_predict
│   └── utils/openai_client.py
├── frontend-customer/      # Vue 3 — 고객 웹
│   └── src/views/          # Menu, Cart, Orders, Login, Register
└── frontend-admin/         # Vue 3 — 관리자 웹
    └── src/views/          # Menus, Orders, Reviews, Login
```

---

## 시드 데이터 재초기화

DB 파일을 삭제하면 다음 `docker compose up` 시 자동으로 재생성됩니다.

```bash
rm data/auth/auth.db data/core/core.db
docker compose up --build
```

시드 데이터만 수정하려면 `data/seed.py` 파일의 `USERS`, `MENUS`, `CATEGORIES` 등 상수를 편집한 뒤 위 명령을 실행하세요.
#   O S S _ R e s t a u r a n t  
 