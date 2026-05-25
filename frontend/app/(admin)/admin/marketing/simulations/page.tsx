'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { AdminSection } from '@/components/admin/AdminSection';
import { AdminTable } from '@/components/admin/AdminTable';
import { AdminFilters } from '@/components/admin/AdminFilters';
import { getSimulations, startSimulation, cancelSimulation, deleteSimulation, type SimulationSummaryResponse } from '@/lib/api/marketing/simulationApi';
import { getStories, type StorySummaryResponse } from '@/lib/api/marketing/storyApi';

const STATUS_FILTERS = [
  { value: '', label: '전체' },
  { value: 'QUEUED', label: '대기중' },
  { value: 'RUNNING', label: '실행중' },
  { value: 'COMPLETED', label: '완료' },
  { value: 'FAILED', label: '실패' },
  { value: 'CANCELED', label: '취소' },
];

const STATUS_BADGE_COLORS: Record<string, { bg: string; fg: string }> = {
  QUEUED: { bg: '#e6f0ff', fg: '#0066cc' },
  RUNNING: { bg: '#fff9e6', fg: '#b8860b' },
  COMPLETED: { bg: '#e6f7e6', fg: '#2d7a2d' },
  FAILED: { bg: '#ffe6e6', fg: '#b33333' },
  CANCELED: { bg: '#f5f5f5', fg: '#666' },
};

