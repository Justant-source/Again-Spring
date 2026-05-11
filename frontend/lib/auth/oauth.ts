'use client';

function resolveBaseUrl(): string {
  if (process.env.NEXT_PUBLIC_APP_URL) return process.env.NEXT_PUBLIC_APP_URL;
  if (typeof window !== 'undefined' && window.location.origin) return window.location.origin;
  return 'https://againspring.net';
}

// 현재 다시봄에서 지원하는 OAuth provider는 Google 뿐.
export type Provider = 'google';

function redirectUriFor(provider: Provider): string {
  return `${resolveBaseUrl()}/auth/callback/${provider}`;
}

const GOOGLE_CLIENT_ID = process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID ?? '';

export function oauthRedirect(provider: Provider) {
  const redirectUri = redirectUriFor(provider);
  if (provider === 'google') {
    const url = `https://accounts.google.com/o/oauth2/v2/auth?client_id=${GOOGLE_CLIENT_ID}&redirect_uri=${encodeURIComponent(redirectUri)}&response_type=code&scope=openid%20profile%20email`;
    window.location.href = url;
    return;
  }
  // 미지원 provider 안전 처리
  console.warn('Unsupported OAuth provider:', provider);
}

export function getRedirectUri(provider: Provider): string {
  return redirectUriFor(provider);
}
