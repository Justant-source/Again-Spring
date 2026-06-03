'use client';

interface FeedCardProps {
  /** 대분류 라벨 (연인/부부/...) */
  cat: string;
  /** 작성자 닉네임 */
  id: string;
  /** 상대 시간 ("방금" / "12분 전" / "1시간 전") */
  time: string;
  title: string;
  /** 본문 미리보기 (2줄 clamp) */
  body?: string;
  /** 작성자(피치) 공감 비율 0~100 */
  g: number;
  /** 투표 수 (0이면 '투표' 라벨) */
  votes?: number;
  /** 댓글 수 (0이면 '댓글' 라벨) */
  c?: number;
  /** 조회 수 (없으면 '조회' 라벨) */
  views?: number | string;
  href: string;
}

const STAT_COL: React.CSSProperties = {
  flex: 1,
  display: 'inline-flex',
  alignItems: 'center',
  justifyContent: 'center',
  gap: 5,
};

export function FeedCard({ cat, id, time, title, body, g, votes, c, views, href }: FeedCardProps) {
  return (
    <a
      href={href}
      style={{
        display: 'block',
        background: 'var(--L-card)',
        border: '1px solid var(--L-border)',
        borderRadius: 8,
        overflow: 'hidden',
        textDecoration: 'none',
        color: 'inherit',
      }}
    >
      <div style={{ padding: '15px 16px' }}>
        {/* 상단: 대분류 · 아이디 · 시간 */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 6, minWidth: 0 }}>
          <span style={{ fontSize: 11, color: 'var(--L-bg)', background: 'var(--L-ink)', borderRadius: 999, padding: '2px 9px', flexShrink: 0 }}>{cat}</span>
          <span style={{ fontSize: 12, color: 'var(--L-ink)', fontWeight: 500, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{id}</span>
          <span style={{ fontSize: 11.5, color: 'var(--L-sub)', flexShrink: 0 }}>{time}</span>
        </div>

        {/* 제목 bold */}
        <div style={{ fontSize: 15.5, fontWeight: 700, color: 'var(--L-ink)', marginTop: 12, lineHeight: 1.4 }}>{title}</div>

        {/* 본문 2줄 */}
        {body && (
          <div style={{ fontSize: 13, color: 'var(--L-sub)', lineHeight: 1.6, marginTop: 6, display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden' }}>{body}</div>
        )}

        {/* 하단: 투표수 · 댓글 · 조회수 — 1/3씩 균등 배치, 0이면 텍스트 */}
        <div style={{ display: 'flex', marginTop: 13, fontSize: 12, color: 'var(--L-sub)' }}>
          <span style={STAT_COL}>
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6"><path d="M9 11l3-8 3 8M5 11h14v8a2 2 0 01-2 2H7a2 2 0 01-2-2z" strokeLinejoin="round" /></svg>
            {votes ? votes : '투표'}
          </span>
          <span style={STAT_COL}>
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6"><path d="M21 15a2 2 0 01-2 2H8l-4 4V5a2 2 0 012-2h13a2 2 0 012 2z" strokeLinejoin="round" /></svg>
            {c ? c : '댓글'}
          </span>
          <span style={STAT_COL}>
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6"><path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7-10-7-10-7z" /><circle cx="12" cy="12" r="2.5" /></svg>
            {views ? views : '조회'}
          </span>
        </div>
      </div>

      {/* 투표 현황 — 카드 하단에 꽉 차게 (비율 숫자 없음) */}
      <div style={{ display: 'flex', height: 7 }}>
        <div style={{ width: g + '%', background: 'var(--faction-author)' }} />
        <div style={{ flex: 1, background: 'var(--faction-partner)' }} />
      </div>
    </a>
  );
}
