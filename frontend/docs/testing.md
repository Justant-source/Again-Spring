# FE 테스트 전략

프론트엔드의 테스트 계층별 목표, 도구, 체크리스트를 설명합니다.

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
- **상세**: `docs/policies/forbidden-words-lint.md`

#### ESLint 검사

```bash
npm run lint
```

- **목표**: 코드 품질, 타입 안전
- **설정**: `next.config.mjs`의 `eslint.dirs`
- **CI/CD**: 빌드 전 자동 실행

### 2. 단위 테스트 (Unit Tests)

현재 jest/vitest 미설정 (향후 추가 고려).

권장 테스트 대상:
- **유틸 함수**: `lib/utils/*.ts`
  - `keywordGuard.ts` — 금지어/위기 키워드 검출 로직
  - `styleCalculator.ts` — 온보딩 답변 → 스타일 계산
  - `ratio.ts` — 화해 기여도 표시 헬퍼
  - `cn.ts` — clsx 유틸

- **상수**: `lib/constants/*.ts`
  - `forbiddenWords.ts` — 금지어 리스트 구조 검증
  - `categories.ts` — 카테고리 트리 무결성

- **Zustand store**: `lib/store/*.ts`
  - `userStore.ts` — 상태 변화, localStorage 동기화
  - `sessionStore.ts` — 세션 상태 초기화, 리셋

#### 향후 도입 시 참고 예시 (현재 미설정)

```typescript
// lib/utils/__tests__/keywordGuard.test.ts
import { checkCrisisKeywords, checkForbiddenWords } from '../keywordGuard';

describe('keywordGuard', () => {
  describe('checkCrisisKeywords', () => {
    it('should detect self-harm keywords', () => {
      const result = checkCrisisKeywords('죽고 싶어요');
      expect(result.length).toBeGreaterThan(0);
    });

    it('should not detect non-crisis text', () => {
      const result = checkCrisisKeywords('오늘 날씨 좋네요');
      expect(result.length).toBe(0);
    });
  });

  describe('checkForbiddenWords', () => {
    it('should detect level 1 words', () => {
      const result = checkForbiddenWords('과실비율을 계산합니다');
      expect(result.level).toBe(1);
    });
  });
});
```

#### 목표 커버리지

- `lib/utils/`: 80% 이상
- `lib/constants/`: 70% 이상
- `lib/store/`: 75% 이상

### 3. 통합 테스트 (Integration Tests)

#### MSW Mock API 테스트

MSW는 이미 dev 모드에서 작동하고 있으므로, 실제 페이지 플로우를 테스트할 때 자동으로 mock API 응답을 받습니다.

수동 테스트 체크리스트:

```
[ ] 로그인 → 온보딩 → 세션 시작 → 중재 → 결과 전체 흐름
[ ] 각 Mock 시나리오 (factual, difference, mixed, solo)
[ ] 금지어 입력 시 에러 메시지 표시
[ ] 위기 키워드 입력 시 모달 표시
[ ] B 참여 링크 클릭 시 세션 진입
[ ] Solo 모드 전환 (B 미참여 24시간 후)
```

#### 자동화 테스트 (Playwright — 미설정, 향후)

```bash
npm install -D @playwright/test
npx playwright install

# tests/e2e/flow.spec.ts
import { test, expect } from '@playwright/test';

test('full session flow', async ({ page }) => {
  await page.goto('http://localhost:3000');
  await page.click('text=시작하기');
  // ... 온보딩, 세션, 중재, 결과
  await expect(page).toHaveURL(/\/session\/result\//);
});
```

### 4. 보안 테스트

#### 금지어 및 위기 키워드

```
[ ] Level 1 금지어 입력 시 차단 확인
[ ] Level 2/3 금지어 입력 시 경고 확인
[ ] 위기 키워드 ("때리", "자살" 등) 입력 시 모달 표시
[ ] 모달 닫기 (나중에 버튼으로만 가능)
```

#### XSS 및 인젝션

- Zustand store에 저장되는 사용자 입력은 모두 텍스트로 처리 (JSON 인젝션 불가)
- React는 자동으로 XSS 방지 (JSX에서 `{variable}` 사용 시 이스케이프)
- 외부 HTML 삽입 금지 (`dangerouslySetInnerHTML` 미사용)

#### CSRF 및 토큰

- JWT 토큰은 localStorage에 저장 (httpOnly 불가능한 클라이언트 한계)
- axios interceptor에서 `Authorization: Bearer ${token}` 자동 주입
- CORS 설정은 BE에서 관리

### 5. 접근성 테스트 (A11y)

수동 체크리스트:

```
[ ] 키보드 네비게이션 (Tab, Enter, Escape)
[ ] 포커스 시각화 (outline 또는 highlight)
[ ] 모달의 포커스 트랩 (Tab이 모달 내에서만 순환)
[ ] 색상 대비 (WCAG AA 이상)
[ ] 스크린리더 호환성 (role, aria-label)
[ ] 폰트 크기 조정 가능 (rem 기반, 사용자 폰트 확대 지원)
```

자동 검사 도구 (향후):

```bash
npm install -D axe-core @testing-library/react
# axe-core로 자동 a11y 검사
```

---

## 테스트 실행

### 개발 단계

```bash
# 금지어 검사 (매번)
npm run lint:words

# ESLint 검사
npm run lint

# 개발 서버 시작 (MSW 자동 활성)
npm run dev
```

### Pre-commit Hook (권장)

```bash
# .husky/pre-commit
npm run lint:words
npm run lint
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
      - run: npm run lint
      # - run: npm test        (향후)
      # - run: npm run test:e2e (향후)
```

### 배포 전 체크리스트

```
[ ] npm run lint:words 통과
[ ] npm run lint 통과
[ ] npm run build 성공 (no errors/warnings)
[ ] 전체 플로우 (온보딩 → 세션 → 중재 → 결과) 수동 테스트
[ ] 모든 금지어 대체 표현 사용 확인
[ ] 위기 모달 팝업 테스트
[ ] 모바일 반응형 확인 (PhoneFrame 테스트)
[ ] 접근성 키보드 네비게이션 확인
```

---

## MSW 활용

### Mock 시나리오 전환

개발 중 특정 시나리오를 테스트하려면:

```
/session/result/[id]?mockScenario=factual
/session/result/[id]?mockScenario=difference
/session/result/[id]?mockScenario=mixed
/session/result/[id]?mockScenario=solo
```

### 핸들러 디버깅

MSW는 모든 가로챈 요청을 브라우저 콘솔에 로깅합니다:

```
[MSW] POST /api/sessions 200
[MSW] POST /api/sessions/session_123/turns 200
```

`mocks/handlers/index.ts`에서 핸들러 추가/수정 후 자동 핫 리로드.

---

## 성능 테스트

### Lighthouse (Chrome DevTools)

```
[ ] Performance: 90 이상
[ ] Accessibility: 90 이상
[ ] Best Practices: 90 이상
[ ] SEO: 90 이상
```

### 다음 단계

- Web Vitals 모니터링 (CLS, LCP, FID)
- 번들 크기 분석 (next/bundle-analyzer)

---

## 알려진 이슈

### 미설정 항목

- Jest/Vitest 단위 테스트 인프라
- Playwright E2E 테스트
- Axe-core 접근성 자동 검사

이들은 프로젝트 성숙도에 따라 향후 추가 권장.

