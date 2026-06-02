'use client';

interface FeedCardProps {
  cat: string;
  title: string;
  authorPct: number;
  voteCount: number;
  commentCount?: number;
  paired: boolean;
  href: string;
}

export function FeedCard({
  cat,
  title,
  authorPct,
  voteCount,
  commentCount,
  paired,
  href,
}: FeedCardProps) {
  const partnerPct = 100 - authorPct;

  return (
    <a
      href={href}
      style={{
        display: 'block',
        padding: '14px 16px',
        background: 'var(--P-card)',
        border: '1px solid var(--P-border)',
        borderRadius: 12,
        textDecoration: 'none',
        transition: 'background 0.15s, border-color 0.15s',
      }}
      onMouseEnter={(e) => {
        (e.currentTarget as HTMLElement).style.background = 'rgba(100, 100, 100, 0.02)';
      }}
      onMouseLeave={(e) => {
        (e.currentTarget as HTMLElement).style.background = 'var(--P-card)';
      }}
    >
      {/* 카테고리 칩 + paired 표시 */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8 }}>
        <div
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: 4,
            padding: '4px 10px',
            background: 'var(--L-ink)',
            color: 'white',
            borderRadius: 999,
            fontSize: 11,
            fontWeight: 500,
          }}
        >
          {cat}
        </div>
        {paired && (
          <div style={{ display: 'flex', gap: 4 }}>
            <div
              style={{
                width: 6,
                height: 6,
                borderRadius: '50%',
                background: 'var(--grn)',
              }}
            />
            <div
              style={{
                width: 6,
                height: 6,
                borderRadius: '50%',
                background: 'var(--red)',
              }}
            />
          </div>
        )}
      </div>

      {/* 제목 */}
      <h3
        style={{
          margin: '0 0 10px 0',
          fontSize: 14,
          fontWeight: 600,
          fontFamily: 'var(--font-serif)',
          color: 'var(--P-ink)',
          lineHeight: 1.5,
        }}
      >
        {title}
      </h3>

      {/* 하단: 미니 막대 + 통계 */}
      <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
        <div
          style={{
            display: 'flex',
            height: 20,
            borderRadius: 4,
            overflow: 'hidden',
            border: '1px solid var(--P-border)',
          }}
        >
          <div
            style={{
              flex: authorPct,
              background: 'var(--grn)',
            }}
          />
          <div
            style={{
              flex: partnerPct,
              background: 'var(--red)',
            }}
          />
        </div>
        <div
          style={{
            fontSize: 11,
            color: 'var(--P-sub)',
          }}
        >
          {voteCount}표{commentCount !== undefined ? ` · 댓글 ${commentCount}` : ''}
        </div>
      </div>
    </a>
  );
}
