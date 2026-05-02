# 폴더 구조

```
frontend/
├── package.json                # next 14.2.15, react 18.3.1, zustand 5, axios 1.7, msw 2.6
├── next.config.mjs             # ESLint dirs, strict mode
├── tailwind.config.ts          # 디자인 토큰: tone-l/p/q, canvas, Pretendard, blink·fade-in-up
├── tsconfig.json               # ES2022, strict, path alias @/*
├── postcss.config.mjs
├── Dockerfile                  # multi-stage, non-root
├── capacitor.config.ts         # (모바일 앱 래퍼 향후)
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
│   ├── auth/                   # 비로그인 라우트
│   │   ├── login/page.tsx
│   │   ├── signup/page.tsx
│   │   ├── guest/page.tsx
│   │   ├── forgot-password/page.tsx
│   │   └── callback/[provider]/page.tsx     # OAuth callback
│   │
│   ├── (auth)/                 # 라우트 그룹 — 사일런트
│   │   └── reset-password/[token]/page.tsx
│   │
│   ├── (onboarding)/           # 라우트 그룹
│   │   └── onboarding/
│   │       ├── page.tsx                      # 10문항
│   │       ├── intro/page.tsx
│   │       ├── result/page.tsx               # 스타일 카드
│   │       ├── mbti-test/page.tsx            # 선택
│   │       └── mbti-input/page.tsx           # 선택
│   │
│   ├── (dashboard)/            # 인증 필요
│   │   ├── profile/page.tsx
│   │   └── history/page.tsx
│   │
│   └── session/                # 세션 흐름
│       ├── new/page.tsx
│       ├── wait/page.tsx
│       ├── category/page.tsx
│       ├── describe/page.tsx                # KeywordGuard 적용
│       ├── invite/page.tsx
│       ├── [sessionId]/
│       │   ├── page.tsx                      # 채팅 메인 (ChatLayout)
│       │   └── loading.tsx
│       ├── join/[token]/page.tsx             # B 진입
│       └── result/
│           ├── [id]/page.tsx                 # 결과 리포트
│           └── [id]/solo/page.tsx
│
├── components/
│   ├── shared/
│   │   ├── MSWProvider.tsx                   # 클라이언트 사이드 MSW worker 등록
│   │   ├── Logo.tsx
│   │   ├── Dashes.tsx
│   │   ├── PhoneFrame.tsx
│   │   ├── Motif.tsx
│   │   ├── LegalFooter.tsx                   # 모든 결과 화면 푸터
│   │   ├── RelationshipColorSync.tsx
│   │   ├── CrisisResourceModal.tsx           # 위기 감지 모달
│   │   └── KeywordGuard.tsx                  # 입력 필드용 인라인 가드
│   │
│   ├── onboarding/
│   │   ├── LikertQuestion.tsx                # 5점 리커트
│   │   └── MbtiAxisSlider.tsx                # MBTI 4축
│   │
│   ├── chat/
│   │   ├── ChatLayout.tsx                    # 채팅 컨테이너 레이아웃
│   │   ├── ChatPanel.tsx                     # A/B 채팅 패널
│   │   ├── ChatHeader.tsx                    # 세션 정보 헤더
│   │   ├── ChatInput.tsx                     # 입력 필드 + KeywordGuard
│   │   ├── MessageBubble.tsx                 # 메시지 버블
│   │   ├── PartnerPanel.tsx                  # B 참여 대기/진행 상태 패널
│   │   ├── PartnerStatusBar.tsx              # B 온라인 상태 바
│   │   ├── SwipeContainer.tsx                # 모바일 좌우 스와이프
│   │   ├── CrisisModal.tsx                   # 위기 감지 모달 (채팅용)
│   │   ├── InviteModal.tsx                   # 초대 링크 공유 모달
│   │   ├── FinalizeSuggestionCard.tsx        # 세션 종료 제안
│   │   ├── PartnerJoinedToast.tsx            # B 참여 알림
│   │   └── PartnerJoinNoticeCard.tsx         # B 참여 공지 카드
│   │
│   ├── result/
│   │   ├── ReportLayout.tsx                  # 결과 카드 레이아웃
│   │   ├── StyleCombination.tsx              # 6×6 조합 해석
│   │   ├── ContributionRatio.tsx             # 화해 기여도 도넛
│   │   ├── SoloResult.tsx                    # Solo 모드 결과
│   │   ├── NeedsMap.tsx                      # 욕구 차이 지도
│   │   ├── NVCScript.tsx                     # NVC 4단계 카드
│   │   ├── MetaphorCards.tsx                 # 은유 카드
│   │   ├── RepairSuggestions.tsx             # 관계 회복 제안
│   │   ├── ShareImage.tsx                    # 공유용 추상화 이미지
│   │   ├── ShareCardRatio.tsx                # 공유 카드 (기여도)
│   │   ├── ShareCardBlurredLetter.tsx        # 공유 카드 (편지형)
│   │   └── ShareCardMetaphor.tsx             # 공유 카드 (은유형)
│   │
│   └── ui/                                    # 기본 UI 컴포넌트 (shadcn-ish)
│
├── lib/
│   ├── api/
│   │   └── client.ts                          # axios 인스턴스 + Bearer 인터셉터
│   ├── auth/
│   │   └── oauth.ts                           # OAuth provider redirect 헬퍼
│   ├── store/
│   │   ├── userStore.ts                       # Zustand + persist (again-spring-user)
│   │   └── sessionStore.ts                    # Zustand + persist (again-spring-session)
│   ├── constants/
│   │   ├── onboardingQuestions.ts             # 10문항 (상세: ../../shared/docs/policies/onboarding-mapping.md)
│   │   ├── communicationStyles.ts             # 6스타일 + 36조합
│   │   ├── mbtiMapping.ts                     # MBTI 16유형
│   │   ├── crisisResources.ts                 # 핫라인 카드 (상세: shared/docs/policies/crisis-detection.md)
│   │   ├── categories.ts                      # 카테고리 (상세: ../../shared/docs/policies/categories.md)
│   │   └── forbiddenWords.ts                  # 금지어 (상세: docs/policies/forbidden-words-lint.md)
│   ├── types/
│   │   ├── index.ts
│   │   ├── user.ts
│   │   ├── session.ts
│   │   └── category.ts
│   └── utils/
│       ├── keywordGuard.ts                    # 클라 사이드 검사
│       ├── styleCalculator.ts                 # 답변 → 스타일
│       ├── ratio.ts                           # 화해 기여도 표시 헬퍼
│       ├── needsMapDistance.ts
│       ├── describePlaceholder.ts
│       ├── guestNickname.ts
│       ├── cn.ts                              # clsx + tailwind-merge
│       └── index.ts
│
├── mocks/                                     # MSW
│   ├── browser.ts                             # setupWorker
│   ├── handlers/
│   │   ├── index.ts                           # 핸들러 통합
│   │   ├── user.ts                            # /api/users/*
│   │   ├── session.ts                         # /api/sessions/*
│   │   ├── chat.ts                            # /api/sessions/{id}/messages/*
│   │   ├── historyMessages.ts                 # /api/sessions/{id}/messages/history
│   │   └── mediation.ts                       # /api/sessions/{id}/report
│   └── fixtures/
│       └── mockReports.ts                     # 시나리오별 리포트
│
├── scripts/
│   └── check-forbidden-words.js               # npm run lint:words
│
├── public/
│   └── mockServiceWorker.js                   # MSW가 자동 생성 (gitignored)
│
├── design/                                    # 디자인 핸드오프 자산 (배포 미포함)
│   ├── handoff/                              # Claude Design 원본 (참조용)
│   ├── mockups/                              # 화면별 목업 폴더 (자세한 구조: docs/ui/design-handoff.md)
│   └── tokens/                               # 디자인 토큰 JSON
│
└── docs/                                      # 개발 문서
│   ├── README.md                              # ← 문서 인덱스 (여기서 시작)
│   ├── structure.md                           # 폴더 구조 (본 파일)
│   ├── architecture.md                        # 기술 스택 및 데이터 흐름
│   ├── testing.md                             # 테스트 전략
│   ├── ui/                                    # 디자인 및 목업 (자세한 내용)
│   │   ├── README.md
│   │   ├── design-handoff.md
│   │   └── mock-scenarios.md
│   └── policies/                              # FE 정책 구현 가이드
│       ├── README.md
│       └── forbidden-words-lint.md
```

