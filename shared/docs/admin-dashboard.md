# 관리자 대시보드 (Admin Dashboard) — 기능·운영 가이드

> 권위본 정책: [`policies/user-permissions.md`](./policies/user-permissions.md) — ADMIN 등급 권한 정의
>
> 본 문서는 운영자가 어떤 기능을 어떻게 사용하는지, 그리고 개발자가 어디를 수정해야 하는지 설명한다.

---

## 1. 개요

다시봄의 관리자 대시보드는 **단일 페이지(SPA-style) 운영 콘솔**이다. 운영팀은 한 화면에서:

1. 실시간 KPI 모니터링 (오늘 세션·신규 가입·완료율 등)
2. 사용자 의견(피드백) 수신·처리·메모 관리
3. 모든 가입자 검색·상세 조회·데이터 익명화
4. 위기 키워드(폭력·자해 등) 발생 메시지 메타데이터 실시간 감지

를 수행한다.

페이지 위치: **`/admin`** (FE 경로) — `https://dev.againspring.net/admin`, prod에서는 `https://againspring.net/admin`

---

## 2. 접근 권한

### 2.1 ADMIN 역할 부여

ADMIN 역할은 **이메일 화이트리스트 자동 부여** 방식으로 관리된다.

- **환경변수**: `ADMIN_EMAILS` (콤마 구분 다중 지원)
  - dev 기본값: `againspring2026@gmail.com`
  - 운영 변경 위치: `env/.env.dev`, `env/.env.prod`, `backend/src/main/resources/application.yml`
- **자동 적용 시점**: 회원가입 / 이메일 로그인 / OAuth 로그인 — 매 인증 흐름마다 멱등적(idempotent) 부여
- **구현체**: `backend/src/main/java/com/againspring/service/AdminRoleAssigner.java`
  - `ensureAdminIfWhitelisted(user)` — 이메일이 화이트리스트면 `roles`에 `ADMIN` 추가 후 저장
  - 이미 ADMIN이면 no-op
  - 게스트(이메일 없음)는 항상 no-op

### 2.2 이중 권한 가드

| 계층 | 위치 | 동작 |
|---|---|---|
| **백엔드** | `backend/.../security/SecurityConfig.java` | `requestMatchers("/api/admin/**").hasRole("ADMIN")` — 비ADMIN은 401/403 |
| **프론트엔드** | `frontend/app/(admin)/admin/page.tsx` (useEffect) | `user.isGuest \|\| !user.roles?.includes('ADMIN')` 시 `/`로 즉시 리다이렉트 |
| **API 인터셉터** | `frontend/lib/api/client.ts` | 401/403 응답 시 토큰 정리 후 로그인 페이지로 이동 |

### 2.3 ADMIN 사용자의 일반 화면 격리

ADMIN으로 로그인하면 **랜딩 페이지(`/`) 진입 시 자동으로 `/admin`으로 replace** 되어, "마음 정리하기 / 10문항 등록 / 게스트 모드" 등 일반 사용자 UI는 노출되지 않는다.

- 구현 위치: `frontend/app/page.tsx` (useEffect)
- 깜빡임 방지: ADMIN 판별 직후 `return null`로 일반 콘텐츠 렌더 차단

---

## 3. 화면 구성

`/admin` 페이지는 sticky 헤더 + 5개 카드 섹션으로 구성된다 (max-width 1100px, 모바일 한 컬럼 fallback).

### 3.1 헤더

| 좌측 | 가운데 | 우측 |
|---|---|---|
| `← 다시봄 메인` 버튼 (`/`로 이동) | "관리자 대시보드" 타이틀 | `↻ 새로고침` 버튼 (모든 섹션 데이터 + 사용자 목록 일괄 갱신) |

- 페이지 진입 시 **BetaBanner / LegalFooter 자동 숨김** — admin 페이지 전체 화면 활용
  - `frontend/components/shared/BetaBanner.tsx`, `LegalFooter.tsx`에서 `pathname.startsWith('/admin')` 분기

