'use client';

interface Props {
  onClose: () => void;
}

export function CrisisModal({ onClose }: Props) {
  return (
    <div
      style={{
        position: 'fixed',
        inset: 0,
        background: 'rgba(0,0,0,0.5)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        zIndex: 999,
        padding: 20,
      }}
      onClick={onClose}
    >
      <div
        onClick={e => e.stopPropagation()}
        style={{
          background: 'var(--P-bg)',
          borderRadius: 14,
          padding: '24px 22px',
          maxWidth: 360,
          width: '100%',
        }}
      >
        <div
          className="serif"
          style={{
            fontSize: 18,
            marginBottom: 12,
            color: 'var(--P-ink)',
          }}
        >
          지금 안전이 가장 중요해요
        </div>
        <div
          style={{
            fontSize: 13,
            lineHeight: 1.7,
            color: 'var(--P-ink)',
            marginBottom: 16,
          }}
        >
          이 상황은 저보다 더 전문적인 도움이 필요한 일이에요.
          <br />
          아래 번호로 전화해보시면 24시간 도움을 받을 수 있어요.
        </div>
        <div
          style={{
            padding: '12px 14px',
            background: 'var(--P-card)',
            borderRadius: 10,
            fontSize: 13,
            color: 'var(--P-ink)',
            lineHeight: 1.9,
            marginBottom: 16,
          }}
        >
          <div>
            · 여성긴급전화 —{' '}
            <a
              href="tel:1366"
              style={{ color: 'var(--P-ink)', fontWeight: 500 }}
            >
              1366
            </a>
          </div>
          <div>
            · 자살예방상담 —{' '}
            <a
              href="tel:1393"
              style={{ color: 'var(--P-ink)', fontWeight: 500 }}
            >
              1393
            </a>
          </div>
          <div>
            · 가정폭력 —{' '}
            <a
              href="tel:132"
              style={{ color: 'var(--P-ink)', fontWeight: 500 }}
            >
              132
            </a>
          </div>
          <div>
            · 아동학대 —{' '}
            <a
              href="tel:112"
              style={{ color: 'var(--P-ink)', fontWeight: 500 }}
            >
              112
            </a>
          </div>
        </div>
        <button
          onClick={onClose}
          style={{
            width: '100%',
            padding: 12,
            background: 'var(--P-ink)',
            color: 'var(--P-bg)',
            border: 'none',
            borderRadius: 10,
            fontSize: 14,
            cursor: 'pointer',
          }}
        >
          알겠어요
        </button>
      </div>
    </div>
  );
}
