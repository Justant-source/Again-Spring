'use client';

import { useState } from 'react';
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
import { Textarea } from '@/components/ui/textarea';
import { Label } from '@/components/ui/label';
import { Input } from '@/components/ui/input';
import { Checkbox } from '@/components/ui/checkbox';
import { Badge } from '@/components/ui/badge';
import { AlertCircle, Sparkles, ChevronRight, Check } from 'lucide-react';

type TargetType = 'POST' | 'COMMENT';
type Step = 'edit' | 'review';

interface Props {
  /** 'POST' 개선 시 post를 넘기고 comment는 null */
  post?: AdminPost | null;
  /** 'COMMENT' 개선 시 comment를 넘기고 post는 null */
  comment?: AdminComment | null;
  onClose: () => void;
  onCommitted: () => void;
}

export function AiImproveDialog({ post, comment, onClose, onCommitted }: Props) {
  const targetType: TargetType = post ? 'POST' : 'COMMENT';
  const targetId = post ? post.id : String(comment?.id ?? '');
  const originalBody = post
    ? post.bodyPublished ?? post.bodyRaw ?? ''
    : comment?.body ?? '';

  const isOpen = !!(post || comment);

  // ─── state ───
  const [step, setStep] = useState<Step>('edit');
  const [correctedText, setCorrectedText] = useState(originalBody);
  const [applyLive, setApplyLive] = useState(true);

  // review step
  const [suggestedCaution, setSuggestedCaution] = useState<string | null>(null);
  const [editedCaution, setEditedCaution] = useState('');
  const [suggestedRules, setSuggestedRules] = useState<string[]>([]);
  const [selectedRules, setSelectedRules] = useState<boolean[]>([]);
  const [editedRules, setEditedRules] = useState<string[]>([]);

  const [analyzing, setAnalyzing] = useState(false);
  const [committing, setCommitting] = useState(false);
  const [error, setError] = useState('');
  const [personaId, setPersonaId] = useState('');

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
    setError('');
    setAnalyzing(true);
    try {
      const result = await analyzeCorrection({
        targetType,
        targetId,
        correctedText,
      });
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
    setSelectedRules((prev) => prev.map((v, i) => (i === idx ? !v : v)));
  }

  function updateRule(idx: number, value: string) {
    setEditedRules((prev) => prev.map((v, i) => (i === idx ? value : v)));
  }

  // ─── render ───
  const title = targetType === 'POST' ? 'AI 게시글 개선' : 'AI 댓글 개선';

  return (
    <Dialog open={isOpen} onOpenChange={handleOpenChange}>
      <DialogContent className="max-w-2xl max-h-[90vh] overflow-y-auto">
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

        {/* ── 단계 A: 첨삭 편집 ── */}
        {step === 'edit' && (
          <div className="space-y-4">
            <div>
              <Label className="block text-sm font-medium mb-1 text-muted-foreground">원본</Label>
              <div className="p-3 bg-muted rounded-md text-sm whitespace-pre-wrap max-h-40 overflow-y-auto">
                {originalBody || '(내용 없음)'}
              </div>
            </div>

            <div>
              <Label className="block text-sm font-medium mb-1">수정본 *</Label>
              <Textarea
                value={correctedText}
                onChange={(e) => setCorrectedText(e.target.value)}
                placeholder="AI가 쓴 내용을 수정해주세요."
                rows={8}
                disabled={analyzing}
                className="resize-none"
              />
              <p className="text-xs text-muted-foreground mt-1">
                원본과의 차이가 클수록 더 정확한 학습 신호가 됩니다.
              </p>
            </div>

            <div className="flex items-center gap-2">
              <Checkbox
                id="applyLive"
                checked={applyLive}
                onCheckedChange={(v) => setApplyLive(!!v)}
                disabled={analyzing}
              />
              <Label htmlFor="applyLive" className="text-sm cursor-pointer">
                수정본을 실제 게시글/댓글에 즉시 반영
              </Label>
            </div>
          </div>
        )}

        {/* ── 단계 B: LLM 분석 결과 검토 ── */}
        {step === 'review' && (
          <div className="space-y-5">
            <div className="p-3 bg-purple-50 border border-purple-200 rounded-md text-sm text-purple-800">
              LLM이 첨삭 내용을 분석했습니다. 아래 내용을 검토·편집한 후 확정하세요.
              페르소나 ID: <code className="font-mono text-xs">{personaId}</code>
            </div>

            {/* 페르소나 주의사항 */}
            <div>
              <Label className="block text-sm font-semibold mb-1">
                페르소나 주의사항
                <span className="ml-2 text-xs font-normal text-muted-foreground">
                  (이 AI 작성자가 다음 글 쓸 때 참고)
                </span>
              </Label>
              <Input
                value={editedCaution}
                onChange={(e) => setEditedCaution(e.target.value)}
                placeholder="주의사항 없음 (빈 칸이면 저장 안 됨)"
                disabled={committing}
              />
            </div>

            {/* 전역 금지 규칙 */}
            <div>
              <Label className="block text-sm font-semibold mb-2">
                전역 금지 규칙 제안
                <span className="ml-2 text-xs font-normal text-muted-foreground">
                  (모든 AI 유저 생성 시 적용)
                </span>
              </Label>
              {editedRules.length === 0 ? (
                <p className="text-sm text-muted-foreground">제안된 전역 규칙이 없습니다.</p>
              ) : (
                <div className="space-y-2">
                  {editedRules.map((rule, idx) => (
                    <div key={idx} className="flex items-start gap-2">
                      <Checkbox
                        id={`rule-${idx}`}
                        checked={selectedRules[idx]}
                        onCheckedChange={() => toggleRule(idx)}
                        disabled={committing}
                        className="mt-2"
                      />
                      <Input
                        value={rule}
                        onChange={(e) => updateRule(idx, e.target.value)}
                        disabled={!selectedRules[idx] || committing}
                        className={!selectedRules[idx] ? 'opacity-40' : ''}
                      />
                    </div>
                  ))}
                </div>
              )}
            </div>

            {/* 라이브 반영 확인 */}
            {applyLive && (
              <div className="flex items-center gap-2 text-sm text-amber-700 bg-amber-50 p-2 rounded-md border border-amber-200">
                <Check className="h-4 w-4" />
                수정본이 실제 게시글/댓글에 즉시 반영됩니다.
              </div>
            )}
          </div>
        )}

        <DialogFooter className="gap-2">
          <Button variant="outline" onClick={handleClose} disabled={analyzing || committing}>
            취소
          </Button>

          {step === 'edit' && (
            <Button onClick={handleAnalyze} disabled={analyzing || !correctedText.trim()}>
              {analyzing ? (
                <>분석 중...</>
              ) : (
                <>
                  분석 <ChevronRight className="h-4 w-4 ml-1" />
                </>
              )}
            </Button>
          )}

          {step === 'review' && (
            <>
              <Button variant="outline" onClick={() => setStep('edit')} disabled={committing}>
                돌아가기
              </Button>
              <Button onClick={handleCommit} disabled={committing}>
                {committing ? '제출 중...' : '확정 제출'}
              </Button>
            </>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
