# FE 테스트 전략

프론트엔드의 테스트 계층별 목표, 도구, 실행 방법을 설명합니다.

---

## 테스트 계층

### 1. 린트 검사 (정적 분석)

#### 금지어 검사 (`npm run lint:words`)

```bash
npm run lint:words
```

- **목표**: Level 1/2/3 금지어, 위기 키워드 사전 검출
- **도구**: `scripts/check-forbidden-words.js` (Node.js)
- **대상**: `app/`, `components/`, `lib/`, `mocks/`
- **CI/CD**: PR 마다 실행 필수
- **실패 조건**: Level 1 금지어 1개 이상 감지
- **상세**: [`policies/forbidden-words-lint.md`](policies/forbidden-words-lint.md)

#### 이모지 검사 (`npm run lint:emoji`)

```bash
npm run lint:emoji
```

- **목표**: 하드코딩 이모지 전수 제거
- **대상**: 카피, 컴포넌트 렌더링
- **CI/CD**: PR 마다 실행 필수

### 2. 단위 테스트 (Unit Tests)

#### Vitest 설정

```bash
npm run test          # 모든 유닛 테스트 실행
npm run test:watch   # watch 모드
```

**설정 파일**: `vitest.config.ts`

**권장 테스트 대상**:
- `lib/utils/*.ts` — 유틸 함수 (styleCalculator, cn 등)
- `lib/constants/*.ts` — 상수 구조 검증
- `lib/api/*.ts` — API 클라이언트 인터셉터

#### 예시

```typescript
// lib/utils/__tests__/styleCalculator.test.ts
describe('styleCalculator', () => {
  it('should calculate style from answers', () => {
    const style = styleCalculator([/* answers */])
    expect(style).toBeDefined()
  })
})
```

**커버리지 목표**: 70% 이상 (라이브러리 코드)

### 3. 통합 테스트 (Integration Tests)

#### MSW Mock API 테스트

MSW는 dev 모드에서 자동 활성화됩니다. 실제 페이지 플로우를 테스트할 때 자동으로 mock API 응답을 받습니다.

**수동 테스트 체크리스트**:

```
[ ] 로그인 → 피드 열람 → 게시글 작성 → 배심원 의견 조회
[ ] 투표 (A측/B측)
[ ] 댓글 작성 및 무한스크롤
[ ] 금지어 입력 시 검증
[ ] 위기 키워드 입력 시 모달 표시
[ ] 위기 모달 닫기 (ESC/바깥클릭 차단 확인)
```

### 4. E2E 테스트 (Playwright — 실 BE 대상)

#### 사전 조건 및 실행

```bash
# 1. dev 스택 기동·헬스 (8090) — 검증·e2e 면
curl http://localhost:8090/api/health  # UP 확인

# 2. e2e 실행 (dev 게이트)
cd frontend
E2E_BASE_URL=http://localhost:8090 npm run test:e2e:realbe
```

