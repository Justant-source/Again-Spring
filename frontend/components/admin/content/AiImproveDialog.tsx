'use client';

import { useState, useEffect, useRef, useMemo } from 'react';
import {
  analyzeCorrection,
  commitCorrection,
} from '@/lib/api/admin/corrections';
import type { AdminPost, AdminComment } from '@/lib/api/admin/content';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Checkbox } from '@/components/ui/checkbox';
import { Badge } from '@/components/ui/badge';
import { Label } from '@/components/ui/label';
import { AlertCircle, Sparkles, ChevronRight, Check, ArrowRight } from 'lucide-react';

// ─── 단어 레벨 diff 엔진 ────────────────────────────────────────────────────

type DiffOp = { text: string; type: 'same' | 'removed' | 'added' };

/** 공백 포함 토크나이징 — 한국어 어절 단위 */
function tokenize(text: string): string[] {
  return text.split(/(\s+)/);
}

/** LCS 기반 단어-레벨 diff */
function computeDiff(original: string, corrected: string): { left: DiffOp[]; right: DiffOp[] } {
  const ot = tokenize(original);
  const ct = tokenize(corrected);
  const m = ot.length;
  const n = ct.length;

  // dp[i][j] = LCS length of ot[0..i-1], ct[0..j-1]
  const dp: number[][] = Array.from({ length: m + 1 }, () => new Array(n + 1).fill(0));
  for (let i = 1; i <= m; i++)
    for (let j = 1; j <= n; j++)
      dp[i][j] = ot[i - 1] === ct[j - 1]
        ? dp[i - 1][j - 1] + 1
        : Math.max(dp[i - 1][j], dp[i][j - 1]);

  // 역추적
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

/** diff 결과를 React 요소 배열로 변환 */
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
    return null; // 'left' side의 'added'와 'right' side의 'removed'는 숨김
  });
}

// ─── 컴포넌트 ────────────────────────────────────────────────────────────────

