'use client';

import { useState, useCallback, useEffect, useRef } from 'react';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import { Label } from '@/components/ui/label';
import { Checkbox } from '@/components/ui/checkbox';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { AdminTable } from '@/components/admin/AdminTable';
import { AdminPagination } from '@/components/admin/AdminPagination';
import { AdminSection } from '@/components/admin/AdminSection';
import {
  listGlobalRules,
  createGlobalRule,
  toggleGlobalRule,
  deleteGlobalRule,
  listCautions,
  deleteCaution,
  listCorrectionHistory,
  analyzeCorrectionHistory,
  applyCorrectionHistory,
  skipCorrectionHistory,
  listPromptTemplates,
  updatePromptTemplate,
  getAnthropicApiKey,
  upsertAnthropicApiKey,
  deleteAnthropicApiKey,
  AiGlobalRule,
  AiCaution,
  AiCorrectionHistory,
  AiPromptTemplate,
  AnalyzeResponse,
  ApiKeyStatus,
} from '@/lib/api/admin/corrections';
import { Sparkles, Plus, Trash2, Power, BrainCircuit, CheckCheck, SkipForward, ChevronDown, ChevronUp, Zap, FileText, Save, MessageSquare, Loader2, AlertCircle, KeyRound, Eye, EyeOff } from 'lucide-react';
import { BatchAnalysisReviewDialog } from '@/components/admin/ai-rules/BatchAnalysisReviewDialog';
import { startBatchAnalysis, getBatchAnalysisJob, type BatchJobSnapshot } from '@/lib/api/admin/corrections';

const SCOPE_LABELS: Record<string, string> = {
  ALL: '전체',
  POST: '게시글만',
  COMMENT: '댓글만',
};

const STATUS_LABELS: Record<string, { label: string; color: string }> = {
  PENDING:   { label: '분석 대기', color: 'bg-amber-100 text-amber-700 border-amber-200' },
  PROCESSED: { label: '처리 완료', color: 'bg-green-100 text-green-700 border-green-200' },
  SKIPPED:   { label: '건너뜀',   color: 'bg-gray-100 text-gray-500 border-gray-200' },
};

// ── 첨삭 이력 행 (인라인 분석·적용 패널 포함) ────────────────────────────

interface HistoryRowState {
  open: boolean;
  analyzing: boolean;
  applying: boolean;
  analysis: AnalyzeResponse | null;
  scope: 'PERSONA' | 'GLOBAL' | 'BOTH';
  editedCaution: string;
  selectedRules: boolean[];
  editedRules: string[];
  pushToBank: boolean;
  error: string;
}

function makeDefault(): HistoryRowState {
  return {
    open: false, analyzing: false, applying: false,
    analysis: null, scope: 'BOTH',
    editedCaution: '', selectedRules: [], editedRules: [],
    pushToBank: true, error: '',
  };
}

