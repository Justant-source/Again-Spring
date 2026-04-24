// ✅ MOCKUP APPLIED — source: design/handoff/mediation-screens.jsx (MediationLetter/Bubble/Cards)

import { cn } from '@/lib/utils';

export function MediatorMessage({
  text,
  variant = 'letter',
  turnLabel,
  recipientName = '님께',
}: {
  text: string;
  variant?: 'letter' | 'bubble' | 'card';
  turnLabel?: string;
  recipientName?: string;
}) {
  if (variant === 'bubble') {
    return (
      <div className="flex flex-col gap-1">
        <div
          style={{
            fontSize: '11px',
            color: 'var(--L-sub)',
            paddingLeft: '2px',
          }}
        >
          중재자 {turnLabel && `· ${turnLabel}`}
        </div>
        <div
          className="serif"
          style={{
            background: 'var(--L-card)',
            border: '1px solid var(--L-border)',
            borderRadius: '3px 14px 14px 14px',
            padding: '14px 16px',
            fontSize: '14px',
            lineHeight: 1.75,
            maxWidth: '88%',
          }}
        >
          {text}
        </div>
      </div>
    );
  }

  if (variant === 'card') {
    return (
      <div className="letter-card flex flex-col">
        <div
          className="quote-it"
          style={{ fontSize: '12px', marginBottom: '14px' }}
        >
          Q. {turnLabel || ''}
        </div>
        <div
          className="serif"
          style={{
            fontSize: '18px',
            lineHeight: 1.7,
            flex: 1,
          }}
        >
          {text}
        </div>
        <div
          style={{
            fontSize: '11px',
            color: 'var(--L-sub)',
            marginTop: '14px',
          }}
        >
          답장은 200자 안이 읽기 좋아요
        </div>
      </div>
    );
  }

  // variant === 'letter' (default)
  return (
    <div>
      <div
        className="quote-it"
        style={{
          fontSize: '13px',
          marginBottom: '14px',
        }}
      >
        {recipientName} — 중재자가
      </div>
      <div
        className="serif"
        style={{
          fontSize: '16px',
          lineHeight: 1.9,
          letterSpacing: '-0.005em',
        }}
      >
        {text}
      </div>
    </div>
  );
}
