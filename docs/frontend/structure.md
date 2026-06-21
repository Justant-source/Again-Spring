# 폴더 구조

```
frontend/
├── package.json                # next 14, react 18, zustand, axios, msw
├── next.config.mjs
├── tailwind.config.ts
├── tsconfig.json               # ES2022, strict, path alias @/*
├── vitest.config.ts            # Vitest 설정
├── playwright.config.ts        # Playwright e2e (a11y)
├── playwright.realbe.config.ts # Playwright e2e (실서버)
├── postcss.config.mjs
├── Dockerfile                  # multi-stage, non-root
│
├── app/                        # Next.js App Router
│   ├── layout.tsx              # 루트 레이아웃 (MSWProvider 등록)
│   ├── page.tsx                # / (랜딩)
│   ├── loading.tsx
│   ├── not-found.tsx
│   ├── globals.css
│   ├── privacy/page.tsx
│   ├── terms/page.tsx
│   │
│   ├── (admin)/
│   │   └── admin/
│   │       ├── community/      # 광장 관리
│   │       └── marketing/      # 마케팅 대시보드
│   │           ├── calendar/, contents/, costs/, hashtags/, settings/
│   │           ├── simulations/, stories/, templates/
│   │
│   ├── (auth)/                 # 라우트 그룹 (인증 관련)
│   │   ├── forgot-password/page.tsx
│   │   ├── guest/page.tsx
│   │   ├── login/page.tsx
│   │   ├── reset-password/[token]/page.tsx
│   │   └── signup/page.tsx
│   │
│   ├── auth/
│   │   └── callback/[provider]/page.tsx     # OAuth 콜백
│   │
│   ├── (dashboard)/            # 인증 필요
│   │   └── profile/page.tsx
│   │
│   ├── community/              # 광장형 메인 흐름
│   │   ├── page.tsx            # 피드 (무한스크롤)
│   │   ├── [id]/page.tsx       # 게시글 상세 + 배심원 + 투표 + 댓글
│   │   ├── [id]/comments/page.tsx
│   │   ├── [id]/invite/page.tsx
│   │   ├── [id]/read/page.tsx
│   │   └── new/page.tsx        # 게시글 작성
│   │
│   ├── notifications/page.tsx  # 알림
│   │
│   └── s/[token]/page.tsx      # 초대 토큰 진입점
│
├── components/
│   ├── admin/
│   │   └── marketing/
│   │       └── preview/
│   │
│   ├── auth/                   # 인증 컴포넌트
│   │
│   ├── community/
│   │   └── c3/                 # 광장 핵심 컴포넌트
│   │       ├── FeedCard.tsx
│   │       ├── JurorCard.tsx
│   │       ├── JurorPicker.tsx
│   │       ├── VoteBar.tsx
│   │       ├── CommentBar.tsx
│   │       ├── CommentComposeSheet.tsx
│   │       ├── CommunityComment.tsx
│   │       ├── UserChip.tsx
│   │       ├── BrandBar.tsx          # 광장 헤더 (카테고리 필터·검색 진입)
│   │       ├── SearchPanel.tsx       # 검색 오버레이 패널 (UX-C3 디자인)
│   │       ├── SideStory.tsx
│   │       └── index.ts
│   │
│   ├── feedback/
│   ├── icons/                  # DasibomLogo, Phone, CrisisResources, etc.
│   ├── legal/
│   ├── profile/
│   ├── shared/                 # 공유 컴포넌트
│   │   ├── MSWProvider.tsx
│   │   ├── Logo.tsx
│   │   ├── CrisisResourceModal.tsx   # 위기 모달
│   │   ├── LegalFooter.tsx
│   │   └── ...
│   │
│   └── ui/                     # 기본 UI 컴포넌트 (shadcn-ish)
│
├── lib/
│   ├── api/
│   │   ├── community.ts        # /api/communities/* + /api/posts/*
│   │   ├── user.ts             # /api/users/*
│   │   └── client.ts           # axios 인스턴스 + Bearer 인터셉터
│   │
│   ├── constants/
│   │   ├── forbiddenWords.ts   # CRISIS_KEYWORDS, WARNING_KEYWORDS, FORBIDDEN_UI_WORDS
│   │   ├── userPermissions.ts  # permissionsFor() 함수, 3-tier 권한
│   │   └── ...
│   │
│   ├── store/
│   │   └── uiStore.ts          # Zustand + persist
│   │
│   └── utils/
│       ├── styleCalculator.ts  # 유틸 함수
│       ├── cn.ts               # clsx + tailwind-merge
│       └── ...
│
├── mocks/                      # MSW
│   ├── browser.ts              # setupWorker
│   └── handlers/
│       ├── index.ts
│       ├── community.ts        # /api/communities, /api/posts
│       ├── notifications.ts    # /api/notifications
│       └── user.ts             # /api/users
│
├── scripts/
│   ├── lint:words              # 금지어 검사
│   ├── lint:emoji              # 이모지 검사
│   └── test:e2e:realbe         # 실서버 e2e
│
├── design/                     # 디자인 자산 (배포 미포함)
│   └── 다시봄 광장형 UX (standalone).html  # 28화면 시각 정본
│
├── public/
│   └── mockServiceWorker.js    # MSW 자동 생성 (gitignored)
│
├── tests/
│   ├── e2e/                    # Playwright (a11y)
│   ├── e2e-realbe/             # Playwright (실서버)
│   │   ├── flows/
│   │   │   ├── 01-auth/
│   │   │   ├── 02-permissions/
│   │   │   ├── 03-email-verification/
│   │   │   └── 04-community-plaza/
│   │   ├── invariants/
│   │   │   └── community-legal-notice.spec.ts
│   │   └── support/
│   │       └── selectors.ts    # data-testid 관리
│   │
│   └── unit/                   # Vitest
│
└── docs/                       # 개발 문서
    ├── README.md               # 문서 인덱스
    ├── structure.md            # 폴더 구조 (본 파일)
    ├── architecture.md         # 기술 스택 및 데이터 흐름
    ├── testing.md              # 테스트 전략
    ├── ux/
    │   ├── principles.md       # FE UX 권위본
    │   ├── hax-checklist.md    # 컴포넌트 체크리스트
    │   ├── collaboration.md    # 협업 프로세스
    │   └── flows/
    │       ├── 01-auth.md
    │       ├── 02-permissions.md
    │       ├── 08-crisis.md
    │       └── 09-admin.md
    ├── design/
    │   ├── README.md           # 디자인 문서 인덱스
    │   ├── system.md           # 디자인 시스템 SSOT (색·타이포·시그니처·금지사항)
    │   ├── components.md       # 컴포넌트 인벤토리 + 28화면 인덱스 + 인터랙션 규칙
    │   ├── icons.md            # SVG 아이콘 카탈로그 + emoji 금지 정책
    │   ├── visual-reference/
    │   │   └── README.md       # 시각 정본 HTML 포인터
    │   └── specs/
    │       └── metaphor-illustration-system.md  # 메타포 일러스트 60종 레지스트리
    └── policies/
        ├── README.md
        ├── forbidden-words-lint.md
        └── (기타 정책 문서)
```

