# API 문서 인덱스 — 다시봄 REST API

> 다시봄 백엔드가 제공하는 REST API 전체 목록입니다.
> 모든 문서는 **컨트롤러 코드 기준**으로 작성됩니다. 코드와 문서가 충돌하면 코드가 옳습니다.

## Source of truth

| 항목 | 위치 |
|---|---|
| 엔드포인트 권위본 | `backend/src/main/java/com/againspring/api/**/*Controller.java` |
| DTO 권위본 | `backend/src/main/java/com/againspring/api/dto/` |
| DB 스키마 | `backend/src/main/resources/db/migration/V*.sql` |
| Swagger UI (dev) | `http://localhost:8080/swagger-ui.html` |
| OpenAPI 스펙 (dev) | `http://localhost:8080/v3/api-docs` |

## 문서 구조

| 파일 | 범위 | 컨트롤러 |
|---|---|---|
| [`rest-spec.md`](rest-spec.md) | 공통 규약·에러코드·전체 엔드포인트 마스터 표·인증 매트릭스 | 전체 |
| [`auth.md`](auth.md) | 인증·소셜 로그인 | `AuthController`, `OAuth2Controller` |
| [`user.md`](user.md) | 사용자 프로필·비밀번호·탈퇴 | `UserController` |
| [`feedback.md`](feedback.md) | 피드백 제출 | `FeedbackController` |
| [`admin.md`](admin.md) | 관리자 전용 API | `AdminDashboardController`, `AdminUserController`, `AdminHealthController`, `AdminFeedbackController`, `AdminPromptsController` + 마케팅 (Story/Simulation/Content/Template/Hashtag/Calendar 등) |
| [`database-schema.md`](database-schema.md) | MariaDB 테이블 스키마 · Flyway 마이그레이션 (V1~V56) | — |

## 공통 규약

- Base URL (dev): `http://localhost:8080`
- Base URL (prod): `https://againspring.net`
- 모든 요청/응답: `Content-Type: application/json`
- 인증: `Authorization: Bearer <JWT>` (공개 엔드포인트 제외)
- 에러 형식: `{ "code": "ERROR_CODE", "message": "메시지" }` — `GlobalExceptionHandler` 표준화
- 상세 에러코드 목록: [rest-spec.md#에러코드](rest-spec.md#에러코드)
