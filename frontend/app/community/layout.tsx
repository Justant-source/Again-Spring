'use client';

export default function CommunityLayout({ children }: { children: React.ReactNode }) {
  return (
    <div style={{ minHeight: '100vh', background: 'var(--L-bg)', fontFamily: 'var(--font-sans)' }}>
      <main style={{ maxWidth: 640, margin: '0 auto' }}>
        {children}
      </main>
    </div>
  );
}
