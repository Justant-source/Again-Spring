# REST API 명세

## Source of truth

- 컨트롤러: `backend/src/main/java/com/againspring/api/*Controller.java`
- DTO: `backend/.../api/dto/{request,response}/`
- Swagger UI: `http://localhost:8080/swagger-ui.html` (dev), `https://dev.againspring.net/swagger-ui/` (서버 dev)

코드와 다르면 코드가 옳습니다.

---

## 공통 규약

- **Base URL**: 클라이언트는 항상 `/api/...`로 호출 (nginx가 backend로 라우팅)
- **인증**: `Authorization: Bearer {jwt}` (필요한 엔드포인트에 한해)
- **Content-Type**: `application/json` (요청/응답 모두)
- **시간**: ISO-8601 UTC (`2026-04-26T10:30:00Z`)
- **에러 형식**:
  ```json
  {
    "error": {
      "code": "SESSION_NOT_FOUND",
      "message": "세션을 찾을 수 없어요",
      "timestamp": "2026-04-26T10:30:00Z"
    }
  }
  ```
- **Rate limit**: [policies/auth.md](../policies/auth.md) 참조

## 에러 코드 (`GlobalExceptionHandler`)

| 코드 | HTTP | 설명 |
|---|---|---|
| `INVALID_INPUT` | 400 | Bean Validation 실패 |
| `UNAUTHORIZED` | 401 | 인증 실패 / 토큰 만료 / 토큰 폐기 |
| `FORBIDDEN` | 403 | 권한 없음 |
| `NOT_FOUND` | 404 | 리소스 없음 |
| `INVITE_EXPIRED` | 410 | 초대 토큰 만료 |
| `CRISIS_DETECTED` | 422 | 위험 키워드 감지 — 세션 중단 |
| `FORBIDDEN_WORD_DETECTED` | 422 | 금지어 감지 |
| `LLM_UNAVAILABLE` | 503 | LLM 일시 불가 |
| `INTERNAL_ERROR` | 500 | 서버 오류 |

---

## Auth (`AuthController` + `OAuth2Controller`)

| Method | Path | 인증 | 설명 |
|---|---|---|---|
| POST | `/api/auth/send-verification` | ✗ | 이메일 6자리 코드 발송 (10분 유효) |
| POST | `/api/auth/signup` | ✗ | 회원가입 (코드 검증 포함) |
| POST | `/api/auth/login` | ✗ | 로그인 → JWT 발급 |
| POST | `/api/auth/guest` | ✗ | 게스트 토큰 발급 (1h) |
| POST | `/api/auth/logout` | ✓ | 토큰 폐기 (revoked_tokens) |
| POST | `/api/auth/forgot-password` | ✗ | 재설정 토큰 이메일 발송 |
| POST | `/api/auth/reset-password` | ✗ | 토큰 + 새 비밀번호로 재설정 |
| POST | `/api/auth/oauth2/{provider}` | ✗ | provider ∈ {google, kakao, naver} |

상세 흐름: [policies/auth.md](../policies/auth.md)

### `POST /api/auth/login` 예

```jsonc
// Request
{ "email": "user@example.com", "password": "Pass123!" }

// Response 200
{
  "user": {
    "id": "usr_abc123",
    "email": "user@example.com",
    "nickname": "달콩",
    "isGuest": false,
    "communicationStyle": "wave",
    "onboardingCompleted": true
  },
  "token": { "accessToken": "eyJhbGc...", "expiresIn": 86400 }
}
```

---

## User (`UserController`)

| Method | Path | 인증 | 설명 |
|---|---|---|---|
| GET | `/api/users/me` | ✓ | 내 정보 |
| POST | `/api/users/me/onboarding` | ✓ | 온보딩 결과 저장 → 스타일 반환 |
| DELETE | `/api/users/me` | ✓ | 탈퇴 (소프트 삭제 + 원문 즉시 삭제) |

### `POST /api/users/me/onboarding`

```jsonc
// Request
{ "answers": [4,2,3,5,2,4,3,5,4,3], "mbtiType": "INFP" /* optional */ }

// Response 200
{
  "communicationStyle": "wave",
  "styleInfo": {
    "emoji": "🌊", "label": "파도형",
    "description": "...", "strengths": [...], "caution": [...]
  }
}
```

상세: [policies/onboarding.md](../policies/onboarding.md)

---

## Session (카톡 채팅 API)

### `POST /api/sessions`

새로운 Solo 세션을 생성합니다.

**Request**:
```json
{
  "relationType": "couple",
  "category": { "major": "...", "middle": "...", "minor": "..." }
}
```

**Response 201**:
```json
{
  "id": "uuid",
  "status": "chatting_solo",
  "userAId": "user-id",
  "userBId": null,
  "userAMessageCount": 0,
  "userBMessageCount": 0,
  "inviteToken": null
}
```

---

### `POST /api/sessions/{id}/invite`

상대를 초대하기 위한 초대 토큰을 생성합니다.

**Response 200**:
```json
{
  "inviteToken": "inv_abc123",
  "inviteExpiresAt": "2026-04-29T05:00:00Z"
}
```

---

### `POST /api/sessions/join/{token}`

초대 토큰을 사용해 세션에 참여합니다 (userB 역할).

**Request**:
```json
{ "nickname": "선택" }
```

**Response 200**:
```json
{
  "id": "uuid",
  "status": "chatting_duo",
  "userAId": "user-a-id",
  "userBId": "user-b-id",
  "userAMessageCount": 5,
  "userBMessageCount": 0,
  "inviteToken": null
}
```

