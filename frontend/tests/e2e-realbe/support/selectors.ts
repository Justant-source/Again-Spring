/**
 * data-testid 컨벤션 및 안정적 셀렉터 모음.
 * 새 testid 추가 시 여기에 먼저 등재 후 컴포넌트에 박는다.
 *
 * 선호 우선순위:
 *   1. getByRole  (접근성 동시 검증)
 *   2. getByTestId (data-testid 박힌 경우)
 *   3. getByText  (한국어 리터럴 — i18n 위험, 최후 수단)
 *
 */

// ── 위기 모달 ──────────────────────────────────────────────────
export const CRISIS_MODAL = '[data-testid="crisis-modal"]'
export const CRISIS_MODAL_CLOSE = '[data-testid="crisis-modal-close"]'
// CrisisResourceModal은 role="dialog" 사용 가능
export const CRISIS_RESOURCE_DIALOG = '[role="dialog"]'
// 헤더 SOS 버튼 (aria-label 우선)
export const SOS_BUTTON_ROLE = { name: '위기 지원 연락처 보기' } as const

// ── 화해 기여도 법적 안내 박스 ──────────────────────────────────
// 주의: 컴포넌트에 CJK 오타(분析) 존재 → testid 사용 필수
export const RATIO_LEGAL_NOTICE = '[data-testid="ratio-legal-notice"]'

// ── 인증 ──────────────────────────────────────────────────────
export const EMAIL_INPUT_PLACEHOLDER = '이메일'
export const PASSWORD_INPUT_PLACEHOLDER = '비밀번호'
export const LOGIN_BUTTON = { name: '로그인' } as const
export const GUEST_START_BUTTON = { name: '시작하기' } as const

// ── V17 커뮤니티 (Phase 3에서 추가) ────────────────────────────
export const POST_BODY_INPUT = '[data-testid="post-body-input"]'
export const POST_COMPOSE_PREVIEW = '[data-testid="post-compose-preview"]'
export const VOTE_DISTRIBUTION = '[data-testid="vote-distribution"]'
export const JURY_DISTRIBUTION = '[data-testid="jury-distribution"]'
export const COMMENT_LIKE_BTN = '[data-testid="comment-like-btn"]'
export const COMMUNITY_LEGAL_NOTICE = '[data-testid="ratio-legal-notice"]'

// ── V18 커뮤니티 피드 (C3 광장형) ──────────────────────────────────
// Feed
export const FEED_POST_LIST = '[data-testid="feed-post-list"]'
export const FEED_SORT_LATEST = '[data-testid="feed-sort-latest"]'
export const FEED_SORT_RECOMMENDED = '[data-testid="feed-sort-recommended"]'
// Compose
export const COMPOSE_TITLE = '[data-testid="compose-title"]'
export const COMPOSE_BODY = '[data-testid="compose-body"]'
export const COMPOSE_CHAR_COUNT = '[data-testid="compose-char-count"]'
// Mode 선택
export const MODE_PUBLIC_CARD = '[data-testid="mode-public-card"]'
export const MODE_PRIVATE_CARD = '[data-testid="mode-private-card"]'
export const MODE_SUBMIT_BTN = '[data-testid="mode-submit-btn"]'
// Guest 모달
export const GUEST_NOTICE_CONTINUE = '[data-testid="guest-notice-continue"]'
// Story detail
export const STORY_AUTHOR_BOX = '[data-testid="story-author-box"]'
export const STORY_PARTNER_BOX = '[data-testid="story-partner-box"]'
export const VOTE_COMPLETE_BTN = '[data-testid="vote-complete-btn"]'
export const VOTE_STAMP = '[data-testid="vote-stamp"]'
export const NOTIFICATION_LIST = '[data-testid="notification-list"]'
// 댓글 및 UserChip
export const USER_CHIP = '[data-testid="user-chip"]'
export const GUEST_INFO_SHEET = '[data-testid="guest-info-sheet"]'
export const COMMENT_BAR_PLACEHOLDER = '댓글을 남겨주세요.'
export const COMMENT_COMPOSE_TEXTAREA = 'textarea'
export const COMMENT_SUBMIT_BTN = { name: '등록' } as const
