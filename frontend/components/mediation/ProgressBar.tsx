// ✅ MOCKUP APPLIED — source: design/handoff/mediation-screens.jsx (MediationBubble variant progress indicator)

export function ProgressBar({
  current = 1,
  total = 6,
}: {
  current: number;
  total?: number;
}) {
  return (
    <div className="flex items-center justify-center gap-1.5">
      <div className="flex gap-1">
        {Array.from({ length: total }).map((_, i) => (
          <div
            key={i}
            style={{
              width: '6px',
              height: '6px',
              borderRadius: '50%',
              background: i < current ? 'var(--L-ink)' : 'var(--L-border)',
              transition: 'background 0.3s ease',
            }}
          />
        ))}
      </div>
      <span
        style={{
          fontSize: '12px',
          color: 'var(--L-sub)',
          marginLeft: '8px',
        }}
      >
        {current} / {total}
      </span>
    </div>
  );
}
