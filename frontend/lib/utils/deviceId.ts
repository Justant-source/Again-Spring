import { safeUUID } from './uuid';

const DEVICE_ID_KEY = 'again-spring-device-id';

export function getOrCreateDeviceId(): string {
  if (typeof window === 'undefined') return '';
  try {
    let id = localStorage.getItem(DEVICE_ID_KEY);
    if (!id) {
      id = safeUUID();
      try {
        localStorage.setItem(DEVICE_ID_KEY, id);
      } catch { /* 카카오톡 인앱 등 localStorage 제한 환경 — 에페메럴 ID 사용 */ }
    }
    return id;
  } catch {
    // localStorage 자체가 차단된 환경 — 매번 새 에페메럴 ID
    return safeUUID();
  }
}
