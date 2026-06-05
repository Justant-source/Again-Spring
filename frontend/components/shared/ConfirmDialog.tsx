'use client';

interface ConfirmDialogProps {
  open: boolean;
  title: string;
  message?: string;
  confirmLabel?: string;
  cancelLabel?: string;
  /** true이면 확인 버튼을 테라코타(--L-point)로 표시 (기본값: true) */
  destructive?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}

export function ConfirmDialog({
  open,
  title,
  message,
  confirmLabel = '삭제',
  cancelLabel = '취소',
  destructive = true,
  onConfirm,
  onCancel,
}: ConfirmDialogProps) {
  if (!open) return null;

  const confirmColor = destructive ? 'var(--L-point)' : 'var(--faction-partner)';

  return (
    <>
      <div
        style={{ position: 'fixed', inset: 0, zIndex: 300, background: 'rgba(0,0,0,0.35)' }}
        onClick={onCancel}
      />
      <div
        role="dialog"
        aria-modal="true"
        style={{
          position: 'fixed',
          bottom: 0,
          left: 0,
          right: 0,
          maxWidth: 640,
          margin: '0 auto',
          zIndex: 301,
          background: 'var(--L-card)',
          borderRadius: '14px 14px 0 0',
          padding: '16px 24px 40px',
        }}
      >
        {/* 드래그 핸들 */}
        <div
          style={{
            width: 32,
            height: 3,
            background: 'var(--L-border)',
            borderRadius: 2,
            margin: '0 auto 22px',
          }}
        />

        {/* 제목 */}
        <p
          style={{
            margin: '0 0 6px',
            fontSize: 15,
            fontWeight: 500,
            color: 'var(--L-ink)',
            fontFamily: 'var(--font-sans)',
            textAlign: 'center',
            lineHeight: 1.4,
          }}
        >
          {title}
        </p>

        {/* 부가 설명 */}
        {message && (
          <p
            style={{
              margin: '0 0 0',
              fontSize: 13,
              color: 'var(--L-sub)',
              fontFamily: 'var(--font-sans)',
              textAlign: 'center',
              lineHeight: 1.55,
            }}
          >
            {message}
          </p>
        )}

        {/* 버튼 */}
        <div style={{ display: 'flex', gap: 10, marginTop: 24 }}>
          <button
            data-testid="confirm-dialog-cancel"
            onClick={onCancel}
            style={{
              flex: 1,
              padding: '12px 0',
              background: 'var(--L-bg)',
              border: '1px solid var(--L-border)',
              borderRadius: 4,
              fontSize: 14,
              fontWeight: 400,
              color: 'var(--L-sub)',
              fontFamily: 'var(--font-sans)',
              cursor: 'pointer',
            }}
          >
            {cancelLabel}
          </button>
          <button
            data-testid="confirm-dialog-confirm"
            onClick={onConfirm}
            style={{
              flex: 1,
              padding: '12px 0',
              background: 'transparent',
              border: `1px solid ${confirmColor}`,
              borderRadius: 4,
              fontSize: 14,
              fontWeight: 500,
              color: confirmColor,
              fontFamily: 'var(--font-sans)',
              cursor: 'pointer',
            }}
          >
            {confirmLabel}
          </button>
        </div>
      </div>
    </>
  );
}
