'use client';

import { useState, useEffect, useRef, useMemo } from 'react';
import { saveCorrection } from '@/lib/api/admin/corrections';
import type { AdminPost, AdminComment } from '@/lib/api/admin/content';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Label } from '@/components/ui/label';
import { AlertCircle, Sparkles, Save, Info } from 'lucide-react';

// ─── 단어 레벨 diff 엔진 ────────────────────────────────────────────────────

type DiffOp = { text: string; type: 'same' | 'removed' | 'added' };

function tokenize(text: string): string[] {
  return text.split(/(\s+)/);
}

function computeDiff(original: string, corrected: string): { left: DiffOp[]; right: DiffOp[] } {
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

function renderDiff(parts: DiffOp[], side: 'left' | 'right') {
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

// ─── 컴포넌트 ────────────────────────────────────────────────────────────────

type TargetType = 'POST' | 'COMMENT';

interface Props {
  post?: AdminPost | null;
  comment?: AdminComment | null;
  onClose: () => void;
  onCommitted: () => void;
}

export function AiImproveDialog({ post, comment, onClose, onCommitted }: Props) {
  const targetType: TargetType = post ? 'POST' : 'COMMENT';
  const targetId = post ? post.id : String(comment?.id ?? '');
  const originalBody = post
    ? (post.bodyPublished ?? post.bodyRaw ?? '')
    : (comment?.body ?? '');

  const isOpen = !!(post || comment);

  const [correctedText, setCorrectedText] = useState(originalBody);
  const [applyLive, setApplyLive] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  const leftScrollRef = useRef<HTMLDivElement>(null);
  const rightScrollRef = useRef<HTMLTextAreaElement>(null);

  useEffect(() => {
    if (isOpen) {
      setCorrectedText(originalBody);
      setError('');
    }
  }, [isOpen, originalBody]);

  function handleRightScroll() {
    if (leftScrollRef.current && rightScrollRef.current) {
      leftScrollRef.current.scrollTop = rightScrollRef.current.scrollTop;
    }
  }

  const diff = useMemo(
    () => computeDiff(originalBody, correctedText),
    [originalBody, correctedText]
  );

  const removedCount = diff.left.filter(p => p.type === 'removed' && p.text.trim()).length;
  const addedCount   = diff.right.filter(p => p.type === 'added'   && p.text.trim()).length;
  const hasChanges   = removedCount > 0 || addedCount > 0;

  function handleOpenChange(open: boolean) {
    if (!open) handleClose();
  }

  function handleClose() {
    setCorrectedText(originalBody);
    setError('');
    setSaving(false);
    onClose();
  }

  async function handleSave() {
    if (!correctedText.trim()) {
      setError('수정본을 입력해주세요.');
      return;
    }
    if (!hasChanges) {
      setError('원본과 동일합니다. 수정 후 저장하세요.');
      return;
    }
    setError('');
    setSaving(true);
    try {
      await saveCorrection({ targetType, targetId, correctedText, applyLive });
      onCommitted();
      handleClose();
    } catch (err: any) {
      setError(err?.response?.data?.message || '저장 중 오류가 발생했어요. 잠시 후 다시 시도해주세요.');
    } finally {
      setSaving(false);
    }
  }

  const title = targetType === 'POST' ? 'AI 게시글 개선' : 'AI 댓글 개선';
  const panelH = 'h-56';

  return (
    <Dialog open={isOpen} onOpenChange={handleOpenChange}>
      <DialogContent className="w-[90vw] max-w-5xl max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <Sparkles className="h-5 w-5 text-purple-500" />
            {title}
          </DialogTitle>
        </DialogHeader>

        {/* 안내 배너 */}
        <div className="p-3 bg-blue-50 border border-blue-200 rounded-md flex items-start gap-2 text-sm text-blue-800">
          <Info className="h-4 w-4 shrink-0 mt-0.5" />
          <span>
            저장하면 학습 데이터가 쌓입니다.{' '}
            <strong>AI 규칙 관리 → 첨삭 이력</strong>에서 일괄 분석을 요청할 수 있습니다.
          </span>
        </div>

        {error && (
          <div className="p-3 bg-red-50 border border-red-200 rounded-md flex items-start gap-2">
            <AlertCircle className="w-5 h-5 text-red-600 mt-0.5 shrink-0" />
            <p className="text-sm text-red-700">{error}</p>
          </div>
        )}

        {/* 변경 통계 배지 */}
        <div className="flex items-center gap-2 text-xs">
          {hasChanges ? (
            <>
              {removedCount > 0 && (
                <span className="px-2 py-0.5 rounded-full bg-red-100 text-red-700 font-medium">
                  -{removedCount} 단어 삭제
                </span>
              )}
              {addedCount > 0 && (
                <span className="px-2 py-0.5 rounded-full bg-green-100 text-green-700 font-medium">
                  +{addedCount} 단어 추가
                </span>
              )}
            </>
          ) : (
            <span className="text-muted-foreground">아직 변경 없음 — 오른쪽에서 수정하세요</span>
          )}
        </div>

        {/* 좌우 패널 */}
        <div className="grid grid-cols-2 gap-0 rounded-lg border overflow-hidden">
          {/* 왼쪽: 원본 (읽기 전용, diff 하이라이트) */}
          <div className="flex flex-col border-r">
            <div className="px-3 py-2 bg-gray-50 border-b flex items-center justify-between">
              <span className="text-xs font-semibold text-gray-500 uppercase tracking-wide">원본</span>
              <span className="text-[10px] text-muted-foreground border rounded px-1.5 py-0.5">읽기 전용</span>
            </div>
            <div
              ref={leftScrollRef}
              className={`${panelH} overflow-y-auto p-3 text-sm leading-relaxed whitespace-pre-wrap bg-gray-50/50 text-gray-700`}
            >
              {originalBody
                ? renderDiff(diff.left, 'left')
                : <span className="text-muted-foreground italic">(내용 없음)</span>
              }
            </div>
          </div>

          {/* 오른쪽: 수정본 (편집 가능) */}
          <div className="flex flex-col">
            <div className="px-3 py-2 bg-white border-b flex items-center justify-between">
              <span className="text-xs font-semibold text-purple-600 uppercase tracking-wide">수정본</span>
              <span className="text-[10px] text-muted-foreground">직접 편집</span>
            </div>
            <textarea
              ref={rightScrollRef}
              value={correctedText}
              onChange={e => setCorrectedText(e.target.value)}
              onScroll={handleRightScroll}
              disabled={saving}
              placeholder="AI가 작성한 원본을 이곳에서 수정하세요."
              className={`${panelH} resize-none p-3 text-sm leading-relaxed w-full border-none outline-none bg-white text-gray-900 disabled:opacity-50`}
            />
          </div>
        </div>

        {/* 라이브 반영 체크박스 */}
        <div className="flex items-center gap-2">
          <Checkbox
            id="applyLive"
            checked={applyLive}
            onCheckedChange={v => setApplyLive(!!v)}
            disabled={saving}
          />
          <Label htmlFor="applyLive" className="text-sm cursor-pointer">
            실제 게시글/댓글 본문도 수정본으로 즉시 교체
          </Label>
        </div>

        <DialogFooter className="gap-2 pt-2">
          <Button variant="outline" onClick={handleClose} disabled={saving}>
            취소
          </Button>
          <Button
            onClick={handleSave}
            disabled={saving || !correctedText.trim() || !hasChanges}
            className="bg-purple-600 hover:bg-purple-700 text-white"
          >
            {saving ? '저장 중...' : (
              <>
                <Save className="h-4 w-4 mr-1.5" />
                학습 데이터 저장
              </>
            )}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
