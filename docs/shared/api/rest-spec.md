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
| POST | `/api/community/posts` | **JWT** | 200 / 400 / 409 / 422 | 게시글 작성 (synthetic bot은 내부 멱등성 헤더 지원) |
| GET | `/api/community/posts` | 공개 | 200 | 게시글 목록 |
| GET | `/api/community/posts/search` | 공개 | 200 | 키워드 검색 (`?q=`, `category=`, `page=`, `size=`) |
| GET | `/api/community/posts/counts` | 공개 | 200 | 광장별 글 수 (`{"":.., "COUPLE":.., ...}`) |
| GET | `/api/community/posts/{id}` | 공개 | 200 / 404 | 게시글 상세 |
| PATCH | `/api/community/posts/{id}` | **JWT** | 200 / 403 / 404 | 게시글 수정 (작성자만) |
| DELETE | `/api/community/posts/{id}` | **JWT** | 204 / 403 / 404 | 게시글 삭제 (작성자만) |
| POST | `/api/community/posts/{id}/comments` | **JWT** | 200 / 400 / 409 / 422 | 댓글 작성 (synthetic bot은 내부 멱등성 헤더 지원) |
| GET | `/api/community/posts/{id}/comments` | 공개 | 200 | 댓글 목록 |
| PATCH | `/api/community/posts/{postId}/comments/{id}` | **JWT** | 200 / 403 / 404 | 댓글 수정 (작성자만) |
| DELETE | `/api/community/posts/{postId}/comments/{id}` | **JWT** | 204 / 403 / 404 | 댓글 삭제 (작성자만) |
| GET | `/api/community/posts/{id}/jury` | 공개 | 200 / 404 | AI 배심원 조회 |
| POST | `/api/community/posts/{id}/vote` | **JWT** | 200 / 403 | 투표 생성 (공감 비율 가중치는 §2.0.2 참조) |
| DELETE | `/api/community/posts/{id}/vote` | **JWT** | 200 / 403 | 투표 취소 |
| POST | `/api/community/posts/{postId}/comments/{id}/like` | **JWT** | 201 / 204 | 댓글 좋아요 |
| POST | `/api/community/posts/{id}/like` | **JWT** | 201 / 204 | 게시글 좋아요 |
| POST | `/api/community/posts/{postId}/comments/{id}/report` | **JWT** | 202 | 댓글 신고 |
| POST | `/api/community/posts/{id}/view` | 공개 | 200 / 400 | 조회수 기록 (deviceId 기준 중복 방지) |

#### 2.0.1 Internal synthetic-bot write idempotency

`POST /api/community/posts` 및 `POST /api/community/posts/{postId}/comments`는 **synthetic=1 봇 계정의 인증 JWT**에 한해 `Idempotency-Key` 헤더를 해석한다. 일반 사용자·익명 요청의 같은 헤더는 무시되며, 공개 사용자 API의 멱등성 계약으로 확장하지 않는다.

- 키는 오케스트레이터 plan item의 기존 `idempotency_key`를 그대로 사용한다(1~160자, `A-Za-z0-9._:-`).
- 최초 요청은 글/댓글을 작성하고 내부 `bot_request_dedup`에 `key → target type/id, bot user`를 같은 트랜잭션으로 저장한다.
- 같은 봇이 같은 종류의 키로 재시도하면 기존 글/댓글을 `200`으로 반환하고, 알림·outbox·배심원 생성도 다시 발생시키지 않는다.
- 다른 봇 또는 다른 target type으로 키를 재사용하면 `409 IDEMPOTENCY_KEY_CONFLICT`; 손상된 매핑은 `409 IDEMPOTENCY_TARGET_*`로 실패한다.

#### 2.0.2 공감 비율(투표) 가중치 (2026-07-31~)

`GET /api/community/posts/{id}`, `POST/.../vote`, `DELETE/.../vote` 응답의 옵션별 `percentage`는 사람표와 AI 유저(`users.synthetic=1`)표를 동일 가중치로 세지 않는다. AI 유저 투표는 "커뮤니티가 비어 보이지 않게 하는 시딩" 목적이며, 실제 사람 투표가 쌓일수록 결과에 대한 영향력이 줄어든다.

```
weight_ai   = 1 / (1 + humanVoteCount)   // humanVoteCount=0이면 1(풀 가중치)
weight_human = 1 (고정)
percentage(option) = (humanCount(option)×1 + aiCount(option)×weight_ai) / (humanTotal×1 + aiTotal×weight_ai) × 100
```

