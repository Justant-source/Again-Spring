export default function Loading() {
  return (
    <div
      className="min-h-screen flex items-center justify-center"
      style={{ background: 'var(--L-bg)', color: 'var(--L-sub)' }}
    >
      <span
        className="serif"
        style={{ fontSize: 15, letterSpacing: '-0.01em' }}
      >
        다시봄
        <span className="blink">…</span>
      </span>
    </div>
  );
}
