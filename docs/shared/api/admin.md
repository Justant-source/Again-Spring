# Admin API — 관리자 전용 API

> 대시보드 통계·사용자 관리·피드백 관리·시스템 상태 모니터링·프롬프트 핫리로드·테스트 데이터 조작·세션 컨텍스트 디버그를 담당하는 API.
> **모든 엔드포인트는 ADMIN 권한 필요** (Spring Security 경로 기반 제한 + 일부 `@PreAuthorize`).

## Source of truth

| 항목 | 위치 |
|---|---|
| Dashboard 컨트롤러 | `backend/src/main/java/com/againspring/api/admin/AdminDashboardController.java` |
| User 컨트롤러 | `backend/src/main/java/com/againspring/api/admin/AdminUserController.java` |
| Health 컨트롤러 | `backend/src/main/java/com/againspring/api/admin/AdminHealthController.java` |
| Feedback 컨트롤러 | `backend/src/main/java/com/againspring/api/AdminFeedbackController.java` |
| Prompts 컨트롤러 | `backend/src/main/java/com/againspring/api/AdminPromptsController.java` |
| Test 컨트롤러 | `backend/src/main/java/com/againspring/api/AdminTestController.java` |
| Debug 컨트롤러 | `backend/src/main/java/com/againspring/api/SessionContextDebugController.java` |
| 관리자 가이드 | `docs/shared/admin-dashboard.md` |

## 환경별 활성화 규칙

```mermaid
flowchart LR
    REQ[요청] --> SEC{Spring Security<br/>admin 경로 인증?}
    SEC -->|미인증| 401[401 Unauthorized]
    SEC -->|비ADMIN| 403[403 Forbidden]
    SEC -->|ADMIN| GATE{컨트롤러 게이팅}

    GATE -->|AdminDashboard/User/Health/Feedback| ALWAYS[항상 활성]
    GATE -->|AdminPrompts/SessionContextDebug| PROP{"app.admin.enabled=true?"}
    PROP -->|false| 404["404 Not Found<br/>빈 등록 안 됨"]
    PROP -->|true| ACTIVE[활성]
    GATE -->|AdminTest| PROFILE{"@Profile(dev)?"}

    PROFILE -->|prod| 404
    PROFILE -->|dev| ACTIVE
```

## Dashboard API — PMF 통계 · 리텐션 · 위기 모니터링

**Base path:** `/api/admin/dashboard` — 인증: JWT + ADMIN

| Method | Path | 설명 | 응답 |
|---|---|---|---|
| `GET` | `/summary` | PMF 핵심 지표 (DAU·세션 수·완료율·평균 턴) | `Map<String, Object>` |
| `GET` | `/daily-stats` | 최근 30일 일별 통계 | `List<Map<String, Object>>` |
| `GET` | `/retention` | 최근 14일 코호트별 리텐션 | `List<Map<String, Object>>` |
| `GET` | `/crisis-recent?limit=20` | 위기 감지된 최근 메시지 | `List<CrisisMessageResponse>` |
| `GET` | `/llm-failure-rate?days=7` | LLM 호출 실패율 (일별) | `List<Map<String, Object>>` |

```json
// GET /summary 응답 예시
{
  "todayTotalSessions": 42,
  "todayCompletedSessions": 18,
  "finalizeRate": 42.9,
  "avgTurnsToday": 7.3,
  "todayNewUsers": 12,
  "todayGuestSessions": 8,
  "totalFeedbacks": 156
}
```

## User API — 사용자 조회 · 삭제 · 역할 관리

**Base path:** `/api/admin/users` — 인증: JWT + ADMIN

| Method | Path | 설명 | 요청 | 응답 |
|---|---|---|---|---|
| `GET` | `/search?q=` | 닉네임·이메일 검색 | — | `List<User>` |
| `GET` | `?page=0&size=20&includeGuest=false` | 전체 사용자 페이지네이션 | — | `Page<User>` |
| `GET` | `/{id}` | 사용자 상세 조회 | — | `AdminUserDetailResponse` |
| `DELETE` | `/{id}/data` | 사용자 데이터 익명화 예약 | — | `{ status, userId }` |
| `PATCH` | `/{id}/roles` | 역할 변경 (USER·TESTER 한정) | `{ roles: [...] }` | `{ userId, roles }` |

**역할 변경 규칙:**
- 허용 역할: `USER`, `TESTER` — `ADMIN` 역할은 이 API로 변경 불가 (AdminRoleAssigner 전용)
- 잘못된 역할 지정 → 400 `INVALID_ROLE`
- 사용자 없음 → 404 `USER_NOT_FOUND`

