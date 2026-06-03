const DEVICE_ID_KEY = 'again-spring-device-id';

export function getOrCreateDeviceId(): string {
  if (typeof window === 'undefined') return '';
  let id = localStorage.getItem(DEVICE_ID_KEY);
  if (!id) {
    id = crypto.randomUUID();
    localStorage.setItem(DEVICE_ID_KEY, id);
  }
  return id;
}

export function deviceToGuestNickname(deviceId: string): string {
  let hash = 0;
  for (let i = 0; i < deviceId.length; i++) {
    hash = ((hash << 5) - hash) + deviceId.charCodeAt(i);
    hash |= 0;
  }
  const num = (Math.abs(hash) % 9000) + 1000;
  return `게스트 ${num}`;
}
