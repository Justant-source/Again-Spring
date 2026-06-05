'use client';

import { useEffect, useRef } from 'react';

interface CommentComposeSheetProps {
  value: string;
  onChange: (v: string) => void;
  onSubmit: () => void;
  onClose: () => void;
  replyTo?: string;
  error?: string | null;
}

export function CommentComposeSheet({
  value,
  onChange,
  onSubmit,
  onClose,
  replyTo,
  error,
}: CommentComposeSheetProps) {
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  useEffect(() => {
    // 짧은 지연 후 포커스 (iOS 키보드 트리거), 커서를 텍스트 끝으로 이동
    const t = setTimeout(() => {
      const el = textareaRef.current;
      if (!el) return;
      el.focus();
      const len = el.value.length;
      el.setSelectionRange(len, len);
    }, 50);
    return () => clearTimeout(t);
  }, []);

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) {
      e.preventDefault();
      onSubmit();
    }
  };

  return (
    <>
      {/* 오버레이 — 댓글 목록 흐림 */}
      <div
        style={{
          position: 'fixed',
          inset: 0,
          zIndex: 200,
          background: 'rgba(0,0,0,0.25)',
        }}
        onClick={onClose}
      />

      {/* 바텀시트 */}
      <div
        style={{
          position: 'fixed',
          left: 0,
          right: 0,
          bottom: 0,
          maxWidth: 640,
          margin: '0 auto',
          zIndex: 201,
          background: 'var(--L-bg)',
          borderTopLeftRadius: 18,
          borderTopRightRadius: 18,
          boxShadow: '0 -8px 24px rgba(60,40,20,0.10)',
          paddingBottom: 'max(14px, env(safe-area-inset-bottom, 14px))',
        }}
        onClick={(e) => e.stopPropagation()}
      >
        {/* 드래그 핸들 */}
        <div
          style={{
            width: 44,
            height: 4,
            borderRadius: 2,
            background: 'var(--L-border)',
            margin: '10px auto 0',
          }}
        />

        {/* 대댓글 대상 표시 */}
        {replyTo && (
          <div style={{ padding: '10px 20px 0', fontSize: 12, color: 'var(--L-sub)' }}>
            ↩ @{replyTo} 에게 대댓글
          </div>
        )}

        {/* 텍스트 입력 */}
        <textarea
          ref={textareaRef}
          value={value}
          onChange={(e) => onChange(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder="댓글을 남겨주세요."
          style={{
            display: 'block',
            width: '100%',
            padding: '18px 20px 10px',
            fontSize: 15,
            color: 'var(--L-ink)',
            lineHeight: 1.55,
            minHeight: 88,
            background: 'transparent',
            border: 'none',
            outline: 'none',
            resize: 'none',
            fontFamily: 'var(--font-sans)',
            boxSizing: 'border-box',
          }}
        />

        {/* 에러 메시지 */}
        {error && (
          <div style={{ padding: '0 20px 8px', fontSize: 12, color: 'var(--faction-partner)' }}>{error}</div>
        )}

        {/* 등록 버튼 */}
        <div style={{ display: 'flex', justifyContent: 'flex-end', padding: '0 18px' }}>
          <button
            onClick={onSubmit}
            style={{
              background: 'var(--L-ink)',
              color: 'var(--L-bg)',
              fontSize: 14.5,
              fontWeight: 500,
              padding: '9px 22px',
              borderRadius: 8,
              border: 'none',
              cursor: 'pointer',
              fontFamily: 'inherit',
            }}
          >
            등록
          </button>
        </div>
      </div>
    </>
  );
}