function HistoryRow({
  row,
  onRefresh,
}: {
  row: AiCorrectionHistory;
  onRefresh: () => void;
}) {
  const [state, setState] = useState<HistoryRowState>(makeDefault);

  function update(patch: Partial<HistoryRowState>) {
    setState(prev => ({ ...prev, ...patch }));
  }

  async function handleAnalyze() {
    update({ analyzing: true, error: '' });
    try {
      const res = await analyzeCorrectionHistory(row.id);
      update({
        analysis: res,
        editedCaution: res.suggestedCaution ?? '',
        editedRules: [...res.suggestedGlobalRules],
        selectedRules: res.suggestedGlobalRules.map(() => true),
        open: true,
      });
    } catch (e: any) {
      update({ error: e?.response?.data?.message || '분석 실패. 잠시 후 다시 시도해주세요.' });
    } finally {
      update({ analyzing: false });
    }
  }

  async function handleApply() {
    update({ applying: true, error: '' });
    try {
      const finalRules = state.editedRules.filter((_, i) => state.selectedRules[i]).filter(Boolean);
      await applyCorrectionHistory(row.id, {
        scope: state.scope,
        personaCaution: state.editedCaution.trim() || null,
        globalRules: finalRules,
        pushToBank: state.pushToBank,
      });
      onRefresh();
    } catch (e: any) {
      update({ applying: false, error: e?.response?.data?.message || '적용 실패.' });
    }
  }

  async function handleSkip() {
    if (!window.confirm('이 첨삭을 학습 데이터로 사용하지 않겠습니까?')) return;
    await skipCorrectionHistory(row.id);
    onRefresh();
  }

  const st = STATUS_LABELS[row.status] ?? STATUS_LABELS.PENDING;
  const isPending = row.status === 'PENDING';

  return (
    <div className={`border rounded-lg overflow-hidden mb-2 ${row.status === 'SKIPPED' ? 'opacity-50' : ''}`}>
      {/* 요약 행 */}
      <div className="flex items-start gap-3 p-3 bg-white">
        {/* 상태 배지 */}
        <Badge className={`text-[10px] px-1.5 py-0.5 border shrink-0 mt-0.5 font-normal ${st.color}`}>
          {st.label}
        </Badge>

        {/* 타입 + 작성자 */}
        <div className="shrink-0 text-xs text-muted-foreground w-20">
          <div>{row.targetType}</div>
          <div className="font-mono truncate">{row.personaId.slice(0, 8)}…</div>
        </div>

        {/* 원본 vs 수정본 미리보기 */}
        <div className="flex-1 min-w-0 grid grid-cols-2 gap-2 text-xs">
          <div className="bg-red-50 rounded p-1.5 max-h-14 overflow-hidden">
            <span className="text-[10px] font-semibold text-red-500 block mb-0.5">원본</span>
            <p className="text-gray-700 line-clamp-2">{row.originalText}</p>
          </div>
          <div className="bg-green-50 rounded p-1.5 max-h-14 overflow-hidden">
            <span className="text-[10px] font-semibold text-green-600 block mb-0.5">수정본</span>
            <p className="text-gray-700 line-clamp-2">{row.correctedText}</p>
          </div>
        </div>

        {/* 관리자 의견 + 날짜 */}
        <div className="shrink-0 text-xs text-muted-foreground space-y-0.5">
          <div>{new Date(row.createdAt).toLocaleDateString('ko-KR')}</div>
          {row.adminOpinion && (
            <div className="flex items-start gap-1 max-w-[120px]">
              <MessageSquare className="h-3 w-3 text-purple-400 shrink-0 mt-0.5" />
              <span className="text-[10px] text-purple-700 line-clamp-2">{row.adminOpinion}</span>
            </div>
          )}
        </div>

        {/* 액션 버튼 */}
        <div className="shrink-0 flex gap-1 items-center">
          {isPending && (
            <>
              <Button
                size="sm"
                variant="outline"
                className="h-7 px-2 text-xs text-purple-600 border-purple-200 hover:bg-purple-50"
                onClick={handleAnalyze}
                disabled={state.analyzing}
              >
                <BrainCircuit className="h-3.5 w-3.5 mr-1" />
                {state.analyzing ? '분석 중…' : 'Sonnet 분석'}
              </Button>
              <Button
                size="sm"
                variant="ghost"
                className="h-7 px-2 text-xs text-gray-400 hover:text-gray-600"
                onClick={handleSkip}
                title="건너뜀"
              >
                <SkipForward className="h-3.5 w-3.5" />
              </Button>
            </>
          )}
          {state.analysis && (
            <Button
              size="sm"
              variant="ghost"
              className="h-7 px-2 text-xs"
              onClick={() => update({ open: !state.open })}
            >
              {state.open ? <ChevronUp className="h-3.5 w-3.5" /> : <ChevronDown className="h-3.5 w-3.5" />}
            </Button>
          )}
        </div>
      </div>

      {/* 분석 결과 패널 (펼침) */}
      {state.open && state.analysis && (
        <div className="border-t bg-purple-50/40 p-4 space-y-4">
          {state.error && (
            <p className="text-xs text-red-600 bg-red-50 p-2 rounded">{state.error}</p>
          )}

          <div className="text-xs text-purple-700 font-medium flex items-center gap-1.5">
            <Sparkles className="h-3.5 w-3.5" />
            Sonnet 분석 완료 — 아래 규칙을 검토·편집 후 적용 범위를 선택하세요
          </div>

          {/* 적용 범위 선택 */}
          <div className="grid grid-cols-2 gap-3">
            <div>
              <Label className="text-xs font-semibold mb-1 block">적용 범위</Label>
              <Select value={state.scope} onValueChange={(v: any) => update({ scope: v })}>
                <SelectTrigger className="h-8 text-sm">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="PERSONA">
                    이 AI 유저에게만 (페르소나 주의사항)
                  </SelectItem>
                  <SelectItem value="GLOBAL">
                    모든 AI 유저에게 (전역 금지 규칙)
                  </SelectItem>
                  <SelectItem value="BOTH">
                    둘 다 적용
                  </SelectItem>
                </SelectContent>
              </Select>
            </div>

            {/* example_bank 환류 */}
            <div className="flex items-end gap-2">
              <Checkbox
                id={`bank-${row.id}`}
                checked={state.pushToBank}
                onCheckedChange={v => update({ pushToBank: !!v })}
              />
              <Label htmlFor={`bank-${row.id}`} className="text-xs cursor-pointer">
                학습 예시 뱅크에도 저장
                <span className="block text-[10px] text-muted-foreground">RAG에서 좋은 예시로 활용</span>
              </Label>
            </div>
          </div>

          {/* 페르소나 주의사항 (PERSONA / BOTH) */}
          {(state.scope === 'PERSONA' || state.scope === 'BOTH') && (
            <div>
              <Label className="text-xs font-semibold mb-1 block">
                페르소나 주의사항
                <span className="ml-2 text-[10px] font-normal text-muted-foreground">
                  페르소나 ID: {row.personaId.slice(0, 12)}…
                </span>
              </Label>
              <Input
                value={state.editedCaution}
                onChange={e => update({ editedCaution: e.target.value })}
                placeholder="빈 칸이면 주의사항 저장 안 됨"
                className="h-8 text-sm"
              />
            </div>
          )}

          {/* 전역 금지 규칙 (GLOBAL / BOTH) */}
          {(state.scope === 'GLOBAL' || state.scope === 'BOTH') && (
            <div>
              <Label className="text-xs font-semibold mb-1 block">
                전역 금지 규칙
                <span className="ml-2 text-[10px] font-normal text-muted-foreground">
                  체크된 항목만 저장
                </span>
              </Label>
              {state.editedRules.length === 0 ? (
                <p className="text-xs text-muted-foreground">제안된 전역 규칙이 없습니다.</p>
              ) : (
                <div className="space-y-1.5">
                  {state.editedRules.map((rule, idx) => (
                    <div key={idx} className="flex items-center gap-2">
                      <Checkbox
                        checked={state.selectedRules[idx]}
                        onCheckedChange={() => {
                          const next = [...state.selectedRules];
                          next[idx] = !next[idx];
                          update({ selectedRules: next });
                        }}
                      />
                      <Input
                        value={rule}
                        onChange={e => {
                          const next = [...state.editedRules];
                          next[idx] = e.target.value;
                          update({ editedRules: next });
                        }}
                        disabled={!state.selectedRules[idx]}
                        className={`h-7 text-xs ${!state.selectedRules[idx] ? 'opacity-40' : ''}`}
                      />
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}

          {/* 적용 버튼 */}
          <div className="flex gap-2 justify-end pt-1">
            <Button
              size="sm"
              variant="outline"
              onClick={() => update({ open: false })}
              disabled={state.applying}
            >
              접기
            </Button>
            <Button
              size="sm"
              onClick={handleApply}
              disabled={state.applying}
              className="bg-purple-600 hover:bg-purple-700 text-white"
            >
              <CheckCheck className="h-3.5 w-3.5 mr-1" />
              {state.applying ? '적용 중…' : '학습 데이터 적용'}
            </Button>
          </div>
        </div>
      )}
    </div>
  );
}

// ── 메인 페이지 ──────────────────────────────────────────────────────────────

// ── 기본 프롬프트 탭 ─────────────────────────────────────────────────────────

const PROMPT_LABELS: Record<string, string> = {
  'voice/post':    '게시글 스타일',
  'voice/comment': '댓글 스타일',
  'voice/reply':   '대댓글 스타일',
  'voice/partner': '상대방 게시글 스타일',
};

function PromptTemplateEditor({
  tpl,
  onSaved,
}: {
  tpl: AiPromptTemplate;
  onSaved: () => void;
}) {
  const [content, setContent] = useState(tpl.content);
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [err, setErr] = useState('');

  async function handleSave() {
    setSaving(true); setErr(''); setSaved(false);
    try {
      await updatePromptTemplate(tpl.key, content);
      setSaved(true);
      onSaved();
      setTimeout(() => setSaved(false), 3000);
    } catch (e: any) {
      setErr(e?.response?.data?.message || '저장 실패');
    } finally {
      setSaving(false);
    }
  }

  const label = PROMPT_LABELS[tpl.key] || tpl.key;
  const dirty = content !== tpl.content;

  return (
    <div className="border rounded-lg overflow-hidden mb-4">
      <div className="flex items-center justify-between px-4 py-2 bg-gray-50 border-b">
        <div className="flex items-center gap-2">
          <FileText className="h-4 w-4 text-muted-foreground" />
          <span className="font-medium text-sm">{label}</span>
          <span className="text-xs font-mono text-muted-foreground">{tpl.key}</span>
          {dirty && <span className="text-[10px] px-1.5 py-0.5 rounded bg-amber-100 text-amber-700 border border-amber-200">미저장</span>}
        </div>
        <div className="flex items-center gap-2">
          {tpl.updatedBy && (
            <span className="text-xs text-muted-foreground">
              마지막 수정: {tpl.updatedBy} · {tpl.updatedAt ? new Date(tpl.updatedAt).toLocaleDateString('ko-KR') : '-'}
            </span>
          )}
          <Button
            size="sm"
            onClick={handleSave}
            disabled={saving || !dirty}
            className={`h-7 px-3 text-xs ${saved ? 'bg-green-600 hover:bg-green-700' : ''}`}
          >
            <Save className="h-3.5 w-3.5 mr-1" />
            {saving ? '저장 중…' : saved ? '저장됨' : '저장'}
          </Button>
        </div>
      </div>
      {err && <p className="text-xs text-red-600 bg-red-50 px-3 py-1.5">{err}</p>}
      <textarea
        className="w-full font-mono text-xs p-3 resize-y focus:outline-none focus:ring-1 focus:ring-blue-300"
        style={{ minHeight: '320px' }}
        value={content}
        onChange={e => setContent(e.target.value)}
        spellCheck={false}
      />
    </div>
  );
}

// ── 메인 페이지 ──────────────────────────────────────────────────────────────

export default function AiRulesPage() {
  // ─── 전역 금지 규칙 ───
  const [rules, setRules] = useState<AiGlobalRule[]>([]);
  const [rulesPage, setRulesPage] = useState(0);
  const [rulesTotalPages, setRulesTotalPages] = useState(0);
  const [rulesLoading, setRulesLoading] = useState(false);
  const [rulesActiveFilter, setRulesActiveFilter] = useState<string>('ALL');
  const [newRuleText, setNewRuleText] = useState('');
  const [newRuleScope, setNewRuleScope] = useState('ALL');
  const [addingRule, setAddingRule] = useState(false);

  // ─── 페르소나 주의사항 ───
  const [cautions, setCautions] = useState<AiCaution[]>([]);
  const [cautionsPage, setCautionsPage] = useState(0);
  const [cautionsTotalPages, setCautionsTotalPages] = useState(0);
  const [cautionsLoading, setCautionsLoading] = useState(false);
  const [personaIdFilter, setPersonaIdFilter] = useState('');

  // ─── 첨삭 이력 ───
  const [history, setHistory] = useState<AiCorrectionHistory[]>([]);
  const [historyPage, setHistoryPage] = useState(0);
  const [historyTotalPages, setHistoryTotalPages] = useState(0);
  const [historyTotalElements, setHistoryTotalElements] = useState(0);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [historyStatusFilter, setHistoryStatusFilter] = useState<'ALL' | 'PENDING' | 'PROCESSED' | 'SKIPPED'>('ALL');

  // ─── 기본 프롬프트 ───
  const [prompts, setPrompts] = useState<AiPromptTemplate[]>([]);
  const [promptsLoading, setPromptsLoading] = useState(false);

  const [error, setError] = useState('');

  // ─── 일괄 분석 (페이지 레벨 상태) ───
  const [batchPhase, setBatchPhase] = useState<'idle' | 'starting' | 'polling'>('idle');
  const [batchSnapshot, setBatchSnapshot] = useState<BatchJobSnapshot | null>(null);
  const [batchError, setBatchError] = useState('');
  const [reviewSnapshot, setReviewSnapshot] = useState<BatchJobSnapshot | null>(null);
  const [reviewOpen, setReviewOpen] = useState(false);
  const batchPollingRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const batchJobIdRef   = useRef<string>('');

  // ─── 로드 ───
  const loadRules = useCallback(async (page: number) => {
    setRulesLoading(true);
    try {
      const activeParam = rulesActiveFilter === 'ACTIVE' ? true : rulesActiveFilter === 'INACTIVE' ? false : undefined;
      const res = await listGlobalRules({ page, size: 20, active: activeParam });
      setRules(res.content); setRulesTotalPages(res.totalPages); setRulesPage(page);
    } catch (e) { console.error(e); } finally { setRulesLoading(false); }
  }, [rulesActiveFilter]);

  const loadCautions = useCallback(async (page: number) => {
    setCautionsLoading(true);
    try {
      const res = await listCautions({ page, size: 20, personaId: personaIdFilter || undefined });
      setCautions(res.content); setCautionsTotalPages(res.totalPages); setCautionsPage(page);
    } catch (e) { console.error(e); } finally { setCautionsLoading(false); }
  }, [personaIdFilter]);

  const loadHistory = useCallback(async (page: number) => {
    setHistoryLoading(true);
    try {
      const res = await listCorrectionHistory({ page, size: 20, status: historyStatusFilter });
      setHistory(res.content);
      setHistoryTotalPages(res.totalPages);
      setHistoryTotalElements(res.totalElements);
      setHistoryPage(page);
    } catch (e) { console.error(e); } finally { setHistoryLoading(false); }
  }, [historyStatusFilter]);

  const loadPrompts = useCallback(async () => {
    setPromptsLoading(true);
    try {
      const res = await listPromptTemplates();
      setPrompts(res);
    } catch (e) { console.error(e); } finally { setPromptsLoading(false); }
  }, []);

  useEffect(() => { loadRules(0); }, [rulesActiveFilter, loadRules]);
  useEffect(() => { loadCautions(0); }, [personaIdFilter, loadCautions]);
  useEffect(() => { loadHistory(0); }, [historyStatusFilter, loadHistory]);
  useEffect(() => { loadPrompts(); }, [loadPrompts]);

  // 폴링 cleanup
  useEffect(() => () => { if (batchPollingRef.current) clearInterval(batchPollingRef.current); }, []);

  // ─── 핸들러 ───
  async function handleAddRule() {
    if (!newRuleText.trim()) return;
    setAddingRule(true); setError('');
    try {
      await createGlobalRule(newRuleText.trim(), newRuleScope);
      setNewRuleText(''); loadRules(0);
    } catch (e: any) {
      setError(e?.response?.data?.message || '규칙 추가에 실패했습니다.');
    } finally { setAddingRule(false); }
  }

  async function handleToggleRule(rule: AiGlobalRule) {
    try { await toggleGlobalRule(rule.id, !rule.active); loadRules(rulesPage); }
    catch { alert('상태 변경에 실패했습니다.'); }
  }

  async function handleDeleteRule(rule: AiGlobalRule) {
    if (!window.confirm('이 규칙을 삭제하시겠습니까?')) return;
    try { await deleteGlobalRule(rule.id); loadRules(rulesPage); }
    catch { alert('삭제에 실패했습니다.'); }
  }

  async function handleBatchAnalyze() {
    setBatchPhase('starting');
    setBatchError('');
    setBatchSnapshot(null);
    try {
      const res = await startBatchAnalysis();
      if (!res.jobId) {
        setBatchError(res.message || '분석 대기 중인 첨삭이 없습니다.');
        setBatchPhase('idle');
        return;
      }
      batchJobIdRef.current = res.jobId;
      setBatchPhase('polling');
      if (batchPollingRef.current) clearInterval(batchPollingRef.current);
      batchPollingRef.current = setInterval(async () => {
        try {
          const snap = await getBatchAnalysisJob(batchJobIdRef.current);
          setBatchSnapshot(snap);
          if (snap.status === 'READY') {
            clearInterval(batchPollingRef.current!);
            setReviewSnapshot(snap);
            setReviewOpen(true);
            setBatchPhase('idle');
            setBatchSnapshot(null);
          } else if (snap.status === 'FAILED') {
            clearInterval(batchPollingRef.current!);
            setBatchError(snap.error || '분석 중 오류가 발생했습니다. 다시 시도해주세요.');
            setBatchPhase('idle');
            setBatchSnapshot(null);
          }
        } catch {
          // 일시적 네트워크 오류 무시
        }
      }, 2500);
    } catch (e: any) {
      setBatchError(e?.response?.data?.message || '분석 시작에 실패했습니다. 잠시 후 다시 시도해주세요.');
      setBatchPhase('idle');
    }
  }

  async function handleDeleteCaution(caution: AiCaution) {
    if (!window.confirm('이 주의사항을 삭제하시겠습니까?')) return;
    try { await deleteCaution(caution.id); loadCautions(cautionsPage); }
    catch { alert('삭제에 실패했습니다.'); }
  }

  return (
    <>
    <BatchAnalysisReviewDialog
      open={reviewOpen}
      initialSnapshot={reviewSnapshot}
      onClose={() => { setReviewOpen(false); setReviewSnapshot(null); }}
      onApplied={() => { setReviewOpen(false); setReviewSnapshot(null); loadHistory(0); loadRules(0); loadCautions(0); }}
    />
    <AdminSection title="AI 규칙 관리">
      <div className="flex items-center gap-2 mb-4 text-sm text-muted-foreground">
        <Sparkles className="h-4 w-4 text-purple-500" />
        관리자 수정·AI 개선으로 생성된 학습 데이터를 관리합니다.
      </div>

      {error && (
        <div className="mb-4 p-3 bg-red-50 border border-red-200 rounded-md text-sm text-red-700">{error}</div>
      )}

      <Tabs defaultValue="history">
        <TabsList>
          <TabsTrigger value="history" className="relative">
            첨삭 이력
            {historyTotalElements > 0 && (
              <span className="ml-1.5 px-1.5 py-0.5 rounded-full bg-purple-600 text-white text-[10px] font-bold">
                {historyTotalElements > 99 ? '99+' : historyTotalElements}
              </span>
            )}
          </TabsTrigger>
          <TabsTrigger value="global">전역 금지 규칙</TabsTrigger>
          <TabsTrigger value="cautions">페르소나 주의사항</TabsTrigger>
          <TabsTrigger value="prompts">기본 프롬프트</TabsTrigger>
          <TabsTrigger value="api-settings">API 설정</TabsTrigger>
        </TabsList>

        {/* ── 첨삭 이력 탭 ── */}
        <TabsContent value="history" className="space-y-3 mt-4">
          {/* 설명 */}
          <div className="p-3 bg-purple-50 border border-purple-200 rounded-md text-xs text-purple-800 space-y-1">
            <p className="font-semibold">📋 첨삭 이력이란?</p>
            <p>관리자가 <strong>수정</strong> 버튼으로 편집하거나 <strong>AI 개선</strong>으로 처리한 모든 원본↔수정본 기록입니다.</p>
            <p>PENDING 항목을 <strong>Sonnet 분석</strong> → <strong>범위 선택(이 AI 유저만 / 전체 / 둘 다)</strong> → <strong>학습 데이터 적용</strong>하세요.</p>
          </div>

          {/* 필터 + 일괄 분석 */}
          <div className="flex gap-2 items-center flex-wrap">
            <Select value={historyStatusFilter} onValueChange={(v: any) => setHistoryStatusFilter(v)}>
              <SelectTrigger className="w-36 h-8">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="ALL">전체</SelectItem>
                <SelectItem value="PENDING">분석 대기</SelectItem>
                <SelectItem value="PROCESSED">처리 완료</SelectItem>
                <SelectItem value="SKIPPED">건너뜀</SelectItem>
              </SelectContent>
            </Select>
            <span className="text-xs text-muted-foreground">총 {historyTotalElements}건</span>

            <div className="ml-auto flex flex-col items-end gap-1.5">
              <Button
                size="sm"
                onClick={handleBatchAnalyze}
                disabled={batchPhase !== 'idle'}
                className="bg-purple-600 hover:bg-purple-700 text-white h-8 px-3 disabled:opacity-60"
              >
                {batchPhase !== 'idle' ? (
                  <><Loader2 className="h-3.5 w-3.5 mr-1.5 animate-spin" />분석 중…</>
                ) : (
                  <><Zap className="h-3.5 w-3.5 mr-1.5" />PENDING 일괄 분석</>
                )}
              </Button>

              {/* 인라인 진행 표시 */}
              {(batchPhase === 'starting' || batchPhase === 'polling') && (
                <div className="w-64 p-2 bg-purple-50 border border-purple-200 rounded-md space-y-1.5">
                  <div className="flex items-center justify-between text-xs text-purple-700">
                    <span>
                      {batchPhase === 'starting'
                        ? '분석 시작 중…'
                        : batchSnapshot
                          ? `MAP ${batchSnapshot.chunksDone}/${batchSnapshot.chunksTotal} 청크`
                          : '청크 분석 중…'}
                    </span>
                    {batchSnapshot && (
                      <span className="text-[10px] text-purple-500">{batchSnapshot.pendingCount}건</span>
                    )}
                  </div>
                  <div className="h-1.5 bg-purple-100 rounded-full overflow-hidden">
                    <div
                      className="h-full bg-purple-500 transition-all duration-500"
                      style={{
                        width: batchSnapshot && batchSnapshot.chunksTotal > 0
                          ? `${Math.max(5, Math.round((batchSnapshot.chunksDone / batchSnapshot.chunksTotal) * 90))}%`
                          : '5%',
                      }}
                    />
                  </div>
                  {batchSnapshot && batchSnapshot.chunksTotal > 0 && batchSnapshot.chunksDone >= batchSnapshot.chunksTotal && (
                    <p className="text-[10px] text-purple-600">REDUCE(Opus) 통합 중…</p>
                  )}
                </div>
              )}

              {/* 오류 표시 */}
              {batchPhase === 'idle' && batchError && (
                <div className="flex items-start gap-1.5 w-64 p-2 bg-red-50 border border-red-200 rounded-md">
                  <AlertCircle className="h-3.5 w-3.5 text-red-500 shrink-0 mt-0.5" />
                  <p className="text-xs text-red-700">{batchError}</p>
                </div>
              )}
            </div>
          </div>

          {/* 이력 목록 */}
          {historyLoading ? (
            <p className="text-sm text-muted-foreground text-center py-8">로딩 중…</p>
          ) : history.length === 0 ? (
            <p className="text-sm text-muted-foreground text-center py-8">
              첨삭 이력이 없습니다. 콘텐츠 관리에서 글이나 댓글을 수정하면 여기에 쌓입니다.
            </p>
          ) : (
            history.map(row => (
              <HistoryRow key={row.id} row={row} onRefresh={() => loadHistory(historyPage)} />
            ))
          )}

          <AdminPagination
            page={historyPage}
            totalPages={historyTotalPages}
            onPageChange={loadHistory}
          />
        </TabsContent>

        {/* ── 전역 금지 규칙 탭 ── */}
        <TabsContent value="global" className="space-y-4">
          <div className="flex gap-2 items-end">
            <div className="flex-1">
              <label className="text-sm font-medium block mb-1">새 규칙 직접 추가</label>
              <Input
                placeholder="예: 전여친/전남친 외모 비교를 과도하게 반복하지 말 것"
                value={newRuleText}
                onChange={(e) => setNewRuleText(e.target.value)}
                onKeyDown={(e) => e.key === 'Enter' && handleAddRule()}
                disabled={addingRule}
              />
            </div>
            <div className="w-36">
              <label className="text-sm font-medium block mb-1">적용 범위</label>
              <Select value={newRuleScope} onValueChange={setNewRuleScope} disabled={addingRule}>
                <SelectTrigger><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem value="ALL">전체</SelectItem>
                  <SelectItem value="POST">게시글만</SelectItem>
                  <SelectItem value="COMMENT">댓글만</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <Button onClick={handleAddRule} disabled={addingRule || !newRuleText.trim()} className="flex items-center gap-1">
              <Plus className="h-4 w-4" />추가
            </Button>
          </div>

          <div className="flex gap-2">
            <Select value={rulesActiveFilter} onValueChange={setRulesActiveFilter}>
              <SelectTrigger className="w-36 h-8"><SelectValue /></SelectTrigger>
              <SelectContent>
                <SelectItem value="ALL">전체</SelectItem>
                <SelectItem value="ACTIVE">활성</SelectItem>
                <SelectItem value="INACTIVE">비활성</SelectItem>
              </SelectContent>
            </Select>
          </div>

          <div className="border rounded-lg overflow-hidden">
            <AdminTable<AiGlobalRule>
              data={rules}
              loading={rulesLoading}
              columns={[
                { key: 'ruleText', header: '규칙 내용', render: (row) => <span className="text-sm">{row.ruleText}</span> },
                { key: 'scope', header: '범위', render: (row) => <Badge variant="outline">{SCOPE_LABELS[row.scope] || row.scope}</Badge> },
                { key: 'active', header: '상태', render: (row) => <Badge variant={row.active ? 'default' : 'secondary'}>{row.active ? '활성' : '비활성'}</Badge> },
                { key: 'sourceCorrectionId', header: '출처', render: (row) => <span className="text-xs text-muted-foreground">{row.sourceCorrectionId ? `첨삭 #${row.sourceCorrectionId}` : '수동'}</span> },
                { key: 'createdAt', header: '생성일', render: (row) => <span className="text-xs text-muted-foreground">{new Date(row.createdAt).toLocaleDateString('ko-KR')}</span> },
                {
                  key: 'actions', header: '액션',
                  render: (row) => (
                    <div className="flex gap-1">
                      <Button variant="ghost" size="sm" onClick={() => handleToggleRule(row)} title={row.active ? '비활성화' : '활성화'}><Power className="h-4 w-4" /></Button>
                      <Button variant="ghost" size="sm" onClick={() => handleDeleteRule(row)} className="text-red-500 hover:text-red-700"><Trash2 className="h-4 w-4" /></Button>
                    </div>
                  ),
                },
              ]}
              rowKey={(row) => row.id}
            />
          </div>
          <AdminPagination page={rulesPage} totalPages={rulesTotalPages} onPageChange={loadRules} />
        </TabsContent>

        {/* ── 페르소나 주의사항 탭 ── */}
        <TabsContent value="cautions" className="space-y-4">
          <div className="flex gap-2">
            <div className="flex-1 max-w-xs">
              <label className="text-sm font-medium block mb-1">페르소나 ID 필터</label>
              <Input
                placeholder="페르소나 ID (빈 칸이면 전체)"
                value={personaIdFilter}
                onChange={(e) => setPersonaIdFilter(e.target.value)}
              />
            </div>
          </div>

          <div className="border rounded-lg overflow-hidden">
            <AdminTable<AiCaution>
              data={cautions}
              loading={cautionsLoading}
              columns={[
                { key: 'personaId', header: '페르소나', render: (row) => <span className="text-xs font-mono text-muted-foreground truncate block max-w-[100px]">{row.personaId}</span> },
                { key: 'personaCaution', header: '주의사항', render: (row) => <span className="text-sm">{row.personaCaution || '(없음)'}</span> },
                { key: 'targetType', header: '대상', render: (row) => <Badge variant="secondary">{row.targetType}</Badge> },
                { key: 'createdAt', header: '첨삭일', render: (row) => <span className="text-xs text-muted-foreground">{new Date(row.createdAt).toLocaleDateString('ko-KR')}</span> },
                {
                  key: 'actions', header: '액션',
                  render: (row) => (
                    <Button variant="ghost" size="sm" onClick={() => handleDeleteCaution(row)} className="text-red-500 hover:text-red-700">
                      <Trash2 className="h-4 w-4" />
                    </Button>
                  ),
                },
              ]}
              rowKey={(row) => row.id}
            />
          </div>
          <AdminPagination page={cautionsPage} totalPages={cautionsTotalPages} onPageChange={loadCautions} />
        </TabsContent>

        {/* ── 기본 프롬프트 탭 ── */}
        <TabsContent value="prompts" className="space-y-4">
          <div className="p-3 bg-blue-50 border border-blue-200 rounded-md text-xs text-blue-800 space-y-1">
            <p className="font-semibold">📝 기본 프롬프트란?</p>
            <p>AI 유저가 게시글·댓글·대댓글을 작성할 때 기본으로 주입되는 스타일 가이드입니다.</p>
            <p>저장하면 <strong>즉시</strong> AI 유저 생성 서비스에 반영됩니다. <code className="bg-blue-100 px-1 rounded">ㅠ</code> 빈도, 말투, 이모지 규칙 등을 여기서 조정하세요.</p>
          </div>

          {promptsLoading ? (
            <p className="text-sm text-muted-foreground text-center py-8">로딩 중…</p>
          ) : prompts.length === 0 ? (
            <p className="text-sm text-muted-foreground text-center py-8">
              프롬프트가 없습니다. AI 유저 서비스 재시작 후 자동 시드됩니다.
            </p>
          ) : (
            prompts.map(tpl => (
              <PromptTemplateEditor key={tpl.key} tpl={tpl} onSaved={loadPrompts} />
            ))
          )}
        </TabsContent>

        {/* ── API 설정 탭 ── */}
        <TabsContent value="api-settings" className="space-y-4">
          <div className="p-3 bg-amber-50 border border-amber-200 rounded-md text-xs text-amber-800 space-y-1">
            <p className="font-semibold">🔑 Claude / Anthropic API 키</p>
            <p>Anthropic API를 직접 호출하는 기능(수정 분석, API 모드 AI 유저 등)에 사용되는 키입니다.</p>
            <p>키는 DB에 저장되며 응답 시 항상 마스킹됩니다.</p>
          </div>
          <AnthropicApiKeyPanel />
        </TabsContent>
      </Tabs>
    </AdminSection>
    </>
  );
}

// ── Anthropic API 키 관리 패널 ──────────────────────────────────────────────

function AnthropicApiKeyPanel() {
  const [status, setStatus] = useState<ApiKeyStatus | null>(null);
  const [loading, setLoading] = useState(true);
  const [inputValue, setInputValue] = useState('');
  const [showInput, setShowInput] = useState(false);
  const [saving, setSaving] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  useEffect(() => {
    loadStatus();
  }, []);

  const loadStatus = async () => {
    setLoading(true);
    try {
      const data = await getAnthropicApiKey();
      setStatus(data);
    } catch {
      setError('API 키 상태를 불러오지 못했습니다.');
    } finally {
      setLoading(false);
    }
  };

  const handleSave = async () => {
    if (!inputValue.trim()) { setError('API 키를 입력해주세요.'); return; }
    setSaving(true);
    setError('');
    setSuccess('');
    try {
      const data = await upsertAnthropicApiKey(inputValue.trim());
      setStatus(data);
      setInputValue('');
      setSuccess('API 키가 저장됐습니다.');
    } catch {
      setError('저장에 실패했습니다.');
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async () => {
    if (!confirm('API 키를 삭제하시겠습니까?')) return;
    setDeleting(true);
    setError('');
    setSuccess('');
    try {
      await deleteAnthropicApiKey();
      setStatus({ isSet: false, maskedValue: '', updatedAt: null, updatedBy: null });
      setSuccess('API 키가 삭제됐습니다.');
    } catch {
      setError('삭제에 실패했습니다.');
    } finally {
      setDeleting(false);
    }
  };

  if (loading) return <p className="text-sm text-muted-foreground text-center py-8">로딩 중…</p>;

  return (
    <div className="space-y-4 max-w-xl">
      {/* 현재 상태 */}
      <div className="rounded-lg border bg-white p-5 space-y-3">
        <div className="flex items-center gap-2">
          <KeyRound className="w-4 h-4 text-amber-600" />
          <span className="font-medium text-sm">Anthropic API 키</span>
          {status?.isSet ? (
            <span className="px-2 py-0.5 rounded-full bg-green-100 text-green-700 text-xs font-medium">설정됨</span>
          ) : (
            <span className="px-2 py-0.5 rounded-full bg-gray-100 text-gray-500 text-xs font-medium">미설정</span>
          )}
        </div>

        {status?.isSet && (
          <div className="space-y-1 text-sm">
            <div className="font-mono bg-gray-50 rounded px-3 py-2 text-gray-600 text-sm">
              {status.maskedValue}
            </div>
            {status.updatedAt && (
              <p className="text-xs text-gray-400">
                마지막 수정: {new Date(status.updatedAt).toLocaleString('ko-KR')}
                {status.updatedBy ? ` (${status.updatedBy})` : ''}
              </p>
            )}
          </div>
        )}

        <div className="flex gap-2 pt-1">
          <Button variant="outline" size="sm" onClick={() => { setShowInput(!showInput); setError(''); setSuccess(''); }}>
            <KeyRound className="w-3 h-3 mr-1" />
            {status?.isSet ? '키 변경' : '키 등록'}
          </Button>
          {status?.isSet && (
            <Button variant="outline" size="sm" onClick={handleDelete} disabled={deleting}
              className="text-red-600 hover:text-red-700 hover:border-red-300">
              {deleting ? <Loader2 className="w-3 h-3 mr-1 animate-spin" /> : <Trash2 className="w-3 h-3 mr-1" />}
              삭제
            </Button>
          )}
        </div>
      </div>

      {/* 입력 폼 */}
      {showInput && (
        <div className="rounded-lg border bg-white p-5 space-y-3">
          <p className="text-sm font-medium">새 API 키 입력</p>
          <p className="text-xs text-gray-500">
            <a href="https://console.anthropic.com/settings/keys" target="_blank" rel="noopener noreferrer"
              className="text-blue-600 hover:underline">Anthropic Console</a>에서 발급한 <code className="bg-gray-100 px-1 rounded text-xs">sk-ant-...</code> 형식의 키를 입력하세요.
          </p>
          <div className="relative">
            <Input
              type={showInput ? 'password' : 'text'}
              placeholder="sk-ant-api03-..."
              value={inputValue}
              onChange={(e) => setInputValue(e.target.value)}
              className="font-mono text-sm pr-10"
              onKeyDown={(e) => { if (e.key === 'Enter') handleSave(); }}
            />
          </div>
          <div className="flex gap-2">
            <Button size="sm" onClick={handleSave} disabled={saving || !inputValue.trim()}>
              {saving ? <Loader2 className="w-3 h-3 mr-1 animate-spin" /> : <Save className="w-3 h-3 mr-1" />}
              저장
            </Button>
            <Button variant="outline" size="sm" onClick={() => { setShowInput(false); setInputValue(''); setError(''); }}>
              취소
            </Button>
          </div>
        </div>
      )}

      {error && <p className="text-sm text-red-600">{error}</p>}
      {success && <p className="text-sm text-green-600">{success}</p>}
    </div>
  );
}
