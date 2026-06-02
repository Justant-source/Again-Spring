'use client';

export default function CommunityLayout({ children }: { children: React.ReactNode }) {
  return (
    <div style={{ minHeight: '100vh', background: 'var(--P-bg)', fontFamily: 'sans-serif' }}>
      {/* 최소 헤더 — 타이틀만. ← 홈 링크·가로탭은 글로벌 하단 내비로 대체 */}
      <header
        style={{
          position: 'sticky',
          top: 0,
          zIndex: 50,
          background: 'white',
          borderBottom: '1px solid var(--P-border)',
          padding: '14px 20px',
        }}
      >
        <span style={{ fontSize: 15, fontWeight: 600, color: 'var(--P-ink)' }}>커뮤니티</span>
      </header>

      {/* 페이지 콘텐츠 */}
      <main style={{ maxWidth: 640, margin: '0 auto', padding: '20px 16px 96px' }}>
        {children}
      </main>
    </div>
  );
}
