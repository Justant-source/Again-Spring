'use client';

// Legal footer with links to terms, privacy, crisis hotline, and feedback.
// Place manually at end of result pages. Tone Q styling.

import Link from 'next/link';
import { CRISIS_RESOURCES } from '@/lib/constants/crisisResources';
export function LegalFooter() {
  // Find 1393 (자살예방상담) in crisis resources
  const crisisPhone = CRISIS_RESOURCES.find((r) => r.phone === '1393')?.phone;

  return (
    <div
      style={{
        position: 'fixed',
        bottom: 0,
        left: 0,
        right: 0,
        background: 'var(--Q-bg)',
        borderTop: '1px solid var(--Q-border)',
        padding: '12px 28px',
        textAlign: 'center',
        fontSize: '11px',
        color: 'var(--Q-sub)',
        display: 'flex',
        justifyContent: 'center',
        gap: '16px',
        flexWrap: 'wrap',
      }}
    >
      <Link
        href="/terms"
        style={{
          color: 'var(--Q-sub)',
          textDecoration: 'none',
        }}
        onMouseEnter={(e) => (e.currentTarget.style.textDecoration = 'underline')}
        onMouseLeave={(e) => (e.currentTarget.style.textDecoration = 'none')}
      >
        이용약관
      </Link>

      <span style={{ opacity: 0.5 }}>·</span>

      <Link
        href="/privacy"
        style={{
          color: 'var(--Q-sub)',
          textDecoration: 'none',
        }}
        onMouseEnter={(e) => (e.currentTarget.style.textDecoration = 'underline')}
        onMouseLeave={(e) => (e.currentTarget.style.textDecoration = 'none')}
      >
        개인정보 처리방침
      </Link>

      <span style={{ opacity: 0.5 }}>·</span>

      {crisisPhone && (
        <a
          href={`tel:${crisisPhone.replace(/\D/g, '')}`}
          style={{
            color: 'var(--Q-sub)',
            textDecoration: 'none',
          }}
          onMouseEnter={(e) => (e.currentTarget.style.textDecoration = 'underline')}
          onMouseLeave={(e) => (e.currentTarget.style.textDecoration = 'none')}
        >
          위기 상황이라면 1393
        </a>
      )}

    </div>
  );
}
