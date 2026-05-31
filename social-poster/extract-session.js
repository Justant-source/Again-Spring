/**
 * 브라우저 콘솔에서 실행하는 세션 추출 스크립트
 *
 * 사용법:
 *   1. X (https://x.com) 또는 Instagram (https://www.instagram.com) 에 로그인
 *   2. F12 → Console 탭
 *   3. 아래 코드를 붙여넣고 Enter
 *   4. 출력된 JSON을 admin UI의 "브라우저 세션 시드" 란에 붙여넣기
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
