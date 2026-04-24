# 다시봄 · Again Spring

> **"다시 봄. 다시 바라봄."**
> 싸운 두 사람 사이에서 AI 중재자가 양쪽 이야기를 중립적으로 정리해, 관계 회복을 돕는 웹앱의 프론트엔드 프로토타입.

본 레포는 Mock API + Next.js 14 기반의 **프로토타입**입니다. 실제 LLM 연동은 다음 단계로 분리돼 있어요.

---

## 빠르게 실행하기

```bash
npm install      # 이미 돼 있다면 생략
npm run dev      # localhost:3000
```

- MSW(Mock Service Worker)가 dev 모드에서 자동으로 시작돼 `/api/*` 요청을 가로챕니다.
- 프로덕션 빌드는 `npm run build` → `npm start`.
- 금지어 스캐너: `npm run lint:words`.

## 주요 플로우

1. `/` — 랜딩 (Tone L 편지지)
2. `/signup` · `/login` · `/guest` — 인증 진입 (Phase 3)
3. `/onboarding` — 10문항 경향성 테스트 → 6스타일 결과 (Phase 4)
4. `/session/new → /session/category → /session/describe → /session/invite → /session/wait` — A 쪽 세션 시작 (Phase 5)
5. `/session/join/[token]` — B 쪽 참여 (Phase 6)
6. `/session/mediation` — 6턴 중재 UI (편지지/말풍선/카드 3가지 뷰, Phase 7)
7. `/session/result/[id]` — 결과 리포트 (욕구 차이 지도 + 관계 온도 + 화해 기여도, Phase 8)
8. `/session/mediation/solo` — Solo 모드 3턴 (Phase 9)
9. `/history`, `/profile` — 대시보드 (Phase 10)
10. `/terms`, `/privacy` — 법적 안내 (Phase 12)

## Mock 시나리오 전환

`/session/result/[id]?scenario=factual|difference|mixed|solo|four_horsemen` 로 리포트 시나리오를 강제할 수 있어요. Mock API는 기본적으로 `sessionId` 해시로 시나리오를 돌려가며 반환합니다.

## 폴더 구조

```
Again-Spring/
├── app/                    # Next.js App Router 페이지
│   ├── (auth)/             # signup · login · guest
│   ├── (onboarding)/       # 10문항 온보딩
│   ├── (session)/          # 세션 시작·중재·결과
│   ├── (dashboard)/        # 이력·프로필
│   ├── terms/, privacy/    # 법적 페이지
│   ├── globals.css         # Tone L/P/Q 디자인 토큰 + 공통 CSS
│   └── layout.tsx          # MSWProvider 포함 루트
├── components/
│   ├── shared/             # PhoneFrame, PhoneHeader, Motif, Logo, Dashes 등
│   ├── onboarding/         # LikertQuestion
│   ├── mediation/          # MediatorMessage, TurnInput, ProgressBar 등
│   └── result/             # NeedsMap, Temperature, ContributionRatio 등
├── lib/
│   ├── types/              # TypeScript 타입
│   ├── constants/          # CATEGORIES, ONBOARDING_QUESTIONS, styles, forbidden/crisis
│   ├── utils/              # keywordGuard, styleCalculator, ratio, cn
│   ├── store/              # Zustand (session, user)
│   └── api/                # axios client
├── mocks/                  # MSW 핸들러 + fixtures
│   ├── handlers/           # session · mediation · user
│   └── fixtures/           # mockReports (5개), mockMediations (6턴)
├── design/
│   ├── handoff/            # Claude Design 원본 HTML/JSX (참조용, 배포 미포함)
│   ├── mockups/            # 화면별 확정 목업 폴더
│   ├── tokens/             # design-tokens.json
│   └── README.md           # 목업 반영 현황
├── docs/                   # 작업지시서 전체
├── public/                 # mockServiceWorker.js 등
└── scripts/                # 금지어 스캐너
```

## 디자인 원칙

1. **앵커링 방지** — 상대방 원문은 양쪽 입력 완료 전까지 공개 금지
2. **판결 금지** — 과실비율·판사·유죄 등 법률 용어는 `FORBIDDEN_WORDS.md` 기준으로 차단
3. **차이 존중** — "다름"을 "잘못"으로 환원하지 않기
4. **긍정 프레이밍** — 모든 라벨을 긍정적 표현으로
5. **프라이버시** — 갈등 내용을 시각화에 노출하지 않기
6. **안전 우선** — 위기 키워드 감지 시 즉시 전문 기관 연결 (1366/1393/132)

## 테마 토큰

세 톤을 `:root` CSS 변수로 제공합니다. 자세한 값은 `app/globals.css`.

- **Tone L (편지지)** — 랜딩·온보딩·세션·중재
- **Tone P (파스텔)** — 결과 리포트·카톡 공유·온도
- **Tone Q (조용함)** — 이용약관·개인정보

관계 유형에 따라 `--P-a`/`--P-b` 변수가 `RelationshipColorSync`로 실시간 변경돼 참가자 색을 살짝 바꿉니다.

## 다음 단계

- [ ] 실제 LLM 연동 (Anthropic Claude API)
- [ ] Spring Boot + MongoDB + MariaDB + Neo4j 백엔드
- [ ] Gottman 지식 베이스 RAG
- [ ] Capacitor 빌드 (iOS / Android)
- [ ] 결제 모듈
- [ ] 상담사 제휴 연결

## 목업 통합 가이드

각 Phase 구현 전 `design/mockups/XX-XXX/` 폴더를 확인하고, 있으면 목업 기반 구현, 없으면 기본 디자인 + `⚠️ MOCKUP PENDING` 주석을 답니다. 자세한 절차는 `docs/MOCKUP_INTEGRATION.md`.

목업을 추가한 뒤 재작업을 원하시면:

```
design/mockups/09-result/ 에 목업을 추가했어. 해당 화면을 목업에 맞춰 재작업해줘.
```

---

**프로토타입 완성도 체크리스트는 `docs/WORK_ORDER.md`의 Phase 14 참조.**
