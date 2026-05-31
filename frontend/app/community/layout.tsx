'use client';
import { usePathname } from 'next/navigation';
import Link from 'next/link';

const COMMUNITY_TABS = [
  { label: '사연 피드', href: '/community' },
  { label: '글쓰기', href: '/community/new' },
];

export default function CommunityLayout({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();

  const isTabActive = (tabHref: string) => {
    if (tabHref === '/community') {
      return pathname === '/community' || pathname.startsWith('/community/');
    }
    return pathname.startsWith(tabHref);
  };

  return (
    <div style={{ minHeight: '100vh', background: 'var(--P-bg)', fontFamily: 'sans-serif' }}>
      {/* 헤더 */}
      <header
        style={{
          position: 'sticky',
          top: 0,
          zIndex: 50,
          background: 'white',
          borderBottom: '1px solid var(--P-border)',
          padding: '12px 20px',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
          <Link href="/" style={{ fontSize: 13, color: '#888', textDecoration: 'none' }}>
            ← 홈
          </Link>
          <span style={{ fontSize: 14, fontWeight: 600, color: 'var(--P-ink)' }}>다시봄 커뮤니티</span>
        </div>
      </header>

      {/* 탭 네비게이션 */}
      <nav
        style={{
          background: 'white',
          borderBottom: '1px solid var(--P-border)',
          padding: '0 20px',
          display: 'flex',
          gap: 0,
        }}
      >
        {COMMUNITY_TABS.map((tab) => {
          const isActive = isTabActive(tab.href);
          return (
            <Link
              key={tab.href}
              href={tab.href}
              style={{
                padding: '14px 18px',
                fontSize: 13,
                fontWeight: 500,
                color: isActive ? 'var(--P-ink)' : '#888',
                borderBottom: isActive ? '2px solid var(--P-ink)' : '2px solid transparent',
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
      <main style={{ maxWidth: 640, margin: '0 auto', padding: '20px 16px 60px' }}>
        {children}
      </main>
    </div>
  );
}
