'use client';

import { useEffect, useState } from 'react';
import { useRouter, useSearchParams, useParams } from 'next/navigation';
import { useUserStore } from '@/lib/store/userStore';
import { api } from '@/lib/api/client';
import { getRedirectUri } from '@/lib/auth/oauth';

export default function OAuthCallbackPage() {
  const router = useRouter();
  const params = useParams();
  const searchParams = useSearchParams();
  const setUser = useUserStore((s) => s.setUser);
  const [error, setError] = useState('');

  useEffect(() => {
    const provider = params.provider as string;
    const code = searchParams.get('code');

    if (!code) {
      setError('인증 코드를 받지 못했어요. 다시 시도해주세요.');
      return;
    }

    const redirectUri = getRedirectUri(provider as 'google' | 'kakao' | 'naver');

    api
      .post(`/api/auth/oauth2/${provider}`, { code, redirectUri })
      .then((res) => {
        const data = res.data;
        setUser({
          ...data.user,
          temperatureHistory: [],
        });
        if (data.token?.accessToken) {
          localStorage.setItem('again-spring-token', data.token.accessToken);
        }
        router.replace('/');
      })
      .catch((err) => {
        const msg = err.response?.data?.message ?? '소셜 로그인에 실패했어요';
        setError(msg);
      });
  }, []);

  if (error) {
    return (
      <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', height: '100dvh', gap: 16 }}>
        <p style={{ fontSize: 15, color: '#c0392b' }}>{error}</p>
        <button onClick={() => router.push('/login')} style={{ fontSize: 14, textDecoration: 'underline', background: 'none', border: 'none', cursor: 'pointer' }}>
          로그인 페이지로 돌아가기
        </button>
      </div>
    );
  }

  return (
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100dvh' }}>
      <p style={{ fontSize: 15, color: '#888' }}>로그인 처리 중...</p>
    </div>
  );
}
