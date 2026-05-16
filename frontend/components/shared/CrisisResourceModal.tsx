'use client';

import { useEffect } from 'react';
import { CRISIS_RESOURCES } from '@/lib/constants/crisisResources';
import { CrisisResources } from '@/components/icons/CrisisResources';

export function CrisisResourceModal({
  open,
  onClose,
  severity = 'advisory',
}: {
  open: boolean;
  onClose: () => void;
  severity?: 'critical' | 'advisory';
}) {
  // Trap focus and handle ESC
  useEffect(() => {
    if (!open) return;

    document.body.style.overflow = 'hidden';
    return () => {
      document.body.style.overflow = '';
    };
  }, [open]);

  if (!open) return null;

  const isCritical = severity === 'critical';
  const title = '중요한 안내';
  const bodyText = isCritical
    ? '말씀해주신 상황은 저희 서비스의 범위를 넘어서는 매우 중요한 문제예요. 지금 바로 전문 기관의 도움을 받아주세요.'
    : '법적 결정은 저희 서비스가 도와드릴 수 없어요. 이 서비스는 관계 회복을 위한 대화 정리를 돕는 것이 목표입니다.';

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="crisis-title"
      style={{
        position: 'fixed',
        inset: 0,
        background: 'rgba(0, 0, 0, 0.5)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        zIndex: 9999,
      }}
    >
      <div
        style={{
          background: 'white',
          borderRadius: '16px',
          padding: '32px 28px',
          maxWidth: '340px',
          maxHeight: '90vh',
          overflowY: 'auto',
          boxShadow: '0 4px 20px rgba(0, 0, 0, 0.15)',
        }}
      >
        {/* Title */}
        <div
          id="crisis-title"
          style={{
            fontFamily: 'var(--font-serif)',
            fontSize: '20px',
            fontWeight: 500,
            color: '#1A1A1A',
            marginBottom: '16px',
            textAlign: 'center',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            gap: 8,
          }}
        >
          <CrisisResources width={20} height={20} color="#C0392B" />
          {title}
        </div>

        {/* Body text */}
        <div
          style={{
            fontSize: '14px',
            lineHeight: 1.7,
            color: '#4A4A4A',
            marginBottom: '28px',
            textAlign: 'center',
          }}
        >
          {bodyText}
        </div>

        {/* Crisis resources list */}
        <div style={{ marginBottom: '28px' }}>
          {CRISIS_RESOURCES.map((resource, idx) => (
            <div
              key={idx}
              style={{
                paddingBottom: '16px',
                marginBottom: '16px',
                borderBottom: idx < CRISIS_RESOURCES.length - 1 ? '1px solid #E8E6E0' : 'none',
              }}
            >
              <div
                style={{
                  fontSize: '13px',
                  fontWeight: 500,
                  color: '#1A1A1A',
                  marginBottom: '6px',
                }}
              >
                {resource.label}
              </div>
              <div
                style={{
                  fontSize: '12px',
                  color: '#6B7A8F',
                  marginBottom: '4px',
                }}
              >
                <a
                  href={`tel:${resource.phone.replace(/\D/g, '')}`}
                  style={{
                    color: '#2563EB',
                    textDecoration: 'none',
                    fontWeight: 500,
                  }}
                >
                  {resource.phone}
                </a>
                {' · '}
                {resource.hours}
              </div>
              <div
                style={{
                  fontSize: '12px',
                  color: '#8A8A8A',
                  lineHeight: 1.5,
                }}
              >
                {resource.description}
              </div>
            </div>
          ))}
        </div>

        {/* Buttons — 위기 모달은 dismiss 마찰 유지(절대 규칙)이라 '닫기' 한 개만 둠 */}
        <div
          style={{
            display: 'flex',
            flexDirection: 'column',
            gap: '8px',
          }}
        >
          <button
            onClick={onClose}
            style={{
              padding: '12px 16px',
              background: '#1A1A1A',
              color: 'white',
              border: 'none',
              borderRadius: '3px',
              fontSize: '14px',
              fontWeight: 500,
              cursor: 'pointer',
            }}
          >
            닫기
          </button>
        </div>
      </div>
    </div>
  );
}
