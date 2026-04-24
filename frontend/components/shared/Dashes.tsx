export function Dashes({ n = 4, done = 0 }: { n?: number; done?: number }) {
  return (
    <div className="dash-row">
      {Array.from({ length: n }).map((_, i) => (
        <span
          key={i}
          className={'dash' + (i < done ? ' on' : '')}
          aria-hidden
        />
      ))}
    </div>
  );
}
