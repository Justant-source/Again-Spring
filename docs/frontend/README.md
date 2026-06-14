# 다시봄 (Again Spring) 프론트엔드 문서

> **"다시 봄. 다시 바라봄."**  
> 갈등 커뮤니티 플랫폼. 갈등을 게시하면 AI 배심원(심리상담사 페르소나)과 커뮤니티가 양쪽 입장을 분석하고 공감 비율을 제공하는 웹앱입니다.

본 디렉토리는 Next.js 14 기반의 **프로토타입** 프론트엔드 개발 문서입니다.

---

## 문서 구성

### 핵심 아키텍처

1. **[structure.md](./structure.md)** — 폴더 구조 및 파일 조직
   - `app/`, `components/`, `lib/`, `mocks/` 디렉토리 설명
   - 코드 위치 → 책임 매핑 테이블

2. **[architecture.md](./architecture.md)** — 기술 스택 및 데이터 흐름
   - Next.js 14, React 18, TypeScript, Tailwind, Zustand, axios, MSW
   - 페이지 흐름 (신규 사용자 → 커뮤니티 광장 진입)
   - 인증 흐름, 환경 변수, 디자인 시스템

### UX & 디자인 (V14에서 재구성)

3. **[design/](./design/)** — 디자인 시스템 (Claude Design 협업, V14)
   - [design/system.md](./design/system.md) — 3-Tone 시스템 (L/P/Q), 절대 금지, 시그니처 요소
   - [design/components.md](./design/components.md) — 컴포넌트 매핑 + HAX 체크리스트 링크
   - [design/icons.md](./design/icons.md) — SVG 아이콘 카탈로그 + emoji 금지 정책
   - [design/visual-reference/](./design/visual-reference/README.md) — 현재 디자인 캡처
   - [design/specs/](./design/specs/) — 화면별 UX 스펙 (신규 화면 추가 시)

4. **[ux/](./ux/)** — UX 원칙·체크리스트·흐름
   - [ux/principles.md](./ux/principles.md) — 4원칙군 (권위본)
   - [ux/hax-checklist.md](./ux/hax-checklist.md) — 컴포넌트별 PR 체크리스트
   - [ux/collaboration.md](./ux/collaboration.md) — Claude Design + Claude Code 협업 흐름 (Phase 5)
   - [ux/flows/](./ux/flows/) — 전체 UX 흐름 as-is (가입~리포트, mermaid 다이어그램)

### 정책 및 안전

5. **[policies/](./policies/)** — FE 정책 강제 방법
   - [forbidden-words-lint.md](./policies/forbidden-words-lint.md) — `npm run lint:words` 사용법, Level 1/2/3 금지어
   - [README.md](./policies/README.md) — 정책 문서 인덱스
   - 위기 처리 흐름: [`ux/flows/08-crisis.md`](./ux/flows/08-crisis.md)

### 테스트 및 품질

6. **[testing.md](./testing.md)** — 테스트 전략 + Mock API 시나리오
   - 린트 검사, 단위 테스트, 통합 테스트, 보안 테스트
   - MSW Mock API 활용
   - 배포 전 체크리스트

---

## 빠른 시작

### 개발 서버 실행

```bash
npm install
npm run dev           # localhost:3000 (MSW 자동 활성)
npm run lint:words    # 금지어 검사
npm run lint:emoji    # 이모지 금지 검사
npm run test          # Vitest 유닛 테스트
```

### 신규 컴포넌트 작성 시

1. 실제 경로 확인 → **[structure.md](./structure.md)**
2. UX 원칙 적용 → **[ux/principles.md](./ux/principles.md)**
3. HAX 체크리스트 → **[ux/hax-checklist.md](./ux/hax-checklist.md)**
4. 금지어 검사 → **[policies/forbidden-words-lint.md](./policies/forbidden-words-lint.md)**

### 금지어 검사

```bash
npm run lint:words    # exit 0: 통과, exit 1: 금지어 발견
```

정책 상세: **[policies/forbidden-words-lint.md](./policies/forbidden-words-lint.md)**

### 위기 감지

광장형에서는 사용자 입력에 필터를 적용하지 않습니다. 위기 키워드가 포함된 경우:
- **관리자 위기 마크** (admin crisis flag) 설정 가능
- **상시 핫라인 리소스** 노출 (CrisisResourceModal)
- 입력 차단이나 세션 중단 없음

정책 상세: **[shared/policies/forbidden-words.md](../shared/policies/forbidden-words.md)**

---

## 중요 원칙

### 1. 디자인 시스템

- **Tone L** (편지지 · 미스트 세이지): 입력·탐색·작성·대기·인증·마이페이지·알림·신고
- **Tone P** (파스텔 · 미스트 세이지): 결과 리포트(혼자/함께/마감)·상대 답변 도착
- **Tone Q** (조용함): (향후) PDF·프리미엄·상담사 연결

