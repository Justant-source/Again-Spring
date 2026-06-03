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

### 4. E2E 테스트 (Playwright)

#### Playwright 설정 및 실행

```bash
# 개발 환경 (localhost:3000 + MSW)
npm run test:e2e

# 실서버 (dev/prod 환경)
npm run test:e2e:realbe
```

**설정 파일**:
- `playwright.config.ts` — 로컬 a11y 테스트
- `playwright.realbe.config.ts` — 실서버 e2e

#### Flow 테스트

위치: `tests/e2e-realbe/flows/`

| Flow | 파일 | 대상 |
|---|---|---|
| 01 | `01-auth/` | 로그인, OAuth, 게스트 진입 |
| 02 | `02-permissions/` | 권한 게이팅 (3-tier) |
| 03 | `03-email-verification/` | 이메일 인증 |
| 04 | `04-community-plaza/` | 광장 피드, 게시글 작성, 배심원 조회, 투표, 댓글 |

#### Invariant 테스트

위치: `tests/e2e-realbe/invariants/`

| Invariant | 파일 | 목표 |
|---|---|---|
| community-legal-notice | `community-legal-notice.spec.ts` | 모든 공개 게시글에 법적 안내(약관 링크)가 표시되는가 |

#### Selector 관리

`tests/e2e-realbe/support/selectors.ts` — `data-testid` 중앙화

```typescript
export const selectors = {
  feedCard: (id) => `feed-card-${id}`,
  jurorCard: (id) => `juror-card-${id}`,
  voteBar: (id) => `vote-bar-${id}`,
  // ...
}
```

**중요**: `data-testid` 변경 시 항상 `selectors.ts` 동기화.

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
- Playwright e2e-realbe 실행 시 실서버 필요 (dev 또는 prod)
- 성능 모니터링 자동화 미구현

---

**마지막 업데이트**: 2026-06-03
