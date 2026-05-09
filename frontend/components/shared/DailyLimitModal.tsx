'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { useUiStore } from '@/lib/store/uiStore';

export function DailyLimitModal() {
  const router = useRouter();
  const { dailyLimitModal, hideDailyLimitModal } = useUiStore();

  useEffect(() => {
    if (!dailyLimitModal) return;
    document.body.style.overflow = 'hidden';
    return () => { document.body.style.overflow = ''; };
  }, [dailyLimitModal]);

  if (!dailyLimitModal) return null;

  const handleViewSessions = () => {
    hideDailyLimitModal();
    router.push('/history');
  };

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="daily-limit-title"
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
          id="daily-limit-title"
          style={{
            fontFamily: 'var(--font-serif)',
            fontSize: '20px',
            fontWeight: 700,
            marginBottom: '12px',
            color: '#111',
          }}
        >
          오늘의 대화를 모두 마쳤어요
        </div>
        <p style={{ fontSize: '14px', color: '#555', lineHeight: 1.6, marginBottom: '8px' }}>
          다시봄은 깊은 이야기를 위해 하루 5세션으로 운영해요.
        </p>
        <p style={{ fontSize: '14px', color: '#888', lineHeight: 1.6, marginBottom: '24px' }}>
          내일 자정 후 다시 만나요.
        </p>

        <button
          onClick={handleViewSessions}
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
          세션 목록 보기
        </button>

        <button
          onClick={hideDailyLimitModal}
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
          닫기
        </button>
      </div>
    </div>
  );
}
