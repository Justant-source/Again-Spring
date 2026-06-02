'use client';

import React from 'react';

interface GuestNoticeModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSignup: () => void;
  onContinueAsGuest?: () => void;
}

export function GuestNoticeModal({ isOpen, onClose, onSignup, onContinueAsGuest }: GuestNoticeModalProps) {
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

        {/* 게스트 뱃지 */}
        <div
          style={{
            display: 'inline-block',
            padding: '4px 12px',
            background: 'var(--L-card)',
            borderRadius: 6,
            fontSize: 12,
            color: 'var(--L-sub)',
            alignSelf: 'flex-start',
            fontWeight: 600,
          }}
        >
          게스트
        </div>

        {/* 제목 */}
        <div className="serif" style={{ fontSize: 20, lineHeight: 1.6, marginBottom: 12 }}>
          게스트로 올리면<br />이렇게 달라져요
        </div>

        {/* 제약사항 */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          {[
            '올린 뒤 수정·삭제할 수 없어요',
            '내 사연 목록에 저장되지 않아요',
            '상대 초대·결과 알림을 받을 수 없어요',
          ].map((text, idx) => (
            <div key={idx} style={{ display: 'flex', alignItems: 'flex-start', gap: 12 }}>
              <span
                style={{
                  fontSize: 18,
                  color: 'var(--L-point)',
                  fontWeight: 700,
                  lineHeight: 1,
                  marginTop: 1,
                }}
              >
                ✕
              </span>
              <span style={{ fontSize: 14, color: 'var(--L-ink)', lineHeight: 1.6, flex: 1 }}>
                {text}
              </span>
            </div>
          ))}
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
            가입하고 올리기
          </button>
          <button
            onClick={onContinueAsGuest || onClose}
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
            게스트로 올리기
          </button>
        </div>
      </div>
    </div>
  );
}
