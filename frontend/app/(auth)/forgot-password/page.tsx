// Forgot Password Page
'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { PhoneFrame, PhoneHeader } from '@/components/shared/PhoneFrame';
import { api } from '@/lib/api/client';

export default function ForgotPasswordPage() {
  const router = useRouter();

  const [email, setEmail] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [submitted, setSubmitted] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    if (!email) {
      setError('이메일을 입력해주세요');
      return;
    }

    setLoading(true);
    try {
      await api.post('/api/auth/forgot-password', { email });
      setSubmitted(true);
    } catch (err: any) {
      setError(err.response?.data?.message || '요청 중 오류가 발생했어요');
    } finally {
      setLoading(false);
    }
  };

  return (
    <PhoneFrame tone="L">
      <PhoneHeader title="비밀번호 재설정" onBack={() => router.back()} />
      <div style={{ padding: '8px 28px 28px', flex: 1, display: 'flex', flexDirection: 'column' }}>
        <div className="letter-card" style={{ padding: '28px' }}>
          {submitted ? (
            <>
              <div style={{ fontSize: 13, color: 'var(--L-sub)', marginBottom: 14 }}>이메일 확인</div>
              <div className="serif" style={{ fontSize: 20, lineHeight: 1.6, marginBottom: 28 }}>
                메일을 확인해주세요
              </div>
              <div style={{ fontSize: 14, color: 'var(--L-sub)', lineHeight: 1.6, marginBottom: 28 }}>
                {email}로 비밀번호 재설정 링크를 보내드렸어요. 이메일을 확인해주세요.
              </div>
              <button
                onClick={() => router.push('/login')}
                className="btn-L"
                style={{ marginTop: 12 }}
              >
                로그인 페이지로
              </button>
            </>
          ) : (
            <>
              <div style={{ fontSize: 13, color: 'var(--L-sub)', marginBottom: 14 }}>비밀번호를 잊으셨나요?</div>
              <div className="serif" style={{ fontSize: 20, lineHeight: 1.6, marginBottom: 28 }}>
                이메일로 재설정 링크를 보내드릴게요
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

                {error && <div style={{ fontSize: 13, color: 'var(--L-point)', marginTop: 8 }}>{error}</div>}

                <button type="submit" disabled={loading} className="btn-L" style={{ marginTop: 12 }}>
                  {loading ? '발송 중...' : '비밀번호 재설정 링크 받기'}
                </button>
              </form>
            </>
          )}
        </div>
      </div>
    </PhoneFrame>
  );
}
