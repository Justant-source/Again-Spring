/**
 * ⚠️ 사용 금지 (DEPRECATED) — 이 방식은 발행 실패를 유발합니다.
 *
 * document.cookie 는 httpOnly 쿠키를 읽지 못합니다. 그런데 X 의 `auth_token`,
 * Instagram 의 `sessionid` (실제 로그인 토큰)은 모두 httpOnly 입니다.
 * 따라서 이 스크립트로 추출한 세션에는 로그인 토큰이 빠져 있어, 발행 시
 * compose/create 화면 대신 로그인 화면으로 리다이렉트되어 실패합니다.
 *
 * ✅ 대신 사용하세요:
 *   1) Cookie-Editor 확장 프로그램 → 로그인 상태에서 Export → "Export as JSON"
 *      → admin UI "브라우저 세션 시드" 란에 붙여넣기 (httpOnly 포함 캡처됨)
 *   2) 또는 로컬에서: node src/seed-cli.js --platform x|instagram (헤드풀 로그인)
 *
 * 아래 코드는 누락된 httpOnly 인증 쿠키를 감지하면 경고를 출력합니다.
 */

(function () {
  function getCookies() {
    return document.cookie.split(';').map(c => {
      const [name, ...rest] = c.trim().split('=');
      return {
        name: name.trim(),
        value: rest.join('='),
        domain: location.hostname.startsWith('www.')
          ? location.hostname.slice(4)
          : location.hostname,
        path: '/',
        expires: -1,
        httpOnly: false,
        secure: location.protocol === 'https:',
        sameSite: 'Lax',
      };
    }).filter(c => c.name);
  }

  function getLocalStorage() {
    const items = [];
    for (let i = 0; i < localStorage.length; i++) {
      const key = localStorage.key(i);
      items.push({ name: key, value: localStorage.getItem(key) });
    }
    return items;
  }

  const storageState = {
    cookies: getCookies(),
    origins: [{
      origin: location.origin,
      localStorage: getLocalStorage(),
    }],
  };

  const json = JSON.stringify(storageState, null, 2);

  // httpOnly 인증 쿠키 누락 감지 — 누락 시 이 세션은 로그인 안 된 상태로 저장됨
  const host = location.hostname.replace(/^www\./, '');
  const requiredCookie = host.includes('x.com') || host.includes('twitter.com')
    ? 'auth_token'
    : host.includes('instagram.com') ? 'sessionid' : null;
  const names = storageState.cookies.map(c => c.name);
  if (requiredCookie && !names.includes(requiredCookie)) {
    console.error(
      `\n🚨🚨🚨 이 세션은 사용할 수 없습니다 🚨🚨🚨\n` +
      `필수 로그인 쿠키 '${requiredCookie}' 가 없습니다 (httpOnly 라서 document.cookie 로 못 읽음).\n` +
      `이대로 등록하면 발행이 실패합니다.\n\n` +
      `✅ Cookie-Editor 확장으로 'Export as JSON' 하거나, seed-cli.js 를 사용하세요.\n`
    );
  }

  // 클립보드 복사 시도
  if (navigator.clipboard && navigator.clipboard.writeText) {
    navigator.clipboard.writeText(json)
      .then(() => console.log('✅ 클립보드에 복사됨! admin UI에 붙여넣으세요.'))
      .catch(() => {});
  }

  console.log('=== STORAGE STATE (admin UI에 붙여넣으세요) ===');
  console.log(json);
  console.log('=== END ===');

  return storageState;
})();
