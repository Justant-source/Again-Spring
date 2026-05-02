'use client';

import { useState } from 'react';
import { checkKeywords } from '@/lib/utils/keywordGuard';

interface Props {
  onSend: (content: string) => void;
  disabled?: boolean;
  onCrisis?: () => void;
}

export function ChatInput({ onSend, disabled, onCrisis }: Props) {
  const [text, setText] = useState('');

  const handleSend = () => {
    const content = text.trim();
    if (!content || disabled) return;

    // Check for crisis keywords before sending
    const keywordCheck = checkKeywords(content);
    if (keywordCheck.level === 1) {
      onCrisis?.();
      return;
    }

    onSend(content);
    setText('');
  };

  return (
    <div style={{
      padding: '8px 14px 12px',
      borderTop: '1px solid var(--P-border)',
      background: 'var(--P-bg)',
    }}>
      <div style={{ fontSize: 11, color: 'var(--P-sub)', marginBottom: 6, opacity: 0.75 }}>
        이 글은 AI가 정리해서 전달돼요 — 원문은 전달되지 않아요
      </div>
      <div style={{ display: 'flex', gap: 8, alignItems: 'flex-end' }}>
      <textarea
        value={text}
        onChange={e => setText(e.target.value)}
        onKeyDown={e => {
          if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            handleSend();
          }
        }}
        placeholder="편한 말로 적어주세요"
        rows={1}
        style={{
          flex: 1,
          padding: '10px 14px',
          border: '1px solid var(--P-border)',
          borderRadius: 18,
          fontSize: 14,
          lineHeight: 1.5,
          resize: 'none',
          outline: 'none',
          fontFamily: 'inherit',
          background: 'var(--P-card)',
          color: 'var(--P-ink)',
          maxHeight: 120,
        }}
      />
      <button
        onClick={handleSend}
        disabled={disabled || !text.trim()}
        style={{
          padding: '10px 18px',
          background: 'var(--P-ink)',
          color: 'var(--P-bg)',
          border: 'none',
          borderRadius: 18,
          fontSize: 13,
          cursor: 'pointer',
          opacity: (disabled || !text.trim()) ? 0.4 : 1,
        }}
      >
        전송
      </button>
      </div>
    </div>
  );
}
