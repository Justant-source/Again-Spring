# REST API 전체 명세 — 다시봄

> 공통 규약, 에러코드, 전체 마스터 표, 인증 매트릭스를 기술합니다.
> 도메인별 상세(시퀀스 다이어그램·요청/응답 예시)는 각 도메인 문서를 참조하세요.
> 
> **주의**: 2026-06-02 커뮤니티 광장 피벗. 구 Session/Turn/Message 모델 삭제 (ADR-0001 참조).

## Source of truth

| 항목 | 위치 |
|---|---|
| 컨트롤러 | `backend/src/main/java/com/againspring/api/**/*Controller.java` |
| DTO | `backend/src/main/java/com/againspring/api/dto/{request,response}/` |
| 에러 처리 | `backend/src/main/java/com/againspring/common/exception/GlobalExceptionHandler.java` |
| Swagger UI (dev) | `http://localhost:8080/swagger-ui.html` |
| Swagger UI (서버 dev) | `https://dev.againspring.net/swagger-ui/` |
| OpenAPI JSON (dev) | `http://localhost:8080/v3/api-docs` |

코드와 문서가 충돌하면 **코드가 옳습니다**. Swagger 자동생성 스펙이 이 문서보다 항상 우선합니다.

---

## 공통 규약

| 항목 | 값 |
|---|---|
| Base URL | `/api/...` (nginx → backend 라우팅) |
| Content-Type | `application/json` |
| 시간 형식 | ISO-8601 UTC (`2026-04-26T10:30:00Z`) |
| 인증 헤더 | `Authorization: Bearer {JWT}` |
| 에러 형식 | `{ "code": "...", "message": "..." }` |

---

## 에러코드 (`GlobalExceptionHandler`)

| 코드 | HTTP | 설명 |
|---|---|---|
| `VALIDATION_ERROR` | 400 | Bean Validation 실패 (`@Valid`) |
| `INVALID_ROLE` | 400 | 허용되지 않는 역할 (admin 역할 변경 시도) |
| `UNAUTHORIZED` | 401 | 인증 실패 / 토큰 만료 / 폐기된 토큰 |
| `FORBIDDEN` | 403 | 권한 없음 (비ADMIN 등) |
| `NOT_FOUND` | 404 | 리소스 없음 |
| `EMAIL_ALREADY_EXISTS` | 409 | 이메일 중복 (회원가입) |
| `FORBIDDEN_WORD_DETECTED` | 422 | 금지어 감지 (게시글/댓글 작성) |
| `USER_NOT_FOUND` | 404 | 사용자 없음 (admin 조회) |
| `LLM_UNAVAILABLE` | 503 | Claude CLI 불가 (fallback 응답 반환) |
| `INTERNAL_ERROR` | 500 | 서버 내부 오류 |

---

## 인증 · 권한 매트릭스

```mermaid
flowchart LR
    REQ[요청] --> AUTH{JWT 토큰?}
    AUTH -->|없음| PUBLIC{공개 엔드포인트?}
    PUBLIC -->|Yes| HANDLE[처리]
    PUBLIC -->|No| ERR401[401 Unauthorized]

    AUTH -->|있음| VALID{토큰 유효?}
    VALID -->|폐기/만료| ERR401
    VALID -->|유효| ROLE{역할 확인}

    ROLE -->|USER/TESTER/ADMIN| DUOGATE{duo-mode 게이팅?}
    DUOGATE -->|invite/join 엔드포인트| DUOCHECK{duo=true OR TESTER?}
    DUOCHECK -->|No| ERR403DUO[403 DUO_MODE_DISABLED]
    DUOCHECK -->|Yes| HANDLE

    DUOGATE -->|일반 엔드포인트| ADMINGATE{admin 경로?}
    ADMINGATE -->|/api/admin/**| ISADMIN{ADMIN 역할?}
    ISADMIN -->|No| ERR403[403 Forbidden]
    ISADMIN -->|Yes| ENVGATE{환경 게이팅?}
    ENVGATE -->|app.admin.enabled=false| NOBEAN[404 Not Found]
    ENVGATE -->|dev profile 아님| NOBEAN
    ENVGATE -->|통과| HANDLE

    ADMINGATE -->|일반 경로| HANDLE
```

---

## 전체 엔드포인트 마스터 표

### 1. Auth / OAuth2

