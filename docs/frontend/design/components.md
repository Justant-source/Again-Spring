# 컴포넌트 & 인터랙션 (`docs/design/components.md`)

> 디자인 시스템 토큰·철학: [system.md](./system.md) | UX 원칙: [../ux/principles.md](../ux/principles.md) | HAX 체크리스트: [../ux/hax-checklist.md](../ux/hax-checklist.md)

---

## 핵심 인터랙션 규칙 (8가지)

1. **인라인 투표(사연 상세).** 진영 박스 탭 = 그 진영 선택(짙은 `_dk` 테두리 2.5px + "· 선택됨"), 하단 비율 막대가 부드럽게 갱신. **"투표 완료하기" 버튼으로 확정.** 언제든 다른 박스 탭으로 변경 가능. 박스 안의 **"더 보기 ›"는 투표와 분리**(stopPropagation → 전문 읽기로 이동).
2. **투표 완료 표식.** 우상단 기표 도장 SVG + "투표함". 별도 결과 페이지로 이동하지 않는다.
3. **게시.** `/community/new`에서 원문 게시 후 투표·댓글이 핵심 상호작용이다.
4. **상대 초대 자동 합류.** "먼저 올리기" 시 단독 공개 후, 상대 답변 도착하면 오른쪽(세이지)에 자동 추가.
5. **미응답 단독 게시.** 대기 화면에서 "지금 혼자 올리기" 가능.
6. **댓글 진영 표시.** 작성자/상대방 댓글은 `* 닉네임` + 진영색(피치/세이지). 일반 유저는 검정 닉네임, 별표 없음. BEST 댓글은 상단 고정.
7. **게스트 제약.** 글·투표·댓글 O / 초대·수정·이력·알림 X → 가입 유도 바텀시트(`C3_GuestSheet`).
8. **전역 탭바.** `광장 / 알림(뱃지) / ＋글쓰기(중앙 FAB) / 내 활동`. **입력·결과·세션·인증 화면에서는 숨겨** 몰입을 유지한다.

---

## 컴포넌트 인벤토리

### 공용 컴포넌트 (`ux-shared.jsx`)

| 컴포넌트 | 역할 |
|---|---|
| `UxSteps` | 입력 진행 표시 — 늘어나는 dash |
| `UxTop` | 상단 바(닫기/뒤로 + 진행) |
| `UxCard` | 큰 선택 카드 |
| `UxChip` | 칩(카테고리·필터) |
| `UxBtn` | 메인 버튼 (`tone`·`ghost`·`disabled`) |
| `UxFoot` | 하단 고정 버튼 영역(페이드) |
| `UxVote` | 결과지용 양면 투표 게이지 |
| `UxStat` | 통계 표시(값 + 라벨) |

### 광장형 전용 컴포넌트 (`ux-c3.jsx`)

| 컴포넌트 | 역할 |
|---|---|
| `BrandBar` / `UserChip` | 브랜드 + 우상단 사용자 칩(게스트 점선/회원 채움) |
| `BackRow` | ‹ + 라벨 + 우측 슬롯, 일관 뒤로가기 |
| `TabBar` / `TabIcon` | 전역 하단 탭바 + 뱃지 |
| `VoteBar` | 피치:세이지 양면 비율 막대(합 100) |
| `FeedCard` | 피드 카드 (카테고리·닉·시간 / 제목 / 본문 2줄 / 투표·댓글·조회 / 하단 색 비율) |
| `SideStory` | 진영 사연 박스(`clamp`·`selected`·`onSelect`·`onMore`) |
| `Comment` / `CommentBar` | 댓글·대댓글(작성자/상대방 `*`·BEST) + 하단 입력 |

---

## 라우트 → React 컴포넌트 매핑

