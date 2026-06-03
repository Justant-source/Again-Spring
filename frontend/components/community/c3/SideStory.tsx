'use client';

interface SideStoryProps {
  side: 'g' | 'r';
  label: string;
  body: string;
  clamp?: boolean;
  selected?: boolean;
  onSelect?: () => void;
  onMore?: () => void;
}

export function SideStory({
  side,
  label,
  body,
  clamp = false,
  selected = false,
  onSelect,
  onMore,
}: SideStoryProps) {
  const c = side === 'g' ? 'var(--grn)' : 'var(--red)';
  const cDk = side === 'g' ? 'var(--grn-dk)' : 'var(--red-dk)';
  const bg = side === 'g' ? 'var(--grn-bg)' : 'var(--red-bg)';

  return (
    <div
      onClick={onSelect}
      style={{
        background: bg,
        borderRadius: 12,
        padding: '13px 14px',
        border: `2.5px solid ${selected ? cDk : 'transparent'}`,
        cursor: onSelect ? 'pointer' : clamp ? 'pointer' : 'default',
        transition: 'border-color 0.15s',
      }}
    >
      {/* 상단: 색점 + 라벨 + 선택됨 / 더 보기 */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 6 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
          <span style={{ width: 8, height: 8, borderRadius: '50%', background: c, flexShrink: 0 }} />
          <span style={{ fontSize: 11, color: c, fontWeight: 500 }}>{label}</span>
          {selected && <span style={{ fontSize: 11, color: cDk, fontWeight: 500 }}>· 선택됨</span>}
        </div>
        {clamp && onMore && (
          <span
            onClick={(e) => {
              e.stopPropagation();
              onMore();
            }}
            style={{ fontSize: 11, color: c, cursor: 'pointer' }}
          >
            더 보기 ›
          </span>
        )}
      </div>

      {/* 본문 */}
      <div
        style={{
          fontSize: 12.5,
          color: 'var(--P-ink)',
          lineHeight: 1.65,
          fontFamily: 'var(--font-serif)',
          ...(clamp && {
            display: '-webkit-box',
            WebkitLineClamp: 3,
            WebkitBoxOrient: 'vertical',
            overflow: 'hidden',
          }),
        }}
      >
        {body}
      </div>
    </div>
  );
}