> **prod 배포 전제**: dev 배포·수동 검증 → e2e-realbe(`:8090`) 전체 통과 → 명시적 prod 지시 → 백업 → prod 배포 (`CLAUDE.md` 절대 규칙 #4).
> **prod에서 e2e·직접 반영 금지.** 기본 대상 = localhost:8090 + `againspring-mariadb-dev` + `.env.dev`.
> `global-setup`이 `test%@again.com` 페르소나를 `INSERT IGNORE`로 보장한다.
> `cleanup-test-db.sh`는 V56에서 삭제된 legacy `messages`/`sessions`/`turns`/`reports`가 없으면 해당 DELETE를 건너뛴다.
> Journey 08은 `@example.com`(RFC 2606)으로만 인증 메일을 보낸다 — SMTP 실패 시에도 코드는 DB에 저장되고 200을 반환한다.
> dev auth rate-limit 기본 `SECURITY_RATE_LIMIT_AUTH_PER_MINUTE=1000` (compose.dev).

**설정 파일**: `playwright.realbe.config.ts`

#### Journey 테스트

위치: `tests/e2e-realbe/journeys/`

| Journey | 파일 | 시나리오 |
|---|---|---|
| 01 | `01-guest-golden-path.spec.ts` | 게스트 진입·피드·상세 (@mobile). 투표/댓글은 04/05에 위임 |
| 02 | `02-member-auth-session.spec.ts` | 이메일 로그인·로그아웃·storageState 재사용 |
| 03 | `03-community-feed-compose.spec.ts` | 피드·정렬·카테고리·작성 폼·게스트/회원 올리기 |
| 04 | `04-voting.spec.ts` | 게스트 투표 지속성·회원 투표·soft-delete 복구 |
| 05 | `05-comments-lifecycle.spec.ts` | 댓글 CRUD·신고 제출·중복 id 회귀 |
| 06 | `06-partner-invite-answer.spec.ts` | 초대·paired·답변 화면·publish-mode/publish-now |
| 07 | `07-profile.spec.ts` | 마이페이지 탭·profile/info (게스트 가드는 09) |
| 08 | `08-email-verification-signup.spec.ts` | send-verification·DB 코드·가입 완주 |
| 09 | `09-permissions-guards.spec.ts` | 라우트 가드·admin API 403 스모크·하단 시트·로그인 정리 |
| 10 | `10-landing.spec.ts` | 방금 올라온 사연·오늘의 사연·CTA (@mobile) |
| 11 | `11-admin-ai-rules.spec.ts` | AI 규칙 UI·전역 CRUD 1경로·content UI·prompts slash-key·voice 회귀 |
| 12 | `12-og-card.spec.ts` | OG 메타·opengraph-image |
| 13 | `13-marketing-jobs.spec.ts` (+ callbacks) | 마케팅 UI·콜백 토큰 (ASM create는 stub 환경만) |
| 14 | `14-marketing-credentials.spec.ts` | 플랫폼 계정 탭·마스킹 (ASM read-only) |
| 15 | `15-admin-ai-generation-status.spec.ts` | AI 생성 관제 패널 (route mock) |
| 16 | `16-admin-dashboard-home.spec.ts` | 대시보드 ActionCenter·KPI·Cmd+K |
| 17 | `17-admin-crawl-freshness.spec.ts` · `17-admin-community-insights.spec.ts` | 크롤 배지·stats 인사이트 UI |
| 18 | `18-visit-tracking.spec.ts` | UTM POST·재방문 dedupe·/admin 제외 |
| 19 | `19-search-panel.spec.ts` | SearchPanel 열기/검색/최근/닫기 |
| 20 | `20-notifications.spec.ts` | 시드 알림·모두 읽음 |
| 21 | `21-password-reset.spec.ts` | forgot UI·토큰 재설정→로그인 |
| 22 | `22-jury-seeded-ui.spec.ts` | SQL 시드 배심원 → 작성자 GET /jury (광장 UI는 JurySection 미연결) |

API 계약(대시보드·크롤·visit validation)은 BE 유닛으로 이관: `AdminDashboardControllerTest`, `AdminCrawlStatusControllerTest`, `PublicVisitControllerTest`.

#### ⚠️ LLM 절대 호출 금지 규칙

**모든 spec은 `@playwright/test` 대신 `support/no-llm-fixture.ts`를 import한다.**

```typescript
import { test, expect } from '../support/no-llm-fixture'
```

가드레일이 자동 차단하는 엔드포인트:
- `POST /api/community/posts/{id}/jury/retry`
- `POST /api/admin/content/corrections/analyze`
- `POST /api/admin/ai-rules/history/*/analyze`, `/analyze-batch`
- `POST /api/admin/marketing/*/(generate|simulation|story)`
- `POST /api/community/posts` — `jurorCount > 0`인 경우

**왜 필요한가**: BE의 `RemoteLlmProvider`가 `@Primary` 무조건 → `application-test.yml`의 `llm.provider:mock`은 실행 중인 BE에 무효. `jurorCount=0` 하드코딩(`app/community/new/page.tsx`)과 이 가드레일이 두 겹으로 보호.

게시글 셋업은 반드시 `support/api.ts`의 `createPost`를 사용한다(항상 `jurorCount:0` 강제).

#### 기능↔e2e 동기화 규칙

| 변경 유형 | e2e 대응 |
|---|---|
| FE/BE 기능 **추가** | `journeys/`에 대응 spec 또는 테스트 케이스 추가 |
| FE/BE 기능 **수정** | 해당 journey spec 갱신 |
| FE/BE 기능 **삭제** | 해당 journey spec 또는 테스트 케이스 제거 |
| `data-testid` 추가/변경 | `support/selectors.ts` 동시 갱신 |

#### storageState 및 DB 관리

- **storageState**: `global-setup.ts`가 test1(ADMIN)/test2(TESTER)/test3(TESTER)/test5(USER) 로그인을 1회 실행해 `.auth/<email>.json`에 저장. 이후 spec은 `test.use({ storageState })` 또는 `tokenFromStorageState(email)`로 재사용.
- **사연 삭제 (필수)**: `createPost` → afterEach가 API DELETE + **SQL 강제 삭제** + 잔존 시 throw.
  ⚠️ prod(`mariadb-prod`)는 호스트 포트 없음. `dev_db_sql.py`(localhost:3309)는 **dev DB**. prod SQL은 docker exec만 — python 폴백 금지(폴백 시 삭제된 척하고 prod에 E2E 글이 남음).
- **DB 정리 (안전망)**: `cleanup-test-db.sh`를 setup/teardown에서 실행. 대상은 `E2E_BASE_URL` 파생 (`:8091`→prod, `:8090`→dev). ambient `DB_CONTAINER`/`MARIADB_*` 무시. teardown 잔존 시 **실패(throw)**.
- **AI-user 고아 정리 (§2b, 2026-08-01)**: posts raw SQL 삭제 시 outbox 미발행 → cleanup이 ai_* 를 posts와 함께 지운다. API `deletePost`는 outbox 정상 발행(1순위).
- **미공개 cleanup**: 기본 `localhost:8091` + `againspring-mariadb-prod`. 공개 URL cleanup 거부.

#### Selector 관리

`tests/e2e-realbe/support/selectors.ts` — `data-testid` 중앙화

**선호 우선순위**: `getByRole` > `getByTestId` > `getByText` (한국어 리터럴 최후 수단)

**중요**: `data-testid` 추가·변경·삭제 시 반드시 `selectors.ts` 동기화. 컴포넌트에 testid가 없으면 `getByRole`을 우선 사용한다.

### 5. 보안 테스트

#### 금지어 및 위기 키워드

```
[ ] Level 1 금지어 입력 시 검증 (배포 전 필수)
[ ] 위기 키워드 ("자살", "폭력" 등) 입력 시 모달 표시
[ ] 모달은 ESC/바깥클릭으로 닫히지 않음 (명시적 버튼만)
```

#### XSS 및 인젝션

- React는 자동으로 JSX 이스케이프 (`{variable}` 사용 시)
- `dangerouslySetInnerHTML` 미사용
- 사용자 입력은 모두 텍스트로 저장 (JSON 인젝션 불가)

#### CSRF 및 토큰

- JWT 토큰: localStorage 저장
- axios interceptor: `Authorization: Bearer ${token}` 자동 주입
- CORS: Backend에서 관리

### 6. 접근성 테스트 (A11y)

#### Playwright A11y 테스트

```bash
npm run test:e2e        # 자동화된 a11y 검사 (axe-core)
```

#### 수동 체크리스트

```
[ ] 키보드 네비게이션 (Tab, Enter, Escape)
[ ] 포커스 시각화 (outline)
[ ] 위기 모달: Tab이 모달 내에서만 순환 (포커스 트랩)
[ ] 색상 대비 (WCAG AA 이상)
[ ] 스크린리더 호환성 (role, aria-label)
[ ] 폰트 크기 조정 가능 (rem 기반)
```

---

## 테스트 실행

### 개발 단계

```bash
# 금지어 검사 (매번)
npm run lint:words

# 이모지 검사
npm run lint:emoji

# 개발 서버 시작 (MSW 자동 활성)
npm run dev

# Vitest 유닛 테스트
npm run test
```

### Pre-commit Hook (권장)

```bash
# .husky/pre-commit
npm run lint:words
npm run lint:emoji
npm run test        # 또는 SKIP_TESTS=1로 우회 가능
```

### CI/CD (GitHub Actions)

```yaml
name: Test & Lint

on: [pull_request, push]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-node@v3
        with:
          node-version: '20'
      - run: npm ci
      - run: npm run lint:words
      - run: npm run lint:emoji
      - run: npm run test
      - run: npm run build
```

### 배포 전 체크리스트

```
[ ] npm run lint:words 통과
[ ] npm run lint:emoji 통과
[ ] npm run test 통과 (또는 생략 사유 명시)
[ ] npm run build 성공 (no errors/warnings)
[ ] 전체 플로우 수동 테스트
  [ ] 로그인 → 피드 열람 → 게시글 작성 → 배심원 조회 → 투표 → 댓글
  [ ] 위기 모달 팝업 테스트
[ ] 모바일 반응형 확인
[ ] 실서버 e2e 테스트 (prod 배포 시 필수)
  npm run test:e2e:realbe
```

---

## MSW (Mock Service Worker)

### 개발 모드에서의 활성화

```typescript
// app/layout.tsx (클라이언트)
import { MSWProvider } from '@/components/shared/MSWProvider'

export default function RootLayout({ children }) {
  return (
    <html>
      <body>
        <MSWProvider>
          {children}
        </MSWProvider>
      </body>
    </html>
  )
}
```

### 핸들러 구조

```typescript
// mocks/handlers/community.ts
export const communityHandlers = [
  http.get('/api/posts', () => json([...])),
  http.post('/api/posts', () => json({...})),
  http.get('/api/posts/:id/jury-opinions', () => json([...])),
]

// mocks/handlers/notifications.ts
// mocks/handlers/user.ts
```

**중요**: MSW는 dev 전용. prod에서는 무시되고 실제 Backend로 요청.

---

## 성능 테스트

### Lighthouse (Chrome DevTools)

```
[ ] Performance: 85+ (모바일)
[ ] Accessibility: 90+ (a11y)
[ ] Best Practices: 90+
[ ] SEO: 90+
```

### Web Vitals

- **LCP (Largest Contentful Paint)**: < 2.5s
- **FID (First Input Delay)**: < 100ms
- **CLS (Cumulative Layout Shift)**: < 0.1

---

## 알려진 이슈 & 향후 과제

- 유닛 테스트 커버리지 아직 낮음 (점진적 보강 예정)
- Playwright e2e-realbe는 **prod(:8091)** 실서버 필요 (미공개 기간). workers=1 직렬.
- 2026-08 스위트 슬림화: 중복·고정 sleep·API-only→BE 이관 + corner case(20–22) 보강.
  - 케이스 수: ~161 → ~97 (`test()` 기준, **-40%**).
  - 벽시계 측정(2026-08-01, `localhost:8091`, workers=1, retries=1):
    - 슬림 직후(CORS 깨진 `127.0.0.1`): ~**9.4–11분** (다수 실패·retry)
    - CORS 수정 후: **102 passed / 0 failed / 0 skipped — 1.5분**
  - `E2E_BASE_URL`에 `127.0.0.1`을 넣어도 Playwright config가 `localhost`로 정규화한다.
  - cleanup: 포스트/댓글/투표 + marketing_job·notifications·community_reports·게스트 users 행까지 teardown에서 삭제.
- 성능 모니터링 자동화 미구현
- FE KeywordGuard는 스텁(서버 KeywordGuard만) — 위기 키워드 e2e 없음

---
