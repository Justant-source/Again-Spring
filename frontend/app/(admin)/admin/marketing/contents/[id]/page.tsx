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
import {
  publishSocial, getPublishStatus,
  type SocialPlatform, type SocialPublishResult,
} from '@/lib/api/marketing/socialApi';
import { PerformanceForm } from '@/components/admin/marketing/PerformanceForm';
import { AuthImage } from '@/components/admin/marketing/AuthImage';
import { PlatformPreview } from '@/components/admin/marketing/preview/PlatformPreview';
import { parseImagePaths } from '@/components/admin/marketing/preview/parseImagePaths';
import { scheduleContent, publishContent } from '@/lib/api/marketing/performanceApi';

const STATUS_BADGE_COLORS: Record<string, { bg: string; fg: string }> = {
  DRAFT: { bg: '#f0f0f0', fg: '#666' },
  REVIEW: { bg: '#e6f0ff', fg: '#0066cc' },
  APPROVED: { bg: '#e6f7e6', fg: '#2d7a2d' },
  EXPORTED: { bg: '#f0e6ff', fg: '#7a2d7a' },
  REJECTED: { bg: '#ffe6e6', fg: '#b33333' },
  PUBLISHING: { bg: '#fff3cd', fg: '#856404' },
  PARTIAL: { bg: '#ffd6a5', fg: '#7a3000' },
  PUBLISHED: { bg: '#d1fae5', fg: '#065f46' },
  FAILED: { bg: '#fee2e2', fg: '#991b1b' },
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
  const [scheduledAtInput, setScheduledAtInput] = useState('');
  const [schedulingErr, setSchedulingErr] = useState('');
  const [isScheduling, setIsScheduling] = useState(false);
  const [publishUrl, setPublishUrl] = useState('');
  const [isPublishing, setIsPublishing] = useState(false);
  const [publishErr, setPublishErr] = useState('');
  const [socialTargets, setSocialTargets] = useState<SocialPlatform[]>([]);
  const [linkMode, setLinkMode] = useState<'last_tweet' | 'first_reply'>('last_tweet');
  const [socialPublishResults, setSocialPublishResults] = useState<SocialPublishResult[]>([]);
  const [isAutoPublishing, setIsAutoPublishing] = useState(false);
  const [autoPublishErr, setAutoPublishErr] = useState('');
  const [publishPolling, setPublishPolling] = useState(false);

  useEffect(() => {
    loadContent();
    loadPublishStatus(); // 페이지 진입 시 기존 발행 결과(실패 사유 포함) 표시
  }, [contentId]);

  // content.platform 기반으로 발행 대상 자동 설정
  useEffect(() => {
    if (!content) return;
    const p = content.platform?.toUpperCase();
    if (p === 'X' || p === 'INSTAGRAM' || p === 'NAVER_BLOG') {
      setSocialTargets([p as SocialPlatform]);
    }
  }, [content?.platform]);

  // 발행 결과 조회 (마운트 시 + 폴링 종료 시)
  async function loadPublishStatus() {
    try {
      const status = await getPublishStatus(contentId);
      setSocialPublishResults(status.results);
    } catch (e) {
      // 결과 없으면 무시 (최초 발행 전)
    }
  }

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

  async function handleSchedule() {
    if (!scheduledAtInput) {
      setSchedulingErr('날짜를 선택해주세요.');
      return;
    }
    setIsScheduling(true);
    setSchedulingErr('');
    try {
      const updated = await scheduleContent(contentId, new Date(scheduledAtInput).toISOString());
      setContent(updated);
    } catch (e: any) {
      setSchedulingErr('예약 설정에 실패했습니다.');
      console.error(e);
    } finally {
      setIsScheduling(false);
    }
  }

  async function handlePublish() {
    setIsPublishing(true);
    setPublishErr('');
    try {
      const updated = await publishContent(contentId, publishUrl || undefined);
      setContent(updated);
    } catch (e: any) {
      setPublishErr('발행 처리에 실패했습니다.');
      console.error(e);
    } finally {
      setIsPublishing(false);
    }
  }

  async function handleAutoPublish() {
    if (socialTargets.length === 0) { setAutoPublishErr('발행 대상을 선택해주세요'); return; }
    setIsAutoPublishing(true);
    setAutoPublishErr('');
    try {
      await publishSocial(contentId, socialTargets, linkMode);
      setPublishPolling(true);
      let attempts = 0;
      // X/IG 발행 실패는 셀렉터 타임아웃 등으로 90초 이상 걸릴 수 있어 넉넉히 폴링
      const MAX_ATTEMPTS = 50; // 50 × 3s = 150초
      const pollInterval = setInterval(async () => {
        attempts++;
        try {
          const status = await getPublishStatus(contentId);
          setSocialPublishResults(status.results);
          const allDone = status.results.length > 0 &&
            status.results.every(r => r.state === 'SUCCEEDED' || r.state === 'FAILED');
          if (allDone || attempts >= MAX_ATTEMPTS) {
            clearInterval(pollInterval);
            setPublishPolling(false);
            if (!allDone) {
              setAutoPublishErr('발행이 오래 걸려 자동 갱신을 멈췄어요. "상태 새로고침"으로 결과를 확인하세요.');
            }
            await loadContent();
            await loadPublishStatus(); // 최종 결과(실패 사유) 재조회
          }
        } catch (e) {
          // 일시적 오류는 무시하고 계속 폴링 (네트워크 흔들림 대응)
          if (attempts >= MAX_ATTEMPTS) {
            clearInterval(pollInterval);
            setPublishPolling(false);
          }
        }
      }, 3000);
    } catch (e: any) {
      if (e.response?.status === 409) setAutoPublishErr('이미 발행된 플랫폼입니다 (재발행 불가)');
      else setAutoPublishErr(e.response?.data?.error || '발행 요청 실패');
    } finally {
      setIsAutoPublishing(false);
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
      <AdminSection title={`콘텐츠 #${content.id}`} subtitle={`사연: ${content.sourcePostId ?? '-'}`}>
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

        {/* 플랫폼 미리보기 */}
        <PlatformPreview content={content} />

        {/* 생성된 이미지 자산 */}
        {(() => {
          const images = parseImagePaths(content.imagePaths);
          if (images.length === 0) return null;
          return (
            <div style={{ marginBottom: 24 }}>
              <label style={{ fontSize: 11, color: '#666', fontWeight: 600, display: 'block', marginBottom: 8 }}>
                생성된 이미지 자산 ({images.length}장)
              </label>
              <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
                {images.map((img) => {
                  const isSquare = img.role === 'QUOTE_CARD' || img.role === 'COVER' || img.role === 'SCENE' || img.role === 'FEELING' || img.role === 'NVC' || img.role === 'CTA' || img.role === 'BONUS';
                  const w = 160;
                  const h = isSquare ? 160 : 200;
                  return (
                    <div key={img.filename} style={{ border: '1px solid #eee', borderRadius: 8, overflow: 'hidden', background: '#f9f9f9', maxWidth: w + 20 }}>
                      <AuthImage
                        src={`/api/admin/marketing/images/${img.filename}`}
                        alt={img.alt || img.role}
                        style={{ display: 'block', width: w, height: h, objectFit: 'cover' }}
                      />
                      <div style={{ padding: '6px 10px', fontSize: 10, color: '#888', borderTop: '1px solid #eee' }}>
                        <span style={{ fontWeight: 600, color: '#555' }}>{img.role || '이미지'}</span>
                        <br />
                        {img.filename}
                      </div>
                    </div>
                  );
                })}
              </div>
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

        {/* 발행 예약 */}
        <div style={{ marginBottom: 24, padding: '16px', background: '#f9f9f9', borderRadius: 8 }}>
          <h3 style={{ fontSize: 13, fontWeight: 600, color: '#1A1A2E', margin: '0 0 12px' }}>
            발행 예약
          </h3>
          {content.scheduledAt && (
            <p style={{ margin: '0 0 12px', fontSize: 13, color: '#446620' }}>
              예약 시각: {new Date(content.scheduledAt).toLocaleString('ko-KR')}
            </p>
          )}
          <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
            <input
              type="datetime-local"
              value={scheduledAtInput}
              onChange={(e) => setScheduledAtInput(e.target.value)}
              style={{
                padding: '6px 10px',
                border: '1px solid #ddd',
                borderRadius: 6,
                fontSize: 13,
              }}
            />
            <button
              onClick={handleSchedule}
              disabled={isScheduling}
              style={{
                padding: '7px 14px',
                background: '#1A1A2E',
                color: 'white',
                border: 'none',
                borderRadius: 6,
                cursor: isScheduling ? 'not-allowed' : 'pointer',
                fontSize: 13,
                opacity: isScheduling ? 0.6 : 1,
              }}
            >
              {isScheduling ? '설정 중...' : '예약 설정'}
            </button>
          </div>
          {schedulingErr && (
            <p style={{ margin: '8px 0 0', fontSize: 12, color: '#b33333' }}>{schedulingErr}</p>
          )}
        </div>

        {/* 소셜 자동 발행 */}
        {(content.status === 'APPROVED' || content.status === 'PUBLISHING' || content.status === 'PARTIAL' || content.status === 'PUBLISHED' || content.status === 'FAILED') && (
          <div style={{ marginBottom: 24, padding: '16px', background: '#f9f9f9', borderRadius: 8 }}>
            <h3 style={{ fontSize: 13, fontWeight: 600, color: '#1A1A2E', margin: '0 0 12px' }}>소셜 자동 발행</h3>
            {/* Target platform — content.platform 에 맞는 플랫폼만 표시 */}
            <div style={{ display: 'flex', gap: 8, marginBottom: 12, flexWrap: 'wrap', alignItems: 'center' }}>
              {content && (() => {
                const p = content.platform?.toUpperCase() as SocialPlatform;
                if (p !== 'X' && p !== 'INSTAGRAM' && p !== 'NAVER_BLOG') return null;
                const succeeded = socialPublishResults.some(r => r.platform === p && r.state === 'SUCCEEDED');
                const failed = socialPublishResults.some(r => r.platform === p && r.state === 'FAILED');
                return (
                  <label key={p} style={{ display: 'flex', alignItems: 'center', gap: 4, cursor: 'pointer', fontSize: 13 }}>
                    <input
                      type="checkbox"
                      checked={socialTargets.includes(p)}
                      disabled={succeeded}
                      onChange={e => setSocialTargets(e.target.checked ? [p] : [])}
                    />
                    {p}
                    {succeeded && <span style={{ fontSize: 11, color: '#065f46', background: '#d1fae5', borderRadius: 4, padding: '1px 5px' }}>발행완료</span>}
                    {failed && <span style={{ fontSize: 11, color: '#991b1b', background: '#fee2e2', borderRadius: 4, padding: '1px 5px' }}>실패</span>}
                  </label>
                );
              })()}
            </div>
            {/* 발행 결과 필터 — content.platform에 해당하는 결과만 표시 */}
            {/* Link mode — X 콘텐츠 전용 */}
            {content?.platform?.toUpperCase() === 'X' && (
            <div style={{ marginBottom: 12, display: 'flex', gap: 12, fontSize: 13 }}>
              <label style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                <input type="radio" name="linkMode" value="last_tweet" checked={linkMode === 'last_tweet'} onChange={() => setLinkMode('last_tweet')} />
                마지막 트윗에 링크
              </label>
              <label style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                <input type="radio" name="linkMode" value="first_reply" checked={linkMode === 'first_reply'} onChange={() => setLinkMode('first_reply')} />
                첫 댓글에 링크
              </label>
            </div>
            )}
            {/* Publish button + 상태 새로고침 */}
            <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
              <button
                onClick={handleAutoPublish}
                disabled={isAutoPublishing || publishPolling}
                style={{
                  padding: '7px 16px',
                  background: (isAutoPublishing || publishPolling) ? '#ccc' : '#1A1A2E',
                  color: 'white',
                  border: 'none',
                  borderRadius: 6,
                  cursor: (isAutoPublishing || publishPolling) ? 'not-allowed' : 'pointer',
                  fontSize: 13,
                }}
              >
                {publishPolling ? '발행 중...' : isAutoPublishing ? '요청 중...' : '발행'}
              </button>
              <button
                onClick={loadPublishStatus}
                style={{
                  padding: '7px 12px', background: '#fff', color: '#446620',
                  border: '1px solid #446620', borderRadius: 6, cursor: 'pointer', fontSize: 12,
                }}
              >
                상태 새로고침
              </button>
            </div>
            {/* Per-platform results — content.platform 에 해당하는 플랫폼만 표시 */}
            {socialPublishResults.filter(r => r.platform === content?.platform?.toUpperCase()).length > 0 && (
              <div style={{ marginTop: 12, display: 'flex', flexDirection: 'column', gap: 8 }}>
                {socialPublishResults.filter(r =>
                  r.platform === content?.platform?.toUpperCase()
                ).map(r => (
                  <div key={r.platform} style={{ fontSize: 12, display: 'flex', flexDirection: 'column', gap: 4, padding: '8px 10px', background: '#fff', border: '1px solid #eee', borderRadius: 6 }}>
                    <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
                      <span style={{ fontWeight: 600, minWidth: 80 }}>{r.platform}</span>
                      <span style={{
                        padding: '2px 6px', borderRadius: 4,
                        background: r.state === 'SUCCEEDED' ? '#d1fae5' : r.state === 'FAILED' ? '#fee2e2' : '#fff3cd',
                        color: r.state === 'SUCCEEDED' ? '#065f46' : r.state === 'FAILED' ? '#991b1b' : '#856404',
                      }}>
                        {r.state === 'SUCCEEDED' ? '성공' : r.state === 'FAILED' ? '실패' : '진행 중'}
                      </span>
                      {r.publishedUrl && (
                        <a href={r.publishedUrl} target="_blank" rel="noopener noreferrer" style={{ color: '#0066cc', fontSize: 11 }}>게시물 보기</a>
                      )}
                    </div>
                    {r.errorReason && (
                      <div style={{
                        color: '#991b1b', fontSize: 11, lineHeight: 1.5, whiteSpace: 'pre-wrap',
                        wordBreak: 'break-word', maxHeight: 120, overflowY: 'auto',
                        background: '#fef2f2', border: '1px solid #fecaca', borderRadius: 4, padding: '6px 8px',
                      }}>
                        <b>실패 사유:</b> {r.errorReason}
                      </div>
                    )}
                  </div>
                ))}
              </div>
            )}
            {autoPublishErr && <p style={{ margin: '8px 0 0', fontSize: 12, color: '#b33333' }}>{autoPublishErr}</p>}
            {/* Legacy manual record */}
            <details style={{ marginTop: 16 }}>
              <summary style={{ fontSize: 12, color: '#888', cursor: 'pointer' }}>수동 기록 (레거시)</summary>
              <div style={{ display: 'flex', gap: 8, marginTop: 8, alignItems: 'center', flexWrap: 'wrap' }}>
                <input
                  type="url"
                  placeholder="발행 URL (선택)"
                  value={publishUrl}
                  onChange={(e) => setPublishUrl(e.target.value)}
                  style={{ padding: '6px 10px', border: '1px solid #ddd', borderRadius: 6, fontSize: 13, minWidth: 220 }}
                />
                <button
                  onClick={handlePublish}
                  disabled={isPublishing}
                  style={{ padding: '7px 14px', background: '#446620', color: 'white', border: 'none', borderRadius: 6, cursor: isPublishing ? 'not-allowed' : 'pointer', fontSize: 13 }}
                >
                  {isPublishing ? '처리 중...' : '수동 발행 완료'}
                </button>
              </div>
              {publishErr && <p style={{ margin: '4px 0 0', fontSize: 12, color: '#b33333' }}>{publishErr}</p>}
            </details>
          </div>
        )}

        {/* 성과 입력 */}
        {(content.status === 'APPROVED' || content.status === 'EXPORTED') && (
          <div style={{ marginBottom: 24, padding: '16px', background: '#f9f9f9', borderRadius: 8 }}>
            <h3 style={{ fontSize: 13, fontWeight: 600, color: '#1A1A2E', margin: '0 0 12px' }}>
              성과 입력
            </h3>
            {content.performanceJson && (() => {
              try {
                const perf = JSON.parse(content.performanceJson);
                return (
                  <div style={{ marginBottom: 12, fontSize: 12, color: '#666' }}>
                    최근 기록: {perf.recordedAt ? new Date(perf.recordedAt).toLocaleString('ko-KR') : ''}
                  </div>
                );
              } catch { return null; }
            })()}
            <PerformanceForm contentId={contentId} onSaved={loadContent} />
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
