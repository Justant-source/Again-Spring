'use client';

import { usePathname } from 'next/navigation';
import { useUiStore } from '@/lib/store/uiStore';

export function BetaBanner() {
  const pathname = usePathname();
  const { showFeedbackModal } = useUiStore();

  if (pathname?.startsWith('/admin')) return null;

  return (
    <div
      style={{
        position: 'fixed',
        top: 0,
        left: 0,
        right: 0,
        background: '#1A1A2E',
        color: 'rgba(255,255,255,0.85)',
        fontSize: '12px',
        textAlign: 'center',
        padding: '6px 16px',
        zIndex: 9998,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        gap: '10px',
      }}
    >
      <span>베타 서비스 — 경험을 개선하고 있어요</span>
      <button
        onClick={() => showFeedbackModal()}
        style={{
          background: 'rgba(255,255,255,0.15)',
          border: '1px solid rgba(255,255,255,0.3)',
          borderRadius: 4,
          color: 'white',
          fontSize: '11px',
          padding: '2px 8px',
          cursor: 'pointer',
          whiteSpace: 'nowrap',
        }}
      >
        의견 보내주세요
      </button>
    </div>
  );
}
