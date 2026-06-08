'use client';

import { useState, useEffect, useCallback, useRef } from 'react';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Input } from '@/components/ui/input';
import { Checkbox } from '@/components/ui/checkbox';
import { Label } from '@/components/ui/label';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import {
  startBatchAnalysis,
  getBatchAnalysisJob,
  applyBatchPlan,
  type BatchJobSnapshot,
  type GlobalRuleProposal,
  type PersonaCautionProposal,
  type ApprovedGlobalRule,
  type ApprovedPersonaCaution,
} from '@/lib/api/admin/corrections';
import { Sparkles, CheckCheck, AlertCircle, Loader2, Zap } from 'lucide-react';

interface Props {
  open: boolean;
  onClose: () => void;
  onApplied: () => void;
  /** 페이지 레벨에서 이미 분석 완료된 snapshot — 있으면 review 단계부터 바로 시작 */
  initialSnapshot?: BatchJobSnapshot | null;
}

// ── 전역 규칙 편집 상태 ─────────────────────────────────────────────────────
interface GlobalRuleState {
  included: boolean;
  ruleText: string;
  scope: 'ALL' | 'POST' | 'COMMENT';
  sourceCorrIds: number[];
  rationale: string;
}

// ── 페르소나 주의사항 편집 상태 ────────────────────────────────────────────────
interface PersonaCautionState {
  included: boolean;
  personaId: string;
  cautionText: string;
  sourceCorrIds: number[];
  rationale: string;
}

const SCOPE_LABELS: Record<string, string> = {
  ALL: '전체',
  POST: '게시글만',
  COMMENT: '댓글만',
};

