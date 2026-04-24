// ✅ MOCKUP APPLIED — source: design/handoff/mediation-screens.jsx (MediationLetter input section)

'use client';

import { useState, useEffect } from 'react';
import { CrisisInline } from './CrisisInline';
import type { KeywordLevel } from '@/lib/utils/keywordGuard';

const MAX_CHARS = 600;

export function TurnInput({
  value,
  onChange,
  onSubmit,
  onSkip,
  disabled = false,
  canSkip = false,
  keywordLevel,
  warningMessage,
}: {
  value: string;
  onChange: (v: string) => void;
  onSubmit: () => void;
  onSkip?: () => void;
  disabled?: boolean;
  canSkip?: boolean;
  keywordLevel: KeywordLevel;
  warningMessage?: string;
}) {
  const charCount = value.length;
  const isOverLimit = charCount > MAX_CHARS;
  const level1Blocked = keywordLevel === 1;

  return (
    <div className="flex flex-col gap-4">
      {/* Level 1: Crisis Inline */}
      {level1Blocked && <CrisisInline />}

      {/* Level 2: Warning Banner */}
      {keywordLevel === 2 && (
        <div
          style={{
            border: '1px solid var(--L-border)',
            background: 'var(--L-card)',
            borderRadius: '3px',
            padding: '12px 14px',
            fontSize: '12px',
            color: 'var(--L-sub)',
            lineHeight: 1.6,
          }}
        >
          <div style={{ fontWeight: 500, marginBottom: '4px' }}>⚠️ 안내</div>
          {warningMessage || (
            <>
              이혼 등과 같은 법적 결정은 저희 서비스가 도와드릴 수
              없어요. 이 서비스는 관계 회복을 위한 대화 정리를 돕는 것이
              목표입니다. 법적 조언이 필요하시면 대한법률구조공단(132)을
              이용해주세요.
            </>
          )}
        </div>
      )}

      {/* Textarea */}
      <textarea
        className="ta-L"
        placeholder="편한 말로 적어주세요"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        disabled={disabled || level1Blocked}
        style={{
          opacity: level1Blocked ? 0.5 : 1,
          cursor: level1Blocked ? 'not-allowed' : 'default',
        }}
      />

      {/* Helper text */}
      <div
        style={{
          marginTop: '-8px',
          fontSize: '11px',
          color: 'var(--L-sub)',
        }}
      >
        작성하신 글은 중재자가 정돈해 전달해요.
      </div>

      {/* Character counter */}
      <div
        style={{
          fontSize: '12px',
          color: isOverLimit ? 'var(--L-point)' : 'var(--L-sub)',
          textAlign: 'right',
        }}
      >
        {charCount} / {MAX_CHARS}
      </div>

      {/* Buttons */}
      <div className="flex gap-2">
        <button
          className="btn-L ghost"
          style={{ flex: 1 }}
          onClick={() => {
            // no-op or localStorage save
          }}
          disabled={disabled || level1Blocked}
        >
          초안 저장
        </button>
        <button
          className="btn-L"
          style={{ flex: 2 }}
          onClick={onSubmit}
          disabled={disabled || level1Blocked || isOverLimit}
        >
          중재자에게 보내기
        </button>
        {canSkip && (
          <button
            className="btn-L ghost"
            onClick={onSkip}
            disabled={disabled}
            style={{ flex: 1 }}
          >
            이번 턴 건너뛰기
          </button>
        )}
      </div>
    </div>
  );
}
