'use client';

interface Props {
  onClose: () => void;
}

export function PartnerJoinedToast({ onClose }: Props) {
  return (
    <div
      style={{
        position: 'fixed',
        top: 20,
        left: 20,
        right: 20,
        padding: '14px 18px',
        background: 'var(--P-ink)',
        color: 'var(--P-bg)',
        borderRadius: 12,
        fontSize: 13,
        zIndex: 1000,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        gap: 12,
        boxShadow: '0 4px 20px rgba(0,0,0,0.15)',
      }}
    >
      <div>
        <div style={{ fontWeight: 500, marginBottom: 2 }}>
          상대가 함께 정리하기 시작했어요
        </div>
        <div style={{ fontSize: 11, opacity: 0.8 }}>
          대화는 서로 보이지 않아요. 그대로 적어주세요.
        </div>
      </div>
      <button
        onClick={onClose}
        style={{
          background: 'transparent',
          border: 'none',
          color: 'var(--P-bg)',
          fontSize: 16,
          cursor: 'pointer',
          padding: 4,
        }}
      >
        ×
      </button>
    </div>
  );
}
