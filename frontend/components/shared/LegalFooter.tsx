'use client';

// Legal footer with links to terms, privacy, and crisis hotline.
// 결과 화면·과거 대화 상세에서만 노출.
// BottomNav가 표시되는 모든 화면(홈·커뮤니티·대화기록·내정보 등)에서는 숨겨
// 두 막대가 겹치지 않도록 한다.

import Link from 'next/link';
import { usePathname } from 'next/navigation';
// LegalFooter를 표시할 경로 패턴 (BottomNav가 숨겨진 몰입 화면 중 법적 고지 필요한 곳)
const LEGAL_SHOW_PATHS = [
  '/session/result',
  '/session/history',
];

export function LegalFooter() {
  const pathname = usePathname();

  const shouldShow = LEGAL_SHOW_PATHS.some(
    p => pathname === p || pathname.startsWith(p + '/')
  );
  if (!shouldShow) return null;

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
        zIndex: 150, // BottomNav(200)보다 낮고 위기모달(999)보다 낮음
      }}
    >
      <Link
        href="/terms"
        style={{ color: 'var(--Q-sub)', textDecoration: 'none' }}
        onMouseEnter={(e) => (e.currentTarget.style.textDecoration = 'underline')}
        onMouseLeave={(e) => (e.currentTarget.style.textDecoration = 'none')}
      >
        이용약관
      </Link>

      <span style={{ opacity: 0.5 }}>·</span>

      <Link
        href="/privacy"
        style={{ color: 'var(--Q-sub)', textDecoration: 'none' }}
        onMouseEnter={(e) => (e.currentTarget.style.textDecoration = 'underline')}
        onMouseLeave={(e) => (e.currentTarget.style.textDecoration = 'none')}
      >
        개인정보 처리방침
      </Link>
    </div>
  );
}
