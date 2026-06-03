'use client';

import { JurorCard } from './JurorCard';
import { VoteBar } from './VoteBar';
import LegalNoticeBox from '@/components/shared/LegalNoticeBox';
import { AUTHOR, PARTNER } from '@/lib/constants/factionColors';
import type { JuryResult } from '@/lib/api/community/postApi';

interface JurySectionProps {
  jury: JuryResult | null;
  /** post.jurorCount ?? 0 — 0이면 섹션 전체 숨김 */
  jurorCount: number;
}

export function JurySection({ jury, jurorCount }: JurySectionProps) {
  // 배심원 없이 올린 글이면 아무것도 렌더 안 함
  if (jurorCount === 0) return null;

  // 아직 생성 중 (null 이거나 jurors 수 미달)
  const isComplete = jury !== null && jury.jurors.length >= jurorCount;

  if (!isComplete) {
    return (
      <div data-testid="jury-section">
        <div
          data-testid="jury-pending"
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 10,
            padding: '14px 16px',
            background: 'var(--P-card)',
            border: '1px solid var(--P-border)',
            borderRadius: 14,
          }}
        >
          {/* 스피너 */}
          <div
            style={{
              width: 18,
              height: 18,
              borderRadius: '50%',
              border: `2px solid color-mix(in srgb, var(--P-sub) 25%, transparent)`,
              borderTopColor: 'var(--faction-author)',
              flexShrink: 0,
              animation: 'jury-spin 0.8s linear infinite',
            }}
          />
          <span style={{ fontSize: 13, color: 'var(--P-sub)', fontFamily: 'var(--font-serif)' }}>
            AI 배심원이 사연을 읽고 있어요…
          </span>
          <style>{`
            @keyframes jury-spin {
              to { transform: rotate(360deg); }
            }
          `}</style>
        </div>
      </div>
    );
  }

  // ── 완료 상태 렌더 ──────────────────────────────────────────────

  // 공감 분포 계산 (작성자 비율)
  const authorDist = jury.distribution.find(d => d.label.includes('작성자'));
  const authorPct = Math.round(authorDist?.percentage ?? 50);

  // 요약 줄 (BE가 summaryLine 반환 시 우선; 없으면 FE 계산)
  const authorCount = authorDist?.count ?? 0;
  const total = jury.jurors.length;
  const summaryLine =
    jury.summaryLine ??
    `AI 배심원 ${total}인 중 ${authorCount}인이 작성자에 공감했어요`;

  return (
    <div data-testid="jury-section" style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
      {/* 헤더 */}
      <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--P-ink)' }}>
        AI 배심원 {jurorCount}인의 시선
      </div>

      {/* 공감 분포 바 */}
      <div data-testid="jury-distribution-bar">
        <VoteBar authorPct={authorPct} />
      </div>

      {/* 요약 줄 */}
      <div
        data-testid="jury-summary"
        style={{ fontSize: 12.5, color: 'var(--P-sub)', textAlign: 'center' }}
      >
        {summaryLine}
      </div>

      {/* 배심원 카드 */}
      {jury.jurors.map((j, i) => {
        const isAuthorSide = j.chosenOptionLabel.includes('작성자');
        return (
          <JurorCard
            key={i}
            name={`${j.ageGroup} ${j.gender}`}
            lens={isAuthorSide ? '작성자에 공감' : '상대방에 공감'}
            text={j.empathyComment}
            accent={isAuthorSide ? AUTHOR : PARTNER}
          />
        );
      })}

      {/* 법적 고지 */}
      <LegalNoticeBox message={jury.legalNotice} testId="jury-legal-notice" />
    </div>
  );
}
