'use client';

import { useMemo, useRef } from 'react';

// ─── 단어 레벨 diff 엔진 ────────────────────────────────────────────────────

export type DiffOp = { text: string; type: 'same' | 'removed' | 'added' };

export function tokenize(text: string): string[] {
  return text.split(/(\s+)/);
}

export function computeDiff(original: string, corrected: string): { left: DiffOp[]; right: DiffOp[] } {
  const ot = tokenize(original);
  const ct = tokenize(corrected);
  const m = ot.length;
  const n = ct.length;

  const dp: number[][] = Array.from({ length: m + 1 }, () => new Array(n + 1).fill(0));
  for (let i = 1; i <= m; i++)
    for (let j = 1; j <= n; j++)
      dp[i][j] = ot[i - 1] === ct[j - 1]
        ? dp[i - 1][j - 1] + 1
        : Math.max(dp[i - 1][j], dp[i][j - 1]);

  const left: DiffOp[] = [];
  const right: DiffOp[] = [];
  let i = m, j = n;
  while (i > 0 || j > 0) {
    if (i > 0 && j > 0 && ot[i - 1] === ct[j - 1]) {
      left.unshift({ text: ot[i - 1], type: 'same' });
      right.unshift({ text: ct[j - 1], type: 'same' });
      i--; j--;
    } else if (j > 0 && (i === 0 || dp[i][j - 1] >= dp[i - 1][j])) {
      right.unshift({ text: ct[j - 1], type: 'added' });
      j--;
    } else {
      left.unshift({ text: ot[i - 1], type: 'removed' });
      i--;
    }
  }
  return { left, right };
}

export function renderDiff(parts: DiffOp[], side: 'left' | 'right') {
  return parts.map((p, i) => {
    if (p.type === 'same') return <span key={i}>{p.text}</span>;
    if (side === 'left' && p.type === 'removed')
      return (
        <mark key={i} className="bg-red-100 text-red-700 line-through rounded-sm px-0.5">
          {p.text}
        </mark>
      );
    if (side === 'right' && p.type === 'added')
      return (
        <mark key={i} className="bg-green-100 text-green-700 rounded-sm px-0.5">
          {p.text}
        </mark>
      );
    return null;
  });
}

// ─── DiffPanel ───────────────────────────────────────────────────────────────

export interface DiffPanelProps {
  label: string;
  original: string;
  corrected: string;
  onChange: (v: string) => void;
  disabled: boolean;
  height?: string;
  singleLine?: boolean;
  placeholder?: string;
}

export function DiffPanel({
  label,
  original,
  corrected,
  onChange,
  disabled,
  height = 'h-44',
  singleLine = false,
  placeholder = '직접 수정하세요.',
}: DiffPanelProps) {
  const leftScrollRef = useRef<HTMLDivElement>(null);
  const rightScrollRef = useRef<HTMLTextAreaElement>(null);

  const diff = useMemo(() => computeDiff(original, corrected), [original, corrected]);
  const removedCount = diff.left.filter(p => p.type === 'removed' && p.text.trim()).length;
  const addedCount   = diff.right.filter(p => p.type === 'added'   && p.text.trim()).length;
  const hasChanges   = removedCount > 0 || addedCount > 0;

  function handleRightScroll() {
    if (leftScrollRef.current && rightScrollRef.current) {
      leftScrollRef.current.scrollTop = rightScrollRef.current.scrollTop;
    }
  }

  return (
    <div className="space-y-1.5">
      <div className="flex items-center justify-between">
        <span className="text-xs font-semibold text-gray-700">{label}</span>
        <div className="flex items-center gap-1.5 text-xs">
          {hasChanges ? (
            <>
              {removedCount > 0 && (
                <span className="px-1.5 py-0.5 rounded-full bg-red-100 text-red-700 font-medium">
                  -{removedCount} 단어
                </span>
              )}
              {addedCount > 0 && (
                <span className="px-1.5 py-0.5 rounded-full bg-green-100 text-green-700 font-medium">
                  +{addedCount} 단어
                </span>
              )}
            </>
          ) : (
            <span className="text-[10px] text-muted-foreground">변경 없음</span>
          )}
        </div>
      </div>

      <div className="grid grid-cols-2 gap-0 rounded-lg border overflow-hidden">
        {/* 원본 (읽기 전용) */}
        <div className="flex flex-col border-r">
          <div className="px-3 py-1.5 bg-gray-50 border-b flex items-center justify-between">
            <span className="text-[10px] font-semibold text-gray-500 uppercase tracking-wide">원본</span>
            <span className="text-[10px] text-muted-foreground border rounded px-1 py-0.5">읽기 전용</span>
          </div>
          <div
            ref={leftScrollRef}
            className={`${height} overflow-y-auto p-3 text-sm leading-relaxed whitespace-pre-wrap bg-gray-50/50 text-gray-700`}
          >
            {original
              ? renderDiff(diff.left, 'left')
              : <span className="text-muted-foreground italic">(내용 없음)</span>
            }
          </div>
        </div>

        {/* 수정본 (편집) */}
        <div className="flex flex-col">
          <div className="px-3 py-1.5 bg-white border-b flex items-center justify-between">
            <span className="text-[10px] font-semibold text-purple-600 uppercase tracking-wide">수정본</span>
            <span className="text-[10px] text-muted-foreground">직접 편집</span>
          </div>
          <textarea
            ref={rightScrollRef}
            value={corrected}
            onChange={e => onChange(e.target.value)}
            onScroll={handleRightScroll}
            disabled={disabled}
            placeholder={placeholder}
            rows={singleLine ? 1 : undefined}
            className={`${height} resize-none p-3 text-sm leading-relaxed w-full border-none outline-none bg-white text-gray-900 disabled:opacity-50`}
          />
        </div>
      </div>
    </div>
  );
}
