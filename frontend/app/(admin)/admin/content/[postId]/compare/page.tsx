'use client';

import { useEffect, useState, useCallback } from 'react';
import { useParams, useRouter } from 'next/navigation';
import Link from 'next/link';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { AdminSection } from '@/components/admin/AdminSection';
import { DiffPanel } from '@/components/admin/content/DiffPanel';
import {
  getSourceComparison,
  type SourceComparisonResponse,
} from '@/lib/api/admin/content';
import {
  analyzeReconstruction,
  commitReconstruction,
  type ReconstructionAnalyzeResponse,
} from '@/lib/api/admin/corrections';
import {
  AlertCircle,
  ArrowLeft,
  CheckCircle2,
  ExternalLink,
  Sparkles,
} from 'lucide-react';
import { Checkbox } from '@/components/ui/checkbox';
import { Label } from '@/components/ui/label';
import { toast } from 'sonner';

// ─── 상수 ────────────────────────────────────────────────────────────────────

const COMMUNITY_LABELS: Record<string, string> = {
  natepan: '네이트판',
  dcinside: '디시인사이드',
  theqoo: '더쿠',
  fmkorea: 'FM코리아',
  ppomppu: '뽐뿌',
  clien: '클리앙',
  mlbpark: 'MLB파크',
  ruliweb: '루리웹',
  blind: '블라인드',
  bobaedream: '보배드림',
};

// ─── 메인 컴포넌트 ────────────────────────────────────────────────────────────

type Phase = 'view' | 'analyzing' | 'analyzed' | 'committing' | 'committed';