| 화면 | 경로 | 주요 컴포넌트 |
|---|---|---|
| 커뮤니티 피드 | `app/community/page.tsx` | FeedCard, BrandBar |
| 사연 상세 | `app/community/[id]/page.tsx` | VoteBar, SideStory, CommunityComment |
| 댓글 | `app/community/[id]/comments/page.tsx` | CommentBar, CommunityComment, CommentComposeSheet |
| 사연 작성 | `app/community/new/page.tsx` | — |
| 파트너 초대 | `app/community/[id]/invite/page.tsx` | — |
| 읽기 전용 | `app/community/[id]/read/page.tsx` | — |
| 초대 토큰 진입 | `app/s/[token]/page.tsx` | — |
| 알림 | `app/notifications/page.tsx` | — |
| 프로필 | `app/(dashboard)/profile/page.tsx` | — |
| 인증 | `app/(auth)/login`, `signup`, `guest` 등 | — |
| 관리자 | `app/(admin)/admin/` | AdminCommunity, marketing/* |

---

## 28화면 인덱스 (C3_* 컴포넌트)

> 시각 정본: [`다시봄 광장형 UX (standalone).html`](../../design/다시봄%20광장형%20UX%20(standalone).html)
> 컴포넌트명은 `ux-c3.jsx` export명과 1:1.

| # | 컴포넌트 | 계열 | 톤 |
|---|---|---|---|
| 0 | `C3_Landing` | 작성자 | L |
| 1 | `C3_Feed` | 작성자 | L |
| 2 | `C3_Compose` | 작성자 | L |
| 3 | `C3_Mode` / `C3_ModeGuest` | 작성자 | L |
| 4 | `C3_Analyzing` | 작성자 | L |
| 5 | `C3_ResultSolo` | 작성자 | P |
| 6a | `C3_Invite` | 상대 초대 | L |
| 6b | `C3_PublishChoice` | 상대 초대 | L |
| 6c | `C3_Closing` (투표 기간) | 상대 초대 | L |
| 6d | `C3_Waiting` | 상대 초대 | L |
| 6e | `C3_PartnerArrived` | 상대 초대 | P |
| 6f | `C3_PartnerWrite` | 상대 초대 | L |
| 7 | `C3_ResultPair` | 상대 초대 | P |
| 8 | `C3_StoryDetail` (인라인 투표) | 관람·투표·댓글 | L |
| 8-1 | `C3_StoryRead` (전문) | 관람·투표·댓글 | L |
| 9 | `C3_Comments` | 관람·투표·댓글 | L |
| 9-1 | `C3_CommentCompose` | 관람·투표·댓글 | L |
| R | `C3_Report` | 관람·투표·댓글 | L |
| L1 | `C3_Login` | 인증 | L |
| L2 | `C3_Signup` | 인증 | L |
| G1 | `C3_GuestNotice` | 인증 | L |
| G2 | `C3_GuestConvert` | 인증 | L |
| G3 | `C3_GuestSheet` | 인증 | L |
| M1 | `C3_MyPage` (내 사연) | 관리 | L |
| M1b | `C3_MyPageVoted` / `C3_MyPageSaved` / `C3_MyPageInfo` | 관리 | L |
| M2 | `C3_Notifications` | 관리 | L |
| M4 | `C3_Closed` | 관리 | P |

---

## 핵심 컴포넌트 UX 규칙

### FeedCard
- **역할**: 피드 목록의 개별 사연 카드
- **하단 색 비율 막대**: 작성자(피치) / 상대방(세이지) — 숫자 없이 색 비율만
- 클릭 → 사연 상세 (`/community/[id]`)

### SideStory
- **역할**: 진영 사연 박스 (clamp·selected·onSelect·onMore)
- **선택 상태**: `_dk` 색 2.5px 테두리 + "· 선택됨"
- **"더 보기 ›"**: stopPropagation — 투표와 독립된 전문 읽기

### VoteBar
- **역할**: 피치:세이지 양면 비율 막대 (합 100)
- big 모드: `%` 표기 / 미니(카드 하단): 숫자 없이 색 비율만
- 실제 집계값 표시 (임의값·추정값 금지)

### CommunityComment
- **진영 댓글**: 작성자 닉네임 앞 `*` + 피치색 / 상대방 `*` + 세이지색
- 일반 사용자: 검정 닉네임, `*` 없음
- BEST 댓글: 상단 고정

### CrisisResourceModal
- **절대 불변**: ESC·바깥 클릭으로 닫히지 않음. 닫기 버튼 단일 경로.
- 표시 시점: crisisFlag 게시글 진입 시 또는 SOS 버튼 클릭 시

---

## SVG 아이콘 컴포넌트 (`components/icons/`)

자세한 카탈로그·사용법: [icons.md](./icons.md)

*최종 갱신: 2026-06-03*
