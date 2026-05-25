'use client';

import { useEffect, useState } from 'react';
import { useRouter, useParams } from 'next/navigation';
import { AdminSection } from '@/components/admin/AdminSection';
import {
  getStory,
  approveStory,
  rejectStory,
  type StoryResponse,
} from '@/lib/api/marketing/storyApi';

const STATUS_BADGE_COLORS: Record<string, { bg: string; fg: string }> = {
  pending: { bg: '#fff9e6', fg: '#b8860b' },
  approved: { bg: '#e6f7e6', fg: '#2d7a2d' },
  rejected: { bg: '#ffe6e6', fg: '#b33333' },
  used: { bg: '#f0f0f0', fg: '#666' },
};

export default function StoryDetailPage() {
  const router = useRouter();
  const params = useParams();
  const storyId = Number(params.id);

  const [story, setStory] = useState<StoryResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [actionLoading, setActionLoading] = useState(false);
  const [showRejectReason, setShowRejectReason] = useState(false);
  const [rejectReason, setRejectReason] = useState('');

  useEffect(() => {
    loadStory();
  }, [storyId]);


  async function loadStory() {
    setLoading(true);
    try {
      const data = await getStory(storyId);
      setStory(data);
      setError('');
    } catch (e: any) {
      setError('사연을 불러오지 못했어요.');
      console.error(e);
    } finally {
      setLoading(false);
    }
  }

  async function handleApprove() {
    if (!story) return;
    setActionLoading(true);
    try {
      const updated = await approveStory(story.id);
      setStory(updated);
    } catch (e: any) {
      setError(e.response?.data?.message || '승인하지 못했어요.');
    } finally {
      setActionLoading(false);
    }
  }

  async function handleReject() {
    if (!story || !rejectReason.trim()) {
      setError('거부 사유를 입력하세요.');
      return;
    }
    setActionLoading(true);
    try {
      const updated = await rejectStory(story.id, rejectReason.trim());
      setStory(updated);
      setShowRejectReason(false);
      setRejectReason('');
    } catch (e: any) {
      setError(e.response?.data?.message || '거부하지 못했어요.');
    } finally {
      setActionLoading(false);
    }
  }

  if (loading) {
    return (
      <AdminSection title="사연 상세">
        <p style={{ color: '#888', fontSize: 13, padding: '12px 4px' }}>불러오는 중...</p>
      </AdminSection>
    );
  }

  if (!story) {
    return (
      <AdminSection title="사연 상세">
        <p style={{ color: '#e55', fontSize: 13, padding: '12px 4px' }}>
          {error || '사연을 찾을 수 없어요.'}
        </p>
      </AdminSection>
    );
  }

  const statusBadge = STATUS_BADGE_COLORS[story.status] || { bg: '#f0f0f0', fg: '#666' };

  return (
    <AdminSection title="사연 상세">
      {error && (
        <div style={{
          padding: 12,
          background: '#ffe6e6',
          color: '#b33333',
          borderRadius: 6,
          marginBottom: 16,
          fontSize: 13,
        }}>
          {error}
        </div>
      )}

      {/* 메타데이터 */}
      <div style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))',
        gap: 12,
        marginBottom: 24,
      }}>
        <div>
          <p style={{ fontSize: 12, color: '#888', margin: '0 0 4px' }}>ID</p>
          <p style={{ fontSize: 13, fontWeight: 600, margin: 0, color: '#1A1A2E' }}>#{story.id}</p>
        </div>
        <div>
          <p style={{ fontSize: 12, color: '#888', margin: '0 0 4px' }}>출처 플랫폼</p>
          <p style={{ fontSize: 13, fontWeight: 600, margin: 0, color: '#1A1A2E' }}>{story.sourcePlatform}</p>
        </div>
        <div>
          <p style={{ fontSize: 12, color: '#888', margin: '0 0 4px' }}>관계 유형</p>
          <p style={{ fontSize: 13, fontWeight: 600, margin: 0, color: '#1A1A2E' }}>{story.relationType}</p>
        </div>
        <div>
          <p style={{ fontSize: 12, color: '#888', margin: '0 0 4px' }}>상태</p>
          <span style={{
            display: 'inline-block',
            padding: '2px 8px',
            borderRadius: 4,
            fontSize: 11,
            fontWeight: 600,
            background: statusBadge.bg,
            color: statusBadge.fg,
          }}>
            {story.status}
          </span>
        </div>
        <div>
          <p style={{ fontSize: 12, color: '#888', margin: '0 0 4px' }}>등록일</p>
          <p style={{ fontSize: 13, fontWeight: 600, margin: 0, color: '#1A1A2E' }}>
            {new Date(story.createdAt).toLocaleDateString('ko-KR')}
          </p>
        </div>
        <div>
          <p style={{ fontSize: 12, color: '#888', margin: '0 0 4px' }}>등록자</p>
          <p style={{ fontSize: 13, fontWeight: 600, margin: 0, color: '#1A1A2E' }}>{story.createdBy}</p>
        </div>
      </div>

      {/* 원문 사연 */}
      <div style={{
        padding: 16,
        background: '#f9f9f9',
        borderRadius: 8,
        border: '1px solid #e7e3d8',
        marginBottom: 24,
      }}>
        <h3 style={{ fontSize: 13, fontWeight: 600, color: '#1A1A2E', marginBottom: 12, margin: '0 0 12px' }}>
          원문 사연
        </h3>
        <p style={{
          fontSize: 13,
          lineHeight: 1.6,
          color: '#333',
          margin: 0,
          whiteSpace: 'pre-wrap',
          wordBreak: 'break-word',
        }}>
          {story.rawText}
        </p>
      </div>

      {/* 거부 사유 (상태가 rejected인 경우) */}
      {story.status === 'rejected' && story.blockedReason && (
        <div style={{
          padding: 12,
          background: '#ffe6e6',
          borderRadius: 6,
          marginBottom: 20,
          border: '1px solid #f3c8c8',
        }}>
          <p style={{ fontSize: 12, color: '#888', margin: '0 0 4px', fontWeight: 600 }}>거부 사유</p>
          <p style={{ fontSize: 13, color: '#b33333', margin: 0 }}>
            {story.blockedReason}
          </p>
        </div>
      )}

      {/* 액션 버튼 */}
      {(story.status === 'pending' || story.status === 'rejected') && (
        <div style={{
          display: 'flex',
          gap: 8,
          justifyContent: 'flex-start',
          flexWrap: 'wrap',
        }}>
          {story.status === 'pending' && (
            <>
              <button
                onClick={handleApprove}
                disabled={actionLoading}
                style={{
                  padding: '9px 18px',
                  background: '#2d7a2d',
                  color: 'white',
                  border: 'none',
                  borderRadius: 6,
                  cursor: actionLoading ? 'wait' : 'pointer',
                  fontSize: 13,
                  fontWeight: 500,
                  opacity: actionLoading ? 0.6 : 1,
                }}
              >
                {actionLoading ? '...' : '승인'}
              </button>
              <button
                onClick={() => setShowRejectReason(true)}
                disabled={actionLoading}
                style={{
                  padding: '9px 18px',
                  background: '#b33333',
                  color: 'white',
                  border: 'none',
                  borderRadius: 6,
                  cursor: 'pointer',
                  fontSize: 13,
                  fontWeight: 500,
                }}
              >
                거부
              </button>
            </>
          )}
          {story.status === 'rejected' && (
            <button
              onClick={handleApprove}
              disabled={actionLoading}
              style={{
                padding: '9px 18px',
                background: '#2d7a2d',
                color: 'white',
                border: 'none',
                borderRadius: 6,
                cursor: actionLoading ? 'wait' : 'pointer',
                fontSize: 13,
                fontWeight: 500,
                opacity: actionLoading ? 0.6 : 1,
              }}
            >
              {actionLoading ? '...' : '재승인'}
            </button>
          )}
        </div>
      )}

      {/* 거부 사유 모달 */}
      {showRejectReason && (
        <div style={{
          position: 'fixed',
          top: 0,
          left: 0,
          right: 0,
          bottom: 0,
          background: 'rgba(0, 0, 0, 0.5)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          zIndex: 100,
          fontFamily: 'sans-serif',
        }}>
          <div style={{
            background: 'white',
            borderRadius: 8,
            padding: 24,
            maxWidth: 400,
            width: 'calc(100% - 32px)',
            boxShadow: '0 20px 60px rgba(0,0,0,0.3)',
          }}>
            <h2 style={{ fontSize: 15, fontWeight: 600, color: '#1A1A2E', margin: '0 0 12px' }}>
              거부 사유 입력
            </h2>
            <textarea
              placeholder="거부 사유를 입력하세요."
              value={rejectReason}
              onChange={(e) => setRejectReason(e.target.value)}
              style={{
                width: '100%',
                padding: '10px 12px',
                border: '1px solid #ddd',
                borderRadius: 6,
                fontSize: 13,
                fontFamily: 'inherit',
                boxSizing: 'border-box',
                minHeight: 100,
                resize: 'vertical',
                marginBottom: 16,
              }}
            />
            <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
              <button
                onClick={() => {
                  setShowRejectReason(false);
                  setRejectReason('');
                }}
                disabled={actionLoading}
                style={{
                  padding: '9px 18px',
                  background: 'white',
                  color: '#555',
                  border: '1px solid #ddd',
                  borderRadius: 6,
                  cursor: 'pointer',
                  fontSize: 13,
                  fontWeight: 500,
                }}
              >
                취소
              </button>
              <button
                onClick={handleReject}
                disabled={actionLoading || !rejectReason.trim()}
                style={{
                  padding: '9px 18px',
                  background: '#b33333',
                  color: 'white',
                  border: 'none',
                  borderRadius: 6,
                  cursor: actionLoading || !rejectReason.trim() ? 'not-allowed' : 'pointer',
                  fontSize: 13,
                  fontWeight: 500,
                  opacity: actionLoading || !rejectReason.trim() ? 0.6 : 1,
                }}
              >
                {actionLoading ? '처리 중...' : '거부'}
              </button>
            </div>
          </div>
        </div>
      )}
    </AdminSection>
  );
}
