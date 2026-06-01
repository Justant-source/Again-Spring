'use client';

import Link from 'next/link';

const MIN_TURNS = 5;

interface Props {
  isDuo: boolean;
  canFinalize: boolean;
  turnCount?: number;
  canInvite: boolean;
  onOpenInvite?: () => void;
  onFinalize: () => void;
  finalizing?: boolean;
  /** @deprecated SOS 버튼 제거됨 (이모지 금지 정책). 위기 감지는 ChatInput KeywordGuard 자동 트리거. */
  onOpenCrisis?: () => void;
}

export function ChatHeader({ isDuo, canFinalize, turnCount = 0, canInvite, onOpenInvite, onFinalize, finalizing }: Props) {
  return (
    <div style={{
      padding: '12px 16px',
      borderBottom: '1px solid var(--P-border)',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'space-between',
      background: 'var(--P-bg)',
    }}>
      {/* 좌측: 나가기 + 세션 상태 */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
        {/* 명시적 "나가기" — HAX G8/G17: 1탭 탈출 */}
        <Link
          href="/history"
          style={{
            fontSize: 13,
            color: 'var(--P-sub)',
            lineHeight: 1,
            textDecoration: 'none',
            padding: '6px 8px',
            borderRadius: 6,
            border: '1px solid var(--P-border)',
            display: 'flex',
            alignItems: 'center',
            gap: 4,
            whiteSpace: 'nowrap',
          }}
          aria-label="채팅 나가기"
        >
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor"
            strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <polyline points="15 18 9 12 15 6" />
          </svg>
          나가기
        </Link>
        <div style={{
          width: 7, height: 7, borderRadius: 4,
          background: isDuo ? 'var(--P-b)' : 'var(--P-a)',
        }} />
        <div style={{ fontSize: 13, color: 'var(--P-ink)', fontWeight: 500 }}>
          {isDuo ? '두 분과 정리 중' : '중재자와 정리 중'}
        </div>
      </div>

      {/* 우측: 상대 초대 + 정리하기 */}
      <div style={{ display: 'flex', gap: 6 }}>
        {canInvite && onOpenInvite && (
          <button
            onClick={onOpenInvite}
            style={{
              fontSize: 12,
              padding: '6px 10px',
              border: '1px solid var(--P-border)',
              borderRadius: 6,
              background: 'transparent',
              color: 'var(--P-sub)',
              cursor: 'pointer',
            }}
          >
            상대 초대
          </button>
        )}
        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: 4 }}>
          <button
            onClick={canFinalize ? onFinalize : undefined}
            disabled={!canFinalize || finalizing}
            title={!canFinalize ? `5번 이상 대화하면 정리할 수 있어요 (${Math.min(turnCount, MIN_TURNS)}/${MIN_TURNS})` : undefined}
            style={{
              fontSize: 12,
              padding: '6px 10px',
              border: `1px solid ${canFinalize ? 'var(--P-ink)' : 'var(--P-border)'}`,
              borderRadius: 6,
              background: canFinalize ? 'var(--P-ink)' : 'transparent',
              color: canFinalize ? 'var(--P-bg)' : 'var(--P-sub)',
              cursor: canFinalize && !finalizing ? 'pointer' : 'not-allowed',
              opacity: finalizing ? 0.6 : 1,
              transition: 'background 0.2s, border-color 0.2s, color 0.2s',
            }}
          >
            {finalizing ? '정리 중...' : '정리하기'}
          </button>
          {!canFinalize && (
            <div style={{ display: 'flex', gap: 3 }}>
              {Array.from({ length: MIN_TURNS }).map((_, i) => (
                <div
                  key={i}
                  style={{
                    width: 5,
                    height: 5,
                    borderRadius: '50%',
                    background: i < turnCount ? 'var(--P-ink)' : 'var(--P-border)',
                    transition: 'background 0.2s',
                  }}
                />
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