## App Router 라우팅 정리

### 라우트 그룹 vs 일반 폴더

- `(auth)`, `(onboarding)`, `(dashboard)` — **라우트 그룹** (URL에 미반영, 레이아웃/조직화)
- `auth/`, `session/`, `privacy/`, `terms/` — 일반 폴더 (URL 반영)

### 동적 라우트

- `[provider]` — `/auth/callback/google`, `/auth/callback/kakao`
- `[token]` — `/auth/reset-password/abc123`, `/session/join/inv_xyz`
- `[id]` — `/session/result/ses_abc`

### 보호 정책

현재 라우트 보호는 페이지 레벨 (`useUserStore` 검사 후 redirect). 미들웨어 미사용. 향후 `middleware.ts`로 통합 검토 가능.

## 코드 위치 → 책임

| 작업 | 파일 위치 |
|---|---|
| 새 페이지 | `app/<path>/page.tsx` |
| 재사용 컴포넌트 | `components/<domain>/` |
| API 호출 | `lib/api/client.ts` (인터셉터 추가) + 각 페이지에서 axios 사용 |
| 클라이언트 상태 | `lib/store/<store>.ts` |
| 타입 | `lib/types/<domain>.ts` |
| 유틸 함수 | `lib/utils/<feature>.ts` |
| 상수 | `lib/constants/<feature>.ts` |
| MSW 핸들러 | `mocks/handlers/<domain>.ts` + `mocks/fixtures/` |
