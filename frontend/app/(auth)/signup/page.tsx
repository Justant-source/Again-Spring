// ✅ MOCKUP APPLIED — source: design/handoff/tone-L-screens.jsx (LandingScreen)
'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';
import { PhoneFrame, PhoneHeader } from '@/components/shared/PhoneFrame';
import { useUserStore } from '@/lib/store/userStore';
import { api } from '@/lib/api/client';
import { oauthRedirect } from '@/lib/auth/oauth';

export default function SignupPage() {
  const router = useRouter();
  const setUser = useUserStore((s) => s.setUser);

  const [nickname, setNickname] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [passwordConfirm, setPasswordConfirm] = useState('');
  const [verificationCode, setVerificationCode] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [sendingCode, setSendingCode] = useState(false);
  const [codeSent, setCodeSent] = useState(false);
  const [sentToEmail, setSentToEmail] = useState('');

  const inputStyle = {
    width: '100%',
    borderBottom: '1px solid var(--L-border)',
    background: 'transparent',
    color: 'var(--L-ink)',
    fontSize: 15,
    padding: '8px 0',
    outline: 'none',
    fontFamily: 'var(--font-sans)',
  };

  const handleSendCode = async () => {
    if (!email) {
      setError('이메일을 입력해주세요');
      return;
    }
    setError('');
    setSendingCode(true);
    try {
      await api.post('/api/auth/send-verification', { email });
      setCodeSent(true);
      setSentToEmail(email);
    } catch (err: any) {
      setError(err.response?.data?.message || '인증코드 발송에 실패했어요');
    } finally {
      setSendingCode(false);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    if (nickname.length < 3 || nickname.length > 12) {
      setError('이름은 3자 이상 12자 이하여야 해요');
      return;
    }
    if (!email) {
      setError('이메일을 입력해주세요');
      return;
    }
    if (!codeSent) {
      setError('이메일 인증코드를 먼저 전송해주세요');
      return;
    }
    if (email !== sentToEmail) {
      setError('이메일이 변경되었어요. 새 주소로 인증코드를 다시 전송해주세요');
      return;
    }
    if (verificationCode.length !== 6) {
      setError('인증코드 6자리를 입력해주세요');
      return;
    }
    if (password.length < 8) {
      setError('비밀번호는 8자 이상이어야 해요');
      return;
    }
    if (password !== passwordConfirm) {
      setError('비밀번호가 일치하지 않아요');
      return;
    }

    setLoading(true);
    try {
      const response = await api.post('/api/auth/signup', {
        nickname,
        email,
        password,
        verificationCode,
      });
      setUser({ ...response.data.user, temperatureHistory: [] });
      router.push('/');
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
                style={inputStyle}
                onFocus={(e) => (e.target.style.borderBottomColor = 'var(--L-ink)')}
                onBlur={(e) => (e.target.style.borderBottomColor = 'var(--L-border)')}
              />
              <div style={{ fontSize: 11, color: 'var(--L-sub)', marginTop: 4 }}>3~12자</div>
            </div>

            <div>
              <div style={{ display: 'flex', alignItems: 'flex-end', gap: 8 }}>
                <input
                  type="email"
                  placeholder="이메일"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  style={{ ...inputStyle, flex: 1 }}
                  onFocus={(e) => (e.target.style.borderBottomColor = 'var(--L-ink)')}
                  onBlur={(e) => (e.target.style.borderBottomColor = 'var(--L-border)')}
                />
                <button
                  type="button"
                  onClick={handleSendCode}
                  disabled={sendingCode || !email}
                  style={{
                    padding: '6px 12px',
                    background: 'var(--L-ink)',
                    color: 'var(--L-bg)',
                    border: 'none',
                    borderRadius: 6,
                    fontSize: 12,
                    cursor: 'pointer',
                    whiteSpace: 'nowrap',
                    opacity: sendingCode || !email ? 0.5 : 1,
                  }}
                >
                  {sendingCode ? '전송 중...' : codeSent ? '재전송' : '인증코드 전송'}
                </button>
              </div>
              {codeSent && (
                <div style={{ fontSize: 11, color: '#4caf50', marginTop: 4 }}>
                  {sentToEmail}로 인증코드를 발송했어요
                </div>
              )}
            </div>

            {codeSent && (
              <div>
                <input
                  type="text"
                  placeholder="인증코드 6자리"
                  value={verificationCode}
                  onChange={(e) => setVerificationCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
                  maxLength={6}
                  inputMode="numeric"
                  style={{ ...inputStyle, letterSpacing: '0.3em', fontSize: 18, textAlign: 'center' }}
                  onFocus={(e) => (e.target.style.borderBottomColor = 'var(--L-ink)')}
                  onBlur={(e) => (e.target.style.borderBottomColor = 'var(--L-border)')}
                />
              </div>
            )}

            <div>
              <input
                type="password"
                placeholder="비밀번호"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                style={inputStyle}
                onFocus={(e) => (e.target.style.borderBottomColor = 'var(--L-ink)')}
                onBlur={(e) => (e.target.style.borderBottomColor = 'var(--L-border)')}
              />
              <div style={{ fontSize: 11, color: 'var(--L-sub)', marginTop: 4 }}>8자 이상</div>
            </div>

            <div>
              <input
                type="password"
                placeholder="비밀번호 확인"
                value={passwordConfirm}
                onChange={(e) => setPasswordConfirm(e.target.value)}
                style={inputStyle}
                onFocus={(e) => (e.target.style.borderBottomColor = 'var(--L-ink)')}
                onBlur={(e) => (e.target.style.borderBottomColor = 'var(--L-border)')}
              />
            </div>

            {error && (
              <div style={{ fontSize: 13, color: 'var(--L-point)' }}>{error}</div>
            )}

            <button
              type="submit"
              disabled={loading}
              className="btn-L"
              style={{ marginTop: 12 }}
            >
              {loading ? '가입 중...' : '가입하기'}
            </button>
          </form>
        </div>

        {/* 소셜 회원가입 */}
        <div style={{ marginTop: 20 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 16 }}>
            <div style={{ flex: 1, height: 1, background: 'var(--L-border)' }} />
            <span style={{ fontSize: 11, color: 'var(--L-sub)', whiteSpace: 'nowrap' }}>또는 소셜 계정으로</span>
            <div style={{ flex: 1, height: 1, background: 'var(--L-border)' }} />
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
            <button
              onClick={() => oauthRedirect('google')}
              style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8, padding: '10px 0', border: '1px solid var(--L-border)', borderRadius: 8, background: 'white', fontSize: 13, cursor: 'pointer', color: '#333' }}
            >
              <svg width="18" height="18" viewBox="0 0 18 18" fill="none">
                <path d="M17.64 9.2c0-.637-.057-1.251-.164-1.84H9v3.481h4.844a4.14 4.14 0 0 1-1.796 2.716v2.259h2.908c1.702-1.567 2.684-3.875 2.684-6.615Z" fill="#4285F4"/>
                <path d="M9 18c2.43 0 4.467-.806 5.956-2.18l-2.908-2.259c-.806.54-1.837.86-3.048.86-2.344 0-4.328-1.584-5.036-3.711H.957v2.332A8.997 8.997 0 0 0 9 18Z" fill="#34A853"/>
                <path d="M3.964 10.71A5.41 5.41 0 0 1 3.682 9c0-.593.102-1.17.282-1.71V4.958H.957A8.996 8.996 0 0 0 0 9c0 1.452.348 2.827.957 4.042l3.007-2.332Z" fill="#FBBC05"/>
                <path d="M9 3.58c1.321 0 2.508.454 3.44 1.345l2.582-2.58C13.463.891 11.426 0 9 0A8.997 8.997 0 0 0 .957 4.958L3.964 7.29C4.672 5.163 6.656 3.58 9 3.58Z" fill="#EA4335"/>
              </svg>
              Google로 가입하기
            </button>
          </div>
        </div>

        <div style={{ marginTop: 20, textAlign: 'center', fontSize: 12, color: 'var(--L-sub)' }}>
          <Link href="/login" style={{ color: 'var(--L-ink)', textDecoration: 'underline' }}>
            이미 계정이 있어요
          </Link>
        </div>
      </div>
    </PhoneFrame>
  );
}
