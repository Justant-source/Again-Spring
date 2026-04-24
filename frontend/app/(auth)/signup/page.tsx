// ✅ MOCKUP APPLIED — source: design/handoff/tone-L-screens.jsx (LandingScreen)
'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';
import { PhoneFrame, PhoneHeader } from '@/components/shared/PhoneFrame';
import { useUserStore } from '@/lib/store/userStore';
import { api } from '@/lib/api/client';

export default function SignupPage() {
  const router = useRouter();
  const setUser = useUserStore((s) => s.setUser);

  const [nickname, setNickname] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [passwordConfirm, setPasswordConfirm] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const validateForm = () => {
    if (nickname.length < 3 || nickname.length > 12) {
      setError('이름은 3자 이상 12자 이하여야 해요');
      return false;
    }
    if (!email) {
      setError('이메일을 입력해주세요');
      return false;
    }
    if (password.length < 6) {
      setError('비밀번호는 6자 이상이어야 해요');
      return false;
    }
    if (password !== passwordConfirm) {
      setError('비밀번호가 일치하지 않아요');
      return false;
    }
    return true;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    if (!validateForm()) return;

    setLoading(true);
    try {
      const response = await api.post('/api/auth/signup', {
        nickname,
        email,
        password,
      });
      setUser(response.data);
      router.push('/onboarding/intro');
    } catch (err: any) {
      setError(err.response?.data?.message || '회원가입에 실패했어요');
    } finally {
      setLoading(false);
    }
  };

  return (
    <PhoneFrame tone="L">
      <PhoneHeader
        title="회원가입"
        onBack={() => router.back()}
      />
      <div style={{ padding: '8px 28px 28px', flex: 1, display: 'flex', flexDirection: 'column' }}>
        <div className="letter-card" style={{ padding: '28px' }}>
          <div style={{ fontSize: 13, color: 'var(--L-sub)', marginBottom: 14 }}>
            다시 봄을
          </div>
          <div className="serif" style={{ fontSize: 20, lineHeight: 1.6, marginBottom: 28 }}>
            시작할 이름을<br />알려주세요
          </div>

          <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
            <div>
              <input
                type="text"
                placeholder="닉네임"
                value={nickname}
                onChange={(e) => setNickname(e.target.value)}
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
              <div style={{ fontSize: 11, color: 'var(--L-sub)', marginTop: 4 }}>
                3~12자
              </div>
            </div>

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
              <div style={{ fontSize: 11, color: 'var(--L-sub)', marginTop: 4 }}>
                6자 이상
              </div>
            </div>

            <div>
              <input
                type="password"
                placeholder="비밀번호 확인"
                value={passwordConfirm}
                onChange={(e) => setPasswordConfirm(e.target.value)}
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
              <div style={{ fontSize: 13, color: 'var(--L-point)', marginTop: 8 }}>
                {error}
              </div>
            )}

            <button
              type="submit"
              disabled={loading}
              className="btn-L"
              style={{ marginTop: 12 }}
            >
              {loading ? '로그인 중...' : '가입하기'}
            </button>
          </form>
        </div>

        <div style={{ marginTop: 24, textAlign: 'center', fontSize: 12, color: 'var(--L-sub)' }}>
          <Link href="/login" style={{ color: 'var(--L-ink)', textDecoration: 'underline' }}>
            이미 계정이 있어요
          </Link>
        </div>
      </div>
    </PhoneFrame>
  );
}
