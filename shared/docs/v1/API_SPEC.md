# 다시봄 API 명세

**버전**: v1.0
**Base URL**: `http://localhost:8080/api` (dev), `https://api.againspring.app/api` (prod)
**인증**: Bearer JWT (`Authorization: Bearer {token}`)

---

## 📋 엔드포인트 목록

| 카테고리 | Method | Path | 설명 | 인증 |
|---|---|---|---|---|
| Auth | POST | `/auth/signup` | 회원가입 | - |
| Auth | POST | `/auth/login` | 로그인 | - |
| Auth | POST | `/auth/guest` | 게스트 토큰 발급 | - |
| Auth | POST | `/auth/refresh` | 토큰 갱신 | ✓ |
| User | GET | `/users/me` | 내 정보 | ✓ |
| User | PATCH | `/users/me` | 내 정보 수정 | ✓ |
| User | DELETE | `/users/me` | 탈퇴 | ✓ |
| User | POST | `/users/me/onboarding` | 온보딩 결과 저장 | ✓ |
| Session | POST | `/sessions` | 세션 생성 | ✓ |
| Session | GET | `/sessions/me` | 내 세션 목록 | ✓ |
| Session | GET | `/sessions/{id}` | 세션 상세 | ✓ |
| Session | POST | `/sessions/join/{token}` | B 참여 | - |
| Session | POST | `/sessions/{id}/solo` | Solo 모드 전환 | ✓ |
| Session | DELETE | `/sessions/{id}` | 세션 삭제 | ✓ |
| Mediation | POST | `/sessions/{id}/turns` | 턴 진행 | ✓ |
| Mediation | GET | `/sessions/{id}/turns/current` | 현재 턴 상태 | ✓ |
| Mediation | GET | `/sessions/{id}/stream` | SSE 스트림 (턴 이벤트) | ✓ |
| Report | POST | `/sessions/{id}/report` | 리포트 생성 | ✓ |
| Report | GET | `/reports/{id}` | 리포트 조회 | ✓ |
| Relationship | GET | `/users/me/relationships` | 관계 목록 | ✓ |
| Relationship | GET | `/users/me/relationships/{personId}/history` | 관계별 이력 | ✓ |

---

## 🔐 인증

### 공통 응답 헤더
```
Content-Type: application/json
X-Request-ID: {uuid}
```

### 표준 에러 응답
```json
{
  "error": {
    "code": "SESSION_NOT_FOUND",
    "message": "세션을 찾을 수 없어요",
    "timestamp": "2026-04-24T10:30:00Z",
    "requestId": "req_abc123"
  }
}
```

### 에러 코드 목록

| 코드 | HTTP | 설명 |
|---|---|---|
| `INVALID_INPUT` | 400 | 입력 검증 실패 |
| `UNAUTHORIZED` | 401 | 인증 실패 |
| `FORBIDDEN` | 403 | 권한 없음 |
| `NOT_FOUND` | 404 | 리소스 없음 |
| `SESSION_NOT_FOUND` | 404 | 세션 없음 |
| `INVITE_EXPIRED` | 410 | 초대 링크 만료 |
| `CRISIS_DETECTED` | 422 | 위험 키워드 감지 — 세션 중단 |
| `TURN_MISMATCH` | 409 | 현재 턴이 아님 |
| `LLM_UNAVAILABLE` | 503 | LLM 일시 불가 |
| `INTERNAL_ERROR` | 500 | 서버 오류 |

---

## 🔑 Auth APIs

### `POST /auth/signup`

**Request**
```json
{
  "email": "user@example.com",
  "password": "Password123!",
  "nickname": "달콩"
}
```

**Response 201**
```json
{
  "user": {
    "id": "usr_abc123",
    "email": "user@example.com",
    "nickname": "달콩",
    "isGuest": false,
    "createdAt": "2026-04-24T10:30:00Z"
  },
  "token": {
    "accessToken": "eyJhbGc...",
    "refreshToken": "eyJhbGc...",
    "expiresIn": 86400
  }
}
```

### `POST /auth/login`

**Request**
```json
{
  "email": "user@example.com",
  "password": "Password123!"
}
```

**Response 200**
```json
{
  "user": { /* ... */ },
  "token": { /* ... */ }
}
```

### `POST /auth/guest`

**Request**
```json
{
  "nickname": "게스트"
}
```

**Response 200**
```json
{
  "user": {
    "id": "gst_xyz789",
    "nickname": "게스트",
    "isGuest": true
  },
  "token": {
    "accessToken": "eyJhbGc...",
    "expiresIn": 3600
  }
}
```

### `POST /auth/refresh`

**Request**
```json
{
  "refreshToken": "eyJhbGc..."
}
```

**Response 200**
```json
{
  "accessToken": "eyJhbGc...",
  "expiresIn": 86400
}
```

---

## 👤 User APIs

### `GET /users/me`

**Response 200**
```json
{
  "id": "usr_abc123",
  "email": "user@example.com",
  "nickname": "달콩",
  "communicationStyle": "wave",
  "isGuest": false,
  "onboardingCompleted": true,
  "createdAt": "2026-04-24T10:30:00Z"
}
```

