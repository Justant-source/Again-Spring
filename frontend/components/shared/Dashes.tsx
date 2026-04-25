export function Dashes({
  n = 4,
  done = 0,
  current,
  onDashClick,
}: {
  n?: number;
  done?: number;
  current?: number;
  onDashClick?: (index: number) => void;
}) {
  const clickable = typeof onDashClick === 'function';
  return (
    <div className="dash-row">
      {Array.from({ length: n }).map((_, i) => {
        const isCurrent = current === i;
        const className =
          'dash' +
          (i < done ? ' on' : '') +
          (isCurrent ? ' current' : '') +
          (clickable ? ' clickable' : '');
        if (clickable) {
          return (
            <button
              key={i}
              type="button"
              className={className}
              onClick={() => onDashClick!(i)}
              aria-label={`질문 ${i + 1}로 이동`}
            />
          );
        }
        return <span key={i} className={className} aria-hidden />;
      })}
    </div>
  );
}
