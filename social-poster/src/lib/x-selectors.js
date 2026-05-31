/**
 * X (Twitter) UI selectors
 *
 * NOTE: X UI 변경 시 이 파일만 수정하세요 (publish-x.js와 분리된 이유)
 * X의 UI는 자주 변경되므로, 셀렉터만 별도 파일로 관리합니다.
 */

module.exports = {
  // ===== Login flow =====
  // X new UI (2025): single-page login with username_or_email + password
  LOGIN_USERNAME_INPUT: 'input[name="username_or_email"], input[autocomplete="username"]',
  LOGIN_NEXT_BUTTON:
    '[data-testid="LoginForm_Login_Button"], button[role="button"]:has-text("다음"), button:has-text("Next")',
  LOGIN_PASSWORD_INPUT: 'input[name="password"], input[type="password"]',
  LOGIN_SUBMIT_BUTTON:
    '[data-testid="LoginForm_Login_Button"], button:has-text("로그인"), button:has-text("Log in")',

  // ===== 2FA / TOTP =====
  TOTP_INPUT: 'input[data-testid="ocfEnterTextTextInput"], input[inputmode="numeric"]',
  TOTP_SUBMIT:
    'button[data-testid="ocfEnterTextNextButton"], button:has-text("확인"), button:has-text("Next")',

  // ===== Challenge detection (unknown/email/phone verification prompts) =====
  CHALLENGE_HEADING:
    '[data-testid="challenge"], h1:has-text("인증"), h1:has-text("Verify"), h1:has-text("확인해")',

  // ===== Compose =====
  // X UI는 자주 바뀌므로 여러 셀렉터를 우선순위 순으로 나열
  COMPOSE_TWEET_BUTTON:
    '[data-testid="SideNav_NewTweet_Button"], a[href="/compose/tweet"], [aria-label="게시하기"], [aria-label="Post"], [aria-label="Tweet"], [data-testid="FloatingActionButton_Tweet_Button"]',
  // 미디어(이미지) 첨부 파일 input
  MEDIA_INPUT: 'input[data-testid="fileInput"], input[type="file"][accept*="image"]',
  TWEET_TEXT_AREA_0: '[data-testid="tweetTextarea_0"]',
  TWEET_TEXT_AREA_N: (n) => `[data-testid="tweetTextarea_${n}"]`,
  ADD_TWEET_BUTTON: '[data-testid="addButton"]',
  TWEET_SUBMIT_BUTTON:
    '[data-testid="tweetButtonInline"], [data-testid="tweetButton"]',

  // ===== After posting — to capture permalink =====
  FIRST_TWEET_LINK: (handle) => `a[href*="/${handle}/status/"]`,
};
