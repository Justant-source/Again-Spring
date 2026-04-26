'use client';

function resolveBaseUrl(): string {
  if (process.env.NEXT_PUBLIC_APP_URL) return process.env.NEXT_PUBLIC_APP_URL;
  if (typeof window !== 'undefined' && window.location.origin) return window.location.origin;
  return 'https://againspring.net';
}

type Provider = 'google' | 'kakao' | 'naver';

function redirectUriFor(provider: Provider): string {
  return `${resolveBaseUrl()}/auth/callback/${provider}`;
}

const GOOGLE_CLIENT_ID = process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID ?? '';
const KAKAO_CLIENT_ID = process.env.NEXT_PUBLIC_KAKAO_CLIENT_ID ?? '';
const NAVER_CLIENT_ID = process.env.NEXT_PUBLIC_NAVER_CLIENT_ID ?? '';

export function oauthRedirect(provider: Provider) {
  const redirectUri = redirectUriFor(provider);
  let url = '';

  if (provider === 'google') {
    url = `https://accounts.google.com/o/oauth2/v2/auth?client_id=${GOOGLE_CLIENT_ID}&redirect_uri=${encodeURIComponent(redirectUri)}&response_type=code&scope=openid%20profile%20email`;
  } else if (provider === 'kakao') {
    url = `https://kauth.kakao.com/oauth/authorize?client_id=${KAKAO_CLIENT_ID}&redirect_uri=${encodeURIComponent(redirectUri)}&response_type=code`;
  } else if (provider === 'naver') {
    const state = Math.random().toString(36).substring(2, 10);
    url = `https://nid.naver.com/oauth2.0/authorize?client_id=${NAVER_CLIENT_ID}&redirect_uri=${encodeURIComponent(redirectUri)}&response_type=code&state=${state}`;
  }

  window.location.href = url;
}

export function getRedirectUri(provider: Provider): string {
  return redirectUriFor(provider);
}