표시되는 절대 투표 개수(`count`)는 사람표+AI표 단순 합산이며 가중치는 `percentage`에만 적용된다. 배심원(`GET .../jury`) 투표는 이 계산과 무관한 별도 데이터다. 구현: `backend/.../service/community/VoteService.java`, `CommunityPostController.castVote/cancelVote`, `PostDetailResponse.from()`.

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

### 8.1. Admin — Crawl Status (크롤 신선도)

| Method | Path | Auth | 상태코드 | 상세 문서 |
|---|---|---|---|---|
| GET | `/api/admin/crawl-status` | **JWT + ADMIN** | 200 | [admin.md](admin.md) |

### 9. Admin — Feedbacks

| Method | Path | Auth | 상태코드 | 상세 문서 |
|---|---|---|---|---|
| GET | `/api/admin/feedbacks` | **JWT + ADMIN** | 200 | [admin.md](admin.md) |
| PATCH | `/api/admin/feedbacks/{id}` | **JWT + ADMIN** | 200 / 400 / 404 | [admin.md](admin.md) |

### 10. Admin — AI User Generation (AI 유저 생성 정책·진행 현황)

| Method | Path | Auth | 상태코드 | 상세 문서 |
|---|---|---|---|---|
| GET | `/api/admin/ai-user/generation-config` | **JWT + ADMIN** | 200 | [admin.md](admin.md) |
| PUT | `/api/admin/ai-user/generation-config` | **JWT + ADMIN** | 200 | [admin.md](admin.md) |
| GET | `/api/admin/ai-user/generation-status` | **JWT + ADMIN** | 200 | [admin.md](admin.md) |
| POST | `/api/admin/ai-user/cleanup/reduce-ㅠ` | **JWT + ADMIN** | 200 | [admin.md](admin.md) |
| POST | `/api/admin/ai-user/backfill-comment-likes` | **JWT + ADMIN** | 202 | [admin.md](admin.md) |
| POST | `/api/admin/ai-user/kill` | **JWT + ADMIN** | 200 | [admin.md](admin.md) |

> **2026-07-31~**: `generation-config` GET/PUT에서 레거시 필드(`backendPost`/`backendComment`/`backendReply`/`promptCaching`/`dailyTokenBudget`/`schedulerMode`)가 삭제되고 PLAN 모드로 일원화됐다. 기존 3개 provider(`providerAiPostBundle`/`providerHumanPostPlan`/`providerHumanInteraction`)에 `providerVoteLike`(`"CLAUDE"|"CODEX"|"OFF"`)가 추가되어 AI 투표·좋아요 생성도 PLAN 파이프라인으로 이관됨. `kill`은 이제 4개 provider 전부를 OFF로 설정한다.

### 10.1. Admin — Prompts (app.admin.enabled=true)

| Method | Path | Auth | 상태코드 | 상세 문서 |
|---|---|---|---|---|
| POST | `/api/admin/prompts/reload` | **JWT + ADMIN** | 200 / 500 | [admin.md](admin.md) |

### 11. Admin — Content Management (AI 콘텐츠 조회·첨삭)