---

## App Router 라우팅

### 라우트 그룹 (URL 미반영)
- `(admin)` — `/admin/*`의 레이아웃 그룹화
- `(auth)` — 인증 플로우 관련
- `(dashboard)` — 인증 필요 페이지 그룹화

### 일반 폴더 (URL 반영)
- `auth/` — OAuth 콜백
- `community/` — 광장 메인
- `notifications/` — 알림
- `s/` — 초대 토큰 진입

### 동적 라우트
- `[provider]` → `/auth/callback/google`, `/auth/callback/kakao`
- `[token]` → `/s/{token}`, `/reset-password/{token}`
- `[id]` → `/community/{id}`, `/community/{id}/comments`

---

## 실제 구조 vs 부재 경로

### 실재 하는 것
- `app/community/**` — 광장 피드·상세·작성
- `app/(admin)/admin/community/` — 광장 관리
- `app/(admin)/admin/marketing/**` — 마케팅 대시보드
- `components/community/c3/` — FeedCard, JurorCard, SearchPanel, BrandBar 등 12개 컴포넌트
- `lib/api/community.ts` + `lib/api/user.ts` — API 클라이언트
- `lib/constants/forbiddenWords.ts` — 3-tier 금지어
- `lib/constants/userPermissions.ts` — 3-tier 권한
- `lib/store/uiStore.ts` — 상태 관리
- `mocks/handlers/community.ts`, `notifications.ts`, `user.ts`
- `tests/e2e-realbe/flows/{01,02,03,04}/`, `invariants/community-legal-notice.spec.ts`

### 부재하는 것 (삭제됨)
- `app/(onboarding)/**` — 온보딩 페이지 (광장형 모델로 변경)
- `app/(dashboard)/history/` — 기존 세션 이력 (광장형에서 비관련)
- `components/chat/`, `components/result/` — 구 카톡 채팅/결과 (광장형 전환)
- `components/onboarding/` — 온보딩 컴포넌트
- `lib/constants/onboardingQuestions.ts`, `communicationStyles.ts`, `mbtiMapping.ts`
- `lib/utils/keywordGuard.ts` — 클라이언트 사이드 검사 (서버만 사용)
- `lib/store/sessionStore.ts`, `communityStore.ts` — 삭제됨 (uiStore로 통합)
- `mocks/handlers/session.ts`, `chat.ts`, `mediation.ts`
- `mocks/fixtures/mockReports.ts`

---

## 코드 위치 → 책임

| 작업 | 파일 위치 |
|---|---|
| 새 페이지 | `app/<path>/page.tsx` |
| 재사용 컴포넌트 | `components/<domain>/` |
| API 호출 | `lib/api/{community,user}.ts` |
| 클라이언트 상태 | `lib/store/uiStore.ts` |
| 타입 | `lib/types/` |
| 유틸 함수 | `lib/utils/` |
| 상수 | `lib/constants/` |
| MSW 핸들러 | `mocks/handlers/{community,notifications,user}.ts` |
| e2e 테스트 | `tests/e2e-realbe/` |
| 테스트 ID 관리 | `tests/e2e-realbe/support/selectors.ts` |