### `PATCH /users/me`

**Request**
```json
{
  "nickname": "새 닉네임"
}
```

### `POST /users/me/onboarding`

**Request**
```json
{
  "answers": [4, 2, 3, 5, 2, 4, 3, 5, 4, 3]
}
```

**Response 200**
```json
{
  "communicationStyle": "wave",
  "styleInfo": {
    "emoji": "🌊",
    "label": "파도형",
    "description": "감정 표현이 풍부하고 즉각적인 스타일",
    "strengths": ["진솔한 감정 표현", "따뜻한 공감 능력"],
    "caution": ["감정 격앙 시 휴식 필요"]
  }
}
```

---

## 💬 Session APIs

### `POST /sessions` — 세션 생성 (A)

**Request**
```json
{
  "relationType": "couple",
  "category": {
    "major": "couple",
    "middle": "connection",
    "minor": "infrequent_contact",
    "customMinor": null
  },
  "description": "3주 동안 연락이 너무 적어서 서운함...",
  "inviteMessage": {
    "tone": "soft",
    "customText": null
  }
}
```

**Response 201**
```json
{
  "id": "ses_abc123",
  "inviteToken": "inv_xyz789",
  "inviteUrl": "https://againspring.app/join/inv_xyz789",
  "inviteMessage": "우리 얘기 좀 정리해보고 싶어서...",
  "status": "waiting_b",
  "currentTurn": 1,
  "createdAt": "2026-04-24T10:30:00Z",
  "expiresAt": "2026-04-25T10:30:00Z"
}
```

### `GET /sessions/me` — 내 세션 목록

**Query Parameters**
- `status`: `waiting_b | in_mediation | completed | all` (default: all)
- `page`: 0
- `size`: 20

**Response 200**
```json
{
  "sessions": [
    {
      "id": "ses_abc123",
      "relationType": "couple",
      "partnerName": "상대방 이름",
      "status": "completed",
      "createdAt": "2026-04-24T10:30:00Z",
      "completedAt": "2026-04-24T11:15:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "total": 47
}
```

### `GET /sessions/{id}` — 세션 상세

**Response 200**
```json
{
  "id": "ses_abc123",
  "relationType": "couple",
  "category": { /* ... */ },
  "status": "in_mediation",
  "currentTurn": 3,
  "currentRole": "A",
  "myRole": "A",
  "partnerNickname": "민수",
  "turns": [
    {
      "turnNumber": 1,
      "role": "A",
      "mediatorMessage": "이야기 시작해주셔서 감사해요...",
      "myTurn": true,
      "completed": true,
      "createdAt": "2026-04-24T10:30:00Z"
    }
    // 주의: 상대방의 content는 내가 같은 턴을 완료하기 전까지 응답에 포함 안 됨
  ],
  "createdAt": "2026-04-24T10:30:00Z"
}
```

### `POST /sessions/join/{token}` — B 참여

**Request**
```json
{
  "nickname": "민수",
  "asGuest": true
}
```

**Response 200**
```json
{
  "session": { /* 세션 정보 */ },
  "token": {
    "accessToken": "eyJhbGc...",
    "expiresIn": 86400
  },
  "mediatorSummary": "A님은 연락 빈도에 대한 어려움을 공유하셨어요. 상세한 내용은 B님 답변 후 함께 공개됩니다."
}
```

### `POST /sessions/{id}/solo` — Solo 모드 전환

**Response 200**
```json
{
  "session": {
    "id": "ses_abc123",
    "status": "solo_mode",
    "currentTurn": 1,
    "totalTurns": 3
  }
}
```

---

## 🎭 Mediation APIs

### `POST /sessions/{id}/turns` — 턴 진행

**Request**
```json
{
  "turnNumber": 1,
  "content": "3주 동안 연락이 너무 적어서 서운했어요...",
  "skip": false
}
```

**Response 200 (성공)**
```json
{
  "turn": {
    "turnNumber": 1,
    "role": "A",
    "completed": true,
    "createdAt": "2026-04-24T10:31:00Z"
  },
  "nextTurn": {
    "turnNumber": 2,
    "role": "B",
    "waitingFor": "partner"
  },
  "mediatorMessage": "말씀해주셔서 감사해요. B님의 답변을 기다리고 있어요."
}
```

**Response 422 (위험 키워드)**
```json
{
  "error": {
    "code": "CRISIS_DETECTED",
    "message": "중요한 안내가 필요한 상황이 감지되었어요",
    "crisisType": "domestic_violence",
    "resources": [
      { "name": "여성긴급전화", "phone": "1366", "available": "24시간" },
      { "name": "정신건강위기상담", "phone": "1577-0199", "available": "24시간" }
    ]
  }
}
```

### `GET /sessions/{id}/turns/current` — 현재 턴 상태

**Response 200**
```json
{
  "currentTurn": 3,
  "currentRole": "A",
  "myRole": "A",
  "isMyTurn": true,
  "mediatorQuestion": "A님, B님 얘기에 대해 궁금한 게 있어요. 다음 두 가지 여쭤볼게요.\n\nQ1. ...\nQ2. ..."
}
```

