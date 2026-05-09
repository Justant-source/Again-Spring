'use client';

import { useEffect, useRef, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useUiStore } from '@/lib/store/uiStore';

const COUNTDOWN_SEC = 10;

export function GuestUpgradeModal() {
  const router = useRouter();
  const { guestLimitModal, hideGuestLimitModal } = useUiStore();
  const [countdown, setCountdown] = useState(COUNTDOWN_SEC);
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const open = guestLimitModal !== null;

  useEffect(() => {
    if (!open) {
      setCountdown(COUNTDOWN_SEC);
      return;
    }
    document.body.style.overflow = 'hidden';
    timerRef.current = setInterval(() => {
      setCountdown((c) => {
        if (c <= 1) {
          clearInterval(timerRef.current!);
          return 0;
        }
        return c - 1;
      });
    }, 1000);
    return () => {
      document.body.style.overflow = '';
      if (timerRef.current) clearInterval(timerRef.current);
    };
  }, [open]);

  if (!open) return null;

  const handleSignup = () => {
    hideGuestLimitModal();
    const sessionId = guestLimitModal?.sessionId;
    const query = sessionId ? `?fromGuestSession=${sessionId}` : '';
    router.push(`/signup${query}`);
  };

  const handleBack = () => {
    hideGuestLimitModal();
  };

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="guest-limit-title"
      style={{
        position: 'fixed',
        inset: 0,
        background: 'rgba(0,0,0,0.5)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        zIndex: 9999,
      }}
    >
      <div
        style={{
          background: 'white',
          borderRadius: '16px',
          padding: '32px 28px',
          maxWidth: '340px',
          width: '90%',
          boxShadow: '0 4px 20px rgba(0,0,0,0.15)',
        }}
      >
        <div
          id="guest-limit-title"
          style={{
            fontFamily: 'var(--font-serif)',
            fontSize: '20px',
            fontWeight: 700,
            marginBottom: '12px',
            color: '#111',
          }}
        >
          더 깊은 이야기를 나눠보세요
        </div>
        <p style={{ fontSize: '14px', color: '#555', lineHeight: 1.6, marginBottom: '24px' }}>
          게스트로는 3턴까지 체험 가능해요.
          <br />
          회원가입 후 제한 없이 이야기할 수 있어요.
        </p>

        <button
          onClick={handleSignup}
          style={{
            display: 'block',
            width: '100%',
            padding: '14px',
            borderRadius: '10px',
            background: '#1A1A2E',
            color: 'white',
            fontSize: '15px',
            fontWeight: 600,
            border: 'none',
            cursor: 'pointer',
            marginBottom: '10px',
          }}
        >
          회원가입{countdown > 0 ? ` (${countdown}초)` : ''}
        </button>

        <button
          onClick={handleBack}
          style={{
            display: 'block',
            width: '100%',
            padding: '12px',
            borderRadius: '10px',
            background: 'transparent',
            color: '#666',
            fontSize: '14px',
            border: '1px solid #ddd',
            cursor: 'pointer',
          }}
        >
          돌아가기
        </button>
      </div>
    </div>
  );
}
