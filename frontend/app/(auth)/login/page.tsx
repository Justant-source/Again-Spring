'use client';

import { useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import Link from 'next/link';
import { PhoneFrame, PhoneHeader } from '@/components/shared/PhoneFrame';
import { useUserStore } from '@/lib/store/userStore';
import { api } from '@/lib/api/client';
import { oauthRedirect } from '@/lib/auth/oauth';

// open redirect 방지 — 내부 경로(/로 시작, // 또는 /\는 거부)만 허용
function safeRedirect(raw: string | null): string {
  if (!raw) return '/';
  if (!raw.startsWith('/')) return '/';
  if (raw.startsWith('//') || raw.startsWith('/\\')) return '/';
  return raw;
}

export default function LoginPage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const setUser = useUserStore((s) => s.setUser);
  const nextPath = safeRedirect(searchParams.get('redirect') || searchParams.get('next'));

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [errorCode, setErrorCode] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setErrorCode('');

    if (!email || !password) {
      setError('이메일과 비밀번호를 입력해주세요');
      return;
    }

    setLoading(true);
    try {
      const response = await api.post('/api/auth/login', {
        email,
        password,
      });
      const { user, token } = response.data;
      if (token?.accessToken) {
        localStorage.setItem('again-spring-token', token.accessToken);
      }
      setUser(user);
      router.push(nextPath);
    } catch (err: any) {
      setError(err.response?.data?.error?.message || '로그인에 실패했어요');
      setErrorCode(err.response?.data?.error?.code || '');
    } finally {
      setLoading(false);
    }
  };

  return (
    <PhoneFrame tone="L">
      <PhoneHeader
        title="로그인"
        onBack={() => router.replace('/')}
      />
      <div style={{ padding: '8px 28px 28px', flex: 1, display: 'flex', flexDirection: 'column' }}>
        <div className="letter-card" style={{ padding: '28px' }}>
          <div style={{ fontSize: 13, color: 'var(--L-sub)', marginBottom: 14 }}>
            다시 봄으로
          </div>
          <div className="serif" style={{ fontSize: 20, lineHeight: 1.6, marginBottom: 28 }}>
            돌아오셨네요
          </div>

          <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
            <div>
              <input
                type="email"
                placeholder="이메일"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                style={{
                  width: '100%',
                  borderBottom: '1px solid var(--L-border)',
                  background: 'transparent',
                  color: 'var(--L-ink)',
                  fontSize: 15,
                  padding: '8px 0',
                  outline: 'none',
                  fontFamily: 'var(--font-sans)',
                }}
                onFocus={(e) => (e.target.style.borderBottomColor = 'var(--L-ink)')}
                onBlur={(e) => (e.target.style.borderBottomColor = 'var(--L-border)')}
              />
            </div>

            <div>
              <input
                type="password"
                placeholder="비밀번호"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                style={{
                  width: '100%',
                  borderBottom: '1px solid var(--L-border)',
                  background: 'transparent',
                  color: 'var(--L-ink)',
                  fontSize: 15,
                  padding: '8px 0',
                  outline: 'none',
                  fontFamily: 'var(--font-sans)',
                }}
                onFocus={(e) => (e.target.style.borderBottomColor = 'var(--L-ink)')}
                onBlur={(e) => (e.target.style.borderBottomColor = 'var(--L-border)')}
              />
            </div>

            {error && (
              <div
                style={{
                  fontSize: 13, marginTop: 8,
                  padding: '10px 12px',
                  background: '#fff3f0',
                  border: '1px solid #f5c0b0',
                  borderRadius: 8,
                  color: '#8a2a10',
                  lineHeight: 1.6,
                }}
              >
                <div>{error}</div>
                {errorCode === 'EMAIL_NOT_REGISTERED' && (
                  <Link
                    href="/signup"
                    style={{ display: 'inline-block', marginTop: 8, fontSize: 12, color: 'var(--L-ink)', textDecoration: 'underline', fontWeight: 600 }}
                  >
                    회원가입 하러 가기 →
                  </Link>
                )}
                {errorCode === 'WRONG_PASSWORD' && (
                  <Link
                    href="/forgot-password"
                    style={{ display: 'inline-block', marginTop: 8, fontSize: 12, color: 'var(--L-ink)', textDecoration: 'underline', fontWeight: 600 }}
                  >
                    비밀번호 찾기 →
                  </Link>
                )}
                {errorCode === 'OAUTH_LOGIN_REQUIRED' && (
                  <div style={{ marginTop: 6, fontSize: 11, opacity: 0.85 }}>
                    아래 소셜 로그인 버튼을 사용해주세요.
                  </div>
                )}
              </div>
            )}

            <button
              type="submit"
              disabled={loading}
              className="btn-L"
              style={{ marginTop: 12 }}
            >
              {loading ? '로그인 중...' : '로그인'}
            </button>
          </form>
        </div>

        {/* 소셜 로그인 */}
        <div style={{ marginTop: 20 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 16 }}>
            <div style={{ flex: 1, height: 1, background: 'var(--L-border)' }} />
            <span style={{ fontSize: 11, color: 'var(--L-sub)', whiteSpace: 'nowrap' }}>또는 소셜 로그인</span>
            <div style={{ flex: 1, height: 1, background: 'var(--L-border)' }} />
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
            <button
              onClick={() => oauthRedirect('google', nextPath)}
              style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8, padding: '10px 0', border: '1px solid var(--L-border)', borderRadius: 8, background: 'white', fontSize: 13, cursor: 'pointer', color: '#333' }}
            >
              <svg width="18" height="18" viewBox="0 0 18 18" fill="none">
                <path d="M17.64 9.2c0-.637-.057-1.251-.164-1.84H9v3.481h4.844a4.14 4.14 0 0 1-1.796 2.716v2.259h2.908c1.702-1.567 2.684-3.875 2.684-6.615Z" fill="#4285F4" />
                <path d="M9 18c2.43 0 4.467-.806 5.956-2.18l-2.908-2.259c-.806.54-1.837.86-3.048.86-2.344 0-4.328-1.584-5.036-3.711H.957v2.332A8.997 8.997 0 0 0 9 18Z" fill="#34A853" />
                <path d="M3.964 10.71A5.41 5.41 0 0 1 3.682 9c0-.593.102-1.17.282-1.71V4.958H.957A8.996 8.996 0 0 0 0 9c0 1.452.348 2.827.957 4.042l3.007-2.332Z" fill="#FBBC05" />
                <path d="M9 3.58c1.321 0 2.508.454 3.44 1.345l2.582-2.58C13.463.891 11.426 0 9 0A8.997 8.997 0 0 0 .957 4.958L3.964 7.29C4.672 5.163 6.656 3.58 9 3.58Z" fill="#EA4335" />
              </svg>
              Google로 계속하기
            </button>
          </div>
        </div>

        <div style={{ marginTop: 16, textAlign: 'center', fontSize: 12, display: 'flex', gap: 16, justifyContent: 'center' }}>
          <Link href="/signup" style={{ color: 'var(--L-sub)', textDecoration: 'underline' }}>
            회원가입
          </Link>
          <span style={{ color: 'var(--L-border)' }}>·</span>
          <Link href="/forgot-password" style={{ color: 'var(--L-sub)', textDecoration: 'underline' }}>
            비밀번호 찾기
          </Link>
        </div>
      </div>
    </PhoneFrame>
  );
}