export default function ContentComparePage() {
  const { postId } = useParams<{ postId: string }>();
  const router = useRouter();

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [data, setData] = useState<SourceComparisonResponse | null>(null);

  // 오른쪽 편집 상태 (제목 + 본문 합산 문자열로 관리)
  const [editedTitle, setEditedTitle] = useState('');
  const [editedBody, setEditedBody] = useState('');

  const [adminOpinion, setAdminOpinion] = useState('');
  const [applyLive, setApplyLive] = useState(true);

  const [phase, setPhase] = useState<Phase>('view');
  const [analyzeResult, setAnalyzeResult] = useState<ReconstructionAnalyzeResponse | null>(null);
  const [rules, setRules] = useState<string[]>([]);

  // ── 데이터 로드 ─────────────────────────────────────────────────────────────

  useEffect(() => {
    if (!postId) return;
    setLoading(true);
    getSourceComparison(postId)
      .then((res) => {
        if (!res.hasSource) {
          // 크롤 원본 없는 AI 글 → AI 개선 화면으로 리다이렉트
          router.replace(`/admin/content?openImprove=${postId}`);
          return;
        }
        setData(res);
        setEditedTitle(res.generated?.title ?? '');
        setEditedBody(res.generated?.body ?? '');
      })
      .catch((e) => setError(e?.message ?? '데이터를 불러올 수 없습니다.'))
      .finally(() => setLoading(false));
  }, [postId, router]);

  // ── Phase A: 분석 ───────────────────────────────────────────────────────────

  const handleAnalyze = useCallback(async () => {
    if (!data?.source?.body) return;
    setPhase('analyzing');
    try {
      const correctedText = buildCorrectedText(editedTitle, editedBody);
      const result = await analyzeReconstruction({
        targetType: 'POST',
        targetId: postId,
        sourceOriginalText: data.source.body,
        correctedText,
        adminOpinion: adminOpinion || null,
      });
      setAnalyzeResult(result);
      setRules(result.suggestedReconstructionRules ?? []);
      setPhase('analyzed');
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : String(e);
      toast.error(`분석 실패: ${msg}`);
      setPhase('view');
    }
  }, [data, editedTitle, editedBody, adminOpinion, postId]);

  // ── Phase B: 확정 ───────────────────────────────────────────────────────────

  const handleCommit = useCallback(async () => {
    if (!data?.source?.body) return;
    setPhase('committing');
    try {
      const correctedText = buildCorrectedText(editedTitle, editedBody);
      await commitReconstruction({
        targetType: 'POST',
        targetId: postId,
        correctedText,
        sourceOriginalText: data.source.body,
        reconstructionRules: rules,
        applyLive,
      });
      setPhase('committed');
      toast.success(
        applyLive
          ? `저장 완료 — 라이브 사연 교체 + 재구성 규칙 ${rules.length}건 추가`
          : `저장 완료 — 재구성 규칙 ${rules.length}건 추가`
      );
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : String(e);
      toast.error(`저장 실패: ${msg}`);
      setPhase('analyzed');
    }
  }, [data, editedTitle, editedBody, rules, applyLive, postId]);

  // ─────────────────────────────────────────────────────────────────────────

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <span className="text-muted-foreground text-sm">불러오는 중…</span>
      </div>
    );
  }

  if (error || !data) {
    return (
      <div className="p-6 flex flex-col items-center gap-4">
        <AlertCircle className="h-8 w-8 text-red-500" />
        <p className="text-sm text-red-600">{error ?? '데이터 없음'}</p>
        <Button variant="outline" size="sm" onClick={() => router.back()}>돌아가기</Button>
      </div>
    );
  }

  const communityLabel = data.source?.community
    ? (COMMUNITY_LABELS[data.source.community] ?? data.source.community)
    : '알 수 없음';
  const isWorking = phase === 'analyzing' || phase === 'committing';
  const isDone = phase === 'committed';

  return (
    <div className="max-w-7xl mx-auto px-4 py-6 space-y-6">
      {/* 헤더 */}
      <div className="flex items-center gap-3">
        <Button variant="ghost" size="sm" asChild>
          <Link href="/admin/content">
            <ArrowLeft className="h-4 w-4 mr-1" />
            콘텐츠 관리
          </Link>
        </Button>
        <h1 className="text-lg font-semibold">원본 비교</h1>
        <span className="text-xs text-muted-foreground ml-auto">게시글 ID: {postId}</span>
      </div>

      {/* 메인: 2-컬럼 */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">

        {/* ── 왼쪽: 크롤 원본 (읽기 전용) ────────────────────────────────── */}
        <div data-testid="compare-source-panel">
        <AdminSection title="크롤 원본">
          <div className="space-y-3">
            {/* 출처 정보 */}
            <div className="flex items-center gap-2 flex-wrap">
              <span
                className="text-xs font-medium px-2 py-1 rounded-full bg-gray-100 text-gray-700 border"
                data-testid="compare-source-community"
              >
                {communityLabel}
              </span>
              {data.source?.url && (
                <a
                  href={data.source.url}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="text-xs text-blue-500 hover:underline flex items-center gap-1"
                  data-testid="compare-source-url"
                >
                  <ExternalLink className="h-3 w-3" />
                  원문 보기
                </a>
              )}
            </div>

            {/* 원본 제목 */}
            {data.source?.title && (
              <div data-testid="compare-source-title">
                <p className="text-[10px] font-semibold text-gray-500 uppercase tracking-wide mb-1">원본 제목</p>
                <p className="text-sm font-medium text-gray-800 bg-gray-50 rounded p-2 border">
                  {data.source.title}
                </p>
              </div>
            )}

            {/* 원본 본문 */}
            <div data-testid="compare-source-body">
              <p className="text-[10px] font-semibold text-gray-500 uppercase tracking-wide mb-1">원본 본문</p>
              <div className="h-96 overflow-y-auto rounded border bg-gray-50 p-3 text-sm text-gray-800 leading-relaxed whitespace-pre-wrap">
                {data.source?.body ?? <span className="text-muted-foreground italic">(본문 없음)</span>}
              </div>
            </div>
          </div>
        </AdminSection>
        </div>

        {/* ── 오른쪽: AI 생성본 + 편집 ─────────────────────────────────────── */}
        <div data-testid="compare-generated-panel">
        <AdminSection title="AI 재구성 사연 (편집)">
          <div className="space-y-4">
            {/* AI 고지 */}
            <div className="flex items-center gap-2 px-3 py-2 rounded-lg bg-purple-50 border border-purple-100 text-xs text-purple-700">
              <Sparkles className="h-3.5 w-3.5 flex-shrink-0" />
              AI-user(봇)이 크롤 원본을 재구성해 작성한 사연입니다. 직접 편집 후 저장하면 재구성 규칙이 학습됩니다.
            </div>

            {/* 제목 diff */}
            <div data-testid="compare-diff-title">
              <DiffPanel
                label="제목"
                original={data.generated?.title ?? ''}
                corrected={editedTitle}
                onChange={setEditedTitle}
                disabled={isWorking || isDone}
                height="h-16"
                singleLine
                placeholder="제목을 수정하세요."
              />
            </div>

            {/* 본문 diff */}
            <div data-testid="compare-diff-body">
              <DiffPanel
                label="본문"
                original={data.generated?.body ?? ''}
                corrected={editedBody}
                onChange={setEditedBody}
                disabled={isWorking || isDone}
                height="h-72"
                placeholder="본문을 수정하세요."
              />
            </div>

            {/* 관리자 의견 */}
            <div>
              <Label className="text-xs text-gray-600 mb-1 block">수정 의도 (선택)</Label>
              <textarea
                value={adminOpinion}
                onChange={(e) => setAdminOpinion(e.target.value)}
                disabled={isWorking || isDone}
                placeholder="어떤 방향으로 수정했는지 메모 (AI 학습에 활용됩니다)"
                rows={2}
                data-testid="compare-admin-opinion"
                className="w-full rounded border p-2 text-sm resize-none focus:outline-none focus:ring-1 focus:ring-purple-400 disabled:opacity-50"
              />
            </div>

            {/* Phase A: 분석 버튼 */}
            {phase !== 'committed' && (
              <Button
                onClick={handleAnalyze}
                disabled={isWorking || !editedBody.trim()}
                variant="outline"
                size="sm"
                className="text-purple-600 border-purple-200 hover:bg-purple-50"
                data-testid="compare-analyze-btn"
              >
                <Sparkles className="h-4 w-4 mr-1.5" />
                {phase === 'analyzing' ? '분석 중…' : '재구성 규칙 분석'}
              </Button>
            )}

            {/* Phase A 결과: 규칙 목록 편집 */}
            {analyzeResult && phase !== 'committed' && (
              <Card className="p-4 space-y-3 bg-purple-50/50 border-purple-100" data-testid="compare-rules-preview">
                <p className="text-xs font-semibold text-purple-700">제안된 재구성 규칙 (편집 가능)</p>
                <div className="space-y-2">
                  {rules.map((rule, idx) => (
                    <div key={idx} className="flex gap-2">
                      <span className="text-xs text-gray-500 mt-1 w-4 flex-shrink-0">{idx + 1}.</span>
                      <textarea
                        value={rule}
                        onChange={(e) => {
                          const next = [...rules];
                          next[idx] = e.target.value;
                          setRules(next);
                        }}
                        rows={2}
                        className="flex-1 text-sm rounded border p-2 resize-none focus:outline-none focus:ring-1 focus:ring-purple-300"
                        disabled={isWorking}
                      />
                      <button
                        onClick={() => setRules(rules.filter((_, i) => i !== idx))}
                        className="text-gray-400 hover:text-red-500 text-xs mt-1 flex-shrink-0"
                        disabled={isWorking}
                        aria-label="규칙 삭제"
                      >
                        ✕
                      </button>
                    </div>
                  ))}
                  <button
                    onClick={() => setRules([...rules, ''])}
                    className="text-xs text-purple-600 hover:underline"
                    disabled={isWorking}
                  >
                    + 규칙 추가
                  </button>
                </div>

                {/* Phase B: 확정 */}
                <div className="flex items-center gap-4 pt-2 border-t border-purple-100">
                  <div className="flex items-center gap-2">
                    <Checkbox
                      id="apply-live"
                      checked={applyLive}
                      onCheckedChange={(v) => setApplyLive(Boolean(v))}
                      disabled={isWorking}
                      data-testid="compare-apply-live"
                    />
                    <Label htmlFor="apply-live" className="text-xs text-gray-600 cursor-pointer">
                      라이브 사연도 수정본으로 교체
                    </Label>
                  </div>
                  <Button
                    onClick={handleCommit}
                    disabled={isWorking || rules.every((r) => !r.trim())}
                    size="sm"
                    className="bg-purple-600 text-white hover:bg-purple-700 ml-auto"
                    data-testid="compare-commit-btn"
                  >
                    {phase === 'committing' ? '저장 중…' : '규칙 저장'}
                  </Button>
                </div>
              </Card>
            )}

            {/* 완료 상태 */}
            {isDone && (
              <div className="flex items-center gap-2 p-3 rounded-lg bg-green-50 border border-green-200 text-sm text-green-700">
                <CheckCircle2 className="h-5 w-5" />
                저장 완료! 재구성 규칙이 AI 학습에 반영됩니다.
              </div>
            )}
          </div>
        </AdminSection>
        </div>
      </div>
    </div>
  );
}

// ─── 유틸 ──────────────────────────────────────────────────────────────────

/** 제목 + 본문을 하나의 correctedText 문자열로 합친다 (BE가 기대하는 형식). */
function buildCorrectedText(title: string, body: string): string {
  const t = title.trim();
  const b = body.trim();
  if (!t) return b;
  return `${t}\n\n${b}`;
}
