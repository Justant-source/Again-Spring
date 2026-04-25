'use client';

const BASE_URL = process.env.NEXT_PUBLIC_APP_URL ?? 'http://100.99.33.127';

const REDIRECT_URIS = {
  google: `${BASE_URL}/auth/callback/google`,
  kakao: `${BASE_URL}/auth/callback/kakao`,
  naver: `${BASE_URL}/auth/callback/naver`,
} as const;

type Provider = keyof typeof REDIRECT_URIS;

const GOOGLE_CLIENT_ID = process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID ?? '';
const KAKAO_CLIENT_ID = process.env.NEXT_PUBLIC_KAKAO_CLIENT_ID ?? '';
const NAVER_CLIENT_ID = process.env.NEXT_PUBLIC_NAVER_CLIENT_ID ?? '';

export function oauthRedirect(provider: Provider) {
  const redirectUri = REDIRECT_URIS[provider];
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
  return REDIRECT_URIS[provider];
}
