'use client';

import { useState } from 'react';
import { checkKeywords } from '@/lib/utils/keywordGuard';

interface Props {
  onSend: (content: string) => void;
  disabled?: boolean;
}

export function ChatInput({ onSend, disabled }: Props) {
  const [text, setText] = useState('');

  const handleSend = () => {
    const content = text.trim();
    if (!content || disabled) return;

    // Check for crisis keywords before sending
    const keywordCheck = checkKeywords(content);
    if (keywordCheck.level === 1) {
      alert(`이 내용은 저보다 더 전문적인 도움이 필요해요.\n아래 번호로 전화해보세요.\n\n· 여성긴급전화: 1366\n· 자살예방상담: 1393\n· 가정폭력: 132\n· 아동학대: 112`);
      return;
    }

    onSend(content);
    setText('');
  };

  return (
    <div style={{
      padding: '12px 14px',
      borderTop: '1px solid var(--P-border)',
      background: 'var(--P-bg)',
      display: 'flex',
      gap: 8,
      alignItems: 'flex-end',
    }}>
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
  );
}