export function BatchAnalysisReviewDialog({ open, onClose, onApplied, initialSnapshot }: Props) {
  const [phase, setPhase] = useState<'idle' | 'starting' | 'polling' | 'review' | 'applying' | 'done' | 'error'>('idle');
  const [jobSnapshot, setJobSnapshot] = useState<BatchJobSnapshot | null>(null);
  const [globalRules, setGlobalRules] = useState<GlobalRuleState[]>([]);
  const [personaCautions, setPersonaCautions] = useState<PersonaCautionState[]>([]);
  const [pushToBank, setPushToBank] = useState(true);
  const [errorMsg, setErrorMsg] = useState('');
  const [applyResult, setApplyResult] = useState<{ rulesCreated: number; cautionsApplied: number; corrProcessed: number } | null>(null);

  const pollingRef = useRef<NodeJS.Timeout | null>(null);
  const jobIdRef   = useRef<string>('');

  // ── 다이얼로그 열릴 때마다 초기화 ────────────────────────────────────────────
  useEffect(() => {
    if (open) {
      if (pollingRef.current) clearInterval(pollingRef.current);
      setJobSnapshot(null);
      setGlobalRules([]);
      setPersonaCautions([]);
      setPushToBank(true);
      setErrorMsg('');
      setApplyResult(null);
      jobIdRef.current = '';

      // 페이지 레벨에서 이미 분석 완료된 경우 review 단계로 바로 진입
      if (initialSnapshot?.status === 'READY') {
        initReviewState(initialSnapshot);
        setPhase('review');
      } else {
        setPhase('idle');
      }
    }
    return () => {
      if (pollingRef.current) clearInterval(pollingRef.current);
    };
  }, [open, initialSnapshot]);

  // ── 분석 시작 ────────────────────────────────────────────────────────────────
  async function handleStart() {
    setPhase('starting');
    setErrorMsg('');
    try {
      const res = await startBatchAnalysis();
      if (!res.jobId) {
        // PENDING 없음
        setErrorMsg(res.message || '분석 대기 중인 첨삭이 없습니다.');
        setPhase('error');
        return;
      }
      jobIdRef.current = res.jobId;
      setPhase('polling');
      startPolling(res.jobId);
    } catch (e: any) {
      setErrorMsg(e?.response?.data?.message || '분석 시작 실패. 잠시 후 다시 시도해주세요.');
      setPhase('error');
    }
  }

  // ── 폴링 ─────────────────────────────────────────────────────────────────────
  const startPolling = useCallback((jobId: string) => {
    if (pollingRef.current) clearInterval(pollingRef.current);
    pollingRef.current = setInterval(async () => {
      try {
        const snap = await getBatchAnalysisJob(jobId);
        setJobSnapshot(snap);
        if (snap.status === 'READY') {
          clearInterval(pollingRef.current!);
          initReviewState(snap);
          setPhase('review');
        } else if (snap.status === 'FAILED') {
          clearInterval(pollingRef.current!);
          setErrorMsg(snap.error || '분석 중 오류가 발생했습니다.');
          setPhase('error');
        }
      } catch (e: any) {
        // 일시 네트워크 오류는 무시 — 계속 폴링
      }
    }, 2500);
  }, []);

  function initReviewState(snap: BatchJobSnapshot) {
    if (!snap.plan) return;
    setGlobalRules(snap.plan.globalRules.map((r: GlobalRuleProposal) => ({
      included: true,
      ruleText: r.ruleText,
      scope: r.scope,
      sourceCorrIds: r.sourceCorrIds,
      rationale: r.rationale,
    })));
    setPersonaCautions(snap.plan.personaCautions.map((c: PersonaCautionProposal) => ({
      included: true,
      personaId: c.personaId,
      cautionText: c.cautionText,
      sourceCorrIds: c.sourceCorrIds,
      rationale: c.rationale,
    })));
  }

  // ── 적용 ─────────────────────────────────────────────────────────────────────
  async function handleApply() {
    setPhase('applying');
    setErrorMsg('');
    try {
      const approvedRules: ApprovedGlobalRule[] = globalRules
        .filter(r => r.included && r.ruleText.trim())
        .map(r => ({ ruleText: r.ruleText.trim(), scope: r.scope, sourceCorrIds: r.sourceCorrIds }));

      const approvedCautions: ApprovedPersonaCaution[] = personaCautions
        .filter(c => c.included && c.cautionText.trim())
        .map(c => ({ personaId: c.personaId, cautionText: c.cautionText.trim(), sourceCorrIds: c.sourceCorrIds }));

      const result = await applyBatchPlan({
        globalRules: approvedRules,
        personaCautions: approvedCautions,
        pushToBank,
      });
      setApplyResult(result);
      setPhase('done');
    } catch (e: any) {
      setErrorMsg(e?.response?.data?.message || '적용 중 오류가 발생했습니다.');
      setPhase('review');
    }
  }

  function handleDone() {
    onApplied();
    onClose();
  }

  const selectedRulesCount = globalRules.filter(r => r.included).length;
  const selectedCautionsCount = personaCautions.filter(c => c.included).length;
  const hasSelections = selectedRulesCount > 0 || selectedCautionsCount > 0;

  return (
    <Dialog open={open} onOpenChange={(v) => { if (!v) onClose(); }}>
      <DialogContent className="w-[92vw] max-w-4xl max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <Zap className="h-5 w-5 text-purple-500" />
            PENDING 일괄 분석 (map-reduce)
          </DialogTitle>
        </DialogHeader>

        {/* ── idle: 시작 전 안내 ── */}
        {phase === 'idle' && (
          <div className="space-y-4">
            <div className="p-4 bg-purple-50 border border-purple-200 rounded-md text-sm text-purple-800 space-y-2">
              <p className="font-semibold flex items-center gap-1.5"><Sparkles className="h-4 w-4" /> 일괄 분석 방식</p>
              <ul className="list-disc list-inside space-y-1 text-[13px]">
                <li><strong>MAP</strong>: PENDING 첨삭을 청크(≈22,000자)로 나눠 Sonnet이 패턴을 추출합니다.</li>
                <li><strong>REDUCE</strong>: Opus가 모든 패턴을 통합 · 중복 제거 · 전역/페르소나 판정을 합니다.</li>
                <li>결과를 <strong>직접 검토·편집</strong> 후 선택 적용합니다 (자동 적용 안 됨).</li>
              </ul>
              <p className="text-xs text-purple-700 mt-1">청크 수에 따라 수십 초~수 분이 소요될 수 있습니다.</p>
            </div>
            <Button
              onClick={handleStart}
              className="w-full bg-purple-600 hover:bg-purple-700 text-white"
            >
              <Zap className="h-4 w-4 mr-2" />
              분석 시작
            </Button>
          </div>
        )}

        {/* ── starting / polling: 진행 중 ── */}
        {(phase === 'starting' || phase === 'polling') && (
          <div className="py-10 flex flex-col items-center gap-4">
            <Loader2 className="h-10 w-10 text-purple-500 animate-spin" />
            {phase === 'polling' && jobSnapshot ? (
              <div className="text-center space-y-1">
                <p className="text-sm font-medium text-gray-700">
                  MAP 청크 분석 중 {jobSnapshot.chunksDone}/{jobSnapshot.chunksTotal}…
                </p>
                <p className="text-xs text-muted-foreground">
                  총 {jobSnapshot.pendingCount}건 분석 중. REDUCE 단계가 이어집니다.
                </p>
                {/* 진행 바 */}
                <div className="w-64 h-2 bg-gray-100 rounded-full overflow-hidden mt-2">
                  <div
                    className="h-full bg-purple-500 transition-all"
                    style={{
                      width: jobSnapshot.chunksTotal > 0
                        ? `${Math.round((jobSnapshot.chunksDone / jobSnapshot.chunksTotal) * 90)}%`
                        : '10%',
                    }}
                  />
                </div>
              </div>
            ) : (
              <p className="text-sm text-muted-foreground">분석을 시작하는 중…</p>
            )}
          </div>
        )}

        {/* ── error ── */}
        {phase === 'error' && (
          <div className="space-y-4">
            <div className="p-3 bg-red-50 border border-red-200 rounded-md flex items-start gap-2">
              <AlertCircle className="h-4 w-4 text-red-500 mt-0.5 shrink-0" />
              <p className="text-sm text-red-700">{errorMsg}</p>
            </div>
            <Button variant="outline" onClick={() => setPhase('idle')} className="w-full">
              다시 시도
            </Button>
          </div>
        )}

        {/* ── review: 검토 모달 ── */}
        {phase === 'review' && (
          <div className="space-y-6">
            {errorMsg && (
              <div className="p-2 bg-red-50 border border-red-200 rounded text-xs text-red-700">{errorMsg}</div>
            )}

            <div className="text-sm text-purple-700 font-medium flex items-center gap-1.5 border-b pb-2">
              <Sparkles className="h-4 w-4" />
              분석 완료 — 아래 규칙을 검토·편집 후 적용할 항목을 선택하세요
            </div>

            {/* ── 전역 금지 규칙 ── */}
            <div className="space-y-2">
              <div className="flex items-center justify-between">
                <h3 className="text-sm font-semibold text-gray-800">
                  전역 금지 규칙 제안
                  <span className="ml-2 text-xs font-normal text-muted-foreground">
                    ({selectedRulesCount}/{globalRules.length}개 선택)
                  </span>
                </h3>
              </div>

              {globalRules.length === 0 ? (
                <p className="text-xs text-muted-foreground py-2">제안된 전역 규칙이 없습니다.</p>
              ) : (
                <div className="space-y-2">
                  {globalRules.map((rule, idx) => (
                    <div
                      key={idx}
                      className={`border rounded-lg p-3 space-y-2 transition-opacity ${rule.included ? '' : 'opacity-40'}`}
                    >
                      <div className="flex items-start gap-2">
                        <Checkbox
                          checked={rule.included}
                          onCheckedChange={(v) => {
                            const next = [...globalRules];
                            next[idx] = { ...next[idx], included: !!v };
                            setGlobalRules(next);
                          }}
                          className="mt-0.5"
                        />
                        <div className="flex-1 space-y-1.5">
                          <Input
                            value={rule.ruleText}
                            disabled={!rule.included}
                            onChange={(e) => {
                              const next = [...globalRules];
                              next[idx] = { ...next[idx], ruleText: e.target.value };
                              setGlobalRules(next);
                            }}
                            className="h-8 text-sm"
                          />
                          <div className="flex items-center gap-2 flex-wrap">
                            <Select
                              value={rule.scope}
                              disabled={!rule.included}
                              onValueChange={(v: any) => {
                                const next = [...globalRules];
                                next[idx] = { ...next[idx], scope: v };
                                setGlobalRules(next);
                              }}
                            >
                              <SelectTrigger className="h-6 w-28 text-xs">
                                <SelectValue />
                              </SelectTrigger>
                              <SelectContent>
                                <SelectItem value="ALL">전체</SelectItem>
                                <SelectItem value="POST">게시글만</SelectItem>
                                <SelectItem value="COMMENT">댓글만</SelectItem>
                              </SelectContent>
                            </Select>
                            {rule.sourceCorrIds.map(id => (
                              <Badge key={id} variant="outline" className="text-[10px] px-1 py-0">
                                첨삭#{id}
                              </Badge>
                            ))}
                          </div>
                          {rule.rationale && (
                            <p className="text-[11px] text-muted-foreground italic">{rule.rationale}</p>
                          )}
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>

            {/* ── 페르소나 주의사항 ── */}
            <div className="space-y-2">
              <div className="flex items-center justify-between">
                <h3 className="text-sm font-semibold text-gray-800">
                  페르소나 주의사항 제안
                  <span className="ml-2 text-xs font-normal text-muted-foreground">
                    ({selectedCautionsCount}/{personaCautions.length}개 선택)
                  </span>
                </h3>
              </div>

              {personaCautions.length === 0 ? (
                <p className="text-xs text-muted-foreground py-2">제안된 페르소나 주의사항이 없습니다.</p>
              ) : (
                <div className="space-y-2">
                  {personaCautions.map((caution, idx) => (
                    <div
                      key={idx}
                      className={`border rounded-lg p-3 space-y-2 transition-opacity ${caution.included ? '' : 'opacity-40'}`}
                    >
                      <div className="flex items-start gap-2">
                        <Checkbox
                          checked={caution.included}
                          onCheckedChange={(v) => {
                            const next = [...personaCautions];
                            next[idx] = { ...next[idx], included: !!v };
                            setPersonaCautions(next);
                          }}
                          className="mt-0.5"
                        />
                        <div className="flex-1 space-y-1.5">
                          <div className="flex items-center gap-2 flex-wrap">
                            <span className="text-[11px] font-mono text-muted-foreground bg-gray-50 px-1.5 py-0.5 rounded border">
                              {caution.personaId}
                            </span>
                            {caution.sourceCorrIds.map(id => (
                              <Badge key={id} variant="outline" className="text-[10px] px-1 py-0">
                                첨삭#{id}
                              </Badge>
                            ))}
                          </div>
                          <Input
                            value={caution.cautionText}
                            disabled={!caution.included}
                            onChange={(e) => {
                              const next = [...personaCautions];
                              next[idx] = { ...next[idx], cautionText: e.target.value };
                              setPersonaCautions(next);
                            }}
                            className="h-8 text-sm"
                          />
                          {caution.rationale && (
                            <p className="text-[11px] text-muted-foreground italic">{caution.rationale}</p>
                          )}
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>

            {/* ── example_bank 환류 ── */}
            <div className="flex items-center gap-2 border-t pt-3">
              <Checkbox
                id="pushToBank"
                checked={pushToBank}
                onCheckedChange={v => setPushToBank(!!v)}
              />
              <Label htmlFor="pushToBank" className="text-sm cursor-pointer">
                학습 예시 뱅크(example_bank)에도 교정본 저장
                <span className="block text-[11px] text-muted-foreground">RAG에서 좋은 예시로 활용</span>
              </Label>
            </div>
          </div>
        )}

        {/* ── done: 완료 ── */}
        {phase === 'done' && applyResult && (
          <div className="py-8 flex flex-col items-center gap-4 text-center">
            <CheckCheck className="h-12 w-12 text-green-500" />
            <div className="space-y-1">
              <p className="text-lg font-semibold text-gray-800">적용 완료!</p>
              <p className="text-sm text-muted-foreground">
                전역 규칙 <strong>{applyResult.rulesCreated}개</strong> 생성 ·
                페르소나 주의사항 <strong>{applyResult.cautionsApplied}개</strong> 갱신 ·
                첨삭 <strong>{applyResult.corrProcessed}건</strong> PROCESSED 승격
              </p>
            </div>
          </div>
        )}

        {/* ── Footer ── */}
        <DialogFooter className="gap-2 pt-2">
          {phase === 'done' ? (
            <Button onClick={handleDone} className="bg-green-600 hover:bg-green-700 text-white">
              <CheckCheck className="h-4 w-4 mr-1.5" />
              완료
            </Button>
          ) : (
            <>
              <Button variant="outline" onClick={onClose} disabled={phase === 'applying'}>
                닫기
              </Button>
              {phase === 'review' && (
                <Button
                  onClick={handleApply}
                  disabled={!hasSelections || phase === ('applying' as any)}
                  className="bg-purple-600 hover:bg-purple-700 text-white"
                >
                  <CheckCheck className="h-4 w-4 mr-1.5" />
                  선택 항목 적용 ({selectedRulesCount + selectedCautionsCount}개)
                </Button>
              )}
            </>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
