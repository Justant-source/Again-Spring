# 다시봄 — Frontend

> Next.js 14 (App Router) 기반 갈등 커뮤니티 + AI 배심원 플랫폼 프론트엔드.  
> 사용자가 사연(갈등 게시글)을 올리면 AI 배심원 9인이 공감 비율을 분석하고, 커뮤니티 투표/댓글로 의견을 모읍니다.

---

## 기술 스택

| 항목 | 버전 |
|---|---|
| Next.js (App Router) | 14 |
| TypeScript | 5+ |
| Tailwind CSS | 3+ |
| Zustand | — |
| MSW (Mock Service Worker) | — |
| Vitest | — |
| Playwright | — |

---

## 빠른 시작

```bash
cd /home/justant/Data/Again-Spring/frontend
npm install
npm run dev    # localhost:3000
```

> MSW는 로컬 개발 시 자동 활성화됩니다.

---

## 디렉토리 구조

```
frontend/
├── app/                          # Next.js App Router 페이지
│   ├── (admin)/admin/            # 관리자 대시보드 (community, marketing)
│   ├── (auth)/                   # 인증 (login, signup, guest, forgot-password, reset-password)
│   ├── auth/callback/[provider]/ # OAuth 콜백
│   ├── (dashboard)/profile/      # 사용자 프로필
│   ├── community/                # 광장 피드 + 사연 상세/작성
│   │   ├── [id]/{comments,invite,read}/
│   │   └── new/
│   ├── notifications/
│   ├── s/[token]/                # 초대 토큰 진입
│   └── {privacy,terms}/
├── components/
│   ├── community/c3/             # 광장 핵심 컴포넌트
│   │   └── FeedCard, JurorCard, JurorPicker, VoteBar, CommentBar, 
│   │       CommentComposeSheet, CommunityComment, UserChip, BrandBar, SideStory
│   ├── admin/, auth/, feedback/, icons/, legal/, profile/, shared/, ui/
├── lib/
│   ├── api/                      # API 클라이언트 (community, user 등)
│   ├── constants/                # userPermissions, forbiddenWords 등
│   └── store/                    # uiStore (Zustand)
├── mocks/
│   └── handlers/                 # MSW 핸들러 (community, notifications, user)
├── tests/
│   ├── e2e/                      # Playwright a11y
│   ├── e2e-realbe/               # Playwright 실서버 e2e
│   │   ├── flows/                # 01-auth, 02-permissions, 03-email-verification, 04-community-plaza
│   │   └── invariants/           # community-legal-notice
│   └── unit/                     # Vitest 유닛 테스트
└── docs/                         # FE 특화 문서
```

---

## 문서 진입점

| 영역 | 문서 |
|---|---|
| 패키지 구조 | [`docs/structure.md`](docs/structure.md) |
| 아키텍처·데이터 흐름 | [`docs/architecture.md`](docs/architecture.md) |
| 테스트 정책 | [`docs/testing.md`](docs/testing.md) |
| **UX 원칙 (권위본)** | [`docs/ux/principles.md`](docs/ux/principles.md) |
| HAX 컴포넌트 체크리스트 | [`docs/ux/hax-checklist.md`](docs/ux/hax-checklist.md) |
| 디자인 시스템 | [`docs/design/README.md`](docs/design/README.md) |
| 금지어 린트 | [`docs/policies/forbidden-words-lint.md`](docs/policies/forbidden-words-lint.md) |

---

## 주요 스크립트

```bash
npm run dev          # 개발 서버 (localhost:3000)
npm run build        # 프로덕션 빌드
npm run lint:words   # 금지어 하드코딩 검사
npm run lint:emoji   # 이모지 금지 검사
npm run test         # Vitest 유닛 테스트
npm run test:e2e:realbe  # 실서버 e2e (Playwright)
```

---

## 핵심 UX 원칙

> 권위본: [`docs/ux/principles.md`](docs/ux/principles.md)

- **AI 신뢰성 최우선**: 배심원·요약은 AI임을 명확히 표시, 사용자 글과 시각 구분
- **작성자=초록, 상대방=붉은** — 앱 전체 일관 유지
- **판결/처방 표현 금지** (AI 출력만) — 대체: "공감", "관점", "작성자/상대방"
- **사용자 입력에 금지어 필터 미적용** — 사용자가 쓴 텍스트의 책임은 사용자에게 있음
- **위기 모달**: ESC·바깥클릭 차단 (명시적 버튼으로만 닫힘)

---

## 신규 기능 추가 시 안전성 체크

모든 새 화면·입력·공유 기능 PR은 다음 4개 질문에 답해야 합니다:

1. **Abuser**: 가해자가 이 기능을 무기로 쓸 수 있는가?
2. **Survivor**: 학대 상황의 사용자에게 새로운 위험이 생기는가?
3. **Roadblock**: 악용을 막거나 마찰을 어디에 두는가?
4. **Exit**: 이 화면에서 1탭으로 빠져나갈 수 있는가?

---

## PR 병합 전 체크리스트

- [ ] `npm run lint:words` 통과
- [ ] 변경된 컴포넌트의 `docs/ux/hax-checklist.md` 항목 확인
- [ ] `npm run build` 성공
- [ ] 해당하면 `data-testid` 변경 + `tests/e2e-realbe/support/selectors.ts` 동기화
- [ ] 색상 일관성 (초록=A, 붉은=B, 회색=중립)
- [ ] 모바일 반응형 확인

---

**마지막 업데이트**: 2026-06-03
