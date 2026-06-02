'use client';

interface SideStoryProps {
  side: 'g' | 'r';
  label: string;
  body: string;
  clamp?: boolean;
  selected?: boolean;
  onSelect: () => void;
  onMore: () => void;
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
  const bgColor = side === 'g' ? 'var(--grn-bg)' : 'var(--red-bg)';
  const borderColor = selected
    ? (side === 'g' ? 'var(--grn-dk)' : 'var(--red-dk)')
    : 'transparent';

  return (
    <div
      onClick={onSelect}
      style={{
        background: bgColor,
        borderRadius: 12,
        padding: '13px 14px',
        border: `2.5px solid ${borderColor}`,
        cursor: 'pointer',
        transition: 'border-color 0.2s',
      }}
    >
      {/* 상단: 색점 + 라벨 + selected 표시 */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 8 }}>
        <div
          style={{
            width: 8,
            height: 8,
            borderRadius: '50%',
            background: side === 'g' ? 'var(--grn)' : 'var(--red)',
            flexShrink: 0,
          }}
        />
        <span style={{ fontSize: 12, fontWeight: 600, color: 'var(--P-ink)', flex: 1 }}>
          {label}
        </span>
        {selected && (
          <span style={{ fontSize: 11, color: 'var(--P-sub)' }}>· 선택됨</span>
        )}
      </div>

      {/* 본문 */}
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'flex-end',
          gap: 8,
        }}
      >
        <p
          style={{
            margin: 0,
            fontSize: 12.5,
            fontFamily: 'var(--font-serif)',
            lineHeight: 1.6,
            color: 'var(--P-ink)',
            flex: 1,
            ...(clamp && {
              display: '-webkit-box',
              WebkitLineClamp: 3,
              WebkitBoxOrient: 'vertical',
              overflow: 'hidden',
            }),
          }}
        >
          {body}
        </p>
        {clamp && (
          <button
            onClick={(e) => {
              e.stopPropagation();
              onMore();
            }}
            style={{
              background: 'none',
              border: 'none',
              fontSize: 12,
              color: 'var(--P-sub)',
              cursor: 'pointer',
              padding: 0,
              whiteSpace: 'nowrap',
              flexShrink: 0,
            }}
          >
            더 보기 ›
          </button>
        )}
      </div>
    </div>
  );
}
