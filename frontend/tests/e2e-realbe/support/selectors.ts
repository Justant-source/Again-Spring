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

// ── 인증 ──────────────────────────────────────────────────────
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
// Mode 선택
export const MODE_STEP_HEADING = '[data-testid="mode-step-heading"]'
export const MODE_PUBLIC_CARD = '[data-testid="mode-public-card"]'
export const MODE_PRIVATE_CARD = '[data-testid="mode-private-card"]'
export const MODE_SUBMIT_BTN = '[data-testid="mode-submit-btn"]'
// Guest 모달
export const GUEST_NOTICE_CONTINUE = '[data-testid="guest-notice-continue"]'
// UserChip
export const USER_CHIP = '[data-testid="user-chip"]'
export const GUEST_INFO_SHEET = '[data-testid="guest-info-sheet"]'
// 댓글
export const COMMENT_BAR_PLACEHOLDER = '댓글을 남겨주세요.'
export const COMMENT_COMPOSE_TEXTAREA = 'textarea'
export const COMMENT_SUBMIT_BTN = { name: '등록' } as const

// ── Invite 흐름 ──────────────────────────────────────────────────
export const INVITE = {
  linkGenBtn: '[data-testid="invite-link-gen-btn"]',     // 링크 생성 버튼
  arrivedResultBtn: '[data-testid="arrived-result-btn"]', // 답변 도착 후 결과 보기 버튼
} as const

// ── AI 배심원 (Jury) ──────────────────────────────────────────────
export const JURY = {
  section:         '[data-testid="jury-section"]',          // 전체 섹션 컨테이너
  pending:         '[data-testid="jury-pending"]',          // 대기 중 스피너 블록
  distributionBar: '[data-testid="jury-distribution-bar"]', // 공감 분포 바 래퍼
  summary:         '[data-testid="jury-summary"]',          // 요약 줄 ("N인 중 M인이...")
  jurorCard:       '[data-testid="juror-card"]',            // 배심원 카드 (복수)
  legalNotice:     '[data-testid="jury-legal-notice"]',     // 법적 고지
} as const
