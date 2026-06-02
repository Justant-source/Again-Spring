'use client';

import { useState } from 'react';
import { commentApi } from '@/lib/api/community/commentApi';
import { postApi } from '@/lib/api/community/postApi';

interface ReportModalProps {
  isOpen: boolean;
  onClose: () => void;
  postId?: string;
  commentId?: number;
  authorId?: string;
}

const REPORT_REASONS = ['욕설 · 비방', '혐오 · 차별 표현', '개인정보 노출', '광고 · 스팸', '허위 · 도배'];

export function ReportModal({
  isOpen,
  onClose,
  postId,
  commentId,
  authorId,
}: ReportModalProps) {
  const [selectedReason, setSelectedReason] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  if (!isOpen) return null;

  const handleReport = async () => {
    if (!selectedReason) return;

    setIsSubmitting(true);
    try {
      if (commentId && postId) {
        await commentApi.report(postId, commentId, selectedReason);
      } else if (postId) {
        await postApi.report(postId, selectedReason);
      }
      setSelectedReason(null);
      onClose();
    } catch (err) {
      console.error('Failed to report:', err);
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleBlockUser = async () => {
    if (!authorId) return;

    try {
      await commentApi.blockUser(authorId);
    } catch (err) {
      console.error('Failed to block user:', err);
    }
  };

  return (
    <>
      {/* 배경 오버레이 */}
      <div
        onClick={onClose}
        style={{
          position: 'fixed',
          top: 0,
          left: 0,
          right: 0,
          bottom: 0,
          background: 'rgba(0, 0, 0, 0.5)',
          zIndex: 999,
        }}
      />

      {/* 바텀시트 */}
      <div
        style={{
          position: 'fixed',
          bottom: 0,
          left: 0,
          right: 0,
          zIndex: 1000,
          background: 'white',
          borderTopLeftRadius: 16,
          borderTopRightRadius: 16,
          maxHeight: '80vh',
          overflow: 'auto',
          paddingBottom: 'max(20px, env(safe-area-inset-bottom, 0px))',
        }}
      >
        {/* 드래그 핸들 */}
        <div
          style={{
            display: 'flex',
            justifyContent: 'center',
            padding: '12px 0',
            borderBottom: '1px solid var(--P-border)',
          }}
        >
          <div
            style={{
              width: 36,
              height: 4,
              background: 'var(--P-sub)',
              borderRadius: 2,
              opacity: 0.3,
            }}
          />
        </div>

        {/* 헤더 + 설명 */}
        <div style={{ padding: '16px' }}>
          <h2 style={{ fontSize: 16, fontWeight: 600, color: 'var(--P-ink)', marginBottom: 8 }}>
            신고 사유
          </h2>
          <p style={{ fontSize: 12, color: 'var(--P-sub)', lineHeight: 1.6 }}>
            검토 후 조치하며, 신고는 익명이에요.
          </p>
        </div>

        {/* 사유 선택 */}
        <div style={{ padding: '0 16px 16px' }}>
          {REPORT_REASONS.map((reason) => (
            <button
              key={reason}
              onClick={() => setSelectedReason(reason)}
              style={{
                width: '100%',
                padding: '12px 14px',
                marginBottom: 8,
                background: selectedReason === reason ? 'var(--P-a)' : 'var(--P-card)',
                border: `1px solid ${selectedReason === reason ? 'var(--P-a)' : 'var(--P-border)'}`,
                borderRadius: 8,
                fontSize: 13,
                color: 'var(--P-ink)',
                textAlign: 'left',
                cursor: 'pointer',
                transition: 'all 0.2s',
              }}
              onMouseEnter={(e) => {
                const target = e.currentTarget as HTMLButtonElement;
                if (selectedReason !== reason) {
                  target.style.borderColor = 'var(--P-sub)';
                  target.style.background = '#F9F6F0';
                }
              }}
              onMouseLeave={(e) => {
                const target = e.currentTarget as HTMLButtonElement;
                if (selectedReason !== reason) {
                  target.style.borderColor = 'var(--P-border)';
                  target.style.background = 'var(--P-card)';
                }
              }}
            >
              {reason}
            </button>
          ))}
        </div>

        {/* 하단 버튼 */}
        <div style={{ padding: '0 16px 16px', display: 'flex', flexDirection: 'column', gap: 8 }}>
          <button
            onClick={handleReport}
            disabled={!selectedReason || isSubmitting}
            style={{
              padding: '12px 14px',
              background: selectedReason && !isSubmitting ? 'var(--P-ink)' : '#D0D0D0',
              color: 'white',
              border: 'none',
              borderRadius: 8,
              fontSize: 13,
              fontWeight: 600,
              cursor: selectedReason && !isSubmitting ? 'pointer' : 'default',
              transition: 'all 0.2s',
            }}
            onMouseEnter={(e) => {
              if (selectedReason && !isSubmitting) {
                (e.currentTarget as HTMLButtonElement).style.opacity = '0.85';
              }
            }}
            onMouseLeave={(e) => {
              if (selectedReason && !isSubmitting) {
                (e.currentTarget as HTMLButtonElement).style.opacity = '1';
              }
            }}
          >
            신고하기
          </button>

          {authorId && (
            <button
              onClick={handleBlockUser}
              style={{
                padding: '12px 14px',
                background: 'white',
                color: 'var(--P-ink)',
                border: '1px solid var(--P-border)',
                borderRadius: 8,
                fontSize: 13,
                fontWeight: 500,
                cursor: 'pointer',
                transition: 'all 0.2s',
              }}
              onMouseEnter={(e) => {
                const target = e.currentTarget as HTMLButtonElement;
                target.style.borderColor = 'var(--P-ink)';
                target.style.background = '#F9F6F0';
              }}
              onMouseLeave={(e) => {
                const target = e.currentTarget as HTMLButtonElement;
                target.style.borderColor = 'var(--P-border)';
                target.style.background = 'white';
              }}
            >
              이 사용자 차단
            </button>
          )}
        </div>
      </div>
    </>
  );
}
