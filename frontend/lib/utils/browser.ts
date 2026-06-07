export function isInAppBrowser(): boolean {
  if (typeof window === 'undefined') return false;
  const ua = window.navigator.userAgent;
  return /KAKAOTALK|kakaotalk|KAKAO/i.test(ua) ||
    /NAVER|Instagram|FBAN|FBAV|Twitter|Line\//.test(ua);
}

export function isAndroid(): boolean {
  if (typeof window === 'undefined') return false;
  return /android/i.test(window.navigator.userAgent);
}

export function isIOS(): boolean {
  if (typeof window === 'undefined') return false;
  return /iPhone|iPad|iPod/i.test(window.navigator.userAgent);
}

/** Android 인앱 브라우저에서 현재 URL을 외부 브라우저(Chrome 등)로 여는 intent:// URL 생성 */
export function intentOpenUrl(url: string): string {
  const encoded = url.replace(/^https?:\/\//, '');
  return `intent://${encoded}#Intent;scheme=https;action=android.intent.action.VIEW;category=android.intent.category.BROWSABLE;end;`;
}