| Method | Path | Auth | 상태코드 | 설명 |
|---|---|---|---|---|
| GET | `/api/admin/content/posts` | **JWT + ADMIN** | 200 | AI 게시글 목록 (`?status=VOTING&page=&size=`) — `synthetic`·`createdByAdmin` 필드 포함 |
| GET | `/api/admin/content/posts/{postId}` | **JWT + ADMIN** | 200 / 404 | 단일 게시글 원문 조회 |
| GET | `/api/admin/content/posts/{postId}/source-comparison` | **JWT + ADMIN** | 200 / 404 | 원본 비교 조회. 응답: `{synthetic, hasSource, source{community,url,title,body}, generated{title,body}}` |
| PATCH | `/api/admin/content/posts/{postId}` | **JWT + ADMIN** | 200 / 404 | 게시글 수정. Body: `{title?, bodyRaw?, partnerBodyRaw?, status?, category?, viewCount?}`. `title` 수정 시 `title`과 `userTitle`을 함께 동기화. `viewCount`는 단순 컬럼이라 직접 값 지정 가능 (2026-07-31~) |
| DELETE | `/api/admin/content/posts/{postId}` | **JWT + ADMIN** | 204 / 404 | 게시글 soft delete (`deleted_at`, `deleted_by_admin_id`) |
| POST | `/api/admin/content/posts/{postId}/block` | **JWT + ADMIN** | 200 / 404 | 게시글 상태를 `BLOCKED`로 변경 |
| POST | `/api/admin/content/posts/{postId}/unblock` | **JWT + ADMIN** | 200 / 404 | 게시글 상태를 `VOTING`으로 복구 |
| POST | `/api/admin/content/posts` | **JWT + ADMIN** | 200 | **(2026-07-31~)** 게시글 수동 생성. Body: `{title, bodyRaw, category, authorId}`. `authorId`는 자유 텍스트(존재 여부 미검증, FK 없음). `createdByAdmin=true`로 저장 |
| POST | `/api/admin/content/posts/{postId}/likes/adjust` | **JWT + ADMIN** | 200 / 400 / 409 | **(2026-07-31~)** 좋아요 수 증가/감소. Body: `{delta: 1\|-1}`. `post_likes`가 조인테이블 집계라 정확한 값 지정은 불가 — delta=1은 아직 좋아요 안 누른 AI 유저를 골라 행 추가, delta=-1은 AI 유저 소유 행만 삭제(실사용자 좋아요 보존). 후보 없으면 409 |
| GET | `/api/admin/content/comments` | **JWT + ADMIN** | 200 | AI 댓글 목록 (`?status=ACTIVE&page=&size=`) — `synthetic`·`createdByAdmin` 필드 포함 |
| PATCH | `/api/admin/content/comments/{commentId}` | **JWT + ADMIN** | 200 / 404 | 댓글 수정. Body: `{body}` |
| DELETE | `/api/admin/content/comments/{commentId}` | **JWT + ADMIN** | 204 / 404 | 댓글 soft delete |
| POST | `/api/admin/content/comments/{commentId}/block` | **JWT + ADMIN** | 200 / 404 | 댓글 상태를 `BLOCKED`로 변경 |
| POST | `/api/admin/content/comments/{commentId}/unblock` | **JWT + ADMIN** | 200 / 404 | 댓글 상태를 `ACTIVE`로 복구 |
| POST | `/api/admin/content/comments` | **JWT + ADMIN** | 200 | **(2026-07-31~)** 댓글/대댓글 수동 생성. Body: `{postId, parentCommentId?, body, authorId}`. `authorId` 자유 텍스트. `createdByAdmin=true`로 저장 |
| POST | `/api/admin/content/comments/{commentId}/likes/adjust` | **JWT + ADMIN** | 200 / 400 / 409 | **(2026-07-31~)** 좋아요 수 증가/감소. Body: `{delta: 1\|-1}`. `PostComment.likeCount` 컬럼과 `post_likes` 조인테이블을 함께 갱신(동기화) |
| POST | `/api/admin/content/corrections/save` | **JWT + ADMIN** | 201 / 404 | LLM 없이 즉시 PENDING 저장. `applyLive=true`이면 본문도 교체. Body: `{targetType, targetId, correctedText, applyLive, adminOpinion?}` |
| POST | `/api/admin/content/corrections/analyze` | **JWT + ADMIN** | 200 / 404 | 단건 LLM 분석 (DB 미변경). Body: `{targetType, targetId, correctedText}` |
| POST | `/api/admin/content/corrections/commit` | **JWT + ADMIN** | 200 / 404 | 분석 결과 확정 저장. Body: `{targetType, targetId, correctedText, personaCaution?, globalRules[], applyLive}` |
| POST | `/api/admin/content/corrections/reconstruction/analyze` | **JWT + ADMIN** | 200 / 404 | 재구성 규칙 분석 (LLM). Body: `{personaId, generatedText}`. 응답: `{personaId, generatedText, suggestedReconstructionRules}` |
| POST | `/api/admin/content/corrections/reconstruction/commit` | **JWT + ADMIN** | 200 / 404 | 재구성 규칙 적용. Body: `{correctionId, appliedLive, rulesCreated, cautionApplied}`. 응답: `{appliedLive, rulesCreated, cautionApplied}` |

> `adminOpinion` (TEXT, nullable): 관리자가 첨삭 시 남긴 수정 의도·방향. 일괄 분석 MAP 프롬프트에 입력 신호로 사용됨 (V74 추가, 2026-06-08).

### 12. Admin — AI Rules (전역 금지 규칙·페르소나 주의사항·첨삭 이력·일괄 분석)

#### 12-A. 전역 금지 규칙

| Method | Path | Auth | 상태코드 | 설명 |
|---|---|---|---|---|
| GET | `/api/admin/ai-rules/global` | **JWT + ADMIN** | 200 | 목록 (`?page=&size=&active=`) |
| POST | `/api/admin/ai-rules/global` | **JWT + ADMIN** | 201 | 추가. Body: `{ruleText, scope: ALL\|POST\|COMMENT\|RECONSTRUCTION}` |
| PATCH | `/api/admin/ai-rules/global/{id}` | **JWT + ADMIN** | 200 / 404 | 활성/비활성 토글. Body: `{active}` |
| DELETE | `/api/admin/ai-rules/global/{id}` | **JWT + ADMIN** | 204 / 404 | 삭제 |

#### 12-B. 페르소나 주의사항

