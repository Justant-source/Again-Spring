'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { AdminSection } from '@/components/admin/AdminSection';
import { AdminTable } from '@/components/admin/AdminTable';
import { AdminFilters } from '@/components/admin/AdminFilters';
import { getContents, generateContent, type ContentSummaryResponse } from '@/lib/api/marketing/contentApi';
import { getSimulations, type SimulationSummaryResponse } from '@/lib/api/marketing/simulationApi';

const PLATFORM_FILTERS = [
  { value: '', label: '전체' },
  { value: 'x', label: 'X' },
  { value: 'instagram', label: 'Instagram' },
  { value: 'naver_blog', label: '네이버블로그' },
];

const STATUS_FILTERS = [
  { value: '', label: '전체' },
  { value: 'GENERATING', label: '생성 중' },
  { value: 'DRAFT', label: '초안' },
  { value: 'REVIEW', label: '검토' },
  { value: 'APPROVED', label: '승인' },
  { value: 'EXPORTED', label: '내보내기' },
  { value: 'REJECTED', label: '거부' },
];

const STATUS_BADGE_COLORS: Record<string, { bg: string; fg: string }> = {
  GENERATING: { bg: '#fff9e6', fg: '#b8860b' },
  DRAFT: { bg: '#f0f0f0', fg: '#666' },
  REVIEW: { bg: '#e6f0ff', fg: '#0066cc' },
  APPROVED: { bg: '#e6f7e6', fg: '#2d7a2d' },
  EXPORTED: { bg: '#f0e6ff', fg: '#7a2d7a' },
  REJECTED: { bg: '#ffe6e6', fg: '#b33333' },
};

const PLATFORM_BADGE_COLORS: Record<string, { bg: string; fg: string }> = {
  x: { bg: '#222', fg: '#fff' },
  instagram: { bg: '#e1306c', fg: '#fff' },
  naver_blog: { bg: '#00c73c', fg: '#fff' },
};

