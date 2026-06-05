'use client';

import { useState, useCallback, useEffect } from 'react';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
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
  toggleCaution,
  deleteCaution,
  AiGlobalRule,
  AiCaution,
} from '@/lib/api/admin/corrections';
import { Sparkles, Plus, Trash2, Power } from 'lucide-react';

const SCOPE_LABELS: Record<string, string> = {
  ALL: '전체',
  POST: '게시글만',
  COMMENT: '댓글만',
};

export default function AiRulesPage() {
  // ─── 전역 금지 규칙 state ───
  const [rules, setRules] = useState<AiGlobalRule[]>([]);
  const [rulesPage, setRulesPage] = useState(0);
  const [rulesTotalPages, setRulesTotalPages] = useState(0);
  const [rulesLoading, setRulesLoading] = useState(false);
  const [rulesActiveFilter, setRulesActiveFilter] = useState<string>('ALL');
  const [newRuleText, setNewRuleText] = useState('');
  const [newRuleScope, setNewRuleScope] = useState('ALL');
  const [addingRule, setAddingRule] = useState(false);

  // ─── 페르소나 주의사항 state ───
  const [cautions, setCautions] = useState<AiCaution[]>([]);
  const [cautionsPage, setCautionsPage] = useState(0);
  const [cautionsTotalPages, setCautionsTotalPages] = useState(0);
  const [cautionsLoading, setCautionsLoading] = useState(false);
  const [personaIdFilter, setPersonaIdFilter] = useState('');

  const [error, setError] = useState('');

  // ─── 전역 규칙 로드 ───
  const loadRules = useCallback(async (page: number) => {
    setRulesLoading(true);
    try {
      const activeParam =
        rulesActiveFilter === 'ACTIVE' ? true :
        rulesActiveFilter === 'INACTIVE' ? false : undefined;
      const res = await listGlobalRules({ page, size: 20, active: activeParam });
      setRules(res.content);
      setRulesTotalPages(res.totalPages);
      setRulesPage(page);
    } catch (e) {
      console.error('Failed to load global rules:', e);
    } finally {
      setRulesLoading(false);
    }
  }, [rulesActiveFilter]);

  // ─── 주의사항 로드 ───
  const loadCautions = useCallback(async (page: number) => {
    setCautionsLoading(true);
    try {
      const res = await listCautions({
        page,
        size: 20,
        personaId: personaIdFilter || undefined,
      });
      setCautions(res.content);
      setCautionsTotalPages(res.totalPages);
      setCautionsPage(page);
    } catch (e) {
      console.error('Failed to load cautions:', e);
    } finally {
      setCautionsLoading(false);
    }
  }, [personaIdFilter]);

  useEffect(() => { loadRules(0); }, [rulesActiveFilter, loadRules]);
  useEffect(() => { loadCautions(0); }, [personaIdFilter, loadCautions]);

  // ─── 전역 규칙 핸들러 ───
  async function handleAddRule() {
    if (!newRuleText.trim()) return;
    setAddingRule(true);
    setError('');
    try {
      await createGlobalRule(newRuleText.trim(), newRuleScope);
      setNewRuleText('');
      loadRules(0);
    } catch (e: any) {
      setError(e?.response?.data?.message || '규칙 추가에 실패했습니다.');
    } finally {
      setAddingRule(false);
    }
  }

  async function handleToggleRule(rule: AiGlobalRule) {
    try {
      await toggleGlobalRule(rule.id, !rule.active);
      loadRules(rulesPage);
    } catch (e) {
      alert('상태 변경에 실패했습니다.');
    }
  }

  async function handleDeleteRule(rule: AiGlobalRule) {
    if (!window.confirm('이 규칙을 삭제하시겠습니까?')) return;
    try {
      await deleteGlobalRule(rule.id);
      loadRules(rulesPage);
    } catch (e) {
      alert('삭제에 실패했습니다.');
    }
  }

  // ─── 주의사항 핸들러 ───
  async function handleToggleCaution(caution: AiCaution) {
    // 현재 active 상태는 voice_profile 기준이므로 단순 반전 토글
    try {
      await toggleCaution(caution.id, true); // 기본 활성화 토글
      loadCautions(cautionsPage);
    } catch (e) {
      alert('상태 변경에 실패했습니다.');
    }
  }

  async function handleDeleteCaution(caution: AiCaution) {
    if (!window.confirm('이 주의사항을 삭제하시겠습니까?')) return;
    try {
      await deleteCaution(caution.id);
      loadCautions(cautionsPage);
    } catch (e) {
      alert('삭제에 실패했습니다.');
    }
  }

  return (
    <AdminSection title="AI 규칙 관리">
      <div className="flex items-center gap-2 mb-4 text-sm text-muted-foreground">
        <Sparkles className="h-4 w-4 text-purple-500" />
        첨삭을 통해 누적된 AI 학습 규칙을 관리합니다. 비활성화하거나 삭제하면 다음 AI 생성 시 반영됩니다.
      </div>

      {error && (
        <div className="mb-4 p-3 bg-red-50 border border-red-200 rounded-md text-sm text-red-700">
          {error}
        </div>
      )}

      <Tabs defaultValue="global">
        <TabsList>
          <TabsTrigger value="global">전역 금지 규칙</TabsTrigger>
          <TabsTrigger value="cautions">페르소나 주의사항</TabsTrigger>
        </TabsList>

        {/* ── 전역 금지 규칙 탭 ── */}
        <TabsContent value="global" className="space-y-4">
          {/* 수동 추가 */}
          <div className="flex gap-2 items-end">
            <div className="flex-1">
              <label className="text-sm font-medium block mb-1">새 규칙 추가</label>
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
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="ALL">전체</SelectItem>
                  <SelectItem value="POST">게시글만</SelectItem>
                  <SelectItem value="COMMENT">댓글만</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <Button
              onClick={handleAddRule}
              disabled={addingRule || !newRuleText.trim()}
              className="flex items-center gap-1"
            >
              <Plus className="h-4 w-4" />
              추가
            </Button>
          </div>

          {/* 필터 */}
          <div className="flex gap-2 items-end">
            <div className="w-40">
              <label className="text-sm font-medium block mb-1">상태 필터</label>
              <Select value={rulesActiveFilter} onValueChange={setRulesActiveFilter}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="ALL">전체</SelectItem>
                  <SelectItem value="ACTIVE">활성</SelectItem>
                  <SelectItem value="INACTIVE">비활성</SelectItem>
                </SelectContent>
              </Select>
            </div>
          </div>

          {/* 테이블 */}
          <div className="border rounded-lg overflow-hidden">
            <AdminTable<AiGlobalRule>
              data={rules}
              loading={rulesLoading}
              columns={[
                {
                  key: 'ruleText',
                  header: '규칙 내용',
                  render: (row) => <span className="text-sm">{row.ruleText}</span>,
                },
                {
                  key: 'scope',
                  header: '적용 범위',
                  render: (row) => (
                    <Badge variant="outline">{SCOPE_LABELS[row.scope] || row.scope}</Badge>
                  ),
                },
                {
                  key: 'active',
                  header: '상태',
                  render: (row) => (
                    <Badge variant={row.active ? 'default' : 'secondary'}>
                      {row.active ? '활성' : '비활성'}
                    </Badge>
                  ),
                },
                {
                  key: 'sourceCorrectionId',
                  header: '출처',
                  render: (row) => (
                    <span className="text-xs text-muted-foreground">
                      {row.sourceCorrectionId ? `첨삭 #${row.sourceCorrectionId}` : '수동 추가'}
                    </span>
                  ),
                },
                {
                  key: 'createdAt',
                  header: '생성일',
                  render: (row) => (
                    <span className="text-xs text-muted-foreground">
                      {new Date(row.createdAt).toLocaleDateString('ko-KR')}
                    </span>
                  ),
                },
                {
                  key: 'actions',
                  header: '액션',
                  render: (row) => (
                    <div className="flex gap-1">
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => handleToggleRule(row)}
                        title={row.active ? '비활성화' : '활성화'}
                      >
                        <Power className="h-4 w-4" />
                      </Button>
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => handleDeleteRule(row)}
                        className="text-red-500 hover:text-red-700"
                        title="삭제"
                      >
                        <Trash2 className="h-4 w-4" />
                      </Button>
                    </div>
                  ),
                },
              ]}
              rowKey={(row) => row.id}
            />
          </div>

          <AdminPagination
            page={rulesPage}
            totalPages={rulesTotalPages}
            onPageChange={(page) => loadRules(page)}
          />
        </TabsContent>

        {/* ── 페르소나 주의사항 탭 ── */}
        <TabsContent value="cautions" className="space-y-4">
          {/* 필터 */}
          <div className="flex gap-2 items-end">
            <div className="flex-1 max-w-xs">
              <label className="text-sm font-medium block mb-1">페르소나 ID 필터</label>
              <Input
                placeholder="페르소나 ID (빈 칸이면 전체)"
                value={personaIdFilter}
                onChange={(e) => setPersonaIdFilter(e.target.value)}
              />
            </div>
          </div>

          {/* 테이블 */}
          <div className="border rounded-lg overflow-hidden">
            <AdminTable<AiCaution>
              data={cautions}
              loading={cautionsLoading}
              columns={[
                {
                  key: 'personaId',
                  header: '페르소나',
                  render: (row) => (
                    <span className="text-xs font-mono text-muted-foreground">{row.personaId}</span>
                  ),
                },
                {
                  key: 'personaCaution',
                  header: '주의사항',
                  render: (row) => (
                    <span className="text-sm">{row.personaCaution || '(없음)'}</span>
                  ),
                },
                {
                  key: 'targetType',
                  header: '대상',
                  render: (row) => (
                    <Badge variant="secondary">{row.targetType}</Badge>
                  ),
                },
                {
                  key: 'appliedLive',
                  header: '라이브 반영',
                  render: (row) => (
                    <span className="text-xs">
                      {row.appliedLive ? '✓' : '-'}
                    </span>
                  ),
                },
                {
                  key: 'createdAt',
                  header: '첨삭일',
                  render: (row) => (
                    <span className="text-xs text-muted-foreground">
                      {new Date(row.createdAt).toLocaleDateString('ko-KR')}
                    </span>
                  ),
                },
                {
                  key: 'actions',
                  header: '액션',
                  render: (row) => (
                    <div className="flex gap-1">
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => handleDeleteCaution(row)}
                        className="text-red-500 hover:text-red-700"
                        title="삭제"
                      >
                        <Trash2 className="h-4 w-4" />
                      </Button>
                    </div>
                  ),
                },
              ]}
              rowKey={(row) => row.id}
            />
          </div>

          <AdminPagination
            page={cautionsPage}
            totalPages={cautionsTotalPages}
            onPageChange={(page) => loadCautions(page)}
          />
        </TabsContent>
      </Tabs>
    </AdminSection>
  );
}
