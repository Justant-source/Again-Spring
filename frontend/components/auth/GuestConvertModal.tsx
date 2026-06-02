'use client';

import React from 'react';

interface GuestConvertModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSignup: () => void;
}

export function GuestConvertModal({ isOpen, onClose, onSignup }: GuestConvertModalProps) {
  if (!isOpen) return null;

  return (
    <div
      role="dialog"
      aria-modal="true"
      style={{
        position: 'fixed',
        inset: 0,
        background: 'rgba(0, 0, 0, 0.3)',
        display: 'flex',
        alignItems: 'flex-end',
        zIndex: 9999,
      }}
      onClick={onClose}
    >
      <div
        style={{
          width: '100%',
          background: 'var(--L-bg)',
          borderRadius: '20px 20px 0 0',
          padding: '24px',
          display: 'flex',
          flexDirection: 'column',
          gap: 16,
          maxHeight: '80vh',
          overflow: 'auto',
        }}
        onClick={(e) => e.stopPropagation()}
      >
        {/* 드래그 핸들 */}
        <div style={{ display: 'flex', justifyContent: 'center', marginBottom: 8 }}>
          <div
            style={{
              width: 40,
              height: 4,
              background: 'var(--L-border)',
              borderRadius: 2,
            }}
          />
        </div>

        {/* 제목 */}
        <div className="serif" style={{ fontSize: 20, lineHeight: 1.6, marginBottom: 12 }}>
          답이 오면 알려드릴까요?
        </div>

        {/* 설명 */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          <p style={{ fontSize: 14, color: 'var(--L-ink)', lineHeight: 1.7, margin: 0 }}>
            가입하면 내 사연에 투표·댓글이 달리거나 결과가 마감될 때 알림을 받아요.
          </p>
        </div>

        {/* 버튼 */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 10, marginTop: 8 }}>
          <button
            onClick={onSignup}
            style={{
              padding: '14px',
              background: 'var(--L-ink)',
              color: 'var(--L-bg)',
              border: 'none',
              borderRadius: 8,
              fontSize: 14,
              fontWeight: 600,
              cursor: 'pointer',
              width: '100%',
            }}
          >
            가입하고 알림 받기
          </button>
          <button
            onClick={onClose}
            style={{
              padding: '14px',
              background: 'transparent',
              color: 'var(--L-ink)',
              border: '1px solid var(--L-border)',
              borderRadius: 8,
              fontSize: 14,
              fontWeight: 600,
              cursor: 'pointer',
              width: '100%',
            }}
          >
            게스트로 계속
          </button>
        </div>

        {/* 하단 안내 */}
        <div style={{ fontSize: 12, color: 'var(--L-sub)', textAlign: 'center', marginTop: 8 }}>
          투표와 댓글은 게스트도 자유롭게 할 수 있어요
        </div>
      </div>
    </div>
  );
}