### 3.2 오늘 요약 (Stat Cards 8개)

KST 자정 기준 당일 KPI를 카드 형태로 표시. 그리드는 `auto-fill minmax(160px, 1fr)`로 화면 크기에 맞춰 재배열.

| 키 | 표시 라벨 | 데이터 출처 |
|---|---|---|
| `todayTotalSessions` | 오늘 전체 세션 | `Session.created_at` 카운트 |
| `todayCompletedSessions` | 오늘 완료 세션 | `Session.status = COMPLETED` 카운트 |
| `todayGuestSessions` | 오늘 게스트 세션 | `User.isGuest = true` 생성자 카운트 |
| `todayMemberSessions` | 오늘 회원 세션 | total - guest |
| `todayNewUsers` | 오늘 신규 가입 | `User.createdAt` + `isGuest=false` |
| `avgTurnsToday` | 평균 턴 수 | `userAMessageCount + userBMessageCount` 평균 (소수 1자리) |
| `finalizeRate` | 완료율 | completed / total (백분율 표시) |
| `totalFeedbacks` | 총 의견 수 | `Feedback` 전체 |

- API: `GET /api/admin/dashboard/summary`
- 서비스: `backend/.../service/admin/PmfStatsService.java#getDashboardSummary()`

### 3.3 추세 차트 (Recharts)

두 차트를 가로 그리드(`minmax(360px, 1fr)`)로 배치.

