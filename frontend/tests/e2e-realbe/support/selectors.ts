/**
 * data-testid 컨벤션 및 안정적 셀렉터 모음.
 * 새 testid 추가 시 여기에 먼저 등재 후 컴포넌트에 박는다.
 *
 * 선호 우선순위:
 *   1. getByRole  (접근성 동시 검증)
 *   2. getByTestId (data-testid 박힌 경우)
 *   3. getByText  (한국어 리터럴 — i18n 위험, 최후 수단)
 *
 * ✋ 죽은 항목(MODE_*, JURY.*) 제거 완료 (2026-06-07 재편 기준).
 *    컴포넌트에 testid가 없으면 getByRole을 우선 사용한다.
 */

// ── 인증 ──────────────────────────────────────────────────────────
export const EMAIL_INPUT_PLACEHOLDER = '이메일'
export const PASSWORD_INPUT_PLACEHOLDER = '비밀번호'
export const LOGIN_BUTTON = { name: '로그인' } as const
export const GUEST_START_BUTTON = { name: '시작하기' } as const

// ── V18 커뮤니티 피드 (C3 광장형) ──────────────────────────────────
// Feed
export const FEED_POST_LIST = '[data-testid="feed-post-list"]'
export const FEED_SORT_LATEST = '[data-testid="feed-sort-latest"]'
export const FEED_SORT_RECOMMENDED = '[data-testid="feed-sort-recommended"]'
// Compose
export const COMPOSE_TITLE = '[data-testid="compose-title"]'
export const COMPOSE_BODY = '[data-testid="compose-body"]'
export const COMPOSE_CHAR_COUNT = '[data-testid="compose-char-count"]'
// Guest 모달
export const GUEST_NOTICE_CONTINUE = '[data-testid="guest-notice-continue"]'
// UserChip
export const USER_CHIP = '[data-testid="user-chip"]'
export const GUEST_INFO_SHEET = '[data-testid="guest-info-sheet"]'
// 하단 네비게이션 (BottomNav)
export const NAV_PLAZA = '[data-testid="nav-plaza"]'
export const NAV_NOTIFICATIONS = '[data-testid="nav-notifications"]'
export const NAV_ACTIVITY = '[data-testid="nav-activity"]'
// 투표 (SideStory 우측 끝 버튼 · C3StoryDetail 완료 배지)
export const STORY_VOTE_BTN = (side: 'g' | 'r') => `[data-testid="story-vote-btn-${side}"]`
export const VOTE_COMPLETE_BADGE = '[data-testid="vote-complete-badge"]'
// 댓글
export const COMMENT_BAR_PLACEHOLDER = '댓글을 남겨주세요.'
export const COMMENT_COMPOSE_TEXTAREA = 'textarea'
export const COMMENT_SUBMIT_BTN = { name: '등록' } as const
// 댓글 ⋯ 메뉴 (본인=수정/삭제, 타인=신고)
export const COMMENT_MENU_TOGGLE = '[data-testid="comment-menu-toggle"]'
export const COMMENT_MENU_EDIT = '[data-testid="comment-menu-edit"]'
export const COMMENT_MENU_DELETE = '[data-testid="comment-menu-delete"]'
export const COMMENT_MENU_REPORT = '[data-testid="comment-menu-report"]'

// ── Invite 흐름 ──────────────────────────────────────────────────
// 실제 컴포넌트 testid와 일치하는 항목 (2026-06-07 교정)
export const INVITE = {
  partnerBtn: '[data-testid="invite-partner-btn"]',  // 작성자 뷰 초대 버튼
  sheet:      '[data-testid="invite-sheet"]',         // InviteSheet 바텀시트
  urlText:    '[data-testid="invite-url-text"]',      // 생성된 초대 URL 텍스트
} as const

// ── 랜딩 페이지 ──────────────────────────────────────────────────
// app/page.tsx에 추가된 data-testid (2026-06-07 e2e 재편 시 부착)
export const LANDING = {
  latestPill: '[data-testid="landing-latest-pill"]', // 방금 올라온 사연 알약 버튼
  todayCard:  '[data-testid="landing-today-card"]',  // 오늘의 사연 카드
  cta:        '[data-testid="landing-cta"]',          // "다시봄 광장" CTA 버튼
} as const
