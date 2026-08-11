'use client';

import type { CSSProperties } from 'react';

interface InviteManageActionsProps {
  canClaim: boolean;
  canEdit: boolean;
  canDelete: boolean;
  /** 등록 회원만 claim CTA 표시 */
  isRegistered: boolean;
  busy: boolean;
  onClaim: () => void;
  onEdit: () => void;
  onDelete: () => void;
}

const btnBase: CSSProperties = {
  flex: 1,
  padding: '12px 14px',
  borderRadius: 8,
  fontSize: 13,
  fontWeight: 500,
  cursor: 'pointer',
  border: '1px solid var(--P-border)',
  background: 'var(--P-card)',
  color: 'var(--P-ink)',
};

export function InviteManageActions({
  canClaim,
  canEdit,
  canDelete,
  isRegistered,
  busy,
  onClaim,
  onEdit,
  onDelete,
}: InviteManageActionsProps) {
  const showClaim = canClaim && isRegistered;

  if (!showClaim && !canEdit && !canDelete) return null;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
      {showClaim && (
        <button
          type="button"
          data-testid="invite-claim-btn"
          onClick={onClaim}
          disabled={busy}
          style={{
            ...btnBase,
            width: '100%',
            background: 'var(--faction-partner)',
            color: 'white',
            border: 'none',
            opacity: busy ? 0.6 : 1,
          }}
        >
          내 계정으로 연결
        </button>
      )}
      <div style={{ display: 'flex', gap: 10 }}>
        {canEdit && (
          <button
            type="button"
            data-testid="invite-edit-btn"
            onClick={onEdit}
            disabled={busy}
            style={{ ...btnBase, opacity: busy ? 0.6 : 1 }}
          >
            수정
          </button>
        )}
        {canDelete && (
          <button
            type="button"
            data-testid="invite-delete-btn"
            onClick={onDelete}
            disabled={busy}
            style={{
              ...btnBase,
              color: '#C33',
              borderColor: '#F99',
              opacity: busy ? 0.6 : 1,
            }}
          >
            삭제
          </button>
        )}
      </div>
    </div>
  );
}
