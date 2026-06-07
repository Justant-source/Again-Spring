/**
 * 보안 컨텍스트(HTTPS)와 비보안 컨텍스트(HTTP) 모두에서 동작하는 UUID v4 생성기.
 *
 * crypto.randomUUID()는 secure context에서만 제공된다. 카카오톡 인앱 브라우저가
 * 공유 링크를 http://로 열면 window.isSecureContext === false → crypto.randomUUID가
 * undefined가 되어 "crypto.randomUUID is not a function" TypeError가 발생한다.
 * (반면 crypto.getRandomValues는 비보안 컨텍스트에서도 사용 가능하다.)
 */
export function safeUUID(): string {
  try {
    if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
      return crypto.randomUUID();
    }
    if (typeof crypto !== 'undefined' && typeof crypto.getRandomValues === 'function') {
      const b = crypto.getRandomValues(new Uint8Array(16));
      b[6] = (b[6] & 0x0f) | 0x40; // version 4
      b[8] = (b[8] & 0x3f) | 0x80; // variant 10
      const h = Array.from(b, (x) => x.toString(16).padStart(2, '0')).join('');
      return `${h.slice(0, 8)}-${h.slice(8, 12)}-${h.slice(12, 16)}-${h.slice(16, 20)}-${h.slice(20)}`;
    }
  } catch { /* fall through to Math.random 폴백 */ }
  // 최후 폴백 — 암호학적으로 안전하지 않으나 device id 식별 용도로는 충분
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16);
  });
}
