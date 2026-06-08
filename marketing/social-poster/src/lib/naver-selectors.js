/**
 * Naver Blog 셀렉터 모음.
 * 셀렉터가 깨지면 이 파일만 수정하세요.
 */
module.exports = {
  // 로그인 페이지
  LOGIN_ID_INPUT: 'input#id',
  LOGIN_PW_INPUT: 'input#pw',
  LOGIN_SUBMIT_BUTTON: 'button#log\.login, button.btn_login, button[type="submit"].btn_login',

  // 로그인 상태 확인 (메인)
  LOGGED_IN_INDICATOR: '.gnb_name, .MyView-module__user_info, #account, .gnb_my_section',
  LOGGED_OUT_INDICATOR: 'a.link_login, .gnb_login_area',

  // 블로그 글쓰기 에디터 (Smart Editor 3)
  POST_TITLE_INPUT: 'input.se-title-input, input[placeholder*="제목"], input.input_title, textarea.se-title-input',
  EDITOR_IFRAME: 'iframe.se-viewer, iframe[title*="에디터"], iframe[title*="editor"]',
  EDITOR_CONTENT_AREA: 'body[contenteditable="true"], .se-component-content .se-text-paragraph',
  EDITOR_HTML_BTN: 'button[data-log="html"], .se-btn-html, button[title*="HTML"]',

  // 발행 버튼
  PUBLISH_BTN: 'button.publish, button.se-publish, button[data-log="publish"], button.btn_publish, button.b_publish',
  PUBLISH_CONFIRM_BTN: 'button.btn_confirm, button.confirm, button[type="submit"].btn_ok',

  // 발행 후 URL 패턴: blog.naver.com/{blogId}/{postNo}
  POST_URL_PATTERN: /blog\.naver\.com\/[^/]+\/\d+/,
};
