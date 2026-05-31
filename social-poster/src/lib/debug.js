/**
 * debug.js — 발행 실패 시점 진단 캡처
 *
 * src/ 디렉토리가 호스트에 마운트돼 있으므로 (docker-compose.dev.yml),
 * /app/src/debug/ 에 .png 를 저장하면 호스트에서 바로 확인 가능.
 *
 * ⚠️ nodemon 은 js,mjs,cjs,json 을 watch 하므로 .json 을 src/ 하위에 쓰면
 *    발행 도중 컨테이너가 재시작되어 요청이 끊긴다. 따라서 .png 만 저장하고
 *    URL/title/본문텍스트는 console.log 로 출력한다 (docker logs 로 확인).
 */

const fs = require('fs');
const path = require('path');

const DEBUG_DIR = '/app/src/debug';

async function captureFailure(page, label) {
  try {
    if (!fs.existsSync(DEBUG_DIR)) fs.mkdirSync(DEBUG_DIR, { recursive: true });
    const ts = Date.now();
    const pngPath = path.join(DEBUG_DIR, `${label}-${ts}.png`);

    let url = '';
    try { url = page.url(); } catch (e) { /* ignore */ }

    await page.screenshot({ path: pngPath, fullPage: false }).catch(() => {});

    let bodyText = '';
    try {
      bodyText = await page.evaluate(
        () => (document.body ? document.body.innerText.slice(0, 1200).replace(/\s+/g, ' ') : '')
      );
    } catch (e) { /* ignore */ }

    let title = '';
    try { title = await page.title(); } catch (e) { /* ignore */ }

    console.log(`[DEBUG] ===== ${label} =====`);
    console.log(`[DEBUG] png=${pngPath}`);
    console.log(`[DEBUG] url=${url}`);
    console.log(`[DEBUG] title=${title}`);
    console.log(`[DEBUG] bodyText="${bodyText}"`);
    console.log(`[DEBUG] ====================`);
    return pngPath;
  } catch (e) {
    console.warn('[DEBUG] capture failed:', e.message);
    return null;
  }
}

/**
 * Playwright 에러 메시지를 화면 표시용 간결한 한 줄로 축약.
 * (timeout 재시도 로그 등 수백 줄 → 핵심 1줄 + 분류 힌트)
 */
function shortError(err) {
  const msg = (err && err.message ? err.message : String(err)) || 'unknown error';
  const firstLine = msg.split('\n')[0].trim();
  let hint = '';
  if (/Timeout .*exceeded/i.test(msg)) {
    if (/intercepts pointer events/i.test(msg)) hint = ' (요소가 다른 레이어에 가려져 클릭 불가)';
    else if (/element is not enabled/i.test(msg)) hint = ' (버튼 비활성 — 입력 누락 가능)';
    else if (/waiting for/i.test(msg)) hint = ' (요소를 찾지 못함/표시 안 됨)';
  }
  return (firstLine + hint).slice(0, 280);
}

module.exports = { captureFailure, shortError, DEBUG_DIR };