**진영색**: 작성자=피치(#C9785A) / 상대방=세이지(#5F8F76)  
한 화면에서 톤을 섞지 마세요. SSOT: **[design/system.md](./design/system.md)**

### 2. 금지어 정책 (AI 출력에만 적용)

- **Level 1** (법률): "과실비율" → "공감 비율", "판결" → "결과", "처방" 금지
- **Level 2** (진단명): "나르시시스트" → 구체적 행동 기술
- **Level 3** (판결): "이겼다/졌다/맞다/틀렸다" 금지
- **사용자 입력**: 필터 미적용 (사용자 책임)

### 3. 광장형 모델의 위기 처리

- 입력 필드의 자동 필터링 없음
- 관리자가 게시글에 `crisis flag` 설정 가능
- CrisisResourceModal은 상시 노출 (필요 시 강조)
- 사용자가 쓴 텍스트의 책임은 사용자에게

### 4. 대기관 정책

모든 서비스 정책(금지어, 위기 감지, 카테고리, 온보딩 매핑, 화해 기여도 계산, ToS)의 **권위본은 `../shared/policies/` 와 `../shared/v1/`** 에 있습니다.

FE는 이들을 **참조하고 구현**할 뿐, 독립적으로 정의하지 않습니다.

---

## 개발 체크리스트

### 새 페이지/컴포넌트 추가 시

- [ ] `npm run lint:words` 통과
- [ ] `npm run lint` 통과
- [ ] structure.md의 폴더 규칙 준수
- [ ] 위기 키워드 감지 → `CrisisResourceModal` 렌더

### 배포 전

- [ ] `npm run build` 성공 (no errors)
- [ ] `npm run lint:words` 최종 확인
- [ ] 전체 플로우 (온보딩 → 광장 게시 → 배심원 → 투표/댓글) 수동 테스트
- [ ] 모바일 반응형 (PhoneFrame) 확인
- [ ] 댓글 무한스크롤 테스트

---

## 디렉토리 맵

```
frontend/
├── README.md              # 메인 엔트리 (간단한 가이드)
├── package.json           # next 14, react 18, msw 2.6, ...
├── tailwind.config.ts     # 3-Tone 토큰, Noto Sans KR, 애니메이션
│
├── app/                   # Next.js App Router 페이지
│   ├── page.tsx           # / (랜딩)
│   ├── auth/              # 가입/로그인
│   ├── (onboarding)/      # 온보딩 플로우
│   ├── community/         # 광장 피드·게시·댓글
│   ├── (dashboard)/       # 이력·프로필
│   └── globals.css        # 공통 스타일
│
├── components/            # React 컴포넌트
│   ├── shared/            # Logo, PhoneFrame
│   ├── onboarding/        # LikertQuestion
│   ├── community/c3/      # 광장 컴포넌트 (FeedCard, JurorCard, VoteBar, CommentBar)
│   └── ui/                # 기본 UI (Radix)
│
├── lib/
│   ├── api/               # axios client + interceptor
│   ├── store/             # Zustand (user, session)
│   ├── constants/         # 상수 (카테고리, 온보딩, 금지어)
│   ├── types/             # TypeScript 타입
│   └── utils/             # 헬퍼 (keywordGuard, ratio, etc)
│
├── mocks/                 # MSW Mock API
│   ├── handlers/          # 라우트별 핸들러
│   └── fixtures/          # Mock 데이터
│
├── design/                # 디자인 자산 (배포 미포함)
│   ├── handoff/           # Claude Design 원본 (참조용)
│   └── mockups/           # Claude Design 결과 캡처 저장
│
├── docs/                  # ← 본 문서
│   ├── structure.md
│   ├── architecture.md
│   ├── testing.md         # 테스트 전략 + Mock API 시나리오
│   ├── design/            # 디자인 시스템 (V14)
│   ├── ux/                # UX 원칙·체크리스트
│   │   └── flows/         # as-is UX 흐름 문서 (9개 주제, mermaid)
│   └── policies/          # 금지어, 위기 감지
│
├── scripts/
│   └── check-forbidden-words.js   # npm run lint:words
│
└── public/
    └── mockServiceWorker.js       # MSW 자동 생성
```

---

## 다음 단계

### 프로토타입 완성도

- ✅ Next.js 14 + TypeScript + Tailwind
- ✅ MSW Mock API
- ✅ 광장형 UX (게시 → 배심원 → 투표/댓글)
- ✅ 금지어 검사
- ✅ 3-Tone 디자인 시스템
- ✅ 댓글 무한스크롤
- ✅ Vitest 단위 테스트 (`npm run test`)
- ✅ Playwright E2E (e2e-realbe — prod 배포 게이트)

---

## 각 문서가 다루는 내용

| 문서 | 대상 | 주제 |
|---|---|---|
| [structure.md](./structure.md) | 모두 | 폴더·파일 위치 |
| [architecture.md](./architecture.md) | 개발자 | 기술 스택, 데이터 흐름, 라우팅 |
| [design/system.md](./design/system.md) | 디자이너·개발자 | 톤(L/P/Q), 절대 금지, 시그니처 요소 |
| [design/components.md](./design/components.md) | 디자이너·개발자 | 컴포넌트 매핑, HAX 체크리스트 링크 |
| [policies/forbidden-words-lint.md](./policies/forbidden-words-lint.md) | 개발자 | 금지어 검사 방법, CI/CD 통합 |
| [testing.md](./testing.md) | QA·개발자 | 테스트 전략, 체크리스트 |
| [ux/flows/](./ux/flows/) | 개발자·디자이너 | as-is UX 흐름 전체 (가입~리포트 mermaid) |

---

## 추가 참고 자료

### shared 문서 (권위본)

```
../shared/
├── policies/
│   ├── forbidden-words.md          # 금지어 정의 (권위본)
│   ├── categories.md               # 갈등 카테고리 (권위본)
│   └── ratio-calculation.md        # 화해 기여도 계산
├── api/
│   ├── rest-spec.md                # REST API 명세
│   └── database-schema.md          # BE 데이터베이스
└── prompts/
    └── README.md                   # Gottman + NVC + 관계 프롬프트 구조
```

---

**이제 [structure.md](./structure.md)를 읽어 폴더 구조를 파악하거나, [architecture.md](./architecture.md)로 기술 스택을 이해하세요.**

