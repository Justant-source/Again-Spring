// ✅ MOCKUP APPLIED — source: FORBIDDEN_WORDS.md crisis resource modal (inline variant)

'use client';

import { useState } from 'react';
import { CRISIS_RESOURCES } from '@/lib/constants/crisisResources';

export function CrisisInline() {
  const [showModal, setShowModal] = useState(false);

  const firstThree = CRISIS_RESOURCES.slice(0, 3);

  return (
    <>
      <div
        style={{
          border: '1px solid var(--L-border)',
          background: 'var(--L-card)',
          borderRadius: '3px',
          padding: '14px 16px',
          marginBottom: '20px',
        }}
      >
        <div
          style={{
            fontSize: '12px',
            fontWeight: 500,
            color: 'var(--L-ink)',
            marginBottom: '12px',
          }}
        >
          🚨 중요한 안내
        </div>
        <div
          style={{
            fontSize: '13px',
            lineHeight: 1.6,
            color: 'var(--L-sub)',
            marginBottom: '12px',
          }}
        >
          말씀해주신 상황은 저희 서비스의 범위를 넘어서는 매우 중요한 문제예요.
          지금 바로 전문 기관의 도움을 받아주세요.
        </div>

        <div style={{ marginBottom: '12px' }}>
          {firstThree.map((resource) => (
            <div
              key={resource.phone}
              style={{
                fontSize: '12px',
                marginBottom: '8px',
                paddingBottom: '8px',
                borderBottom:
                  firstThree.indexOf(resource) < firstThree.length - 1
                    ? '1px solid var(--L-border)'
                    : 'none',
              }}
            >
              <div
                style={{
                  fontWeight: 500,
                  color: 'var(--L-ink)',
                }}
              >
                {resource.label}
              </div>
              <div
                style={{
                  fontSize: '11px',
                  color: 'var(--L-sub)',
                }}
              >
                📞 {resource.phone} · {resource.hours}
              </div>
            </div>
          ))}
        </div>

        <button
          onClick={() => setShowModal(true)}
          style={{
            width: '100%',
            background: 'var(--L-ink)',
            color: 'var(--L-bg)',
            border: 'none',
            borderRadius: '3px',
            padding: '10px 14px',
            fontSize: '13px',
            fontWeight: 500,
            cursor: 'pointer',
            transition: 'opacity 0.15s',
          }}
          onMouseEnter={(e) => (e.currentTarget.style.opacity = '0.88')}
          onMouseLeave={(e) => (e.currentTarget.style.opacity = '1')}
        >
          지금 도움 받기
        </button>
      </div>

      {showModal && (
        <CrisisModal onClose={() => setShowModal(false)} />
      )}
    </>
  );
}

function CrisisModal({ onClose }: { onClose: () => void }) {
  return (
    <div
      style={{
        position: 'fixed',
        inset: 0,
        background: 'rgba(0, 0, 0, 0.4)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        zIndex: 50,
      }}
      onClick={onClose}
    >
      <div
        style={{
          background: 'var(--L-bg)',
          borderRadius: '3px',
          padding: '28px 24px',
          maxWidth: '320px',
          maxHeight: '80vh',
          overflowY: 'auto',
          boxShadow: '0 8px 40px rgba(60, 40, 20, 0.2)',
        }}
        onClick={(e) => e.stopPropagation()}
      >
        <div
          style={{
            fontSize: '14px',
            fontWeight: 600,
            color: 'var(--L-ink)',
            marginBottom: '16px',
          }}
        >
          🚨 지금 도움을 받으세요
        </div>

        <div style={{ marginBottom: '20px' }}>
          {CRISIS_RESOURCES.map((resource) => (
            <div
              key={resource.phone}
              style={{
                fontSize: '12px',
                marginBottom: '16px',
                paddingBottom: '16px',
                borderBottom: '1px solid var(--L-border)',
              }}
            >
              <div
                style={{
                  fontWeight: 500,
                  color: 'var(--L-ink)',
                  marginBottom: '4px',
                }}
              >
                {resource.label}
              </div>
              <div
                style={{
                  fontSize: '11px',
                  color: 'var(--L-sub)',
                  marginBottom: '6px',
                }}
              >
                📞 {resource.phone} · {resource.hours}
              </div>
              <div
                style={{
                  fontSize: '11px',
                  color: 'var(--L-sub)',
                  lineHeight: 1.5,
                }}
              >
                {resource.description}
              </div>
            </div>
          ))}
        </div>

        <button
          onClick={onClose}
          className="btn-L"
          style={{ width: '100%' }}
        >
          닫기
        </button>
      </div>
    </div>
  );
}
