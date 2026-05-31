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
  // 좌측 네비게이션의 "새로운 게시물" 버튼 (2025 UI: svg aria-label="새로운 게시물")
  // ⚠️ "새 게시물"(X) 아님 — 실제 라벨은 "새로운 게시물"
  NEW_POST_NAV_BUTTON: 'svg[aria-label="새로운 게시물"], svg[aria-label="New post"], svg[aria-label="새 게시물"], [aria-label="새로운 게시물 만들기"]',

  // ⚠️ /create/select/ 직접 URL 은 "페이지 사용 불가"로 죽음 — 사용 금지.
  //    반드시 위 NEW_POST_NAV_BUTTON 클릭으로 모달을 띄울 것.

  // File input for image upload (create 모달 내부, accept 속성 없을 수도 있음)
  FILE_INPUT: 'input[type="file"][accept], input[type="file"]',

  // Step navigation buttons
  NEXT_BUTTON: 'button:has-text("다음"), button:has-text("Next")',
  SHARE_BUTTON: 'button:has-text("공유"), button:has-text("Share")',

  // Caption textarea
  CAPTION_INPUT: 'div[aria-label="문구를 입력하세요..."], div[aria-label="Write a caption..."], div[contenteditable="true"][role="textbox"]',

  // Post shared confirmation
  POST_SHARED_CONFIRM: 'span:has-text("게시물이 공유"), span:has-text("Your post has been shared")',
};
