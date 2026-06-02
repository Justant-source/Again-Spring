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
        padding: '12px 14px',
        background: 'var(--L-card)',
        border: '1px solid var(--L-border)',
        borderRadius: 8,
        textDecoration: 'none',
      }}
    >
      {/* 상단: 카테고리 + paired 점 + 오른쪽 통계 */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 7 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 7 }}>
          <span style={{
            padding: '2px 9px',
            background: 'var(--L-ink)',
            color: 'var(--L-bg)',
            borderRadius: 999,
            fontSize: 11,
          }}>{cat}</span>
          {paired && (
            <div style={{ display: 'flex', gap: 3 }}>
              <span style={{ width: 7, height: 7, borderRadius: '50%', background: 'var(--grn)', display: 'inline-block' }} />
              <span style={{ width: 7, height: 7, borderRadius: '50%', background: 'var(--red)', display: 'inline-block' }} />
            </div>
          )}
        </div>
        {/* 오른쪽 상단: 투표수 + 댓글수 */}
        <span style={{ fontSize: 11, color: 'var(--L-sub)', whiteSpace: 'nowrap' }}>
          {voteCount}표{commentCount !== undefined ? ` · 댓글 ${commentCount}` : ''}
        </span>
      </div>

      {/* 제목 */}
      <div style={{
        fontSize: 15,
        fontWeight: 500,
        fontFamily: 'var(--font-serif)',
        color: 'var(--L-ink)',
        lineHeight: 1.45,
        marginBottom: 10,
      }}>
        {title}
      </div>

      {/* 하단: 미니 투표 막대 */}
      <div style={{ display: 'flex', height: 6, borderRadius: 3, overflow: 'hidden' }}>
        <div style={{ width: authorPct + '%', background: 'var(--grn)' }} />
        <div style={{ flex: 1, background: 'var(--red)' }} />
      </div>
    </a>
  );
}
