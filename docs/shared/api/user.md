# User API — 사용자 프로필 · 비밀번호 · 탈퇴

> 로그인한 사용자 자신의 프로필 조회·수정, 비밀번호 변경, 탈퇴를 담당하는 API.
> 모든 엔드포인트 JWT 필수.

## Source of truth

| 항목 | 위치 |
|---|---|
| 컨트롤러 | `backend/src/main/java/com/againspring/api/UserController.java` |
| 요청 DTO | `UpdateUserRequest`, `ChangePasswordRequest`, `DeleteAccountRequest` |
| 응답 DTO | `UserResponse` |
| 도메인 | `backend/src/main/java/com/againspring/domain/User.java` |
| DB 테이블 | `users` (V1 + V11 MBTI + V17 동의 + V24 튜토리얼) |

## 엔드포인트

| Method | Path | Auth | 요청 | 응답 | 상태코드 |
|---|---|---|---|---|---|
| `GET` | `/api/users/me` | JWT 필수 | — | `UserResponse` | 200 / 401 / 404 |
| `PATCH` | `/api/users/me` | JWT 필수 | `UpdateUserRequest` | `UserResponse` | 200 |
| `PATCH` | `/api/users/me/password` | JWT 필수 | `ChangePasswordRequest` | `UserResponse` | 200 / 401 |
| `DELETE` | `/api/users/me` | JWT 필수 | `DeleteAccountRequest?` | — | 204 / 401 |

### UserResponse 형식 (주요 필드)

```json
{
  "id": "usr_xxxxx",
  "email": "user@example.com",
  "nickname": "홍길동",
  "profileImageUrl": "https://...",
  "isGuest": false,
  "roles": ["USER"],
  "communicationStyle": "wave",
  "mbti": "ENFJ",
  "onboardingCompleted": true,
  "tutorialCompletedAt": "2026-05-01T10:00:00Z",
  "createdAt": "2026-04-01T09:00:00Z"
}
```

### UpdateUserRequest 형식

```json
{
  "nickname": "새로운닉네임",
  "profileImageUrl": "https://..."
}
```

### ChangePasswordRequest 형식

```json
{
  "currentPassword": "oldpassword123",
  "newPassword": "newpassword456"
}
```

## 엔드포인트 상세

### GET /api/users/me — 프로필 조회

로그인한 사용자의 프로필 정보 조회.

**요청**: 없음 (JWT 헤더만)

**응답 (200)**:
```json
{
  "id": "usr_abc123",
  "email": "user@example.com",
  "nickname": "김철수",
  "profileImageUrl": "https://cdn.example.com/profile.jpg",
  "isGuest": false,
  "roles": ["USER"],
  "communicationStyle": "mountain",
  "mbti": "ISTJ",
  "onboardingCompleted": true,
  "tutorialCompletedAt": "2026-05-15T14:30:00Z",
  "createdAt": "2026-04-01T09:00:00Z"
}
```

**에러**:
- `401 UNAUTHORIZED` — 토큰 없음 또는 만료
- `404 NOT_FOUND` — 사용자 없음 (탈퇴했거나 삭제됨)

---

### PATCH /api/users/me — 프로필 수정

닉네임, 프로필 이미지 등을 수정.

**요청**:
```json
{
  "nickname": "새별",
  "profileImageUrl": "https://cdn.example.com/new-profile.jpg"
}
```

**응답 (200)**: 수정된 `UserResponse`

**에러**:
- `400 VALIDATION_ERROR` — nickname 빈 값 또는 닉네임 중복
- `401 UNAUTHORIZED` — 토큰 없음

---

### PATCH /api/users/me/password — 비밀번호 변경

**요청**:
```json
{
  "currentPassword": "old_password_123",
  "newPassword": "new_password_456"
}
```

**응답 (200)**: 수정된 `UserResponse`

**에러**:
- `400 VALIDATION_ERROR` — 새 비밀번호가 정책 미충족 (8자 이상, 영문+숫자+특수문자)
- `401 UNAUTHORIZED` — 현재 비밀번호 불일치

---

### DELETE /api/users/me — 탈퇴 (PII 익명화)

사용자 계정 탈퇴. 개인정보 즉시 삭제, 세션 원문 삭제, JWT 폐기.

**요청**:
```json
{
  "password": "confirm_password"  // nullable (게스트/OAuth 계정은 생략 가능)
}
```

**동작**:
1. 이메일 계정: 비밀번호 검증 필수
2. 게스트/OAuth: 비밀번호 검증 스킵
3. `users.deleted_at = now()` (소프트 삭제)
4. `users.email`, `users.password_hash` → NULL 또는 익명화
5. 해당 사용자의 게시글/댓글 작성자 닉네임 → "탈퇴한 사용자" 마스킹
6. 발급된 JWT → `revoked_tokens` 추가 (로그아웃 처리)

**응답 (204)**: No Content

**에러**:
- `401 UNAUTHORIZED` — 이메일 계정이고 비밀번호 불일치

---

## 변경 시 절차

- 필드 추가 시: `User` 엔티티 + Flyway 마이그레이션 + `UserResponse` DTO + 본 문서 동시 수정
- 비밀번호 정책 변경: FE `lib/constants/passwordPolicy.ts` + BE `AuthService` + 본 문서 동시 갱신