| Method | Path | Auth | 상태코드 | 상세 문서 |
|---|---|---|---|---|
| POST | `/api/auth/send-verification` | 공개 | 200 | [auth.md](auth.md) |
| POST | `/api/auth/signup` | 공개 | 201 / 400 / 409 | [auth.md](auth.md) |
| POST | `/api/auth/login` | 공개 | 200 / 401 | [auth.md](auth.md) |
| POST | `/api/auth/guest` | 공개 | 200 | [auth.md](auth.md) |
| POST | `/api/auth/logout` | 공개 | 204 | [auth.md](auth.md) |
| POST | `/api/auth/forgot-password` | 공개 | 200 | [auth.md](auth.md) |
| POST | `/api/auth/reset-password` | 공개 | 200 / 400 | [auth.md](auth.md) |
| GET | `/api/auth/check-nickname` | 공개 | 200 | [auth.md](auth.md) |
| POST | `/api/auth/agree` | **JWT** | 200 / 400 | [auth.md](auth.md) |
| POST | `/api/auth/oauth2/{provider}` | 공개 | 200 / 400 / 401 | [auth.md](auth.md) |

### 2. Community — Posts · Comments · Voting · Jury

| Method | Path | Auth | 상태코드 | 설명 |
|---|---|---|---|---|
| POST | `/api/community/posts` | **JWT** | 201 / 422 | 게시글 작성 |
| GET | `/api/community/posts` | 공개 | 200 | 게시글 목록 |
| GET | `/api/community/posts/{id}` | 공개 | 200 / 404 | 게시글 상세 |
| PATCH | `/api/community/posts/{id}` | **JWT** | 200 / 403 / 404 | 게시글 수정 (작성자만) |
| DELETE | `/api/community/posts/{id}` | **JWT** | 204 / 403 / 404 | 게시글 삭제 (작성자만) |
| POST | `/api/community/posts/{id}/comments` | **JWT** | 201 / 422 | 댓글 작성 |
| GET | `/api/community/posts/{id}/comments` | 공개 | 200 | 댓글 목록 |
| PATCH | `/api/community/posts/{postId}/comments/{id}` | **JWT** | 200 / 403 / 404 | 댓글 수정 (작성자만) |
| DELETE | `/api/community/posts/{postId}/comments/{id}` | **JWT** | 204 / 403 / 404 | 댓글 삭제 (작성자만) |
| GET | `/api/community/posts/{id}/jury` | 공개 | 200 / 404 | AI 배심원 조회 |
| POST | `/api/community/posts/{id}/votes` | **JWT** | 201 / 422 | 투표 생성 |
| POST | `/api/community/posts/{postId}/comments/{id}/like` | **JWT** | 201 / 204 | 댓글 좋아요 |
| POST | `/api/community/posts/{id}/like` | **JWT** | 201 / 204 | 게시글 좋아요 |
| POST | `/api/community/posts/{postId}/comments/{id}/report` | **JWT** | 202 | 댓글 신고 |
| POST | `/api/community/posts/{id}/view` | 공개 | 200 / 400 | 조회수 기록 (deviceId 기준 중복 방지) |

### 2.1. Partner Invite API

| Method | Path | Auth | 상태코드 | 설명 |
|---|---|---|---|---|
| POST | `/api/community/posts/{id}/invite` | **JWT** | 201 / 403 / 404 | 초대 토큰 생성. 응답: {inviteToken, inviteUrl} |
| GET | `/api/s/{token}` | 공개 | 200 / 400 / 404 | 토큰으로 포스트 프리뷰 조회. 응답: {postId, userTitle, authorBodyPublished, category} |
| POST | `/api/s/{token}/answer` | 공개 | 201 / 400 / 404 | 파트너 답변 제출. 본문: {userTitle?, bodyRaw}. WAIT_FOR_PARTNER 모드면 자동 PUBLIC 발행 |
| PATCH | `/api/community/posts/{id}/publish-mode` | **JWT(author)** | 200 / 403 / 404 | 발행 모드 설정. 본문: {mode: PUBLISH_NOW\|WAIT_FOR_PARTNER, voteDurationHours: 24\|72\|168\|null} |
| POST | `/api/community/posts/{id}/publish-now` | **JWT(author)** | 200 / 403 / 404 | 즉시 광장 공개(visibility=PUBLIC, voteCloseAt 설정) |

**GET /api/community/posts/{id} 응답에 추가된 필드:**
- `paired` (Boolean): 파트너 답변 도착 여부
- `partnerAnsweredAt` (String, nullable): 파트너 답변 도착 시각 (ISO-8601 UTC)
- `partnerBodyPublished` (String, nullable): 파트너 본문
- `inviteToken` (String, nullable): 초대 토큰 (작성자 본인만 조회 가능)