| Method | Path | Auth | 상태코드 | 설명 |
|---|---|---|---|---|
| GET | `/api/admin/ai-rules/cautions` | **JWT + ADMIN** | 200 | 목록 (`?page=&size=&personaId=`) |
| PATCH | `/api/admin/ai-rules/cautions/{corrId}` | **JWT + ADMIN** | 200 | 토글. Body: `{active}` |
| DELETE | `/api/admin/ai-rules/cautions/{corrId}` | **JWT + ADMIN** | 204 | 삭제 |

#### 12-C. 첨삭 이력

| Method | Path | Auth | 상태코드 | 설명 |
|---|---|---|---|---|
| GET | `/api/admin/ai-rules/history` | **JWT + ADMIN** | 200 | 목록 (`?page=&size=&status=PENDING\|PROCESSED\|SKIPPED`). 응답 항목에 `adminOpinion` 포함 |
| POST | `/api/admin/ai-rules/history/{id}/analyze` | **JWT + ADMIN** | 200 / 404 | 단건 Sonnet 분석 (저장 미변경) |
| POST | `/api/admin/ai-rules/history/{id}/apply` | **JWT + ADMIN** | 200 / 404 | 단건 분석 결과 적용. Body: `{scope, personaCaution?, globalRules[], pushToBank}` |
| PATCH | `/api/admin/ai-rules/history/{id}/skip` | **JWT + ADMIN** | 204 / 404 | SKIPPED 처리 |

#### 12-D. 일괄 분석 map-reduce (비동기 job)

> MAP=Sonnet(청크별 패턴 추출) + REDUCE=Opus(통합·scope 판정). CLI 전용(API 키 미사용). (2026-06-08 추가)

| Method | Path | Auth | 상태코드 | 설명 |
|---|---|---|---|---|
| POST | `/api/admin/ai-rules/history/analyze-batch` | **JWT + ADMIN** | 202 | PENDING 전체 일괄 분석 시작. 응답: `{jobId, queued, message}`. LLM 트리거(no-llm 가드레일 대상) |
| GET | `/api/admin/ai-rules/history/analyze-batch/{jobId}` | **JWT + ADMIN** | 200 / 404 | 작업 상태 폴링. 응답: `{jobId, status: RUNNING\|READY\|FAILED, pendingCount, chunksDone, chunksTotal, plan?, error?}` |
| POST | `/api/admin/ai-rules/history/apply-batch-plan` | **JWT + ADMIN** | 200 / 400 | 관리자 승인 플랜 적용(LLM 없음). Body: `{globalRules[], personaCautions[], pushToBank}`. 응답: `{rulesCreated, cautionsApplied, corrProcessed}` |

**BatchPlan 스키마:**
```json
{
  "globalRules":    [{"ruleText":"…","scope":"ALL|POST|COMMENT","sourceCorrIds":[…],"rationale":"…"}],
  "personaCautions":[{"personaId":"…","cautionText":"…","sourceCorrIds":[…],"rationale":"…"}],
  "allSourceCorrIds":[…]
}
```

**job TTL**: 30분(인메모리). 백엔드 재시작 시 유실 → 재실행으로 복구.

#### 12-E. 프롬프트 템플릿

| Method | Path | Auth | 상태코드 | 설명 |
|---|---|---|---|---|
| GET | `/api/admin/ai-rules/prompts` | **JWT + ADMIN** | 200 | 전체 목록 |
| GET | `/api/admin/ai-rules/prompts/{key}` | **JWT + ADMIN** | 200 / 404 | 단건 조회 |
| PUT | `/api/admin/ai-rules/prompts/{key}` | **JWT + ADMIN** | 200 / 404 | 내용 수정. Body: `{content}` |

---

### 13. Admin — Test (@Profile dev only)

| Method | Path | Auth | 상태코드 | 상세 문서 |
|---|---|---|---|---|
| POST | `/api/admin/test/reset` | JWT | 200 | [admin.md](admin.md) |

### Marketing API

마케팅 API는 ASM 서비스(`/api/v1/jobs`)로 이전됨. Again-Spring-Marketing 프로젝트 문서 참조.

---

## 변경 시 절차

1. 컨트롤러에 엔드포인트 추가/변경
2. 해당 도메인 `.md` 파일 업데이트 (예: `auth.md`, `session-chat.md`)
3. 이 문서(rest-spec.md) 마스터 표 및 에러코드 업데이트
4. `admin.md` 또는 `docs/shared/admin-dashboard.md` (admin 엔드포인트인 경우)
5. `database-schema.md` (스키마 변경 있는 경우)
6. Swagger 어노테이션(`@Operation`, `@ApiResponse`) 컨트롤러에 반영
