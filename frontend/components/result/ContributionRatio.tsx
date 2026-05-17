// Phase 2 + Phase 7: 법적 안내문구 박스 추가
'use client';

import type { ContributionRatio as ContributionRatioType } from '@/lib/types';
import { Conversation } from '@/components/icons/Conversation';

interface ContributionRatioProps {
  ratio: ContributionRatioType | null;
  nameA?: string;
  nameB?: string;
  conflictType?: 'factual' | 'difference' | 'mixed' | null;
  isSoloMode?: boolean;
  onInvite?: () => void;
}

export function ContributionRatio({
  ratio,
  nameA = '서현',
  nameB = '준호',
  conflictType,
  isSoloMode,
  onInvite,
}: ContributionRatioProps) {
  if (isSoloMode) {
    return (
      <div>
        <div style={{ fontSize: 12, color: 'var(--P-sub)', marginBottom: 10 }}>화해 기여도</div>
        <div
          style={{
            background: 'var(--P-card)',
            border: '1px solid var(--P-border)',
            borderRadius: 12,
            padding: '18px 16px',
            textAlign: 'center',
          }}
        >
          <div style={{ marginBottom: 8, display: 'flex', justifyContent: 'center' }}>
            <Conversation width={28} height={28} color="var(--P-sub)" />
          </div>
          <div style={{ fontSize: 13, color: 'var(--P-ink)', lineHeight: 1.7, marginBottom: 14 }}>
            화해 기여도는 상대방이 함께<br />참여했을 때 안내드릴 수 있어요.
            <br />
            <span style={{ color: 'var(--P-sub)', fontSize: 12 }}>
              지금은 {nameA}님 한 분의 관점으로 분석한 결과예요.
            </span>
          </div>
          {onInvite && (
            <button
              onClick={onInvite}
              className="btn-P"
              style={{ fontSize: 13 }}
            >
              상대방 초대하기 →
            </button>
          )}
        </div>
        {/* 법적 안내문구 박스 — Solo 모드에도 항상 표시 (절대 불변 규칙 #5) */}
        <div
          data-testid="ratio-legal-notice"
          style={{
            marginTop: 14,
            background: 'color-mix(in srgb, var(--P-sub) 6%, transparent)',
            border: '1px solid color-mix(in srgb, var(--P-sub) 15%, transparent)',
            borderRadius: 10,
            padding: '12px 14px',
            fontSize: 12,
            color: 'var(--P-sub)',
            lineHeight: 1.7,
          }}
        >
          이 수치는 두 분의 회복 시작점을 부드럽게 안내하기 위한 참고용이에요.
          법적 판단이나 과실 비율과는 무관하며, AI 분析에는 한계가 있어요.
          깊은 갈등은 전문 상담을 권해드려요.
        </div>
      </div>
    );
  }

  if (!ratio) return null;

  const aPercent = ratio.a;
  const bPercent = ratio.b;

  const extraNote =
    conflictType === 'difference'
      ? '두 분 모두 잘못한 게 아니라 다를 뿐이에요.'
      : conflictType === 'factual'
        ? '이번 상황에서는 한쪽의 책임이 좀 더 분명해 보여요.'
        : null;

  return (
    <div>
      <div style={{ fontSize: 12, color: 'var(--P-sub)', marginBottom: 10 }}>화해 기여도</div>

      <div style={{ display: 'flex', height: 44, borderRadius: 10, overflow: 'hidden' }}>
        <div
          style={{
            flex: aPercent,
            background: 'var(--P-a)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            color: '#5C4030',
            fontWeight: 500,
            fontSize: 14,
          }}
        >
          {nameA} · {aPercent}
        </div>
        <div
          style={{
            flex: bPercent,
            background: 'var(--P-b)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            color: '#3F4F45',
            fontWeight: 500,
            fontSize: 14,
          }}
        >
          {nameB} · {bPercent}
        </div>
      </div>

      <div style={{ marginTop: 14, fontSize: 13, lineHeight: 1.7 }}>
        <div style={{ display: 'flex', gap: 8, alignItems: 'flex-start' }}>
          <span style={{ color: '#A83020', fontWeight: 500, minWidth: 56 }}>{nameA}</span>
          <span>{ratio.label.a}</span>
        </div>
        <div style={{ display: 'flex', gap: 8, alignItems: 'flex-start', marginTop: 6 }}>
          <span style={{ color: '#2E7040', fontWeight: 500, minWidth: 56 }}>{nameB}</span>
          <span>{ratio.label.b}</span>
        </div>
      </div>

      {/* 법적 안내문구 박스 (Phase 7) */}
      <div
        data-testid="ratio-legal-notice"
        style={{
          marginTop: 14,
          background: 'color-mix(in srgb, var(--P-sub) 6%, transparent)',
          border: '1px solid color-mix(in srgb, var(--P-sub) 15%, transparent)',
          borderRadius: 10,
          padding: '12px 14px',
          fontSize: 12,
          color: 'var(--P-sub)',
          lineHeight: 1.7,
        }}
      >
        {extraNote && (
          <div style={{ marginBottom: 6, color: 'var(--P-ink)', fontWeight: 500, display: 'flex', alignItems: 'flex-start', gap: 5 }}>
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" style={{ flexShrink: 0, marginTop: 1 }} aria-hidden="true">
              <circle cx="12" cy="12" r="9" />
              <line x1="12" y1="8" x2="12" y2="12" />
              <circle cx="12" cy="15.5" r="0.75" fill="currentColor" stroke="none" />
            </svg>
            {extraNote}
          </div>
        )}
        이 수치는 두 분의 회복 시작점을 부드럽게 안내하기 위한 참고용이에요.
        법적 판단이나 과실 비율과는 무관하며, AI 분析에는 한계가 있어요.
        깊은 갈등은 전문 상담을 권해드려요.
      </div>
    </div>
  );
}