### 3. User

| Method | Path | Auth | 상태코드 | 상세 문서 |
|---|---|---|---|---|
| GET | `/api/users/me` | **JWT** | 200 / 404 | [user.md](user.md) |
| PATCH | `/api/users/me` | **JWT** | 200 | [user.md](user.md) |
| POST | `/api/users/me/password` | **JWT** | 200 / 401 | [user.md](user.md) |
| DELETE | `/api/users/me` | **JWT** | 204 / 401 | [user.md](user.md) |
| POST | `/api/users/me/tutorial/complete` | **JWT** | 204 | [user.md](user.md) |

### 4. Feedback

| Method | Path | Auth | 상태코드 | 상세 문서 |
|---|---|---|---|---|
| POST | `/api/feedbacks` | 공개 | 201 / 400 | [feedback.md](feedback.md) |

### 5. Health

| Method | Path | Auth | 상태코드 | 설명 |
|---|---|---|---|---|
| GET | `/api/health` | 공개 | 200 | Liveness probe (status=UP) |

### 6. Admin — Dashboard

| Method | Path | Auth | 상태코드 | 상세 문서 |
|---|---|---|---|---|
| GET | `/api/admin/dashboard/summary` | **JWT + ADMIN** | 200 | [admin.md](admin.md) |
| GET | `/api/admin/dashboard/daily-stats` | **JWT + ADMIN** | 200 | [admin.md](admin.md) |
| GET | `/api/admin/dashboard/retention` | **JWT + ADMIN** | 200 | [admin.md](admin.md) |
| GET | `/api/admin/dashboard/crisis-recent` | **JWT + ADMIN** | 200 | [admin.md](admin.md) |
| GET | `/api/admin/dashboard/llm-failure-rate` | **JWT + ADMIN** | 200 | [admin.md](admin.md) |

### 7. Admin — Users

| Method | Path | Auth | 상태코드 | 상세 문서 |
|---|---|---|---|---|
| GET | `/api/admin/users/search` | **JWT + ADMIN** | 200 | [admin.md](admin.md) |
| GET | `/api/admin/users` | **JWT + ADMIN** | 200 | [admin.md](admin.md) |
| GET | `/api/admin/users/{id}` | **JWT + ADMIN** | 200 / 404 | [admin.md](admin.md) |
| DELETE | `/api/admin/users/{id}/data` | **JWT + ADMIN** | 200 | [admin.md](admin.md) |
| PATCH | `/api/admin/users/{id}/roles` | **JWT + ADMIN** | 200 / 400 / 404 | [admin.md](admin.md) |

### 8. Admin — Health

| Method | Path | Auth | 상태코드 | 상세 문서 |
|---|---|---|---|---|
| GET | `/api/admin/health/system` | **JWT + ADMIN** | 200 | [admin.md](admin.md) |

### 9. Admin — Feedbacks

| Method | Path | Auth | 상태코드 | 상세 문서 |
|---|---|---|---|---|
| GET | `/api/admin/feedbacks` | **JWT + ADMIN** | 200 | [admin.md](admin.md) |
| PATCH | `/api/admin/feedbacks/{id}` | **JWT + ADMIN** | 200 / 400 / 404 | [admin.md](admin.md) |

### 10. Admin — Prompts (app.admin.enabled=true)

| Method | Path | Auth | 상태코드 | 상세 문서 |
|---|---|---|---|---|
| POST | `/api/admin/prompts/reload` | **JWT + ADMIN** | 200 / 500 | [admin.md](admin.md) |

### 11. Admin — Marketing (dev 전용)

| Method | Path | Auth | 상태코드 | 설명 |
|---|---|---|---|---|
| 다수 | `/api/admin/marketing/**` | **JWT + ADMIN** | 200~500 | Story, Simulation, Content, Template, Hashtag, Calendar, Cost, SocialPublish, MarketingImage, Repurpose, Dashboard |

### 14. Admin — Test (@Profile dev only)

| Method | Path | Auth | 상태코드 | 상세 문서 |
|---|---|---|---|---|
| POST | `/api/admin/test/reset` | JWT | 200 | [admin.md](admin.md) |

### 15. Admin — Marketing: Source Stories (app.features.marketing.enabled=true, dev-only)

