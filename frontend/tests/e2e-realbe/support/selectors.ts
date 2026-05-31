/**
 * data-testid 컨벤션 및 안정적 셀렉터 모음.
 * 새 testid 추가 시 여기에 먼저 등재 후 컴포넌트에 박는다.
 *
 * 선호 우선순위:
 *   1. getByRole  (접근성 동시 검증)
 *   2. getByTestId (data-testid 박힌 경우)
 *   3. getByText  (한국어 리터럴 — i18n 위험, 최후 수단)
 *
 * 단계 3에서 추가한 data-testid:
 *   - crisis-modal            CrisisModal.tsx 루트 div
 *   - crisis-modal-close      CrisisModal 닫기 버튼
 *   - ratio-legal-notice      ContributionRatio.tsx 법적 안내 박스
 *   - blurred-bubble          PartnerPanel.tsx BlurredBubble 래퍼
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

// ── Duo 격리 ───────────────────────────────────────────────────
export const BLURRED_BUBBLE = '[data-testid="blurred-bubble"]'

// ── 채팅 입력 ─────────────────────────────────────────────────
export const CHAT_INPUT_PLACEHOLDER = '편한 말로 적어주세요'
export const SEND_BUTTON = { name: '전송' } as const

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

// ── V17 3자 대화 (Phase 6에서 추가) ──────────────────────────────
export const THREE_WAY_MEDIATOR_MSG = '[data-testid="three-way-mediator-msg"]'
export const THREE_WAY_MEDIATOR_LABEL = '[data-testid="three-way-mediator-label"]'
