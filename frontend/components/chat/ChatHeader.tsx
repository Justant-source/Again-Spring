'use client';

import Link from 'next/link';

interface Props {
  isDuo: boolean;
  canFinalize: boolean;
  canInvite: boolean;
  onOpenInvite?: () => void;
  onFinalize: () => void;
  finalizing?: boolean;
}

export function ChatHeader({ isDuo, canFinalize, canInvite, onOpenInvite, onFinalize, finalizing }: Props) {
  return (
    <div style={{
      padding: '12px 16px',
      borderBottom: '1px solid var(--P-border)',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'space-between',
      background: 'var(--P-bg)',
    }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
        <Link
          href="/history"
          style={{
            fontSize: 18,
            color: 'var(--P-sub)',
            lineHeight: 1,
            textDecoration: 'none',
            padding: '0 4px',
          }}
          aria-label="지난 대화 보기"
        >
          ‹
        </Link>
        <div style={{
          width: 8, height: 8, borderRadius: 4,
          background: isDuo ? 'var(--P-b)' : 'var(--P-a)',
        }} />
        <div style={{ fontSize: 13, color: 'var(--P-ink)', fontWeight: 500 }}>
          {isDuo ? '두 분과 정리 중' : '중재자와 정리 중'}
        </div>
      </div>

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
        {canFinalize && (
          <button
            onClick={onFinalize}
            disabled={finalizing}
            style={{
              fontSize: 12,
              padding: '6px 10px',
              border: '1px solid var(--P-ink)',
              borderRadius: 6,
              background: 'var(--P-ink)',
              color: 'var(--P-bg)',
              cursor: finalizing ? 'not-allowed' : 'pointer',
              opacity: finalizing ? 0.6 : 1,
            }}
          >
            {finalizing ? '정리 중...' : '정리하기'}
          </button>
        )}
      </div>
    </div>
  );
}