- **일별 세션 (최근 30일)** — LineChart
  - 회원 세션(잉크색 #1A1A2E) vs 게스트 세션(회색 #888)
  - API: `GET /api/admin/dashboard/daily-stats`
  - 데이터 출처: `DailyStats` 테이블 (배치로 매일 적재)

- **DAU (최근 14일)** — BarChart
  - DAU(검정) vs 신규(연회색)
  - API: `GET /api/admin/dashboard/retention`
  - 서비스: `RetentionCohortService.getLast14DaysRetention()`

### 3.4 위기 모니터링 (실시간 폴링)

위기 키워드(폭력·자해·아동학대 등 — `crisisLevel ≥ 1`)를 포함한 메시지의 **메타데이터**를 최근 N건(기본 20건) 표시.

**🚨 안전 정책 — 본문 절대 노출 금지**: `Message.content` 필드는 응답·UI 어디에도 포함하지 않는다. 운영자는 메타데이터로 발생 패턴만 인지하고, 실제 개입은 위기 핫라인(1366·1393·112)으로 안내된 사용자 본인이 진행한다.

| 컬럼 | 설명 |
|---|---|
| 시각 (KST) | `Message.createdAt` 로컬 변환 |
| Level | 1 (단일 등급), 빨간 배지 |
| 세션 | `sessionId` 앞 12자만 |
| 발신자 | USER_A / USER_B / MEDIATOR_TO_A / MEDIATOR_TO_B |
| 글자수 | `charCount` |

- 헤더 우측에 빨간 배지로 건수 표시 (예: `5건`)
- 빈 목록: "최근 위기 트리거 없음 ✓"
- **자동 갱신**: 30초 주기 폴링 (`CRISIS_POLL_MS = 30_000`)
- API: `GET /api/admin/dashboard/crisis-recent?limit=20`
- 서비스: `backend/.../service/admin/CrisisMonitoringService.java`
- DTO: `CrisisMessageResponse` (content 필드 포함하지 않음, 컴파일 시점에서 차단)
- 리포지토리: `MessageRepository.findRecentCrisisMessages(Pageable)`

### 3.5 의견함 (피드백 관리)

**상태 필터 칩** (전체 / 대기 / 검토 / 해결) → 클릭 시 BE 재조회.

**테이블 컬럼**: ID / 카테고리(색상 배지) / 내용(말줄임) / 상태 / 일시 / `상세` 버튼.

**카테고리 색상 배지**:
| 카테고리 | 라벨 | 배경 / 글자색 |
|---|---|---|
| `ui_bug` | UI 버그 | 연빨강 / 진빨강 |
| `feature` | 기능 제안 | 연파랑 / 진파랑 |
| `content` | 내용/카피 | 연초록 / 진초록 |
| `crisis` | 위기 | 검정 / 흰색 |
| `praise` | 칭찬 | 연노랑 / 갈색 |
| `other` | 기타 | 회색 / 진회색 |

**상세 모달** (`components/admin/FeedbackDetailModal.tsx`):
- 헤더: 카테고리 배지 + `#ID` + 우상단 `×` 닫기
- 본문: 사용자 ID / 작성 일시 / 의견 전체 본문 (`white-space: pre-wrap`)
- 상태 라디오: 대기 → 검토 → 해결 (3-state, 칩 형태)
- 관리자 메모 textarea (4행, 처리 내용·후속 조치 기록)
- 저장 버튼: `PATCH /api/admin/feedbacks/{id}` 호출 → 부모 목록 갱신 + 모달 닫기

**메일 알림 연동**: 새 의견 제출 시 `FeedbackEmailNotifier`(`@Async`)가 `app.mail.support-email`(기본 `againspring2026@gmail.com`)로 즉시 메일 발송. 본문에는 ID·카테고리·사용자ID·KST 시각·전체 내용·관리자 페이지 링크 포함. `crisis` 카테고리는 제목에 `[위기]` 표시.

API:
- 목록: `GET /api/admin/feedbacks?status=&category=&page=`
- 상태 변경: `PATCH /api/admin/feedbacks/{id}` (body: `{ status, adminNote }`)

### 3.6 사용자 관리 (전체 목록 + 검색 + 상세)

**진입 시 자동 로드**: 회원 목록 첫 페이지(20건, 최신 가입순). 헤더 우측에 `총 N명` 배지.

**상단 컨트롤**:
- 검색창 + `검색` 버튼 (닉네임 또는 이메일 contains-ignore-case)
- 검색 모드일 때 `전체 목록` 복귀 버튼 표시
- `게스트 포함` 체크박스 — 게스트 사용자도 목록에 포함

**테이블 컬럼**: 닉네임(ADMIN은 검정 배지) / 이메일 / 등급(게스트/이메일/google) / 가입일 / ID(monospace).

**행 클릭 → 상세 모달** (`components/admin/UserDetailModal.tsx`):

4개 섹션:
- **기본 정보**: ID(monospace) / 닉네임 / 이메일 / 등급 / 역할 / 가입 경로 / 가입일 / 탈퇴일(있으면 빨강)
- **프로필**: MBTI / 통신 스타일 / 온보딩 완료 시각
- **동의 상태**: 이용약관 / 개인정보처리방침 / 전문상담 비대체 / 마케팅 수신 (각각 동의 시각 또는 "미동의")
- **활동 통계**: 총 세션 / 완료 세션 / 의견 제출 / 마지막 세션

하단 **위험 작업** 영역 (탈퇴 사용자엔 표시 안 함):
- `데이터 익명화` 빨간 버튼 → `confirm()` 다이얼로그 ("되돌릴 수 없음" 명시) → `DELETE /api/admin/users/{id}/data`

**페이지네이션** (검색 모드 아닐 때만): `« 처음 / ‹ 이전 / 현재/총 / 다음 › / 마지막 »`

API:
- 전체 목록: `GET /api/admin/users?page=0&size=20&includeGuest=false` (Spring Data `Page<User>` 반환)
- 검색: `GET /api/admin/users/search?q=`
- 상세: `GET /api/admin/users/{id}` → `AdminUserDetailResponse` (User 기본 필드 + `totalSessions` / `completedSessions` / `feedbackCount` / `lastSessionAt`)
- 익명화: `DELETE /api/admin/users/{id}/data` → `UserDeletionService.scheduleAnonymization()`

서비스: `backend/.../service/admin/AdminUserDetailService.java`

---

## 4. 백엔드 API 엔드포인트 전체 목록

모두 `/api/admin/**`로 SecurityConfig에서 `hasRole("ADMIN")` 가드.

| Method | Path | 컨트롤러 | 응답 |
|---|---|---|---|
| GET | `/api/admin/dashboard/summary` | AdminDashboardController | `Map<String,Object>` (8개 KPI) |
| GET | `/api/admin/dashboard/daily-stats` | AdminDashboardController | `List<Map>` (최근 30일) |
| GET | `/api/admin/dashboard/retention` | AdminDashboardController | `List<Map>` (최근 14일) |
| GET | `/api/admin/dashboard/crisis-recent` | AdminDashboardController | `List<CrisisMessageResponse>` (메타데이터만) |
| GET | `/api/admin/users` | AdminUserController | `Page<User>` |
| GET | `/api/admin/users/search?q=` | AdminUserController | `List<User>` |
| GET | `/api/admin/users/{id}` | AdminUserController | `AdminUserDetailResponse` |
| DELETE | `/api/admin/users/{id}/data` | AdminUserController | `{ status, userId }` |
| GET | `/api/admin/feedbacks` | AdminFeedbackController | `Page<Feedback>` |
| PATCH | `/api/admin/feedbacks/{id}` | AdminFeedbackController | `Feedback` |
| POST | `/api/admin/test/reset` | AdminTestController | dev 전용 — 테스트 데이터 초기화 |
| POST | `/api/admin/test/sessions/{id}/terminate` | AdminTestController | dev 전용 — 세션 강제 종료 |
| POST | `/api/admin/prompts/reload` | AdminPromptsController | dev 전용 — 프롬프트 핫리로드 |
| GET | `/api/admin/sessions/{id}/context` | SessionContextDebugController | dev 전용 — 세션 컨텍스트 디버깅 |

---

## 5. 데이터 모델 의존성

| 엔티티 | 사용 위치 |
|---|---|
| `User` | 사용자 관리, 상세 통계, 검색 |
| `Session` | 오늘 요약(카운팅), 일별 추세, 사용자 상세(관여 세션) |
| `Message` | 위기 모니터링(메타데이터만), 평균 턴 |
| `Feedback` | 의견함 목록·상태 변경, 사용자 상세(피드백 카운트) |
| `DailyStats` | 일별 세션 추이, DAU 차트 |
| `RevokedToken` | (간접) 로그아웃 후 admin 토큰 무효화 시 |

---

## 6. 운영 가이드

### 6.1 새 ADMIN 추가

1. `env/.env.dev` 또는 `env/.env.prod` 열기
2. `ADMIN_EMAILS=email1@x.com,email2@y.com,newadmin@z.com` 형식으로 추가
3. 백엔드 컨테이너 재시작
4. 해당 이메일로 회원가입 또는 (이미 있으면) 로그인 → 자동 ADMIN 부여

DB 직접 부여(즉시 적용):
```sql
UPDATE users SET roles = '["USER", "ADMIN"]' WHERE email = 'newadmin@z.com';
```

### 6.2 ADMIN 회수

1. `ADMIN_EMAILS`에서 제거
2. DB에서 해당 사용자의 `roles`에서 `ADMIN` 제거:
   ```sql
   UPDATE users SET roles = '["USER"]' WHERE email = 'former@x.com';
   ```
3. 해당 사용자가 로그인된 상태라면 강제 로그아웃(토큰 revoke) 권장

### 6.3 데이터 익명화

- UI 경로: 사용자 관리 → 검색 또는 목록에서 행 클릭 → 상세 모달 하단 빨간 버튼
- 동작: `UserDeletionService.scheduleAnonymization(userId)` 호출 → 비동기 처리
- **되돌릴 수 없음**, confirm 다이얼로그 1회 후 진행

### 6.4 의견함 처리 워크플로우

권장 절차:
1. 메일(`againspring2026@gmail.com`)로 새 의견 도착 알림 수신
2. 관리자 대시보드 → 의견함 → `상세` 클릭
3. 본문 검토 → 상태를 `검토`로 변경 + 관리자 메모 작성 (예: "FE 팀 전달", "재현 필요")
4. 처리 완료 후 상태 `해결`로 변경 + 메모에 처리 결과 기록

### 6.5 위기 모니터링 운영 원칙

- 위기 섹션은 **참조 정보**일 뿐, 직접 사용자에게 연락하지 않는다
- 사용자에게는 이미 FE에서 자동으로 핫라인 안내 모달(`CrisisModal`)이 표시되고, 입력 필드가 비워진다
- 운영팀은 빈도 패턴(특정 시간대·특정 세션 반복 등)을 관찰해 정책 개선에 활용

---

## 7. 관련 코드 위치 요약

### 백엔드

```
backend/src/main/java/com/againspring/
├── api/admin/
│   ├── AdminDashboardController.java      # /api/admin/dashboard/**
│   └── AdminUserController.java           # /api/admin/users/**
├── api/
│   ├── AdminFeedbackController.java       # /api/admin/feedbacks
│   ├── AdminPromptsController.java        # /api/admin/prompts/reload
│   ├── AdminTestController.java           # /api/admin/test/**
│   └── SessionContextDebugController.java # /api/admin/sessions/{id}/context
├── api/dto/response/
│   ├── AdminUserDetailResponse.java
│   └── CrisisMessageResponse.java         # content 필드 의도적으로 없음
├── service/admin/
│   ├── PmfStatsService.java               # 오늘 요약, 일별 통계
│   ├── RetentionCohortService.java        # DAU/리텐션
│   ├── AdminUserDetailService.java        # 사용자 상세 집계
│   └── CrisisMonitoringService.java       # 위기 메타데이터
├── service/
│   ├── AdminRoleAssigner.java             # 화이트리스트 ADMIN 부여
│   └── notify/FeedbackEmailNotifier.java  # 새 의견 메일 알림
└── security/SecurityConfig.java           # /api/admin/** hasRole(ADMIN)
```

### 프론트엔드

```
frontend/
├── app/(admin)/admin/page.tsx             # 통합 대시보드 페이지
├── app/page.tsx                           # ADMIN 자동 /admin 리다이렉트
├── components/admin/
│   ├── FeedbackDetailModal.tsx
│   └── UserDetailModal.tsx
├── components/shared/
│   ├── BetaBanner.tsx                     # /admin 경로에서 숨김
│   └── LegalFooter.tsx                    # /admin 경로에서 숨김
├── lib/api/admin.ts                       # admin API 호출 함수 일체
├── lib/types/user.ts                      # User.roles 필드
└── lib/store/userStore.ts
```

### 정책 / 설정

```
shared/docs/policies/user-permissions.json  # ADMIN 등급 정책 (권위본)
backend/src/main/resources/application.yml  # app.admin-emails
env/.env.dev / .env.prod                    # ADMIN_EMAILS 환경변수
```

---

## 8. 향후 확장 아이디어

운영 경험 누적 후 검토:

- 사용자 역할 변경 UI (수동 ADMIN 부여/회수)
- 세션 상세 분석 패널 (관여 사용자, 4호스맨 점수, NVC 스크립트 효과)
- Admin 감사 로그 (누가 언제 무엇을 변경했는지)
- 통계 필터 (날짜 범위, 갈등유형, MBTI별 분포)
- 위기 메시지에 대한 행 클릭 → 세션 컨텍스트 디버깅 모달 (이미 BE에 `/api/admin/sessions/{id}/context` 존재, FE 미연결)
- 의견함 일괄 상태 변경 (체크박스 + 일괄 액션)
- CSV 내보내기 (의견함, 사용자 목록)

---

**최근 업데이트**: 2026-05-10
**관련 정책 문서**: [`policies/user-permissions.md`](./policies/user-permissions.md)