### `GET /sessions/{id}/stream` — SSE 이벤트 스트림

Server-Sent Events 기반 실시간 상태 업데이트.

**이벤트 타입**:
- `turn_updated`: 상대방이 턴 완료
- `mediator_thinking`: 중재자 응답 생성 중
- `mediator_response`: 중재자 응답 완료
- `session_status_changed`: 세션 상태 변경
- `report_ready`: 리포트 생성 완료

**이벤트 예시**
```
event: turn_updated
data: {"turnNumber":2,"role":"B","completedAt":"2026-04-24T11:00:00Z"}

event: mediator_thinking
data: {"turnNumber":3,"estimatedSeconds":8}

event: mediator_response
data: {"turnNumber":3,"content":"A님께 두 가지..."}
```

---

## 📊 Report APIs

### `POST /sessions/{id}/report` — 리포트 생성 요청

**Response 202 (비동기 생성 시작)**
```json
{
  "reportId": "rep_abc123",
  "status": "generating",
  "estimatedSeconds": 15
}
```

### `GET /reports/{id}` — 리포트 조회

**Response 200**
```json
{
  "id": "rep_abc123",
  "sessionId": "ses_abc123",
  "conflictType": "difference",
  "isSoloMode": false,
  "contributionRatio": {
    "a": 55,
    "b": 45,
    "label": {
      "a": "먼저 다가가면 좋은 쪽",
      "b": "마음 열고 기다려주면 좋은 쪽"
    }
  },
  "needsMap": {
    "axisX": "connection_autonomy",
    "axisXLabel": "연결성-자율성",
    "axisY": "stability_change",
    "axisYLabel": "안정-변화",
    "positionA": { "x": -70, "y": 0 },
    "positionB": { "x": 60, "y": 0 },
    "interpretation": "두 분은 '연결성-자율성' 축에서 거리가 있어요"
  },
  "nvcScripts": {
    "aToB": {
      "observation": "하루에 연락이 1-2번 정도 오고 있어",
      "feeling": "가끔 혼자 남겨진 것 같고 불안해",
      "need": "나한테는 '함께 있다는 느낌'이 중요해",
      "request": "짧게라도 하루 몇 번 안부 나눌 수 있을까?"
    },
    "bToA": {
      "observation": "연락을 자주 나누고 싶다는 얘기를 들었어",
      "feeling": "혼자만의 시간이 부족하면 에너지가 떨어져서 힘들어",
      "need": "나한테는 '충전할 수 있는 혼자 시간'이 필요해",
      "request": "저녁에 한 번 길게 연락하는 걸로 해보면 어떨까?"
    }
  },
  "repairSuggestions": [
    "우리 서로 다른 게 문제가 아니라는 걸 인정하자",
    "아침과 저녁, 하루 두 번 '안부 시간'을 정해볼까?",
    "서로의 리듬을 존중하는 방법을 찾아보자"
  ],
  "createdAt": "2026-04-24T11:15:00Z"
}
```

---

## 🔗 Relationship APIs

### `GET /users/me/relationships` — 관계 목록

**Response 200**
```json
{
  "relationships": [
    {
      "personId": "usr_456",
      "personNickname": "민수",
      "relationType": "couple",
      "sessionCount": 5,
      "averageTemperature": 36.4,
      "lastSessionAt": "2026-04-24T11:15:00Z"
    }
  ]
}
```

### `GET /users/me/relationships/{personId}/history` — 관계별 이력

**Response 200**
```json
{
  "personNickname": "민수",
  "relationType": "couple",
  "sessions": [
    {
      "sessionId": "ses_abc123",
      "conflictType": "difference",
      "createdAt": "2026-04-24T10:30:00Z"
    }
  ]
}
```

---

## 📏 Rate Limits

| 엔드포인트 | 제한 |
|---|---|
| `/auth/signup`, `/auth/login` | 5회 / 분 / IP |
| `/sessions` (생성) | 10회 / 시간 / 사용자 |
| `/sessions/*/turns` | 30회 / 분 / 세션 |
| 기타 | 60회 / 분 / 사용자 |

**초과 시 응답**
```
HTTP 429 Too Many Requests
Retry-After: 60
```

---

## 🧪 테스트 가이드

### cURL 예시

```bash
# 회원가입
curl -X POST http://localhost:8080/api/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"email":"test@test.com","password":"Pass123!","nickname":"테스트"}'

# 세션 생성
curl -X POST http://localhost:8080/api/sessions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"relationType":"couple","category":{"major":"couple","middle":"connection","minor":"infrequent_contact"},"description":"..."}'

# SSE 구독
curl -N -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/sessions/ses_abc/stream
```

---

## 📂 OpenAPI Spec 생성

`shared/schemas/openapi.yaml`에 OpenAPI 3.0 스펙 자동 생성. SpringDoc 사용:

```yaml
# build.gradle.kts에 추가
implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.5.0")
```

접근: `http://localhost:8080/swagger-ui.html`

---

**끝.**
