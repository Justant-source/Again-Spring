'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { AdminSection } from '@/components/admin/AdminSection';
import { AdminTable } from '@/components/admin/AdminTable';
import { AdminFilters } from '@/components/admin/AdminFilters';
import { getStories, deleteStory, type StorySummaryResponse } from '@/lib/api/marketing/storyApi';

const STATUS_FILTERS = [
  { value: '', label: '전체' },
  { value: 'pending', label: '대기' },
  { value: 'approved', label: '승인' },
  { value: 'rejected', label: '거부' },
  { value: 'used', label: '사용' },
];

const STATUS_BADGE_COLORS: Record<string, { bg: string; fg: string }> = {
  pending: { bg: '#fff9e6', fg: '#b8860b' },
  approved: { bg: '#e6f7e6', fg: '#2d7a2d' },
  rejected: { bg: '#ffe6e6', fg: '#b33333' },
  used: { bg: '#f0f0f0', fg: '#666' },
};

export default function StoriesListPage() {
  const router = useRouter();
  const [stories, setStories] = useState<StorySummaryResponse[]>([]);
  const [filteredStories, setFilteredStories] = useState<StorySummaryResponse[]>([]);
  const [statusFilter, setStatusFilter] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [confirmDeleteId, setConfirmDeleteId] = useState<number | null>(null);
  const [deletingId, setDeletingId] = useState<number | null>(null);

  useEffect(() => {
    loadStories();
  }, [statusFilter]);

  async function loadStories() {
    setLoading(true);
    try {
      const data = await getStories(statusFilter || undefined);
      setStories(data);
      setFilteredStories(data);
      setError('');
    } catch (e: any) {
      setError('사연을 불러오지 못했어요.');
      console.error(e);
    } finally {
      setLoading(false);
    }
  }

  async function handleDelete(id: number) {
    setDeletingId(id);
    try {
      await deleteStory(id);
      setConfirmDeleteId(null);
      setError('');
      await loadStories();
    } catch (e: any) {
      setError(`삭제 실패: ${e.response?.data?.message || '알 수 없는 오류'}`);
    } finally {
      setDeletingId(null);
    }
  }

  const columns = [
    {
      key: 'title',
      header: '제목',
      render: (row: StorySummaryResponse) => (
        <span style={{ fontWeight: 500, color: '#1A1A2E' }}>
          {row.title || <span style={{ color: '#aaa', fontStyle: 'italic' }}>제목 없음</span>}
        </span>
      ),
    },
    {
      key: 'sourcePlatform',
      header: '플랫폼',
      render: (row: StorySummaryResponse) => row.sourcePlatform,
    },
    {
      key: 'relationType',
      header: '관계유형',
      render: (row: StorySummaryResponse) => row.relationType,
    },
    {
      key: 'status',
      header: '상태',
      render: (row: StorySummaryResponse) => {
        const colors = STATUS_BADGE_COLORS[row.status] || { bg: '#f0f0f0', fg: '#666' };
        return (
          <span style={{
            padding: '2px 8px',
            borderRadius: 4,
            fontSize: 11,
            fontWeight: 600,
            background: colors.bg,
            color: colors.fg,
          }}>
            {row.status}
          </span>
        );
      },
    },
    {
      key: 'createdAt',
      header: '등록일',
      render: (row: StorySummaryResponse) => new Date(row.createdAt).toLocaleDateString('ko-KR'),
    },
    {
      key: 'actions',
      header: '액션',
      render: (row: StorySummaryResponse) => (
        <div style={{ display: 'flex', gap: 6 }}>
          <button
            onClick={() => router.push(`/admin/marketing/stories/${row.id}`)}
            style={{ padding: '4px 10px', background: 'white', border: '1px solid #ddd', borderRadius: 4, cursor: 'pointer', fontSize: 12 }}
          >
            보기
          </button>
          {confirmDeleteId === row.id ? (
            <>
              <button
                onClick={() => handleDelete(row.id)}
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
          )}
        </div>
      ),
    },
  ];

  return (
    <AdminSection
      title="사연 관리"
      subtitle="플랫폼에서 수집한 관계 사연"
      badge={stories.length > 0 ? { text: `${stories.length}건`, color: '#555' } : undefined}
    >
      <div style={{ marginBottom: 12, display: 'flex', gap: 8, justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap' }}>
        <AdminFilters
          status={{
            value: statusFilter,
            onChange: setStatusFilter,
            options: STATUS_FILTERS,
          }}
        />
        <button
          onClick={() => router.push('/admin/marketing/stories/new')}
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
          사연 등록
        </button>
      </div>

      {error && (
        <div style={{ padding: 12, background: '#ffe6e6', color: '#b33333', borderRadius: 6, marginBottom: 12, fontSize: 13 }}>
          {error}
        </div>
      )}

      <AdminTable<StorySummaryResponse>
        data={filteredStories}
        columns={columns}
        loading={loading}
        emptyMessage="등록된 사연이 없어요."
        rowKey={(row) => row.id}
      />
    </AdminSection>
  );
}
