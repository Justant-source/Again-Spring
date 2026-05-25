'use client';

import { usePathname } from 'next/navigation';
import Link from 'next/link';

const MARKETING_TABS = [
  { label: '사연', href: '/admin/marketing/stories' },
  { label: '시뮬레이션', href: '/admin/marketing/simulations' },
  { label: '콘텐츠', href: '/admin/marketing/contents' },
  { label: '설정', href: '/admin/marketing/settings' },
];

export default function MarketingLayout({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();

  return (
    <div style={{ minHeight: '100vh', background: '#f7f6f2', fontFamily: 'sans-serif' }}>
      {/* 자체 헤더 */}
      <header
        style={{
          position: 'sticky',
          top: 0,
          zIndex: 50,
          background: 'white',
          borderBottom: '1px solid #e7e3d8',
          padding: '12px 20px',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
          <Link href="/admin" style={{ fontSize: 13, color: '#888', textDecoration: 'none' }}>
            ← 관리자
          </Link>
          <span style={{ fontSize: 14, fontWeight: 600, color: '#1A1A2E' }}>마케팅 관리</span>
        </div>
        <Link
          href="/history"
          style={{ fontSize: 13, color: '#555', textDecoration: 'none', padding: '6px 4px' }}
        >
          지난 대화
        </Link>
      </header>

      {/* 탭 네비게이션 */}
      <nav
        style={{
          background: 'white',
          borderBottom: '1px solid #e7e3d8',
          padding: '0 20px',
          display: 'flex',
          gap: 0,
        }}
      >
        {MARKETING_TABS.map((tab) => {
          const isActive = pathname.startsWith(tab.href);
          return (
            <Link
              key={tab.href}
              href={tab.href}
              style={{
                padding: '14px 18px',
                fontSize: 13,
                fontWeight: 500,
                color: isActive ? '#1A1A2E' : '#888',
                borderBottom: isActive ? '2px solid #1A1A2E' : '2px solid transparent',
                textDecoration: 'none',
                display: 'inline-block',
                transition: 'all 0.15s',
              }}
              onMouseEnter={(e) => {
                if (!isActive) {
                  e.currentTarget.style.color = '#555';
                }
              }}
              onMouseLeave={(e) => {
                if (!isActive) {
                  e.currentTarget.style.color = '#888';
                }
              }}
            >
              {tab.label}
            </Link>
          );
        })}
      </nav>

      {/* 페이지 콘텐츠 */}
      <main style={{ maxWidth: 1100, margin: '0 auto', padding: '20px 16px 60px' }}>
        {children}
      </main>
    </div>
  );
}
