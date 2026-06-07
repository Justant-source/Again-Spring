import { describe, it, expect, afterEach, vi } from 'vitest';
import { safeUUID } from '@/lib/utils/uuid';

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;

describe('safeUUID', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it('crypto.randomUUID가 있으면 그대로 사용한다 (secure context)', () => {
    const spy = vi.spyOn(crypto, 'randomUUID');
    const id = safeUUID();
    expect(spy).toHaveBeenCalled();
    expect(id).toMatch(UUID_RE);
  });

  // 회귀: 카카오톡 인앱이 http://로 열면 isSecureContext=false →
  // crypto.randomUUID가 undefined가 되어 "사연을 불러올 수 없습니다"가 발생하던 버그.
  // getRandomValues 폴백으로 유효한 v4 UUID를 생성해야 한다.
  it('crypto.randomUUID가 없어도(비보안 컨텍스트) 유효한 v4 UUID를 만든다', () => {
    vi.stubGlobal('crypto', {
      // randomUUID 의도적으로 제거 — HTTP insecure context 재현
      getRandomValues: (arr: Uint8Array) => {
        for (let i = 0; i < arr.length; i++) arr[i] = (i * 37 + 11) & 0xff;
        return arr;
      },
    });
    expect(() => safeUUID()).not.toThrow();
    const id = safeUUID();
    expect(id).toMatch(UUID_RE);
  });

  it('crypto 자체가 없어도 Math.random 폴백으로 동작한다', () => {
    vi.stubGlobal('crypto', undefined);
    expect(() => safeUUID()).not.toThrow();
    expect(safeUUID()).toMatch(UUID_RE);
  });

  it('연속 호출 시 서로 다른 값을 반환한다', () => {
    expect(safeUUID()).not.toBe(safeUUID());
  });
});
