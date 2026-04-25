// ✅ MOCKUP APPLIED — source: design/handoff/tone-L-screens.jsx (WaitingB)

'use client';

import { useRouter } from 'next/navigation';
import { useState, useEffect } from 'react';
import { useSessionStore } from '@/lib/store/sessionStore';
import { api } from '@/lib/api/client';
import { PhoneFrame, PhoneHeader } from '@/components/shared';

export default function WaitPage() {
  const router = useRouter();
  const { sessionId } = useSessionStore();
  const [elapsedTime, setElapsedTime] = useState('14분');
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    if (!sessionId) {
      router.push('/session/new');
      return;
    }

    const startTime = Date.now();

    // Timer for elapsed time
    const timerInterval = setInterval(() => {
      const elapsed = Math.floor((Date.now() - startTime) / 1000);
      const hours = Math.floor(elapsed / 3600);
      const minutes = Math.floor((elapsed % 3600) / 60);

      if (hours > 0) {
        setElapsedTime(`${hours}시간 ${minutes}분`);
      } else {
        setElapsedTime(`${minutes}분`);
      }
    }, 1000);

    // Polling for partner arrival (every 5 seconds)
    const pollInterval = setInterval(async () => {
      try {
        const response = await api.get(`/sessions/${sessionId}/status`);
        if (response.data?.hasPartnerJoined) {
          clearInterval(timerInterval);
          clearInterval(pollInterval);
          router.push('/session/mediation?role=A');
        }
      } catch (error) {
        // Silently ignore polling errors, just continue waiting
        console.debug('Polling error (will retry):', error);
      }
    }, 5000);

    return () => {
      clearInterval(timerInterval);
      clearInterval(pollInterval);
    };
  }, [sessionId, router]);

  if (!sessionId) {
    return null;
  }

  const handleSolo = async () => {
    setIsSubmitting(true);
    try {
      await api.post(`/sessions/${sessionId}/solo`);
      router.push('/session/mediation?role=A&solo=true');
    } catch (error) {
      console.error('Error switching to solo mode:', error);
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleSimulate = () => {
    // Dev-mode: simulate partner arrival
    router.push('/session/mediation?role=A');
  };

  return (
    <PhoneFrame tone="L">
      <PhoneHeader title="" back={true} onBack={() => router.push('/session/invite')} />
      <div
        style={{
          padding: '40px 28px 28px',
          textAlign: 'center',
          flex: 1,
          display: 'flex',
          flexDirection: 'column',
        }}
      >
        <div
          style={{
            width: 72,
            height: 72,
            borderRadius: '50%',
            border: '1px solid var(--L-border)',
            margin: '0 auto',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            position: 'relative',
          }}
        >
          <div
            style={{
              position: 'absolute',
              inset: -6,
              border: '1px solid var(--L-border)',
              borderRadius: '50%',
              opacity: 0.5,
            }}
          />
          <div className="serif" style={{ fontSize: 13, color: 'var(--L-sub)' }}>
            기다림
          </div>
        </div>

        <div className="serif" style={{ fontSize: 20, lineHeight: 1.5, marginTop: 28 }}>
          상대방의 도착을<br />기다리고 있어요.
        </div>

        <div style={{ marginTop: 16, fontSize: 13, color: 'var(--L-sub)', lineHeight: 1.7 }}>
          초대를 보낸 지{' '}
          <span style={{ color: 'var(--L-ink)' }}>{elapsedTime}</span> 지났어요.
          <br />
          24시간 안에 도착하지 않으면<br />혼자 정리하는 모드로 바꿀 수 있어요.
        </div>

        <div className="letter-card" style={{ marginTop: 36, textAlign: 'left' }}>
          <div style={{ fontSize: 12, color: 'var(--L-sub)', marginBottom: 6 }}>한 줄 안내</div>
          <div className="quote-it" style={{ fontSize: 14, lineHeight: 1.7 }}>
            지금은 아무것도 하지 않으셔도 괜찮아요. 숨을 한 번 고르는 시간일지도 몰라요.
          </div>
        </div>

        <div style={{ marginTop: 32, display: 'flex', gap: 8 }}>
          <button
            className="btn-L ghost"
            style={{ flex: 1 }}
            onClick={() => {
              /* Keep waiting */
            }}
          >
            더 기다리기
          </button>
          <button
            className="btn-L"
            style={{ flex: 1 }}
            disabled={isSubmitting}
            onClick={handleSolo}
          >
            혼자 정리
          </button>
        </div>

        {/* Dev mode: simulate partner arrival */}
        <div style={{ marginTop: 24, textAlign: 'center' }}>
          <button
            style={{
              background: 'none',
              border: 'none',
              color: 'var(--L-sub)',
              fontSize: 11,
              cursor: 'pointer',
              textDecoration: 'underline',
            }}
            onClick={handleSimulate}
          >
            [DEV] 상대 도착 시뮬레이션
          </button>
        </div>
      </div>
    </PhoneFrame>
  );
}