type TargetType = 'POST' | 'COMMENT';
type Step = 'edit' | 'review';

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

  // ─── state ───
  const [step, setStep] = useState<Step>('edit');
  const [correctedText, setCorrectedText] = useState(originalBody);
  const [applyLive, setApplyLive] = useState(true);

  const [suggestedCaution, setSuggestedCaution] = useState<string | null>(null);
  const [editedCaution, setEditedCaution] = useState('');
  const [suggestedRules, setSuggestedRules] = useState<string[]>([]);
  const [selectedRules, setSelectedRules] = useState<boolean[]>([]);
  const [editedRules, setEditedRules] = useState<string[]>([]);

  const [analyzing, setAnalyzing] = useState(false);
  const [committing, setCommitting] = useState(false);
  const [error, setError] = useState('');
  const [personaId, setPersonaId] = useState('');

  const leftScrollRef = useRef<HTMLDivElement>(null);
  const rightScrollRef = useRef<HTMLTextAreaElement>(null);

  // 다이얼로그가 열릴 때 correctedText 초기화
  useEffect(() => {
    if (isOpen) {
      setCorrectedText(originalBody);
      setStep('edit');
    }
  }, [isOpen, originalBody]);

  // 좌우 스크롤 동기화
  function handleRightScroll() {
    if (leftScrollRef.current && rightScrollRef.current) {
      leftScrollRef.current.scrollTop = rightScrollRef.current.scrollTop;
    }
  }

  // diff 계산 (edit 단계에서 실시간 + review 단계)
  const diff = useMemo(
    () => computeDiff(originalBody, correctedText),
    [originalBody, correctedText]
  );

  const removedCount = diff.left.filter(p => p.type === 'removed' && p.text.trim()).length;
  const addedCount   = diff.right.filter(p => p.type === 'added'   && p.text.trim()).length;
  const hasChanges   = removedCount > 0 || addedCount > 0;

  // ─── handlers ───
  function handleOpenChange(open: boolean) {
    if (!open) handleClose();
  }

  function handleClose() {
    setStep('edit');
    setCorrectedText(originalBody);
    setError('');
    setSuggestedCaution(null);
    setEditedCaution('');
    setSuggestedRules([]);
    setSelectedRules([]);
    setEditedRules([]);
    setAnalyzing(false);
    setCommitting(false);
    onClose();
  }

  async function handleAnalyze() {
    if (!correctedText.trim()) {
      setError('수정본을 입력해주세요.');
      return;
    }
    if (!hasChanges) {
      setError('원본과 동일합니다. 수정 후 분석하세요.');
      return;
    }
    setError('');
    setAnalyzing(true);
    try {
      const result = await analyzeCorrection({ targetType, targetId, correctedText });
      setPersonaId(result.personaId);
      setSuggestedCaution(result.suggestedCaution);
      setEditedCaution(result.suggestedCaution ?? '');
      setSuggestedRules(result.suggestedGlobalRules);
      setSelectedRules(result.suggestedGlobalRules.map(() => true));
      setEditedRules([...result.suggestedGlobalRules]);
      setStep('review');
    } catch (err: any) {
      setError(err?.response?.data?.message || 'LLM 분석 중 오류가 발생했어요. 잠시 후 다시 시도해주세요.');
    } finally {
      setAnalyzing(false);
    }
  }

  async function handleCommit() {
    setError('');
    setCommitting(true);
    try {
      const finalRules = editedRules.filter((_, i) => selectedRules[i]).filter(Boolean);
      await commitCorrection({
        targetType,
        targetId,
        correctedText,
        personaCaution: editedCaution.trim() || null,
        globalRules: finalRules,
        applyLive,
      });
      onCommitted();
      handleClose();
    } catch (err: any) {
      setError(err?.response?.data?.message || '제출 중 오류가 발생했어요. 잠시 후 다시 시도해주세요.');
    } finally {
      setCommitting(false);
    }
  }

  function toggleRule(idx: number) {
    setSelectedRules(prev => prev.map((v, i) => (i === idx ? !v : v)));
  }

  function updateRule(idx: number, value: string) {
    setEditedRules(prev => prev.map((v, i) => (i === idx ? value : v)));
  }

  const title = targetType === 'POST' ? 'AI 게시글 개선' : 'AI 댓글 개선';
  const panelH = 'h-56';   // 편집 패널 높이 (edit step)
  const reviewH = 'h-40';  // review step diff 패널 높이

  return (
    <Dialog open={isOpen} onOpenChange={handleOpenChange}>
      {/* 다이얼로그를 화면 너비의 90%까지 허용 */}
      <DialogContent className="w-[90vw] max-w-5xl max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <Sparkles className="h-5 w-5 text-purple-500" />
            {title}
            {step === 'review' && (
              <Badge variant="secondary" className="ml-2">LLM 분석 완료</Badge>
            )}
          </DialogTitle>
        </DialogHeader>

        {error && (
          <div className="p-3 bg-red-50 border border-red-200 rounded-md flex items-start gap-2">
            <AlertCircle className="w-5 h-5 text-red-600 mt-0.5 flex-shrink-0" />
            <p className="text-sm text-red-700">{error}</p>
          </div>
        )}

        {/* ──────────────────────────────────────────────────────────────────
            단계 A: 좌(원본) / 우(수정본) 나란히 편집
        ────────────────────────────────────────────────────────────────── */}
        {step === 'edit' && (
          <div className="space-y-3">
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
                  <span className="text-muted-foreground">
                    차이가 클수록 더 강한 학습 신호가 됩니다
                  </span>
                </>
              ) : (
                <span className="text-muted-foreground">아직 변경 없음 — 오른쪽에서 수정하세요</span>
              )}
            </div>

            {/* 좌우 패널 */}
            <div className="grid grid-cols-2 gap-0 rounded-lg border overflow-hidden">
              {/* ─ 왼쪽: 원본 (읽기 전용, diff 하이라이트) ─ */}
              <div className="flex flex-col border-r">
                <div className="px-3 py-2 bg-gray-50 border-b flex items-center justify-between">
                  <span className="text-xs font-semibold text-gray-500 uppercase tracking-wide">
                    원본
                  </span>
                  <Badge variant="outline" className="text-[10px] px-1.5 py-0">읽기 전용</Badge>
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

              {/* ─ 오른쪽: 수정본 (편집 가능) ─ */}
              <div className="flex flex-col">
                <div className="px-3 py-2 bg-white border-b flex items-center justify-between">
                  <span className="text-xs font-semibold text-purple-600 uppercase tracking-wide">
                    수정본
                  </span>
                  <span className="text-[10px] text-muted-foreground">직접 편집</span>
                </div>
                <textarea
                  ref={rightScrollRef}
                  value={correctedText}
                  onChange={e => setCorrectedText(e.target.value)}
                  onScroll={handleRightScroll}
                  disabled={analyzing}
                  placeholder="AI가 작성한 원본을 이곳에서 수정하세요."
                  className={`${panelH} resize-none p-3 text-sm leading-relaxed w-full border-none outline-none bg-white text-gray-900 disabled:opacity-50`}
                />
              </div>
            </div>

            {/* 화살표 안내 */}
            <div className="flex items-center justify-center gap-2 text-xs text-muted-foreground">
              <span>원본을 참고해</span>
              <ArrowRight className="h-3 w-3" />
              <span>오른쪽에서 수정 → 분석 클릭</span>
            </div>

            {/* 라이브 반영 체크박스 */}
            <div className="flex items-center gap-2 pt-1">
              <Checkbox
                id="applyLive"
                checked={applyLive}
                onCheckedChange={v => setApplyLive(!!v)}
                disabled={analyzing}
              />
              <Label htmlFor="applyLive" className="text-sm cursor-pointer">
                확정 시 실제 게시글/댓글 본문도 수정본으로 즉시 교체
              </Label>
            </div>
          </div>
        )}

        {/* ──────────────────────────────────────────────────────────────────
            단계 B: 검토 — diff 요약 + LLM 제안 규칙
        ────────────────────────────────────────────────────────────────── */}
        {step === 'review' && (
          <div className="space-y-4">
            {/* LLM 분석 완료 배너 */}
            <div className="p-3 bg-purple-50 border border-purple-200 rounded-md text-sm text-purple-800 flex items-center gap-2">
              <Sparkles className="h-4 w-4 flex-shrink-0" />
              <span>
                LLM이 첨삭을 분석했습니다. 규칙을 검토·편집한 후 확정하세요.
                <code className="ml-2 font-mono text-xs bg-purple-100 px-1 rounded">{personaId}</code>
              </span>
            </div>

            {/* 좌우 diff 뷰 (review 단계) */}
            <div className="grid grid-cols-2 gap-0 rounded-lg border overflow-hidden">
              <div className="flex flex-col border-r">
                <div className="px-3 py-1.5 bg-gray-50 border-b">
                  <span className="text-[11px] font-semibold text-gray-500 uppercase tracking-wide">원본 (제거된 내용 <span className="text-red-600">빨강</span>)</span>
                </div>
                <div className={`${reviewH} overflow-y-auto p-3 text-xs leading-relaxed whitespace-pre-wrap bg-gray-50/50 text-gray-700`}>
                  {renderDiff(diff.left, 'left')}
                </div>
              </div>
              <div className="flex flex-col">
                <div className="px-3 py-1.5 bg-white border-b">
                  <span className="text-[11px] font-semibold text-purple-600 uppercase tracking-wide">수정본 (추가된 내용 <span className="text-green-600">초록</span>)</span>
                </div>
                <div className={`${reviewH} overflow-y-auto p-3 text-xs leading-relaxed whitespace-pre-wrap bg-white text-gray-900`}>
                  {renderDiff(diff.right, 'right')}
                </div>
              </div>
            </div>

            {/* 변경 통계 */}
            <div className="flex items-center gap-2 text-xs">
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
            </div>

            <hr />

            {/* 페르소나 주의사항 */}
            <div>
              <Label className="block text-sm font-semibold mb-1">
                페르소나 주의사항
                <span className="ml-2 text-xs font-normal text-muted-foreground">
                  이 AI 작성자가 다음 글 쓸 때 참고 (빈 칸이면 저장 안 됨)
                </span>
              </Label>
              <Input
                value={editedCaution}
                onChange={e => setEditedCaution(e.target.value)}
                placeholder="예: 전여친 비교 표현을 과도하게 반복하지 말 것"
                disabled={committing}
              />
            </div>

            {/* 전역 금지 규칙 */}
            <div>
              <Label className="block text-sm font-semibold mb-2">
                전역 금지 규칙 제안
                <span className="ml-2 text-xs font-normal text-muted-foreground">
                  모든 AI 유저 생성 시 적용 — 체크 해제하면 제외
                </span>
              </Label>
              {editedRules.length === 0 ? (
                <p className="text-sm text-muted-foreground">제안된 전역 규칙이 없습니다.</p>
              ) : (
                <div className="space-y-2">
                  {editedRules.map((rule, idx) => (
                    <div key={idx} className="flex items-center gap-2">
                      <Checkbox
                        id={`rule-${idx}`}
                        checked={selectedRules[idx]}
                        onCheckedChange={() => toggleRule(idx)}
                        disabled={committing}
                      />
                      <Input
                        value={rule}
                        onChange={e => updateRule(idx, e.target.value)}
                        disabled={!selectedRules[idx] || committing}
                        className={!selectedRules[idx] ? 'opacity-40 line-through' : ''}
                      />
                    </div>
                  ))}
                </div>
              )}
            </div>

            {/* 라이브 반영 알림 */}
            {applyLive && (
              <div className="flex items-center gap-2 text-sm text-amber-700 bg-amber-50 p-2 rounded-md border border-amber-200">
                <Check className="h-4 w-4 flex-shrink-0" />
                확정 시 실제 게시글/댓글 본문이 수정본으로 즉시 교체됩니다.
              </div>
            )}
          </div>
        )}

        <DialogFooter className="gap-2 pt-2">
          <Button variant="outline" onClick={handleClose} disabled={analyzing || committing}>
            취소
          </Button>

          {step === 'edit' && (
            <Button
              onClick={handleAnalyze}
              disabled={analyzing || !correctedText.trim() || !hasChanges}
            >
              {analyzing ? '분석 중...' : (
                <>분석 <ChevronRight className="h-4 w-4 ml-1" /></>
              )}
            </Button>
          )}

          {step === 'review' && (
            <>
              <Button variant="outline" onClick={() => setStep('edit')} disabled={committing}>
                돌아가기
              </Button>
              <Button onClick={handleCommit} disabled={committing}>
                {committing ? '제출 중...' : '학습 데이터 확정'}
              </Button>
            </>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
