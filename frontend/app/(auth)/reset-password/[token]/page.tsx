// Reset Password Page
'use client';

import { useState } from 'react';
import { useRouter, useParams } from 'next/navigation';
import { PhoneFrame, PhoneHeader } from '@/components/shared/PhoneFrame';
import { api } from '@/lib/api/client';

export default function ResetPasswordPage() {
  const router = useRouter();
  const params = useParams();
  const token = params?.token as string;

  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [success, setSuccess] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    if (!newPassword || !confirmPassword) {
      setError('비밀번호를 입력해주세요');
      return;
    }

    if (newPassword.length < 8) {
      setError('비밀번호는 최소 8자 이상이어야 해요');
      return;
    }

    if (newPassword !== confirmPassword) {
      setError('비밀번호가 일치하지 않아요');
      return;
    }

    setLoading(true);
    try {
      await api.post('/api/auth/reset-password', {
        token,
        newPassword,
      });
      setSuccess(true);
      setTimeout(() => {
        router.push('/login');
      }, 2000);
    } catch (err: any) {
      setError(err.response?.data?.error?.message || '비밀번호 재설정에 실패했어요');
    } finally {
      setLoading(false);
    }
  };

  return (
    <PhoneFrame tone="L">
      <PhoneHeader title="비밀번호 재설정" onBack={() => window.history.length > 1 ? router.back() : router.replace('/login')} />
      <div style={{ padding: '8px 28px 28px', flex: 1, display: 'flex', flexDirection: 'column' }}>
        <div className="letter-card" style={{ padding: '28px' }}>
          {success ? (
            <>
              <div style={{ fontSize: 13, color: 'var(--L-sub)', marginBottom: 14 }}>완료</div>
              <div className="serif" style={{ fontSize: 20, lineHeight: 1.6, marginBottom: 28 }}>
                비밀번호가 변경되었어요
              </div>
              <div style={{ fontSize: 14, color: 'var(--L-sub)', marginBottom: 28 }}>
                새 비밀번호로 로그인해주세요.
              </div>
            </>
          ) : (
            <>
              <div style={{ fontSize: 13, color: 'var(--L-sub)', marginBottom: 14 }}>새 비밀번호</div>
              <div className="serif" style={{ fontSize: 20, lineHeight: 1.6, marginBottom: 28 }}>
                비밀번호를 설정해주세요
              </div>

              <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
                <div>
                  <input
                    type="password"
                    placeholder="새 비밀번호 (8자 이상)"
                    value={newPassword}
                    onChange={(e) => setNewPassword(e.target.value)}
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
                    placeholder="비밀번호 확인"
                    value={confirmPassword}
                    onChange={(e) => setConfirmPassword(e.target.value)}
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
                  {loading ? '변경 중...' : '비밀번호 변경'}
                </button>
              </form>
            </>
          )}
        </div>
      </div>
    </PhoneFrame>
  );
}
