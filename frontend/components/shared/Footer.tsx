'use client';

export function Footer() {
  return (
    <footer
      style={{
        padding: '20px 28px 28px',
        borderTop: '1px solid var(--L-border)',
        fontSize: 11,
        color: 'var(--L-sub)',
        lineHeight: 1.7,
      }}
    >
      <div style={{ fontSize: 10, color: 'var(--L-sub)' }}>
        다시봄은 의료행위·심리치료가 아닌 감정 정리 도구예요.
      </div>
    </footer>
  );
}
