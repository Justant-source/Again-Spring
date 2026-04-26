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
| `TURN_MISMATCH` | 409 | 현재 턴이 아님 |
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

## Session (`SessionController`)

| Method | Path | 인증 | 설명 |
|---|---|---|---|
| POST | `/api/sessions` | ✓ | A가 세션 생성 |
| GET | `/api/sessions/me` | ✓ | 내 세션 목록 |
| GET | `/api/sessions/{id}` | ✓ | 세션 상세 |
| GET | `/api/sessions/{id}/status` | ✓ | 세션 상태 (가벼운 폴링용) |
| POST | `/api/sessions/join/{token}` | ✗ | B 참여 (JWT 발급도 함께) |
| DELETE | `/api/sessions/{id}` | ✓ | 세션 삭제 |

### `POST /api/sessions`

```jsonc
// Request
{
  "relationType": "couple",
  "category": {
    "major": "couple",
    "middle": "connection",
    "minor": "infrequent_contact",
    "customMinor": null
  },
  "description": "3주 동안 연락이 너무 적어서...",
  "inviteMessage": { "tone": "soft", "customText": null }
}

// Response 201
{
  "id": "ses_abc123",
  "inviteToken": "inv_xyz789",
  "inviteUrl": "https://dev.againspring.net/session/join/inv_xyz789",
  "inviteMessage": "우리 얘기 좀 정리해보고 싶어서...",
  "status": "WAITING_B",
  "currentTurn": 1,
  "createdAt": "...",
  "expiresAt": "..."   // 초대 토큰 24h
}
```

### `GET /api/sessions/{id}` 응답에서 주의

- 상대방 `turns[].content`는 내가 같은 턴을 완료하기 전까지 응답에 포함되지 않음 (앵커링 방지)
- 만료된 세션 (30일 경과)은 `turns[].content` = null

---

## Mediation (`MediationController`)

`@RequestMapping("/api/sessions/{sessionId}")` 하위.

| Method | Path | 인증 | 설명 |
|---|---|---|---|
| POST | `.../turns` | ✓ | 턴 진행 |
| GET | `.../turns/current` | ✓ | 현재 턴 상태 |
| GET | `.../stream` | ✓ | SSE 스트림 (턴 이벤트) |

### `POST /api/sessions/{id}/turns`

```jsonc
// Request
{ "turnNumber": 1, "content": "...", "skip": false }

// Response 200 (정상)
{
  "turn": { "turnNumber": 1, "role": "A", "completed": true, "createdAt": "..." },
  "nextTurn": { "turnNumber": 2, "role": "B", "waitingFor": "partner" },
  "mediatorMessage": "말씀해주셔서 감사해요. B님의 답변을 기다리고 있어요."
}

// Response 422 (위험 키워드)
{
  "error": {
    "code": "CRISIS_DETECTED",
    "message": "중요한 안내가 필요한 상황이 감지되었어요",
    "crisisType": "domestic_violence",
    "resources": [
      { "name": "여성긴급전화", "phone": "1366", "available": "24시간" }
    ]
  }
}
```

### `GET .../stream` SSE 이벤트

| event | data |
|---|---|
| `turn_updated` | 상대방이 턴 완료 |
| `mediator_thinking` | LLM 응답 생성 중 |
| `mediator_response` | LLM 응답 완료 |
| `session_status_changed` | 세션 상태 변경 |
| `report_ready` | 리포트 생성 완료 |

```
event: turn_updated
data: {"turnNumber":2,"role":"B","completedAt":"..."}

event: mediator_thinking
data: {"turnNumber":3,"estimatedSeconds":8}

event: mediator_response
data: {"turnNumber":3,"content":"A님께 두 가지..."}
```

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
  "conflictType": "DIFFERENCE",
  "isSoloMode": false,
  "contributionRatio": {
    "a": 55, "b": 45,
    "label": { "a": "먼저 다가가면 좋은 쪽", "b": "마음 열고 기다려주면 좋은 쪽" }
  },
  "needsMap": {
    "axisX": "connection_autonomy", "axisXLabel": "연결성-자율성",
    "axisY": "stability_change",    "axisYLabel": "안정-변화",
    "positionA": { "x": -70, "y": 0 }, "positionB": { "x": 60, "y": 0 },
    "interpretation": "두 분은 '연결성-자율성' 축에서 거리가 있어요"
  },
  "fourHorsemen": {
    // 내부 점수, UI 노출 정책은 ratio-calculation.md
    "criticism":     { "detected": false, "intensity": null },
    "defensiveness": { "detected": true,  "intensity": "mild" },
    "contempt":      { "detected": false, "intensity": null },
    "stonewalling":  { "detected": true,  "intensity": "moderate" }
  },
  "nvcScripts": {
    "aToB": { "observation": "...", "feeling": "...", "need": "...", "request": "..." },
    "bToA": { ... }
  },
  "repairSuggestions": [
    "우리 서로 다른 게 문제가 아니라는 걸 인정하자",
    "아침과 저녁, 하루 두 번 '안부 시간'을 정해볼까?"
  ],
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