```json
// PATCH /{id}/roles 요청
{ "roles": ["USER", "TESTER"] }

// 응답
{ "userId": "usr_xxxxx", "roles": ["USER", "TESTER"] }
```

## Health API — 시스템 상태 모니터링

**Base path:** `/api/admin/health` — 인증: JWT + ADMIN

| Method | Path | 설명 | 응답 |
|---|---|---|---|
| `GET` | `/system` | DB·LLM·디스크 상태 점검 | `SystemHealthResponse` |

```json
// GET /system 응답 예시
{
  "status": "UP",
  "database": { "status": "UP", "responseMs": 5 },
  "llmBridge": { "status": "UP", "lastSuccessAt": "2026-05-16T10:00:00Z" },
  "timestamp": "2026-05-16T10:01:00Z"
}
```

## Feedback API — 피드백 목록 · 상태 관리

**Base path:** `/api/admin/feedbacks` — 인증: JWT + ADMIN

| Method | Path | 설명 | 요청 | 응답 |
|---|---|---|---|---|
| `GET` | `?category=&status=&page=0&size=20` | 피드백 목록 (필터·페이지네이션) | — | `Page<Feedback>` |
| `PATCH` | `/{id}` | 처리 상태·관리자 메모 업데이트 | `UpdateFeedbackStatusRequest` | `Feedback` |

## Prompts API — LLM 프롬프트 핫리로드

**Base path:** `/api/admin/prompts` — 인증: JWT + ADMIN + `@PreAuthorize("hasRole('ADMIN')")`
**활성 조건:** `app.admin.enabled=true`

| Method | Path | 설명 | 응답 |
|---|---|---|---|
| `POST` | `/reload` | 디스크에서 LLM 프롬프트 전체 리로드 | `{ status, message }` |

> 프롬프트 파일 변경 후 재배포 없이 즉시 적용하려면 이 API 호출.
> `docs/shared/prompts/` 경로의 `.md` 파일을 모두 재로드.

## AI User API — 생성 정책 · 수동 트리거 프록시

**Base path:** `/api/admin/ai-user` — 인증: JWT + ADMIN

| Method | Path | 설명 | 응답 |
|---|---|---|---|
| `GET` | `/generation-config` | AI 유저 생성 목표량/백엔드/토큰 추정 조회 | `ConfigResponse` |
| `PUT` | `/generation-config` | 생성 목표량/백엔드/토큰 추정 설정 저장 | `ConfigResponse` |
| `GET` | `/generation-status` | 오늘 KST 기준 생성 진행 현황 | `GenerationStatusResponse` |
| `POST` | `/generate-posts?count=2&voice=THEQOO` | backend admin 경유로 orchestrator `generate-posts` 호출 | `{ attempted, personaIds, message }` |
| `POST` | `/reset-counter` | backend admin 경유로 orchestrator `reset-counter` 호출 | `{ status, prev, now }` |
| `POST` | `/backfill-comment-likes?days=30&personasPerPost=8` | backend admin 경유로 orchestrator 백필 호출 | `{ queued, posts, personasPerPost, message }` |
| `POST` | `/kill` | POST/COMMENT/REPLY backend를 모두 `OFF`로 전환 | `{ status, message, killedAt }` |

메모:
- `/admin/trigger/*` direct 경로는 외부 dev shell에서 일관되게 쓰기 어렵다.
- 수동 운영/진단은 `/api/admin/ai-user/*` 프록시 경로를 우선 사용한다.

## Social Publishing API

소셜 게시 API는 ASM 서비스로 이전됨. Again-Spring-Marketing 프로젝트 문서 참조.

## 전체 Admin 엔드포인트 수

| 컨트롤러 | 엔드포인트 수 | 활성 조건 |
|---|---|---|
| AdminDashboardController | 5 | 항상 |
| AdminUserController | 5 | 항상 |
| AdminHealthController | 1 | 항상 |
| AdminFeedbackController | 2 | 항상 |
| AdminPromptsController | 1 | `app.admin.enabled=true` |
| AdminTestController | 2 | `@Profile("dev")` |
| SessionContextDebugController | 1 | `app.admin.enabled=true` |
| SocialPublishController | 7 | `app.features.marketing.enabled=true` (dev) |
| AdminAiUserController | 7 | 항상 |
| **합계** | **31** | |

## 변경 시 절차

- 신규 admin 엔드포인트 추가: 이 문서 + `docs/shared/admin-dashboard.md` 동시 갱신
- TESTER role 지정: `PATCH /api/admin/users/{id}/roles { "roles": ["USER", "TESTER"] }`
- 역할 체계 변경: `docs/shared/policies/user-permissions.md` 권위본 + `AdminRoleAssigner.java`
