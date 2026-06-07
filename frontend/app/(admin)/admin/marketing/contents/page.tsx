'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { AdminSection } from '@/components/admin/AdminSection';
import { AdminTable } from '@/components/admin/AdminTable';
import { AdminFilters } from '@/components/admin/AdminFilters';
import {
  getContents,
  generateFromPost,
  deleteContent,
  type ContentSummaryResponse,
} from '@/lib/api/marketing/contentApi';
import {
  getCandidatePosts,
  type CandidatePostResponse,
} from '@/lib/api/marketing/candidatePostApi';
import { TemplatePickerModal } from '@/components/admin/marketing/TemplatePickerModal';
import { RepurposeModal } from '@/components/admin/marketing/RepurposeModal';

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

type PlatformKey = 'x' | 'instagram' | 'naver_blog';

const DEFAULT_PLATFORMS: PlatformKey[] = ['x', 'instagram', 'naver_blog'];

const CATEGORY_LABELS: Record<string, string> = {
  COUPLE: '연인', MARRIED: '부부', FRIEND: '친구',
  FAMILY: '가족', WORK: '직장', OTHER: '기타',
};

export default function ContentsListPage() {
  const router = useRouter();
  const [contents, setContents] = useState<ContentSummaryResponse[]>([]);
  const [platformFilter, setPlatformFilter] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  // 모달 state
  const [showModal, setShowModal] = useState(false);
  const [candidates, setCandidates] = useState<CandidatePostResponse[]>([]);
  const [candidatesLoading, setCandidatesLoading] = useState(false);
  const [selectedPostId, setSelectedPostId] = useState<string | null>(null);
  const [selectedPlatforms, setSelectedPlatforms] = useState<PlatformKey[]>(DEFAULT_PLATFORMS);
  const [candidateSortBy, setCandidateSortBy] = useState<'recommended' | 'latest'>('recommended');
  const [candidateSearch, setCandidateSearch] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [confirmDeleteId, setConfirmDeleteId] = useState<number | null>(null);
  const [deletingId, setDeletingId] = useState<number | null>(null);

  const [showTemplateModal, setShowTemplateModal] = useState(false);
  const [repurposeContentId, setRepurposeContentId] = useState<number | null>(null);
  const [repurposeSourcePlatform, setRepurposeSourcePlatform] = useState<string>('');

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
    setSelectedPostId(null);
    setSelectedPlatforms(DEFAULT_PLATFORMS);
    setCandidateSearch('');
    await loadCandidates('recommended', '');
  }

  async function loadCandidates(sortBy: 'recommended' | 'latest', q: string) {
    setCandidatesLoading(true);
    try {
      const data = await getCandidatePosts({ sortBy, q: q || undefined });
      setCandidates(data);
    } catch {
      setCandidates([]);
    } finally {
      setCandidatesLoading(false);
    }
  }

  async function handleSortByChange(sortBy: 'recommended' | 'latest') {
    setCandidateSortBy(sortBy);
    await loadCandidates(sortBy, candidateSearch);
  }

  async function handleSearchChange(q: string) {
    setCandidateSearch(q);
    if (q.length === 0 || q.length >= 2) {
      await loadCandidates(candidateSortBy, q);
    }
  }

  function togglePlatform(platform: PlatformKey) {
    setSelectedPlatforms((prev) =>
      prev.includes(platform) ? prev.filter((p) => p !== platform) : [...prev, platform]
    );
  }

  async function handleGenerateContent() {
    if (!selectedPostId) {
      setError('사연을 선택해주세요.');
      return;
    }
    if (selectedPlatforms.length === 0) {
      setError('플랫폼을 하나 이상 선택해주세요.');
      return;
    }
    setSubmitting(true);
    try {
      await generateFromPost(selectedPostId, selectedPlatforms);
      setShowModal(false);
      setSelectedPostId(null);
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

  async function handleDeleteContent(id: number) {
    setDeletingId(id);
    try {
      await deleteContent(id);
      setConfirmDeleteId(null);
      setError('');
      await loadContents();
    } catch (e: any) {
      setError(`삭제 실패: ${e.response?.data?.message || '알 수 없는 오류'}`);
    } finally {
      setDeletingId(null);
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
        const platformLabel =
          row.platform.toUpperCase() === 'NAVER_BLOG' ? '네이버블로그' : row.platform.toUpperCase();
        return (
          <span
            style={{
              padding: '2px 8px', borderRadius: 4, fontSize: 11, fontWeight: 600,
              background: colors.bg, color: colors.fg, display: 'inline-block',
            }}
          >
            {platformLabel}
          </span>
        );
      },
    },
    {
      key: 'sourcePostId',
      header: '사연',
      render: (row: ContentSummaryResponse) => (
        <span style={{ fontSize: 12, color: '#555' }}>
          {row.sourcePostId
            ? <a href={`/community/${row.sourcePostId}`} target="_blank" rel="noreferrer"
                style={{ color: '#0066cc', textDecoration: 'none' }}>
                {row.sourcePostId.slice(0, 16)}…
              </a>
            : '-'}
        </span>
      ),
    },
    {
      key: 'status',
      header: '상태',
      render: (row: ContentSummaryResponse) => {
        const colors = STATUS_BADGE_COLORS[row.status] || { bg: '#f0f0f0', fg: '#666' };
        const isGenerating = row.status === 'GENERATING';
        return (
          <span
            style={{
              padding: '2px 8px', borderRadius: 4, fontSize: 11, fontWeight: 600,
              background: colors.bg, color: colors.fg, display: 'inline-block',
              animation: isGenerating ? 'pulse 2s infinite' : undefined,
            }}
          >
            {isGenerating ? '생성 중' : row.status}
          </span>
        );
      },
    },
    {
      key: 'createdAt',
      header: '등록일',
      render: (row: ContentSummaryResponse) =>
        new Date(row.createdAt).toLocaleString('ko-KR'),
    },
    {
      key: 'actions',
      header: '액션',
      render: (row: ContentSummaryResponse) => (
        <div style={{ display: 'flex', gap: 6 }}>
          <button
            onClick={() => router.push(`/admin/marketing/contents/${row.id}`)}
            disabled={row.status === 'GENERATING'}
            style={{
              padding: '4px 10px', background: 'white', border: '1px solid #ddd',
              borderRadius: 4, cursor: row.status === 'GENERATING' ? 'not-allowed' : 'pointer',
              fontSize: 12, opacity: row.status === 'GENERATING' ? 0.5 : 1,
            }}
          >
            상세보기
          </button>
          {(row.status === 'APPROVED' || row.status === 'EXPORTED') && (
            <button
              onClick={() => {
                setRepurposeContentId(row.id);
                setRepurposeSourcePlatform(row.platform);
              }}
              style={{
                padding: '4px 10px', background: '#e6f0ff', color: '#0066cc',
                border: '1px solid #b3d1ff', borderRadius: 4, cursor: 'pointer', fontSize: 12,
              }}
            >
              리퍼포징
            </button>
          )}
          {row.status !== 'GENERATING' && (
            confirmDeleteId === row.id ? (
              <>
                <button
                  onClick={() => handleDeleteContent(row.id)}
                  disabled={deletingId === row.id}
                  style={{
                    padding: '4px 10px', background: '#b33333', color: 'white',
                    border: 'none', borderRadius: 4, cursor: 'pointer', fontSize: 12,
                    opacity: deletingId === row.id ? 0.6 : 1,
                  }}
                >
                  {deletingId === row.id ? '삭제 중...' : '확인'}
                </button>
                <button
                  onClick={() => setConfirmDeleteId(null)}
                  style={{
                    padding: '4px 10px', background: 'white', border: '1px solid #ddd',
                    borderRadius: 4, cursor: 'pointer', fontSize: 12,
                  }}
                >
                  취소
                </button>
              </>
            ) : (
              <button
                onClick={() => setConfirmDeleteId(row.id)}
                style={{
                  padding: '4px 10px', background: '#ffe6e6', color: '#b33333',
                  border: '1px solid #ffcccc', borderRadius: 4, cursor: 'pointer', fontSize: 12,
                }}
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
        @keyframes pulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.6; } }
      `}</style>

      <AdminSection
        title="콘텐츠 관리"
        subtitle="커뮤니티 사연 기반 마케팅 콘텐츠"
        badge={
          filteredContents.length > 0
            ? { text: `${filteredContents.length}건`, color: '#555' }
            : undefined
        }
      >
        <div
          style={{
            marginBottom: 12, display: 'flex', gap: 8,
            justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap',
          }}
        >
          <div style={{ display: 'flex', gap: 8 }}>
            <AdminFilters
              status={{ value: platformFilter, onChange: setPlatformFilter, options: PLATFORM_FILTERS }}
            />
            <AdminFilters
              status={{ value: statusFilter, onChange: setStatusFilter, options: STATUS_FILTERS }}
            />
          </div>
          <div style={{ display: 'flex', gap: 8 }}>
            <button
              onClick={() => setShowTemplateModal(true)}
              style={{
                padding: '9px 18px', background: 'white', color: '#1A1A2E',
                border: '1px solid #1A1A2E', borderRadius: 6, cursor: 'pointer',
                fontSize: 13, fontWeight: 500,
              }}
            >
              템플릿에서 생성
            </button>
            <button
              onClick={openModal}
              style={{
                padding: '9px 18px', background: '#1A1A2E', color: 'white',
                border: 'none', borderRadius: 6, cursor: 'pointer', fontSize: 13, fontWeight: 500,
              }}
            >
              콘텐츠 생성
            </button>
          </div>
        </div>

        {error && (
          <div
            style={{
              padding: 12, background: '#ffe6e6', color: '#b33333',
              borderRadius: 6, marginBottom: 12, fontSize: 13,
            }}
          >
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

      {showTemplateModal && (
        <TemplatePickerModal
          onClose={() => setShowTemplateModal(false)}
          onGenerated={() => {
            setShowTemplateModal(false);
            setAutoRefresh(true);
            loadContents();
          }}
        />
      )}

      {repurposeContentId !== null && (
        <RepurposeModal
          sourceId={repurposeContentId}
          sourcePlatform={repurposeSourcePlatform}
          onClose={() => {
            setRepurposeContentId(null);
            setRepurposeSourcePlatform('');
          }}
          onRepurposed={() => {
            setRepurposeContentId(null);
            setRepurposeSourcePlatform('');
            loadContents();
          }}
        />
      )}

      {/* 콘텐츠 생성 모달 */}
      {showModal && (
        <div
          style={{
            position: 'fixed', top: 0, left: 0, right: 0, bottom: 0,
            background: 'rgba(0,0,0,0.5)', display: 'flex',
            alignItems: 'center', justifyContent: 'center', zIndex: 1000,
          }}
          onClick={() => setShowModal(false)}
        >
          <div
            style={{
              background: 'white', borderRadius: 12, padding: 24,
              maxWidth: 520, width: '95%', boxShadow: '0 10px 40px rgba(0,0,0,0.2)',
              maxHeight: '90vh', overflowY: 'auto',
            }}
            onClick={(e) => e.stopPropagation()}
          >
            <h3 style={{ margin: '0 0 4px', fontSize: 16, fontWeight: 600, color: '#1A1A2E' }}>
              홍보 사연 선택
            </h3>
            <p style={{ margin: '0 0 12px', fontSize: 12, color: '#888' }}>
              다시봄 커뮤니티에서 홍보할 사연을 골라주세요.
            </p>

            {/* 정렬 토글 + 검색 */}
            <div style={{ display: 'flex', gap: 8, marginBottom: 10, alignItems: 'center' }}>
              <div style={{ display: 'flex', border: '1px solid #ddd', borderRadius: 6, overflow: 'hidden' }}>
                {(['recommended', 'latest'] as const).map((s) => (
                  <button
                    key={s}
                    onClick={() => handleSortByChange(s)}
                    style={{
                      padding: '6px 12px', fontSize: 12, border: 'none', cursor: 'pointer',
                      background: candidateSortBy === s ? '#1A1A2E' : 'white',
                      color: candidateSortBy === s ? 'white' : '#555',
                    }}
                  >
                    {s === 'recommended' ? '인기순' : '최신순'}
                  </button>
                ))}
              </div>
              <input
                type="text"
                placeholder="제목/내용 검색"
                value={candidateSearch}
                onChange={(e) => handleSearchChange(e.target.value)}
                style={{
                  flex: 1, padding: '6px 10px', fontSize: 12, border: '1px solid #ddd',
                  borderRadius: 6, outline: 'none',
                }}
              />
            </div>

            {/* 후보 사연 목록 */}
            {candidatesLoading ? (
              <p style={{ fontSize: 13, color: '#888', textAlign: 'center', padding: '20px 0' }}>
                불러오는 중...
              </p>
            ) : candidates.length === 0 ? (
              <p style={{ fontSize: 13, color: '#b33333', textAlign: 'center', padding: '20px 0' }}>
                공개 사연이 없어요.
              </p>
            ) : (
              <div
                style={{
                  maxHeight: 260, overflowY: 'auto', marginBottom: 16,
                  border: '1px solid #eee', borderRadius: 6,
                }}
              >
                {candidates.map((c) => (
                  <div
                    key={c.id}
                    onClick={() => setSelectedPostId(c.id)}
                    style={{
                      padding: '10px 14px', cursor: 'pointer', borderBottom: '1px solid #f0f0f0',
                      background: selectedPostId === c.id ? '#f0f4ff' : 'white',
                      borderLeft: selectedPostId === c.id ? '3px solid #2d4a7a' : '3px solid transparent',
                    }}
                  >
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                      <div style={{ flex: 1, marginRight: 8 }}>
                        <div style={{ fontSize: 12, fontWeight: 600, color: '#1A1A2E', marginBottom: 2 }}>
                          {c.title}
                        </div>
                        <div style={{ fontSize: 11, color: '#666', marginBottom: 2 }}>
                          {c.snippet}
                        </div>
                        <div style={{ fontSize: 10, color: '#999' }}>
                          {CATEGORY_LABELS[c.category] ?? c.category}
                          {c.voteCount > 0
                            ? ` · 작성자 ${c.authorPct}% : 상대방 ${c.partnerPct}% (${c.voteCount}표)`
                            : ' · 투표 없음'}
                          {` · 댓글 ${c.commentCount}`}
                        </div>
                      </div>
                      {c.synthetic && (
                        <span
                          style={{
                            fontSize: 9, padding: '1px 5px', background: '#eee', borderRadius: 3,
                            color: '#888', flexShrink: 0,
                          }}
                        >
                          AI
                        </span>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            )}

            {/* 플랫폼 체크박스 (3종 기본 체크) */}
            <p style={{ margin: '0 0 8px', fontSize: 13, color: '#666', fontWeight: 500 }}>
              생성할 플랫폼 선택 (기본: 3종 동시 생성)
            </p>
            <div style={{ display: 'flex', gap: 12, marginBottom: 20, flexWrap: 'wrap' }}>
              {(['x', 'instagram', 'naver_blog'] as PlatformKey[]).map((platform) => {
                const label = platform === 'naver_blog' ? '네이버블로그' : platform === 'x' ? 'X' : 'Instagram';
                const colors = PLATFORM_BADGE_COLORS[platform];
                const checked = selectedPlatforms.includes(platform);
                return (
                  <label
                    key={platform}
                    style={{ display: 'flex', alignItems: 'center', gap: 6, cursor: 'pointer' }}
                  >
                    <input
                      type="checkbox"
                      checked={checked}
                      onChange={() => togglePlatform(platform)}
                      style={{ width: 14, height: 14 }}
                    />
                    <span
                      style={{
                        padding: '2px 10px', borderRadius: 4, fontSize: 12, fontWeight: 600,
                        background: checked ? colors.bg : '#f0f0f0',
                        color: checked ? colors.fg : '#999',
                        transition: 'all 0.15s',
                      }}
                    >
                      {label}
                    </span>
                  </label>
                );
              })}
            </div>

            <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
              <button
                onClick={() => setShowModal(false)}
                style={{
                  padding: '8px 16px', background: 'white', border: '1px solid #ddd',
                  borderRadius: 6, cursor: 'pointer', fontSize: 13,
                }}
              >
                취소
              </button>
              <button
                onClick={handleGenerateContent}
                disabled={submitting || !selectedPostId || selectedPlatforms.length === 0}
                style={{
                  padding: '8px 16px', background: '#1A1A2E', color: 'white',
                  border: 'none', borderRadius: 6, fontSize: 13,
                  cursor: submitting || !selectedPostId || selectedPlatforms.length === 0
                    ? 'not-allowed' : 'pointer',
                  opacity: submitting || !selectedPostId || selectedPlatforms.length === 0 ? 0.6 : 1,
                }}
              >
                {submitting
                  ? '요청 중...'
                  : `생성 시작 (${selectedPlatforms.length}종)`}
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
