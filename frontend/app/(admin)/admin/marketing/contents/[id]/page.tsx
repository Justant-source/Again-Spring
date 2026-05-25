'use client';

import { useEffect, useState } from 'react';
import { useRouter, useParams } from 'next/navigation';
import { AdminSection } from '@/components/admin/AdminSection';
import {
  getContent,
  updateContent,
  approveContent,
  rejectContent,
  type ContentResponse,
} from '@/lib/api/marketing/contentApi';

const STATUS_BADGE_COLORS: Record<string, { bg: string; fg: string }> = {
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

export default function ContentDetailPage() {
  const router = useRouter();
  const params = useParams();
  const contentId = Number(params.id);

  const [content, setContent] = useState<ContentResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [editedBodyText, setEditedBodyText] = useState('');
  const [isEditing, setIsEditing] = useState(false);
  const [isSaving, setIsSaving] = useState(false);
  const [isApproving, setIsApproving] = useState(false);
  const [isRejecting, setIsRejecting] = useState(false);
  const [rejectReason, setRejectReason] = useState('');
  const [showRejectModal, setShowRejectModal] = useState(false);

  useEffect(() => {
    loadContent();
  }, [contentId]);

  async function loadContent() {
    try {
      const data = await getContent(contentId);
      setContent(data);
      setEditedBodyText(data.bodyText);
      setError('');
    } catch (e: any) {
      setError('콘텐츠를 불러오지 못했어요.');
      console.error(e);
    } finally {
      setLoading(false);
    }
  }

  async function handleSave() {
    if (!editedBodyText.trim()) {
      setError('콘텐츠를 입력해주세요.');
      return;
    }

    setIsSaving(true);
    try {
      const updated = await updateContent(contentId, editedBodyText);
      setContent(updated);
      setIsEditing(false);
      setError('');
    } catch (e: any) {
      setError(`저장 실패: ${e.response?.data?.error?.message || '알 수 없는 오류'}`);
      console.error(e);
    } finally {
      setIsSaving(false);
    }
  }

  async function handleApprove() {
    setIsApproving(true);
    try {
      const updated = await approveContent(contentId);
      setContent(updated);
      setError('');
    } catch (e: any) {
      setError(`승인 실패: ${e.response?.data?.error?.message || '알 수 없는 오류'}`);
      console.error(e);
    } finally {
      setIsApproving(false);
    }
  }

  async function handleReject() {
    if (!rejectReason.trim()) {
      setError('거부 사유를 입력해주세요.');
      return;
    }

    setIsRejecting(true);
    try {
      const updated = await rejectContent(contentId, rejectReason);
      setContent(updated);
      setRejectReason('');
      setShowRejectModal(false);
      setError('');
    } catch (e: any) {
      setError(`거부 실패: ${e.response?.data?.error?.message || '알 수 없는 오류'}`);
      console.error(e);
    } finally {
      setIsRejecting(false);
    }
  }

  if (loading) {
    return (
      <AdminSection title="콘텐츠 상세">
        <p style={{ color: '#888', fontSize: 13, padding: '12px 4px' }}>불러오는 중…</p>
      </AdminSection>
    );
  }

  if (!content) {
    return (
      <AdminSection title="콘텐츠 상세">
        <p style={{ color: '#b33333', fontSize: 13, padding: '12px 4px' }}>콘텐츠를 찾을 수 없어요.</p>
      </AdminSection>
    );
  }

  const isEditableStatus = content.status === 'DRAFT' || content.status === 'REVIEW';
  const platformLabel = content.platform.toUpperCase() === 'NAVER_BLOG' ? '네이버블로그' : content.platform.toUpperCase();
  const platformColors = PLATFORM_BADGE_COLORS[content.platform.toLowerCase()] || { bg: '#f0f0f0', fg: '#666' };
  const statusColors = STATUS_BADGE_COLORS[content.status] || { bg: '#f0f0f0', fg: '#666' };

  let safetyCheckData: any = null;
  if (content.safetyCheckJson) {
    try {
      safetyCheckData = JSON.parse(content.safetyCheckJson);
    } catch (e) {
      console.error('Failed to parse safetyCheckJson', e);
    }
  }

  return (
    <>
      <AdminSection title={`콘텐츠 #${content.id}`} subtitle={`시뮬레이션 ID: ${content.simulationId}`}>
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

        <div style={{ marginBottom: 24, padding: 16, background: '#f9f9f9', borderRadius: 8 }}>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16, marginBottom: 12 }}>
            <div>
              <label style={{ fontSize: 11, color: '#666', fontWeight: 600, display: 'block', marginBottom: 4 }}>
                플랫폼
              </label>
              <span
                style={{
                  padding: '4px 10px',
                  borderRadius: 4,
                  fontSize: 12,
                  fontWeight: 600,
                  background: platformColors.bg,
                  color: platformColors.fg,
                  display: 'inline-block',
                }}
              >
                {platformLabel}
              </span>
            </div>
            <div>
              <label style={{ fontSize: 11, color: '#666', fontWeight: 600, display: 'block', marginBottom: 4 }}>
                상태
              </label>
              <span
                style={{
                  padding: '4px 10px',
                  borderRadius: 4,
                  fontSize: 12,
                  fontWeight: 600,
                  background: statusColors.bg,
                  color: statusColors.fg,
                  display: 'inline-block',
                }}
              >
                {content.status}
              </span>
            </div>
          </div>
          <div>
            <label style={{ fontSize: 11, color: '#666', fontWeight: 600, display: 'block', marginBottom: 4 }}>
              등록일
            </label>
            <p style={{ margin: 0, fontSize: 13 }}>{new Date(content.createdAt).toLocaleString('ko-KR')}</p>
          </div>
        </div>

        <div style={{ marginBottom: 24 }}>
          <label style={{ fontSize: 11, color: '#666', fontWeight: 600, display: 'block', marginBottom: 8 }}>
            콘텐츠
          </label>
          {isEditing ? (
            <>
              <textarea
                value={editedBodyText}
                onChange={(e) => setEditedBodyText(e.target.value)}
                style={{
                  width: '100%',
                  minHeight: 300,
                  padding: '12px 12px',
                  border: '1px solid #ddd',
                  borderRadius: 6,
                  fontSize: 13,
                  fontFamily: 'monospace',
                  marginBottom: 12,
                  boxSizing: 'border-box',
                }}
              />
              <div style={{ display: 'flex', gap: 8 }}>
                <button
                  onClick={handleSave}
                  disabled={isSaving}
                  style={{
                    padding: '8px 16px',
                    background: '#1A1A2E',
                    color: 'white',
                    border: 'none',
                    borderRadius: 6,
                    cursor: isSaving ? 'not-allowed' : 'pointer',
                    fontSize: 13,
                    opacity: isSaving ? 0.6 : 1,
                  }}
                >
                  {isSaving ? '저장 중...' : '저장'}
                </button>
                <button
                  onClick={() => {
                    setIsEditing(false);
                    setEditedBodyText(content.bodyText);
                  }}
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
              </div>
            </>
          ) : (
            <>
              <div
                style={{
                  padding: 12,
                  background: 'white',
                  border: '1px solid #eee',
                  borderRadius: 6,
                  minHeight: 300,
                  whiteSpace: 'pre-wrap',
                  wordBreak: 'break-word',
                  fontSize: 13,
                  marginBottom: 12,
                  lineHeight: 1.6,
                }}
              >
                {content.bodyText}
              </div>
              {isEditableStatus && (
                <button
                  onClick={() => setIsEditing(true)}
                  style={{
                    padding: '8px 16px',
                    background: 'white',
                    border: '1px solid #ddd',
                    borderRadius: 6,
                    cursor: 'pointer',
                    fontSize: 13,
                  }}
                >
                  수정
                </button>
              )}
            </>
          )}
        </div>

        {/* 채팅 UI 스크린샷 */}
        {content.imagePaths && (() => {
          let filenames: string[] = [];
          try { filenames = JSON.parse(content.imagePaths!); } catch { filenames = []; }
          if (filenames.length === 0) return null;
          return (
            <div style={{ marginBottom: 24 }}>
              <label style={{ fontSize: 11, color: '#666', fontWeight: 600, display: 'block', marginBottom: 8 }}>
                앱 UI 스크린샷
              </label>
              <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
                {filenames.map((fn: string) => (
                  <div key={fn} style={{ border: '1px solid #eee', borderRadius: 8, overflow: 'hidden', background: '#f9f9f9' }}>
                    <img
                      src={`/api/admin/marketing/images/${fn}`}
                      alt="채팅 UI 스크린샷"
                      style={{ display: 'block', width: 195, height: 360, objectFit: 'cover' }}
                    />
                    <div style={{ padding: '6px 10px', fontSize: 11, color: '#888', borderTop: '1px solid #eee' }}>
                      {fn}
                      <a
                        href={`/api/admin/marketing/images/${fn}`}
                        target="_blank"
                        rel="noopener noreferrer"
                        style={{ marginLeft: 8, color: '#0066cc', textDecoration: 'none', fontSize: 11 }}
                      >
                        원본 다운로드
                      </a>
                    </div>
                  </div>
                ))}
              </div>
              <p style={{ margin: '8px 0 0', fontSize: 11, color: '#aaa' }}>
                시뮬레이션 대화를 실제 다시봄 채팅 UI 스타일로 렌더링한 마케팅 이미지입니다.
              </p>
            </div>
          );
        })()}

        {safetyCheckData && (
          <div style={{ marginBottom: 24, padding: 16, background: '#f9f9f9', borderRadius: 8 }}>
            <label style={{ fontSize: 11, color: '#666', fontWeight: 600, display: 'block', marginBottom: 8 }}>
              안전성 검사
            </label>
            <pre
              style={{
                margin: 0,
                fontSize: 12,
                overflow: 'auto',
                maxHeight: 200,
                background: 'white',
                padding: 8,
                borderRadius: 4,
                border: '1px solid #ddd',
              }}
            >
              {JSON.stringify(safetyCheckData, null, 2)}
            </pre>
          </div>
        )}

        {(content.status === 'DRAFT' || content.status === 'REVIEW') && (
          <div style={{ display: 'flex', gap: 8 }}>
            <button
              onClick={handleApprove}
              disabled={isApproving}
              style={{
                padding: '8px 16px',
                background: '#e6f7e6',
                color: '#2d7a2d',
                border: '1px solid #ccf0cc',
                borderRadius: 6,
                cursor: isApproving ? 'not-allowed' : 'pointer',
                fontSize: 13,
                fontWeight: 500,
                opacity: isApproving ? 0.6 : 1,
              }}
            >
              {isApproving ? '승인 중...' : '승인'}
            </button>
            <button
              onClick={() => setShowRejectModal(true)}
              style={{
                padding: '8px 16px',
                background: '#ffe6e6',
                color: '#b33333',
                border: '1px solid #ffcccc',
                borderRadius: 6,
                cursor: 'pointer',
                fontSize: 13,
                fontWeight: 500,
              }}
            >
              거부
            </button>
            <button
              onClick={() => router.back()}
              style={{
                padding: '8px 16px',
                background: 'white',
                border: '1px solid #ddd',
                borderRadius: 6,
                cursor: 'pointer',
                fontSize: 13,
              }}
            >
              뒤로가기
            </button>
          </div>
        )}

        {content.status !== 'DRAFT' && content.status !== 'REVIEW' && (
          <button
            onClick={() => router.back()}
            style={{
              padding: '8px 16px',
              background: 'white',
              border: '1px solid #ddd',
              borderRadius: 6,
              cursor: 'pointer',
              fontSize: 13,
            }}
          >
            뒤로가기
          </button>
        )}
      </AdminSection>

      {showRejectModal && (
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
          onClick={() => setShowRejectModal(false)}
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
              콘텐츠 거부
            </h3>
            <p style={{ margin: '0 0 12px', fontSize: 13, color: '#666' }}>
              거부 사유를 입력하세요
            </p>
            <textarea
              value={rejectReason}
              onChange={(e) => setRejectReason(e.target.value)}
              placeholder="거부 사유"
              style={{
                width: '100%',
                minHeight: 100,
                padding: '10px 12px',
                border: '1px solid #ddd',
                borderRadius: 6,
                fontSize: 13,
                marginBottom: 12,
                boxSizing: 'border-box',
              }}
            />
            <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
              <button
                onClick={() => setShowRejectModal(false)}
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
                onClick={handleReject}
                disabled={isRejecting}
                style={{
                  padding: '8px 16px',
                  background: '#ffe6e6',
                  color: '#b33333',
                  border: '1px solid #ffcccc',
                  borderRadius: 6,
                  cursor: isRejecting ? 'not-allowed' : 'pointer',
                  fontSize: 13,
                  opacity: isRejecting ? 0.6 : 1,
                }}
              >
                {isRejecting ? '거부 중...' : '거부'}
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
