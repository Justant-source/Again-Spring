# 금지어 린트 검사 (npm run lint:words)

금지어 정의의 권위본은 `../../shared/policies/forbidden-words.md` 입니다. 이 문서는 FE에서 그 정책을 어떻게 강제하는지를 다룹니다.

---

## 개요

`npm run lint:words` 스크립트는 `src/` 디렉토리의 모든 코드 파일을 스캔하여 금지된 단어 또는 표현을 검출하고, 발견 시 빌드를 중단합니다.

---

## 사용 방법

### 개발 중 검사

```bash
npm run lint:words
```

### CI/CD 파이프라인 통합

```bash
# package.json의 "lint:words" 스크립트를 pre-commit 훅에 추가
# 또는 GitHub Actions 등 CI 단계에서 실행
```

---

## 스캔 대상

```
frontend/
├── app/          # Next.js 페이지 · 레이아웃
├── components/   # React 컴포넌트
├── lib/          # 유틸, 상수, 타입
└── mocks/        # Mock API 핸들러
```

제외:
- `node_modules/`
- `public/`
- `.next/` 빌드 결과
- `design/` (비코드 자산)
- `tests/` (테스트 시나리오, 필요시만 검사)

**적용 범위**: AI 생성 텍스트 (배심원 의견, 요약, 제목) 중심  
**미적용**: 사용자 입력 (게시글, 댓글) — 사용자 책임

---

## 금지어 카테고리

### Level 1 — 법률 용어 (UI 전면 차단)

| 금지어 | 대체 표현 | 이유 |
|---|---|---|
| "과실비율" | "화해 기여도" | 변호사법 저촉 |
| "판결" | "결과", "결론" | 사법 권한 침해 |
| "판사" | "중재자", "조정자" | — |
| "심판" | "평가", "검토" | — |
| "유죄", "무죄" | — | 금지 |
| "증거" | "사실", "상황" | — |
| "판단" | "관찰", "이해" | — |
| "가해자" | "먼저 다가가면 좋은 쪽" | 낙인 위험 |
| "피해자" | "마음 열고 기다려주면 좋은 쪽" | 낙인 위험 |
| "고소" | — | 금지 |
| "소송" | — | 금지 |

### Level 2 — 진단명/임상 용어 (악용 가능)

| 금지어 | 대체 표현 | 이유 |
|---|---|---|
| "나르시시스트" | 구체적 행동 기술 | 임상 진단 금지 |
| "소시오패스" | — | 임상 진단 금지 |
| "가스라이팅" | "신뢰 훼손", "불신 조장" | 악용 위험 |
| "PTSD" | "깊은 상처" | 임상 용어 금지 |
| "트라우마" | "어려웠던 경험" | 임상 용어 금지 |

### Level 3 — 판결/승패 (관계 파국)

| 금지어 | 대체 표현 | 이유 |
|---|---|---|
| "이겼다", "졌다" | — | 금지 |
| "맞다", "틀렸다" | — | 금지 |
| "승자", "패자" | — | 금지 |
| "헤어지세요" | — | 금지 |
| "절교" | — | 금지 |
| "손절" | — | 금지 |

### 위기 키워드 (세션 즉시 중단)

```
폭력: "때리", "폭행", "폭력", "구타"
성폭력: "강간", "성폭행"
자해: "죽고 싶", "자살", "자해", "목 매"
아동학대: "아이를 때", "아동학대"
```

감지 시 → `CrisisResourceModal` 즉시 표시. 위기 처리 흐름: `docs/frontend/ux/flows/08-crisis.md`

---

## 검사 흐름

```
사용자 입력/코드 문자열
    ↓
scripts/check-forbidden-words.js 실행
    ↓
[ Level 1 ]
  금지어 발견?
    YES → 빌드 중단 (exit 1)
    NO  → 계속
    ↓
[ Level 2 ]
  금지어 발견?
    YES → 경고 출력 (exit 1)
    NO  → 계속
    ↓
[ Level 3 ]
  금지어 발견?
    YES → 경고 출력 (exit 1)
    NO  → 계속
    ↓
[ 위기 키워드 ]
  감지?
    YES → 즉시 CrisisResourceModal 필요
    NO  → 완료 (exit 0)
```

---

## 종료 코드

| 코드 | 의미 |
|---|---|
| 0 | 금지어 없음. 통과. |
| 1 | Level 1, 2, 3 금지어 또는 위기 키워드 발견. 빌드 중단. |

---

## CI/CD 통합

### GitHub Actions 예시

```yaml
name: Lint Forbidden Words

on: [pull_request, push]

jobs:
  lint-words:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-node@v3
        with:
          node-version: '20'
      - run: npm ci
      - run: npm run lint:words
```

### Pre-commit Hook

```bash
# .husky/pre-commit
npm run lint:words
```

---

## 검사 결과 예시

### 통과

```
✓ No forbidden words found
✓ Pre-commit check passed
```

### 실패

```
✗ Forbidden word detected:

File: app/session/result/[id]/page.tsx
Line 45: "가해자와 피해자의 과실비율을 분석합니다"
         ^^^^^^  ^^^^^ ^^^^^^^^^
         Level 1 violations

Replace with: "화해 기여도" (instead of "과실비율")
              "먼저 다가가면 좋은 쪽" (instead of "가해자")
              "마음 열고 기다려주면 좋은 쪽" (instead of "피해자")

Fix and re-run: npm run lint:words
```

---

## 예외 처리

특정 라인을 검사에서 제외하려면 (권장하지 않음):

```tsx
// lint-ignore: forbidden-words
const text = "금지된 단어... (예외적으로 필요한 경우만)";
```

**주의**: 예외는 최소화. 필요 시 리포트에 이유를 명시하고 PR 리뷰에서 승인받아야 합니다.

---

## 정책 업데이트

금지어 목록은 `../../shared/policies/forbidden-words.md`에서 중앙 관리됩니다.

정책 변경 시:
1. shared 문서 수정
2. `lib/constants/forbiddenWords.ts` 동기화
3. `scripts/check-forbidden-words.js` 규칙 업데이트
4. `npm run lint:words` 재실행
