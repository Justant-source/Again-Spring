# 컴포넌트 매핑 (`docs/design/components.md`)

> 화면↔React 컴포넌트 대응 + 각 컴포넌트의 UX 체크리스트 링크  
> 톤 시스템: [system.md](./system.md) | UX 원칙: [ux/principles.md](../ux/principles.md) | HAX 체크리스트: [ux/hax-checklist.md](../ux/hax-checklist.md)

---

## 라우트 페이지

| 화면 | 경로 | 주요 컴포넌트 |
|---|---|---|
| 커뮤니티 피드 | `app/community/page.tsx` | FeedCard, BrandBar |
| 사연 상세 | `app/community/[id]/page.tsx` | JurorCard, JurorPicker, VoteBar, SideStory |
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

## 광장 핵심 컴포넌트 (`components/community/c3/`)

### FeedCard

**역할**: 피드 목록의 개별 사연 카드  
**표시 항목**: 사연 제목, 카테고리, 공감 비율 미리보기, 배심원 분석 여부  
**UX 규칙**:
- AI 배심원 분석 결과가 있으면 "AI 분석" 레이블 표시
- 클릭 → 사연 상세 (`/community/[id]`)

---

### JurorCard

**역할**: 배심원 개인 의견 카드  
**표시 항목**: 페르소나명, 한 줄 소개, 편향(A/B/중립), 의견 요약  
**UX 규칙**:
- "AI 배심원" 레이블 필수
- A편향=연초록, B편향=연붉은, 중립=연회색 배경
- 클릭 시 전체 의견 펼침

---

### JurorPicker

**역할**: 배심원 9인 선택·탐색 UI  
**UX 규칙**: 분석 대기 중(generating) 상태 표시

---

### VoteBar

**역할**: 커뮤니티 투표 비율 바  
**표시 항목**: A측(초록) %, B측(붉은) %, 총 투표 수  
**UX 규칙**:
- 실제 집계값 표시 (임의값·추정값 금지)
- 투표 완료 후 재투표 금지
- 비율은 실시간 갱신

---

### CommentBar

**역할**: 댓글 목록 인라인 무한스크롤 표시  
**UX 규칙**: 무한스크롤 (Intersection Observer), 삭제 시 is_deleted=true 표시

---

### CommentComposeSheet

**역할**: 댓글 작성 바텀시트  
**UX 규칙**: 로그인 미확인 시 로그인 유도, 로그인 확인 시 즉시 입력 포커스

---

### CommunityComment

**역할**: 개별 댓글 컴포넌트 (작성자 닉네임, 내용, 시간, 좋아요)

---

### UserChip

**역할**: 사용자 닉네임 + 역할 표시 칩 (게스트/회원/관리자 구분)

---

### BrandBar

**역할**: 브랜드 로고 + 슬로건 배너

---

### SideStory

**역할**: 상대방 입장 요약 사이드 패널 (상세 페이지)

---

## 공유 컴포넌트 (`components/shared/`)

### CrisisResourceModal

**역할**: 위기 자원 핫라인 모달  
**절대 불변**: ESC·바깥 클릭으로 닫히지 않음. 닫기 버튼 단일 경로.  
**표시 시점**: 관리자 crisisFlag 설정 게시글 진입 시, 또는 SOS 버튼 클릭 시

---

## SVG 아이콘 컴포넌트 — `components/icons/`

| 컴포넌트 | 역할 |
|---|---|
| `DasibomLogo` | 다시봄 새싹 로고 |
| `SafeHaven` | 보호·우산 아이콘 |
| `Phone` | 전화 자원 안내 |
| `CrisisResources` | 위기 자원 메뉴 진입 |
| `IconCheck` | 완료·성공 체크 |
| `StatusDot` | 상태 컬러 점 |

자세한 카탈로그·사용법: [icons.md](./icons.md)

---

*변경 이력: 2026-06-03 — 광장형(커뮤니티 + AI 배심원) 기준으로 전면 재작성. 구 세션/채팅/결과 컴포넌트 매핑 제거.*
