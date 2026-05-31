'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';

const NAV_ITEMS = [
  {
    href: '/',
    label: '홈',
    icon: (active: boolean) => (
      <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={active ? 2 : 1.5} strokeLinecap="round" strokeLinejoin="round">
        <path d="M3 9.5L12 3l9 6.5V20a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V9.5z" />
        <path d="M9 21V12h6v9" />
      </svg>
    ),
  },
  {
    href: '/community',
    label: '커뮤니티',
    icon: (active: boolean) => (
      <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={active ? 2 : 1.5} strokeLinecap="round" strokeLinejoin="round">
        <circle cx="12" cy="8" r="4" />
        <path d="M4 20c0-4 3.6-7 8-7s8 3 8 7" />
        <circle cx="19" cy="8" r="2.5" />
        <path d="M21 20c0-2.2-1.6-4-3.5-4.5" />
      </svg>
    ),
  },
  {
    href: '/history',
    label: '대화기록',
    icon: (active: boolean) => (
      <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={active ? 2 : 1.5} strokeLinecap="round" strokeLinejoin="round">
        <path d="M12 20h9" />
        <path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z" />
      </svg>
    ),
  },
];

// 바텀 내비를 숨길 경로 패턴 (채팅·결과·온보딩 등 몰입 화면)
const HIDE_PATHS = ['/session/chat', '/session/result', '/session/new', '/session/join', '/session/history', '/session/category', '/login', '/signup', '/guest', '/forgot', '/reset', '/onboarding', '/three-way', '/admin', '/community/'];

export function BottomNav() {
  const pathname = usePathname();

  // 몰입 화면에서는 숨김
  if (HIDE_PATHS.some(p => pathname.startsWith(p))) return null;

  return (
    <nav
      style={{
        position: 'fixed',
        bottom: 0,
        left: 0,
        right: 0,
        zIndex: 200,
        background: 'white',
        borderTop: '1px solid #e7e3d8',
        display: 'flex',
        justifyContent: 'space-around',
        padding: '8px 0 max(8px, env(safe-area-inset-bottom))',
      }}
    >
      {NAV_ITEMS.map(item => {
        const active = item.href === '/'
          ? pathname === '/'
          : pathname.startsWith(item.href);
        return (
          <Link
            key={item.href}
            href={item.href}
            style={{
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              gap: 3,
              padding: '2px 20px',
              textDecoration: 'none',
              color: active ? 'var(--P-ink, #5C4030)' : '#bbb',
              transition: 'color 0.15s',
            }}
          >
            {item.icon(active)}
            <span style={{ fontSize: 10, fontWeight: active ? 600 : 400, letterSpacing: '-0.2px' }}>
              {item.label}
            </span>
          </Link>
        );
      })}
    </nav>
  );
}