---

### `POST /api/sessions/{id}/messages`

사용자 메시지를 입력하고 AI 중재자 응답을 받습니다.

**Request**:
```json
{ "content": "..." }
```

**Response 200**:
```json
{
  "userMessage": {
    "id": 12,
    "sender": "USER_A",
    "content": "...",
    "createdAt": "..."
  },
  "mediatorMessage": {
    "id": 13,
    "sender": "MEDIATOR_TO_A",
    "content": "...",
    "createdAt": "..."
  },
  "finalizeSuggested": false,
  "crisisLevel": null
}
```

**Response 409** (위험 키워드 감지):
```json
{
  "crisisLevel": 1
}
```

---

### `GET /api/sessions/{id}/messages?since={epoch_ms}`

본인이 보낸 메시지와 중재자 응답을 조회합니다.

**Response 200**: 메시지 배열. 본인 메시지 + 본인 중재자 응답만 포함.

---

### `GET /api/sessions/{id}/partner-messages`

상대방의 메시지 메타데이터를 조회합니다. **content 필드 절대 없음**.

**Response 200**:
```json
[
  { "id": 7, "sender": "USER_B", "charCount": 142, "createdAt": "..." },
  { "id": 8, "sender": "MEDIATOR_TO_B", "charCount": 38, "createdAt": "..." }
]
```

---

### `GET /api/sessions/{id}/partner-status`

상대방의 온라인 상태 및 활동 정보를 조회합니다.

**Response 200**:
```json
{
  "joined": true,
  "isActive": true,
  "inviteSent": false,
  "messageCount": 4,
  "lastActivityAt": "2026-04-26T05:02:33Z"
}
```

---

### `POST /api/sessions/{id}/finalize`

종료 권유를 전송합니다.

**Response 200**:
```json
{
  "completed": true|false,
  "awaitingPartner": true|false
}
```

---

### `POST /api/sessions/{id}/finalize/agree`

상대방의 종료 권유에 동의합니다.

**Response 200**:
```json
{
  "completed": true|false,
  "awaitingPartner": true|false
}
```

---

### `POST /api/sessions/{id}/finalize/decline`

상대방의 종료 권유를 거절합니다.

**Response 204**: No Content

---

## Report (`ReportController`)

| Method | Path | 인증 | 설명 |
|---|---|---|---|
| POST | `/api/sessions/{sessionId}/report` | ✓ | 리포트 생성 (비동기) |
| GET | `/api/reports/{reportId}` | ✓ | 리포트 조회 |

### `GET /api/reports/{id}` 응답 (요약 구조)

```jsonc
{
  "id": "rep_abc123",
  "sessionId": "ses_abc123",
  "conflictType": "difference",
  "isSoloMode": true,
  "fourHorsemenObservation": {
    "criticism": 2,
    "contempt": 1,
    "defensiveness": 3,
    "stonewalling": 0
  },
  "bidResponseRate": 0.6,
  "repairAttempts": 2,
  "metaphorId": "locked-mailbox",
  "metaphorReason": "...",
  "nvcSuggestion": {
    "observation": "...",
    "feeling": "...",
    "need": "...",
    "request": "...",
    "fourSentenceDraft": "..."
  },
  "patternFeedback": "...",
  "suggestedApproach": "...",
  "inviteAgainCta": "...",
  "rawContributionRatio": {
    "a": 60,
    "b": 40
  },
  "perspectiveRespected": true,
  "createdAt": "..."
}
```

화해 기여도 알고리즘: [policies/ratio-calculation.md](../policies/ratio-calculation.md)

---

## Relationship Graph (`RelationshipController`)

| Method | Path | 인증 | 설명 |
|---|---|---|---|
| GET | `/api/users/me/relationships` | ✓ | 내 관계 목록 (사람별 집계) |
| GET | `/api/users/me/relationships/{counterpartUserId}/history` | ✓ | 특정 상대와의 세션 이력 |

### 응답 예

```jsonc
{
  "relationships": [
    {
      "personId": "usr_456",
      "personNickname": "민수",
      "relationType": "couple",
      "sessionCount": 5,
      "lastSessionAt": "..."
    }
  ]
}
```

DB: `user_relationships` (집계), `conflict_history` (이력 행).

---

## Health (`HealthController`)

| Method | Path | 인증 | 설명 |
|---|---|---|---|
| GET | `/api/health` | ✗ | liveness ping |
| GET | `/actuator/health` | ✗ | Spring Actuator |

prod에서는 `/actuator/health`만 노출 (info, metrics, prometheus 등 비활성).

---

## Admin (`AdminPromptsController`)

| Method | Path | 인증 | 설명 |
|---|---|---|---|
| POST | `/api/admin/prompts/reload` | ✓ (ADMIN) | `PromptLoader` 캐시 무효화 |

`shared/prompts/*.md` 파일을 수정한 후 컨테이너 재시작 없이 즉시 반영.

---

## Swagger UI

- dev (로컬): `http://localhost:8080/swagger-ui.html`
- dev (서버): `https://dev.againspring.net/swagger-ui/`
- prod: 비활성 (`application-prod.yml`에서 `springdoc.swagger-ui.enabled: false`)

OpenAPI 정의: `http://localhost:8080/v3/api-docs`. 정적 스냅샷은 `shared/schemas/openapi.yaml`.