export default function ContentsListPage() {
  const router = useRouter();
  const [contents, setContents] = useState<ContentSummaryResponse[]>([]);
  const [platformFilter, setPlatformFilter] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  // Modal state
  const [showModal, setShowModal] = useState(false);
  const [completedSims, setCompletedSims] = useState<SimulationSummaryResponse[]>([]);
  const [simsLoading, setSimsLoading] = useState(false);
  const [selectedSimId, setSelectedSimId] = useState<number | null>(null);
  const [selectedPlatform, setSelectedPlatform] = useState<'x' | 'instagram' | 'naver_blog'>('x');
  const [submitting, setSubmitting] = useState(false);

  // Auto-refresh when any content is GENERATING
  const [autoRefresh, setAutoRefresh] = useState(false);

  useEffect(() => {
    loadContents();
  }, [platformFilter, statusFilter]);

  useEffect(() => {
    if (!autoRefresh) return;
    const hasGenerating = contents.some((c) => c.status === 'GENERATING');
    if (!hasGenerating) {
      setAutoRefresh(false);
      return;
    }
    const interval = setInterval(loadContents, 4000);
    return () => clearInterval(interval);
  }, [autoRefresh, contents]);

  async function loadContents() {
    try {
      const data = await getContents();
      let filtered = data;
      if (platformFilter) {
        filtered = filtered.filter((c) => c.platform.toLowerCase() === platformFilter.toLowerCase());
      }
      if (statusFilter) {
        filtered = filtered.filter((c) => c.status === statusFilter);
      }
      setContents(filtered);
      setError('');

      const hasGenerating = data.some((c) => c.status === 'GENERATING');
      setAutoRefresh(hasGenerating);
    } catch (e: any) {
      setError('콘텐츠를 불러오지 못했어요.');
      console.error(e);
    } finally {
      setLoading(false);
    }
  }

  async function openModal() {
    setShowModal(true);
    setSelectedSimId(null);
    setSimsLoading(true);
    try {
      const sims = await getSimulations('COMPLETED');
      setCompletedSims(sims);
    } catch {
      setCompletedSims([]);
    } finally {
      setSimsLoading(false);
    }
  }

  async function handleGenerateContent() {
    if (!selectedSimId) {
      setError('시뮬레이션을 선택해주세요.');
      return;
    }
    setSubmitting(true);
    try {
      await generateContent(selectedSimId, selectedPlatform);
      setShowModal(false);
      setSelectedSimId(null);
      setError('');
      setAutoRefresh(true);
      await loadContents();
    } catch (e: any) {
      setError(`콘텐츠 생성 실패: ${e.response?.data?.error?.message || '알 수 없는 오류'}`);
      console.error(e);
    } finally {
      setSubmitting(false);
    }
  }

  const filteredContents = contents.filter((c) => {
    if (platformFilter && c.platform.toLowerCase() !== platformFilter.toLowerCase()) return false;
    if (statusFilter && c.status !== statusFilter) return false;
    return true;
  });

  const columns = [
    {
      key: 'platform',
      header: '플랫폼',
      render: (row: ContentSummaryResponse) => {
        const colors = PLATFORM_BADGE_COLORS[row.platform.toLowerCase()] || { bg: '#f0f0f0', fg: '#666' };
        const platformLabel = row.platform.toUpperCase() === 'NAVER_BLOG' ? '네이버블로그' : row.platform.toUpperCase();
        return (
          <span style={{ padding: '2px 8px', borderRadius: 4, fontSize: 11, fontWeight: 600, background: colors.bg, color: colors.fg, display: 'inline-block' }}>
            {platformLabel}
          </span>
        );
      },
    },
    {
      key: 'simulationId',
      header: '시뮬레이션ID',
      render: (row: ContentSummaryResponse) => row.simulationId,
    },
    {
      key: 'status',
      header: '상태',
      render: (row: ContentSummaryResponse) => {
        const colors = STATUS_BADGE_COLORS[row.status] || { bg: '#f0f0f0', fg: '#666' };
        const isGenerating = row.status === 'GENERATING';
        return (
          <span style={{
            padding: '2px 8px',
            borderRadius: 4,
            fontSize: 11,
            fontWeight: 600,
            background: colors.bg,
            color: colors.fg,
            display: 'inline-block',
            animation: isGenerating ? 'pulse 2s infinite' : undefined,
          }}>
            {isGenerating ? '생성 중' : row.status}
          </span>
        );
      },
    },
    {
      key: 'createdAt',
      header: '등록일',
      render: (row: ContentSummaryResponse) => new Date(row.createdAt).toLocaleString('ko-KR'),
    },
    {
      key: 'actions',
      header: '액션',
      render: (row: ContentSummaryResponse) => (
        <button
          onClick={() => router.push(`/admin/marketing/contents/${row.id}`)}
          disabled={row.status === 'GENERATING'}
          style={{
            padding: '4px 10px',
            background: 'white',
            border: '1px solid #ddd',
            borderRadius: 4,
            cursor: row.status === 'GENERATING' ? 'not-allowed' : 'pointer',
            fontSize: 12,
            opacity: row.status === 'GENERATING' ? 0.5 : 1,
          }}
        >
          상세보기
        </button>
      ),
    },
  ];

  return (
    <>
      <style>{`
        @keyframes pulse {
          0%, 100% { opacity: 1; }
          50% { opacity: 0.6; }
        }
      `}</style>

      <AdminSection
        title="콘텐츠 관리"
        subtitle="시뮬레이션 기반 마케팅 콘텐츠"
        badge={filteredContents.length > 0 ? { text: `${filteredContents.length}건`, color: '#555' } : undefined}
      >
        <div style={{ marginBottom: 12, display: 'flex', gap: 8, justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap' }}>
          <div style={{ display: 'flex', gap: 8 }}>
            <AdminFilters status={{ value: platformFilter, onChange: setPlatformFilter, options: PLATFORM_FILTERS }} />
            <AdminFilters status={{ value: statusFilter, onChange: setStatusFilter, options: STATUS_FILTERS }} />
          </div>
          <button
            onClick={openModal}
            style={{ padding: '9px 18px', background: '#1A1A2E', color: 'white', border: 'none', borderRadius: 6, cursor: 'pointer', fontSize: 13, fontWeight: 500 }}
          >
            콘텐츠 생성
          </button>
        </div>

        {error && (
          <div style={{ padding: 12, background: '#ffe6e6', color: '#b33333', borderRadius: 6, marginBottom: 12, fontSize: 13 }}>
            {error}
          </div>
        )}

        <AdminTable<ContentSummaryResponse>
          data={filteredContents}
          columns={columns}
          loading={loading}
          emptyMessage="등록된 콘텐츠가 없어요."
          rowKey={(row) => row.id}
        />
      </AdminSection>

      {showModal && (
        <div
          style={{ position: 'fixed', top: 0, left: 0, right: 0, bottom: 0, background: 'rgba(0, 0, 0, 0.5)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000 }}
          onClick={() => setShowModal(false)}
        >
          <div
            style={{ background: 'white', borderRadius: 12, padding: 24, maxWidth: 440, width: '90%', boxShadow: '0 10px 40px rgba(0,0,0,0.2)' }}
            onClick={(e) => e.stopPropagation()}
          >
            <h3 style={{ margin: '0 0 16px', fontSize: 16, fontWeight: 600, color: '#1A1A2E' }}>콘텐츠 생성</h3>
            <p style={{ margin: '0 0 8px', fontSize: 13, color: '#666' }}>완료된 시뮬레이션을 선택하세요.</p>

            {simsLoading ? (
              <p style={{ fontSize: 13, color: '#888', textAlign: 'center', padding: '20px 0' }}>불러오는 중...</p>
            ) : completedSims.length === 0 ? (
              <p style={{ fontSize: 13, color: '#b33333', textAlign: 'center', padding: '20px 0' }}>
                완료된 시뮬레이션이 없어요.
              </p>
            ) : (
              <div style={{ maxHeight: 220, overflowY: 'auto', marginBottom: 12, border: '1px solid #eee', borderRadius: 6 }}>
                {completedSims.map((s) => (
                  <div
                    key={s.id}
                    onClick={() => setSelectedSimId(s.id)}
                    style={{
                      padding: '10px 14px',
                      cursor: 'pointer',
                      borderBottom: '1px solid #f0f0f0',
                      background: selectedSimId === s.id ? '#f0f4ff' : 'white',
                      borderLeft: selectedSimId === s.id ? '3px solid #2d4a7a' : '3px solid transparent',
                    }}
                  >
                    <div style={{ fontSize: 12, fontWeight: 600, color: '#1A1A2E' }}>
                      #{s.id} · 사연 #{s.storyId}
                    </div>
                    <div style={{ fontSize: 11, color: '#888', marginTop: 2 }}>
                      {s.turnCount}턴 · {s.finishedAt ? new Date(s.finishedAt).toLocaleString('ko-KR') : '-'}
                    </div>
                  </div>
                ))}
              </div>
            )}

            <p style={{ margin: '0 0 8px', fontSize: 13, color: '#666' }}>플랫폼을 선택하세요.</p>
            <select
              value={selectedPlatform}
              onChange={(e) => setSelectedPlatform(e.target.value as 'x' | 'instagram' | 'naver_blog')}
              style={{ width: '100%', padding: '10px 12px', border: '1px solid #ddd', borderRadius: 6, fontSize: 13, marginBottom: 16, boxSizing: 'border-box' }}
            >
              <option value="x">X</option>
              <option value="instagram">Instagram</option>
              <option value="naver_blog">네이버블로그</option>
            </select>

            <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
              <button
                onClick={() => setShowModal(false)}
                style={{ padding: '8px 16px', background: 'white', border: '1px solid #ddd', borderRadius: 6, cursor: 'pointer', fontSize: 13 }}
              >
                취소
              </button>
              <button
                onClick={handleGenerateContent}
                disabled={submitting || !selectedSimId}
                style={{
                  padding: '8px 16px',
                  background: '#1A1A2E',
                  color: 'white',
                  border: 'none',
                  borderRadius: 6,
                  cursor: submitting || !selectedSimId ? 'not-allowed' : 'pointer',
                  fontSize: 13,
                  opacity: submitting || !selectedSimId ? 0.6 : 1,
                }}
              >
                {submitting ? '요청 중...' : '생성 시작'}
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
