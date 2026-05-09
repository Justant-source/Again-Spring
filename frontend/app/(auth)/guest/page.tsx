// ✅ MOCKUP APPLIED — source: design/handoff/tone-L-screens.jsx (LandingScreen)
'use client';

import { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { PhoneFrame, PhoneHeader } from '@/components/shared/PhoneFrame';
import { useUserStore } from '@/lib/store/userStore';
import { api } from '@/lib/api/client';
import { generateGuestNickname } from '@/lib/utils/guestNickname';

export default function GuestPage() {
  const router = useRouter();
  const setUser = useUserStore((s) => s.setUser);

  const [nickname, setNickname] = useState('');
  const [placeholder, setPlaceholder] = useState('닉네임 (선택)');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    setPlaceholder(generateGuestNickname());
  }, []);

  const handleShuffle = () => {
    setPlaceholder(generateGuestNickname());
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    setLoading(true);
    try {
      const finalNickname = nickname.trim() || placeholder;
      const response = await api.post('/api/auth/guest', {
        nickname: finalNickname,
      });
      const { user, token } = response.data;
      if (token?.accessToken) {
        localStorage.setItem('again-spring-token', token.accessToken);
      }
      setUser(user);
      router.push('/onboarding/intro');
    } catch (err: any) {
      setError(err.response?.data?.error?.message || '게스트 입장에 실패했어요');
    } finally {
      setLoading(false);
    }
  };

  return (
    <PhoneFrame tone="L">
      <PhoneHeader
        title="게스트 입장"
        onBack={() => router.back()}
      />
      <div style={{ padding: '8px 28px 28px', flex: 1, display: 'flex', flexDirection: 'column' }}>
        <div className="letter-card" style={{ padding: '28px' }}>
          <div style={{ fontSize: 13, color: 'var(--L-sub)', marginBottom: 14 }}>
            한번 둘러보기
          </div>
          <div className="serif" style={{ fontSize: 20, lineHeight: 1.6, marginBottom: 28 }}>
            이름을<br />지어주세요
          </div>

          <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
            <div>
              <input
                type="text"
                placeholder={placeholder}
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
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginTop: 4 }}>
                <div style={{ fontSize: 11, color: 'var(--L-sub)' }}>
                  비워두면 “{placeholder}”로 설정돼요
                </div>
                <button
                  type="button"
                  onClick={handleShuffle}
                  style={{ background: 'none', border: 'none', color: 'var(--L-ink)', fontSize: 11, textDecoration: 'underline', cursor: 'pointer', padding: 0 }}
                >
                  다른 이름
                </button>
              </div>
            </div>

            <div style={{ fontSize: 12, color: 'var(--L-sub)', lineHeight: 1.7, marginTop: 12 }}>
              게스트 모드에서는 세션 이력이 저장되지 않아요.<br />
              언제든 회원가입으로 전환할 수 있어요.
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
              {loading ? '입장 중...' : '시작하기'}
            </button>
          </form>
        </div>
      </div>
    </PhoneFrame>
  );
}
