'use client';

export default function CommunityLayout({ children }: { children: React.ReactNode }) {
  return (
    <div style={{ minHeight: '100vh', width: '100%', background: 'var(--L-bg)', fontFamily: 'var(--font-sans)' }}>
      <main style={{ maxWidth: 640, width: '100%', margin: '0 auto' }}>
        {children}
      </main>
    </div>
  );
}