export default function SimulationsListPage() {
  const router = useRouter();
  const [simulations, setSimulations] = useState<SimulationSummaryResponse[]>([]);
  const [statusFilter, setStatusFilter] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [showModal, setShowModal] = useState(false);
  const [approvedStories, setApprovedStories] = useState<StorySummaryResponse[]>([]);
  const [selectedStoryId, setSelectedStoryId] = useState<number | null>(null);
  const [storiesLoading, setStoriesLoading] = useState(false);
  const [startLoading, setStartLoading] = useState(false);
  const [cancelingId, setCancelingId] = useState<number | null>(null);
  const [confirmDeleteId, setConfirmDeleteId] = useState<number | null>(null);
  const [deletingId, setDeletingId] = useState<number | null>(null);
  const [autoRefresh, setAutoRefresh] = useState(true);

  useEffect(() => {
    loadSimulations();
  }, [statusFilter]);

  useEffect(() => {
    if (!autoRefresh) return;

    const hasActiveStatus = simulations.some((s) => s.status === 'QUEUED' || s.status === 'RUNNING');
    if (!hasActiveStatus) {
      setAutoRefresh(false);
      return;
    }

    const interval = setInterval(() => {
      loadSimulations();
    }, 5000);

    return () => clearInterval(interval);
  }, [autoRefresh, simulations]);

  async function loadSimulations() {
    try {
      const data = await getSimulations(statusFilter || undefined);
      setSimulations(data);
      setError('');

      // Check if there are active statuses
      const hasActive = data.some((s) => s.status === 'QUEUED' || s.status === 'RUNNING');
      setAutoRefresh(hasActive);
    } catch (e: any) {
      setError('시뮬레이션을 불러오지 못했어요.');
      console.error(e);
    } finally {
      setLoading(false);
    }
  }

  async function openModal() {
    setShowModal(true);
    setSelectedStoryId(null);
    setStoriesLoading(true);
    try {
      const stories = await getStories('APPROVED');
      setApprovedStories(stories);
    } catch {
      setApprovedStories([]);
    } finally {
      setStoriesLoading(false);
    }
  }

  async function handleStartSimulation() {
    if (!selectedStoryId) {
      setError('사연을 선택해주세요.');
      return;
    }

    setStartLoading(true);
    try {
      await startSimulation(selectedStoryId);
      setShowModal(false);
      setSelectedStoryId(null);
      setError('');
      setAutoRefresh(true);
      await loadSimulations();
    } catch (e: any) {
      setError(`시뮬레이션 시작 실패: ${e.response?.data?.error?.message || '알 수 없는 오류'}`);
      console.error(e);
    } finally {
      setStartLoading(false);
    }
  }

  async function handleDeleteSimulation(id: number) {
    setDeletingId(id);
    try {
      await deleteSimulation(id);
      setConfirmDeleteId(null);
      setError('');
      await loadSimulations();
    } catch (e: any) {
      setError(`삭제 실패: ${e.response?.data?.message || '알 수 없는 오류'}`);
    } finally {
      setDeletingId(null);
    }
  }

  async function handleCancelSimulation(id: number) {
    setCancelingId(id);
    try {
      await cancelSimulation(id);
      setError('');
      await loadSimulations();
    } catch (e: any) {
      setError(`취소 실패: ${e.response?.data?.error?.message || '알 수 없는 오류'}`);
      console.error(e);
    } finally {
      setCancelingId(null);
    }
  }

  function formatDuration(startedAt?: string, finishedAt?: string): string {
    if (!startedAt) return '-';
    const start = new Date(startedAt).getTime();
    const end = finishedAt ? new Date(finishedAt).getTime() : Date.now();
    const seconds = Math.floor((end - start) / 1000);
    const minutes = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return minutes > 0 ? `${minutes}분 ${secs}초` : `${secs}초`;
  }

  const columns = [
    {
      key: 'id',
      header: 'ID',
      render: (row: SimulationSummaryResponse) => row.id,
    },
    {
      key: 'storyId',
      header: '사연ID',
      render: (row: SimulationSummaryResponse) => row.storyId,
    },
    {
      key: 'status',
      header: '상태',
      render: (row: SimulationSummaryResponse) => {
        const colors = STATUS_BADGE_COLORS[row.status] || { bg: '#f0f0f0', fg: '#666' };
        const isPulsing = row.status === 'RUNNING';
        return (
          <span
            style={{
              padding: '2px 8px',
              borderRadius: 4,
              fontSize: 11,
              fontWeight: 600,
              background: colors.bg,
              color: colors.fg,
              display: 'inline-block',
              animation: isPulsing ? 'pulse 2s infinite' : undefined,
            }}
          >
            {row.status === 'RUNNING' ? '처리 중' : row.status}
          </span>
        );
      },
    },
    {
      key: 'turnCount',
      header: '턴수',
      render: (row: SimulationSummaryResponse) => row.turnCount || '-',
    },
    {
      key: 'startedAt',
      header: '시작시각',
      render: (row: SimulationSummaryResponse) =>
        row.startedAt ? new Date(row.startedAt).toLocaleString('ko-KR') : '-',
    },
    {
      key: 'finishedAt',
      header: '완료시각',
      render: (row: SimulationSummaryResponse) =>
        row.finishedAt ? new Date(row.finishedAt).toLocaleString('ko-KR') : '-',
    },
    {
      key: 'duration',
      header: '소요시간',
      render: (row: SimulationSummaryResponse) => formatDuration(row.startedAt, row.finishedAt),
    },
    {
      key: 'actions',
      header: '액션',
      render: (row: SimulationSummaryResponse) => (
        <div style={{ display: 'flex', gap: 8 }}>
          <button
            onClick={() => router.push(`/admin/marketing/simulations/${row.id}`)}
            style={{
              padding: '4px 10px',
              background: 'white',
              border: '1px solid #ddd',
              borderRadius: 4,
              cursor: 'pointer',
              fontSize: 12,
            }}
          >
            보기
          </button>
          {row.status === 'COMPLETED' && (
            <button
              onClick={() => router.push(`/admin/marketing/simulations/${row.id}/conversation`)}
              style={{
                padding: '4px 10px',
                background: '#e6f0ff',
                color: '#0066cc',
                border: '1px solid #b3d1ff',
                borderRadius: 4,
                cursor: 'pointer',
                fontSize: 12,
              }}
            >
              지난 대화
            </button>
          )}
          {(row.status === 'QUEUED' || row.status === 'RUNNING') && (
            <button
              onClick={() => handleCancelSimulation(row.id)}
              disabled={cancelingId === row.id}
              style={{
                padding: '4px 10px',
                background: '#ffe6e6',
                color: '#b33333',
                border: '1px solid #ffcccc',
                borderRadius: 4,
                cursor: cancelingId === row.id ? 'not-allowed' : 'pointer',
                fontSize: 12,
                opacity: cancelingId === row.id ? 0.6 : 1,
              }}
            >
              {cancelingId === row.id ? '취소 중...' : '취소'}
            </button>
          )}
          {(row.status === 'COMPLETED' || row.status === 'FAILED' || row.status === 'CANCELED') && (
            confirmDeleteId === row.id ? (
              <>
                <button
                  onClick={() => handleDeleteSimulation(row.id)}
                  disabled={deletingId === row.id}
                  style={{ padding: '4px 10px', background: '#b33333', color: 'white', border: 'none', borderRadius: 4, cursor: 'pointer', fontSize: 12, opacity: deletingId === row.id ? 0.6 : 1 }}
                >
                  {deletingId === row.id ? '삭제 중...' : '확인'}
                </button>
                <button
                  onClick={() => setConfirmDeleteId(null)}
                  style={{ padding: '4px 10px', background: 'white', border: '1px solid #ddd', borderRadius: 4, cursor: 'pointer', fontSize: 12 }}
                >
                  취소
                </button>
              </>
            ) : (
              <button
                onClick={() => setConfirmDeleteId(row.id)}
                style={{ padding: '4px 10px', background: '#ffe6e6', color: '#b33333', border: '1px solid #ffcccc', borderRadius: 4, cursor: 'pointer', fontSize: 12 }}
              >
                삭제
              </button>
            )
          )}
        </div>
      ),
    },
  ];

  return (
    <>
      <style>{`
        @keyframes pulse {
          0%, 100% { opacity: 1; }
          50% { opacity: 0.7; }
        }
      `}</style>

      <AdminSection
        title="시뮬레이션 관리"
        subtitle="사연 기반 AI 대화 시뮬레이션"
        badge={simulations.length > 0 ? { text: `${simulations.length}건`, color: '#555' } : undefined}
      >
        <div
          style={{
            marginBottom: 12,
            display: 'flex',
            gap: 8,
            justifyContent: 'space-between',
            alignItems: 'center',
            flexWrap: 'wrap',
          }}
        >
          <AdminFilters
            status={{
              value: statusFilter,
              onChange: setStatusFilter,
              options: STATUS_FILTERS,
            }}
          />
          <button
            onClick={openModal}
            style={{
              padding: '9px 18px',
              background: '#1A1A2E',
              color: 'white',
              border: 'none',
              borderRadius: 6,
              cursor: 'pointer',
              fontSize: 13,
              fontWeight: 500,
            }}
          >
            시뮬레이션 시작
          </button>
        </div>

        {error && (
          <div
            style={{
              padding: 12,
              background: '#ffe6e6',
              color: '#b33333',
              borderRadius: 6,
              marginBottom: 12,
              fontSize: 13,
            }}
          >
            {error}
          </div>
        )}

        <AdminTable<SimulationSummaryResponse>
          data={simulations}
          columns={columns}
          loading={loading}
          emptyMessage="등록된 시뮬레이션이 없어요."
          rowKey={(row) => row.id}
        />
      </AdminSection>

      {showModal && (
        <div
          style={{
            position: 'fixed',
            top: 0,
            left: 0,
            right: 0,
            bottom: 0,
            background: 'rgba(0, 0, 0, 0.5)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            zIndex: 1000,
          }}
          onClick={() => setShowModal(false)}
        >
          <div
            style={{
              background: 'white',
              borderRadius: 12,
              padding: 24,
              maxWidth: 400,
              width: '90%',
              boxShadow: '0 10px 40px rgba(0,0,0,0.2)',
            }}
            onClick={(e) => e.stopPropagation()}
          >
            <h3 style={{ margin: '0 0 16px', fontSize: 16, fontWeight: 600, color: '#1A1A2E' }}>
              시뮬레이션 시작
            </h3>
            <p style={{ margin: '0 0 12px', fontSize: 13, color: '#666' }}>시뮬레이션할 사연을 선택하세요.</p>

            {storiesLoading ? (
              <p style={{ fontSize: 13, color: '#888', textAlign: 'center', padding: '20px 0' }}>사연 로딩 중...</p>
            ) : approvedStories.length === 0 ? (
              <p style={{ fontSize: 13, color: '#b33333', textAlign: 'center', padding: '20px 0' }}>
                시뮬레이션 가능한 사연이 없어요. 먼저 사연을 등록해주세요.
              </p>
            ) : (
              <div style={{ maxHeight: 240, overflowY: 'auto', marginBottom: 12, border: '1px solid #eee', borderRadius: 6 }}>
                {approvedStories.map((s) => (
                  <div
                    key={s.id}
                    onClick={() => setSelectedStoryId(s.id)}
                    style={{
                      padding: '10px 14px',
                      cursor: 'pointer',
                      borderBottom: '1px solid #f0f0f0',
                      background: selectedStoryId === s.id ? '#f0f4ff' : 'white',
                      borderLeft: selectedStoryId === s.id ? '3px solid #2d4a7a' : '3px solid transparent',
                    }}
                  >
                    <div style={{ fontSize: 12, fontWeight: 600, color: '#1A1A2E' }}>#{s.id} · {s.sourcePlatform}</div>
                    <div style={{ fontSize: 11, color: '#888', marginTop: 2 }}>{s.relationType} · {new Date(s.createdAt).toLocaleDateString('ko-KR')}</div>
                  </div>
                ))}
              </div>
            )}

            <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
              <button
                onClick={() => setShowModal(false)}
                style={{
                  padding: '8px 16px',
                  background: 'white',
                  border: '1px solid #ddd',
                  borderRadius: 6,
                  cursor: 'pointer',
                  fontSize: 13,
                }}
              >
                취소
              </button>
              <button
                onClick={handleStartSimulation}
                disabled={startLoading}
                style={{
                  padding: '8px 16px',
                  background: '#1A1A2E',
                  color: 'white',
                  border: 'none',
                  borderRadius: 6,
                  cursor: startLoading ? 'not-allowed' : 'pointer',
                  fontSize: 13,
                  opacity: startLoading ? 0.6 : 1,
                }}
              >
                {startLoading ? '시작 중...' : '시작'}
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
