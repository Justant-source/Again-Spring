// NOTE: Instagram UI 변경 시 이 파일만 수정하세요 (publish-instagram.js와 분리된 이유)
// Instagram 웹 UI는 안티봇 탐지가 강하므로 셀렉터가 자주 바뀔 수 있습니다.

module.exports = {
  // Login page (2025 UI: name="email" / name="pass"; submit via Enter on password field)
  LOGIN_USERNAME_INPUT: 'input[name="email"], input[name="username"]',
  LOGIN_PASSWORD_INPUT: 'input[name="pass"], input[name="password"]',
  LOGIN_SUBMIT_BUTTON: 'input[type="submit"], button[type="submit"]',
  LOGIN_SUBMIT_KEY: 'Enter',

  // 2FA / TOTP code
  TOTP_INPUT: 'input[name="verificationCode"], input[autocomplete="one-time-code"]',
  TOTP_SUBMIT: 'button:has-text("확인"), button:has-text("Confirm"), button:has-text("다음")',

  // Challenge (SMS/email verification that we can't auto-pass)
  CHALLENGE_INDICATOR: 'h2:has-text("인증"), h2:has-text("Verify"), form input[name="email"], form input[name="phoneNumber"]',

  // New post creation flow
  // The "+" or "new post" button in the nav
  NEW_POST_NAV_BUTTON: 'svg[aria-label="새 게시물"], svg[aria-label="New post"], [aria-label="새로운 게시물 만들기"]',

  // Or try the "Create" in the menu
  NEW_POST_CREATE_LINK: 'a[href="/create/select/"]',

  // File input for image upload
  FILE_INPUT: 'input[type="file"][accept]',

  // Step navigation buttons
  NEXT_BUTTON: 'button:has-text("다음"), button:has-text("Next")',
  SHARE_BUTTON: 'button:has-text("공유"), button:has-text("Share")',

  // Caption textarea
  CAPTION_INPUT: 'div[aria-label="문구를 입력하세요..."], div[aria-label="Write a caption..."], div[contenteditable="true"][role="textbox"]',

  // Post shared confirmation
  POST_SHARED_CONFIRM: 'span:has-text("게시물이 공유"), span:has-text("Your post has been shared")',
};
