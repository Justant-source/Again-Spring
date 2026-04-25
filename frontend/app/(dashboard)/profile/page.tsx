// ⚠️ MOCKUP PENDING — design/mockups/10-profile/ not yet provided; baseline Tone L layout used

'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { useUserStore } from '@/lib/store/userStore';
import { PhoneFrame, PhoneHeader } from '@/components/shared/PhoneFrame';
import { STYLE_MOTIF } from '@/components/shared/Motif';
import { COMMUNICATION_STYLES } from '@/lib/constants/communicationStyles';
import type { CommunicationStyle } from '@/lib/types';

export default function ProfilePage() {
  const router = useRouter();
  const user = useUserStore((s) => s.user);
  const clearUser = useUserStore((s) => s.clear);

  useEffect(() => {
    if (!user) {
      router.push('/login');
    }
  }, [user, router]);

  if (!user) {
    return null;
  }

  const style = user.communicationStyle
    ? COMMUNICATION_STYLES[user.communicationStyle]
    : null;

  const MotifComponent = user.communicationStyle
    ? STYLE_MOTIF[user.communicationStyle]
    : null;

  const handleLogout = async () => {
    clearUser();
    router.push('/');
  };

  return (
    <PhoneFrame tone="L">
      <PhoneHeader title="내 대화 성향" tone="L" onBack={() => router.back()} />
      <div
        style={{
          padding: '8px 28px 40px',
          display: 'flex',
          flexDirection: 'column',
          gap: 16,
        }}
      >
        {/* User info card */}
        <div
          className="letter-card"
          style={{
            padding: '18px 16px',
            marginTop: 8,
          }}
        >
          <div style={{ fontSize: 12, color: 'var(--L-sub)', marginBottom: 4 }}>
            닉네임
          </div>
          <div
            className="serif"
            style={{
              fontSize: 16,
              color: 'var(--L-ink)',
              fontWeight: 500,
              marginBottom: 12,
            }}
          >
            {user.nickname}
          </div>

          {user.email && (
            <>
              <div style={{ fontSize: 12, color: 'var(--L-sub)', marginBottom: 4 }}>
                이메일
              </div>
              <div
                style={{
                  fontSize: 13,
                  color: 'var(--L-ink)',
                  marginBottom: 12,
                  wordBreak: 'break-all',
                }}
              >
                {user.email}
              </div>
            </>
          )}

          {user.isGuest && (
            <div
              style={{
                padding: '10px 12px',
                background: 'var(--L-bg)',
                border: '1px solid var(--L-border)',
                borderRadius: '3px',
                fontSize: '12px',
                color: 'var(--L-sub)',
              }}
            >
              게스트 모드
            </div>
          )}
        </div>

        {/* Communication style card */}
        {style && MotifComponent && (
          <div
            className="letter-card"
            style={{
              padding: '18px 16px',
            }}
          >
            <div style={{ fontSize: 12, color: 'var(--L-sub)', marginBottom: 10 }}>
              당신의 대화 스타일
            </div>
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 12,
                marginBottom: 14,
              }}
            >
              <div
                style={{
                  width: 56,
                  height: 56,
                  borderRadius: '50%',
                  background: style.color,
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  color: 'white',
                }}
              >
                <MotifComponent size={28} color="white" />
              </div>
              <div>
                <div
                  className="serif"
                  style={{
                    fontSize: 16,
                    color: 'var(--L-ink)',
                    fontWeight: 500,
                  }}
                >
                  {style.label}
                </div>
                <div style={{ fontSize: 12, color: 'var(--L-sub)', marginTop: 2 }}>
                  {style.description}
                </div>
              </div>
            </div>

            <div style={{ fontSize: 13, lineHeight: 1.6, color: 'var(--L-sub)' }}>
              <div style={{ fontWeight: 500, marginBottom: 8 }}>강점</div>
              <ul style={{ paddingLeft: '16px', marginBottom: 12 }}>
                {style.strengths.map((s: string, i: number) => (
                  <li key={i}>{s}</li>
                ))}
              </ul>

              <div style={{ fontWeight: 500, marginBottom: 8 }}>유의할 점</div>
              <ul style={{ paddingLeft: '16px' }}>
                {style.caution.map((c: string, i: number) => (
                  <li key={i}>{c}</li>
                ))}
              </ul>
            </div>
          </div>
        )}

        {/* Temperature history placeholder */}
        <div
          className="letter-card"
          style={{
            padding: '18px 16px',
          }}
        >
          <div style={{ fontSize: 12, color: 'var(--L-sub)', marginBottom: 12 }}>
            온도 추이
          </div>
          <div
            style={{
              padding: '20px 0',
              textAlign: 'center',
              color: 'var(--L-sub)',
              fontSize: '13px',
            }}
          >
            아직 추이 데이터가 모이고 있어요.
          </div>
        </div>

        {/* Actions */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 8, marginTop: 12 }}>
          <button
            onClick={() => router.push('/onboarding/intro')}
            className="btn-L ghost"
            style={{ width: '100%' }}
          >
            온보딩 다시 하기
          </button>
          <button onClick={handleLogout} className="btn-L" style={{ width: '100%' }}>
            로그아웃
          </button>
          <button
            disabled
            style={{
              width: '100%',
              padding: '12px 16px',
              background: '#E8E6E0',
              color: '#9B9890',
              border: 'none',
              borderRadius: '3px',
              fontSize: '14px',
              fontWeight: 500,
              cursor: 'not-allowed',
            }}
          >
            계정 삭제
          </button>
        </div>
      </div>
    </PhoneFrame>
  );
}
