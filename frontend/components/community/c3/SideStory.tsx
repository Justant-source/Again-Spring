'use client';

interface SideStoryProps {
  side: 'g' | 'r';
  label: string;
  body: string;
  clamp?: boolean;
  selected?: boolean;
  /** 라벨 오른쪽에 표시할 메타 정보 (닉네임 · 시간 등) */
  meta?: string;
  /** 박스(본문) 클릭 — 사연 전문 보기로 이동 */
  onSelect?: () => void;
  /** (legacy) clamp 시 우상단 "더 보기 ›" 링크 */
  onMore?: () => void;
  /** 우측 끝 투표 버튼 — 이 쪽에 투표 */
  onVote?: () => void;
  /** 이 쪽에 투표 완료됨 (버튼 라벨/색 반전) */
  voted?: boolean;
  /** 투표가 이미 끝나 버튼 비활성 */
  voteDisabled?: boolean;
}

export function SideStory({
  side,
  label,
  body,
  clamp = false,
  selected = false,
  meta,
  onSelect,
  onMore,
  onVote,
  voted = false,
  voteDisabled = false,
}: SideStoryProps) {
  const c = side === 'g' ? 'var(--faction-author)' : 'var(--faction-partner)';
  const cDk = side === 'g' ? 'var(--faction-author-dk)' : 'var(--faction-partner-dk)';
  const bg = side === 'g' ? 'var(--faction-author-bg)' : 'var(--faction-partner-bg)';

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
      {/* 상단: 색점 + 라벨 + 메타(라벨 바로 오른쪽) / 우측 투표 버튼 */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 6 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 6, minWidth: 0 }}>
          <span style={{ width: 8, height: 8, borderRadius: '50%', background: c, flexShrink: 0 }} />
          <span style={{ fontSize: 11, color: c, fontWeight: 500, flexShrink: 0 }}>{label}</span>
          {meta && (
            <span style={{ fontSize: 11, color: 'var(--L-sub)', whiteSpace: 'nowrap' }}>
              {meta}
            </span>
          )}
        </div>
        {onVote ? (
          <button
            type="button"
            data-testid={`story-vote-btn-${side}`}
            onClick={(e) => {
              e.stopPropagation();
              if (!voteDisabled) onVote();
            }}
            disabled={voteDisabled}
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: 5,
              fontSize: 11.5,
              fontWeight: 500,
              padding: '5px 11px',
              borderRadius: 999,
              cursor: voteDisabled ? 'default' : 'pointer',
              background: voted ? c : 'transparent',
              color: voted ? '#fff' : c,
              border: `1px solid ${c}`,
              opacity: voteDisabled && !voted ? 0.45 : 1,
              fontFamily: 'inherit',
            }}
          >
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7">
              <path d="M9 11l3-8 3 8M5 11h14v8a2 2 0 01-2 2H7a2 2 0 01-2-2z" strokeLinejoin="round" />
            </svg>
            {voted ? '완료' : '투표'}
          </button>
        ) : clamp && onMore ? (
          <span
            onClick={(e) => {
              e.stopPropagation();
              onMore();
            }}
            style={{ fontSize: 11, color: c, cursor: 'pointer' }}
          >
            더 보기 ›
          </span>
        ) : null}
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
