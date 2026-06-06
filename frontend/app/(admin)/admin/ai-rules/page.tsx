'use client';

import { useState, useCallback, useEffect } from 'react';
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
  analyzeBatchCorrections,
  AiGlobalRule,
  AiCaution,
  AiCorrectionHistory,
  AnalyzeResponse,
} from '@/lib/api/admin/corrections';
import { Sparkles, Plus, Trash2, Power, BrainCircuit, CheckCheck, SkipForward, ChevronDown, ChevronUp, Zap } from 'lucide-react';

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

        {/* 날짜 */}
        <div className="shrink-0 text-xs text-muted-foreground">
          {new Date(row.createdAt).toLocaleDateString('ko-KR')}
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

  const [error, setError] = useState('');
  const [batchAnalyzing, setBatchAnalyzing] = useState(false);
  const [batchMessage, setBatchMessage] = useState('');

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

  useEffect(() => { loadRules(0); }, [rulesActiveFilter, loadRules]);
  useEffect(() => { loadCautions(0); }, [personaIdFilter, loadCautions]);
  useEffect(() => { loadHistory(0); }, [historyStatusFilter, loadHistory]);

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
    if (!window.confirm('PENDING 상태의 첨삭 전체를 백그라운드에서 LLM 분석 후 자동 적용합니다.\n계속하시겠습니까?')) return;
    setBatchAnalyzing(true); setBatchMessage(''); setError('');
    try {
      const res = await analyzeBatchCorrections();
      setBatchMessage(res.message);
      if (res.queued > 0) {
        setTimeout(() => loadHistory(0), 3000);
      }
    } catch (e: any) {
      setError(e?.response?.data?.message || '일괄 분석 요청에 실패했습니다.');
    } finally {
      setBatchAnalyzing(false);
    }
  }

  async function handleDeleteCaution(caution: AiCaution) {
    if (!window.confirm('이 주의사항을 삭제하시겠습니까?')) return;
    try { await deleteCaution(caution.id); loadCautions(cautionsPage); }
    catch { alert('삭제에 실패했습니다.'); }
  }

  return (
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

            <div className="ml-auto flex items-center gap-2">
              {batchMessage && (
                <span className="text-xs text-purple-700 bg-purple-50 border border-purple-200 px-2 py-1 rounded">
                  {batchMessage}
                </span>
              )}
              <Button
                size="sm"
                onClick={handleBatchAnalyze}
                disabled={batchAnalyzing}
                className="bg-purple-600 hover:bg-purple-700 text-white h-8 px-3"
              >
                <Zap className="h-3.5 w-3.5 mr-1.5" />
                {batchAnalyzing ? '요청 중…' : 'PENDING 일괄 분석'}
              </Button>
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
      </Tabs>
    </AdminSection>
  );
}