| Method | Path | Auth | 상태코드 | 설명 |
|---|---|---|---|---|
| POST | `/api/admin/marketing/source-stories` | **JWT + ADMIN** | 201 / 400 / 422 | 사연 등록 (익명화 포함) |
| GET | `/api/admin/marketing/source-stories` | **JWT + ADMIN** | 200 | 목록 조회 |
| GET | `/api/admin/marketing/source-stories/{id}` | **JWT + ADMIN** | 200 / 404 | 단건 조회 |
| DELETE | `/api/admin/marketing/source-stories/{id}` | **JWT + ADMIN** | 204 / 404 | 삭제 |
| POST | `/api/admin/marketing/source-stories/{id}/approve` | **JWT + ADMIN** | 200 / 404 | 승인 |
| POST | `/api/admin/marketing/source-stories/{id}/reject` | **JWT + ADMIN** | 200 / 404 | 거부 (이유 포함) |

### 16. Admin — Marketing: Simulations (app.features.marketing.enabled=true, dev-only)

| Method | Path | Auth | 상태코드 | 설명 |
|---|---|---|---|---|
| POST | `/api/admin/marketing/simulations` | **JWT + ADMIN** | 201 / 400 | 시뮬레이션 생성 + 큐 등록 |
| GET | `/api/admin/marketing/simulations` | **JWT + ADMIN** | 200 | 목록 조회 |
| GET | `/api/admin/marketing/simulations/{id}` | **JWT + ADMIN** | 200 / 404 | 단건 조회 |
| DELETE | `/api/admin/marketing/simulations/{id}` | **JWT + ADMIN** | 204 / 404 | 삭제 |
| GET | `/api/admin/marketing/simulations/{id}/cost` | **JWT + ADMIN** | 200 | 비용 상세 |

### 17. Admin — Marketing: Contents (app.features.marketing.enabled=true, dev-only)

| Method | Path | Auth | 상태코드 | 설명 |
|---|---|---|---|---|
| POST | `/api/admin/marketing/contents/generate` | **JWT + ADMIN** | 202 / 400 | 콘텐츠 비동기 생성 (`?simulationId=&platform=`) |
| GET | `/api/admin/marketing/contents` | **JWT + ADMIN** | 200 | 콘텐츠 목록 |
| GET | `/api/admin/marketing/contents/{id}` | **JWT + ADMIN** | 200 / 404 | 콘텐츠 단건 조회 |
| PUT | `/api/admin/marketing/contents/{id}` | **JWT + ADMIN** | 200 / 404 | 본문 수정 (`bodyText`) |
| DELETE | `/api/admin/marketing/contents/{id}` | **JWT + ADMIN** | 204 / 404 | 삭제 |
| POST | `/api/admin/marketing/contents/{id}/approve` | **JWT + ADMIN** | 200 / 404 | 승인 (APPROVED) |
| POST | `/api/admin/marketing/contents/{id}/reject` | **JWT + ADMIN** | 200 / 404 | 거부 (`?reason=`, REJECTED) |

### 18. Admin — Marketing: Images (app.features.marketing.enabled=true, dev-only)

| Method | Path | Auth | 상태코드 | 설명 |
|---|---|---|---|---|
| GET | `/api/admin/marketing/images/{filename}` | **JWT + ADMIN** | 200 / 400 / 404 | 채팅 스크린샷 PNG 서빙. `..` / `/` 포함 파일명 400 |

### 19. Admin — Marketing: Cost (app.features.marketing.enabled=true, dev-only)

| Method | Path | Auth | 상태코드 | 설명 |
|---|---|---|---|---|
| GET | `/api/admin/marketing/cost/monthly` | **JWT + ADMIN** | 200 | 월 누적 비용 + 한도 퍼센트 |
| GET | `/api/admin/marketing/cost/simulation/{id}` | **JWT + ADMIN** | 200 / 404 | 시뮬레이션별 비용 내역 |

---

## 변경 시 절차

1. 컨트롤러에 엔드포인트 추가/변경
2. 해당 도메인 `.md` 파일 업데이트 (예: `auth.md`, `session-chat.md`)
3. 이 문서(rest-spec.md) 마스터 표 및 에러코드 업데이트
4. `admin.md` 또는 `shared/docs/admin-dashboard.md` (admin 엔드포인트인 경우)
5. `database-schema.md` (스키마 변경 있는 경우)
6. Swagger 어노테이션(`@Operation`, `@ApiResponse`) 컨트롤러에 반영
