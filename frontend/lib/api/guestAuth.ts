'use client';

import axios from 'axios';
import { useUserStore } from '@/lib/store/userStore';
import { getOrCreateDeviceId } from '@/lib/utils/deviceId';

/**
 * 게스트 인증 단일 진입점.
 *
 * 게스트 인증 복구 로직이 useGuestInit·AuthBootstrap·axios 인터셉터에 흩어져
 * 서로 레이스를 일으키던 문제를 해소하기 위해, "유효한 게스트 토큰을 보장한다"는
 * 책임을 이 모듈 하나로 모은다.
 *
 * 핵심 규칙:
 * - 게스트는 익명이므로 토큰이 없거나 만료/거부되면 "조용히" 재발급한다 (로그아웃·리다이렉트 금지).
 * - deviceId 기준으로 재발급 → 동일 기기는 동일 게스트 계정·닉네임·투표내역 유지.
 * - 동시 호출은 단일 in-flight 프라미스로 합쳐 중복 발급을 막는다.
 */

const TOKEN_KEY = 'again-spring-token';

interface GuestAuthResponse {
  user: { id: string; nickname: string; isGuest: boolean };
  token: { accessToken: string; expiresIn: number };
}

/** JWT exp 클레임이 지났으면 true (파싱 실패 시 false = 일단 유효 취급) */
export function isJwtExpired(token: string): boolean {
  try {
    const base64 = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    const payload = JSON.parse(atob(base64));
    return typeof payload.exp === 'number' && payload.exp * 1000 < Date.now();
  } catch {
    return false;
  }
}

/** JWT의 type 클레임이 "guest"이면 true (게스트 토큰 식별) */
export function isGuestToken(token: string): boolean {
  try {
    const base64 = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    const payload = JSON.parse(atob(base64));
    return payload.type === 'guest';
  } catch {
    return false;
  }
}

/** localStorage에 만료되지 않은 토큰이 있으면 반환, 없으면 null */
export function getValidToken(): string | null {
  if (typeof window === 'undefined') return null;
  try {
    const token = localStorage.getItem(TOKEN_KEY);
    if (!token) return null;
    return isJwtExpired(token) ? null : token;
  } catch {
    return null;
  }
}

// 동시 발급 방지용 in-flight 프라미스 (중복 /api/auth/guest 호출 차단)
let inflight: Promise<string> | null = null;

async function requestGuestToken(): Promise<string> {
  const deviceId = getOrCreateDeviceId();
  // 인터셉터가 걸린 `api` 대신 순수 axios 사용 — 발급 중 401/403이 재귀 인터셉트되지 않도록.
  // nickname은 보내지 않음 → BE가 유니크 검증된 닉네임을 생성/유지.
  const res = await axios.post<GuestAuthResponse>(
    '/api/auth/guest',
    { deviceId },
    { headers: { 'Content-Type': 'application/json' } },
  );

  const { user, token } = res.data;
  if (token?.accessToken) {
    try {
      localStorage.setItem(TOKEN_KEY, token.accessToken);
    } catch { /* 카카오톡 인앱 등 localStorage 제한 환경 — 세션 내에서만 유효 */ }
  }
  if (user) {
    // BE가 돌려준 닉네임으로 갱신 → 구버전 "게스트 4179" 등 stale 닉네임도 자동 치유.
    useUserStore.getState().setUser({
      id: user.id,
      nickname: user.nickname,
      isGuest: true,
      createdAt: new Date().toISOString(),
    });
  }
  return token.accessToken;
}

/**
 * 유효한 게스트 토큰을 보장한다.
 *
 * @param force true면 기존 토큰이 있어도 무조건 재발급 (서버가 토큰을 거부한 401/403 복구용).
 * @returns 유효한 accessToken
 */
export function ensureGuestToken(force = false): Promise<string> {
  if (!force) {
    const valid = getValidToken();
    if (valid) return Promise.resolve(valid);
  }
  // 이미 발급 진행 중이면 그 프라미스를 공유
  if (inflight) return inflight;
  inflight = requestGuestToken().finally(() => {
    inflight = null;
  });
  return inflight;
}
