import { describe, it, expect } from 'vitest';
import { authHref, decodeState, encodeState, safeRedirect } from '@/lib/auth/oauth';

describe('safeRedirect', () => {
  it('내부 경로만 허용한다', () => {
    expect(safeRedirect('/s/tok_abc')).toBe('/s/tok_abc');
    expect(safeRedirect('/community')).toBe('/community');
    expect(safeRedirect('/admin?tab=1')).toBe('/admin?tab=1');
  });

  it('open redirect를 차단한다', () => {
    expect(safeRedirect('https://evil.com')).toBe('/');
    expect(safeRedirect('//evil.com')).toBe('/');
    expect(safeRedirect('/\\evil.com')).toBe('/');
    expect(safeRedirect(null)).toBe('/');
    expect(safeRedirect('')).toBe('/');
  });

  it('custom fallback을 지원한다', () => {
    expect(safeRedirect('//evil', '')).toBe('');
    expect(safeRedirect(null, '')).toBe('');
  });
});

describe('authHref', () => {
  it('next가 홈이면 query를 붙이지 않는다', () => {
    expect(authHref('/login')).toBe('/login');
    expect(authHref('/signup', '/')).toBe('/signup');
  });

  it('안전한 next를 query로 붙인다', () => {
    expect(authHref('/login', '/s/tok_abc')).toBe('/login?next=%2Fs%2Ftok_abc');
    expect(authHref('/signup', '/s/tok_abc')).toBe('/signup?next=%2Fs%2Ftok_abc');
  });

  it('위험한 next는 query를 붙이지 않는다', () => {
    expect(authHref('/login', '//evil.com')).toBe('/login');
  });
});

describe('encodeState / decodeState', () => {
  it('초대 경로를 round-trip한다', () => {
    const next = '/s/tok_invite123';
    const encoded = encodeState(next);
    expect(encoded).toBeTruthy();
    expect(decodeState(encoded)).toBe(next);
  });

  it('open redirect 경로는 encode하지 않는다', () => {
    expect(encodeState('//evil.com')).toBe('');
    expect(encodeState('https://evil.com')).toBe('');
  });

  it('잘못된 state는 null을 반환한다', () => {
    expect(decodeState(null)).toBeNull();
    expect(decodeState('%%%')).toBeNull();
  });
});
