'use client';

import { useEffect, useState } from 'react';
import { useParams, useRouter, useSearchParams } from 'next/navigation';
import { useSessionStore } from '@/lib/store/sessionStore';
import { useUserStore } from '@/lib/store/userStore';
import { api } from '@/lib/api/client';
import type { Report, CommunicationStyle } from '@/lib/types';
import { PhoneFrame, PhoneHeader } from '@/components/shared/PhoneFrame';
import { ReportLayout } from '@/components/result/ReportLayout';
import { SoloReport } from '@/components/result/solo/SoloReport';
import { ShareImage } from '@/components/result/ShareImage';

export default function ResultPage() {
  const params = useParams();
  const searchParams = useSearchParams();
  const router = useRouter();
  const sessionId = params.id as string;

  const sessionStore = useSessionStore();
  const userStore = useUserStore();

  const [report, setReport] = useState<Report | null>(null);
  const [loading, setLoading] = useState(true);
  const [generating, setGenerating] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [shareModalOpen, setShareModalOpen] = useState(false);
  const [shareVariant, setShareVariant] = useState<'b' | 'c' | 'd'>('c');
  const [soloCapturing, setSoloCapturing] = useState(false);
  const [soloShareModalOpen, setSoloShareModalOpen] = useState(false);
  const [soloImageUrl, setSoloImageUrl] = useState<string | null>(null);
  const [soloImageBlob, setSoloImageBlob] = useState<Blob | null>(null);
  const [convertLoading, setConvertLoading] = useState(false);

  // Determine names
  const myRole = sessionStore.role || 'A';
  const nameA = myRole === 'A' ? (userStore.user?.nickname || '서현') : (sessionStore.partnerNickname || '준호');
  const nameB = myRole === 'A' ? (sessionStore.partnerNickname || '준호') : (userStore.user?.nickname || '서현');

  // Determine styles
  const styleA = myRole === 'A' ? userStore.user?.communicationStyle : undefined;
  const styleB = myRole === 'B' ? userStore.user?.communicationStyle : undefined;

  useEffect(() => {
    let cancelled = false;
    let pollTimer: ReturnType<typeof setTimeout> | null = null;

    const fetchReport = async (attempt = 0) => {
      if (cancelled) return;
      if (attempt === 0) {
        setLoading(true);
        setError(null);
        setGenerating(false);
      }

      try {
        const scenario = searchParams.get('scenario');
        const url = scenario
          ? `/api/mock/report?scenario=${scenario}`
          : `/api/sessions/${sessionId}/report`;
        const res = await api.get(url);
        if (!cancelled) {
          setReport(res.data);
          setGenerating(false);
        }
      } catch (err: any) {
        if (cancelled) return;
        // 404 → 리포트 생성 중, 최대 20회(60초) 폴링
        if (err?.response?.status === 404 && attempt < 60) {
          setGenerating(true);
          pollTimer = setTimeout(() => fetchReport(attempt + 1), 3000);
        } else {
          setError('리포트를 불러오지 못했어요');
        }
      } finally {
        if (!cancelled && attempt === 0) setLoading(false);
      }
    };

    if (sessionId) fetchReport();

    return () => {
      cancelled = true;
      if (pollTimer) clearTimeout(pollTimer);
    };
  }, [sessionId, searchParams]);

  const handleShareClick = async () => {
    if (report?.isSoloMode) {
      setSoloCapturing(true);
      try {
        const el = document.getElementById('solo-report-shareable');
        if (!el) { setSoloCapturing(false); return; }

        // 1) Pre-rasterize all <img> in the source DOM to PNG data URLs.
        //    html2canvas frequently fails to draw SVG <img> directly.
        const sourceImgs = Array.from(el.querySelectorAll<HTMLImageElement>('img'));
        const rasterized = new Map<string, string>();
        await Promise.all(sourceImgs.map(async (img) => {
          const src = img.src;
          if (!src || src.startsWith('data:') || rasterized.has(src)) return;
          try {
            const w = (img.naturalWidth || img.width || 160);
            const h = (img.naturalHeight || img.height || 160);
            const res = await fetch(src);
            const blob = await res.blob();
            const blobUrl = URL.createObjectURL(blob);
            try {
              const tmpImg = new Image();
              await new Promise<void>((resolve, reject) => {
                tmpImg.onload = () => resolve();
                tmpImg.onerror = reject;
                tmpImg.src = blobUrl;
              });
              const scale = 2;
              const cnv = document.createElement('canvas');
              cnv.width = w * scale;
              cnv.height = h * scale;
              const ctx = cnv.getContext('2d');
              if (ctx) {
                ctx.drawImage(tmpImg, 0, 0, w * scale, h * scale);
                rasterized.set(src, cnv.toDataURL('image/png'));
              }
            } finally {
              URL.revokeObjectURL(blobUrl);
            }
          } catch {}
        }));

        const html2canvas = (await import('html2canvas')).default;
        const canvas = await html2canvas(el, {
          useCORS: true,
          allowTaint: true,
          scale: 2,
          backgroundColor: '#FBF3EC',
          onclone: async (_doc: Document, clonedEl: HTMLElement) => {
            const imgs = clonedEl.querySelectorAll<HTMLImageElement>('img');
            await Promise.all(Array.from(imgs).map(async (img) => {
              const orig = img.src;
              if (!orig || orig.startsWith('data:')) return;
              const dataUrl = rasterized.get(orig);
              if (!dataUrl) return;
              img.removeAttribute('srcset');
              img.src = dataUrl;
              if (!img.complete) {
                await new Promise<void>((resolve) => {
                  const done = () => resolve();
                  img.addEventListener('load', done, { once: true });
                  img.addEventListener('error', done, { once: true });
                });
              }
            }));
          },
        });
        canvas.toBlob((blob) => {
          if (blob) {
            setSoloImageBlob(blob);
            setSoloImageUrl(URL.createObjectURL(blob));
            setSoloShareModalOpen(true);
          }
          setSoloCapturing(false);
        }, 'image/png');
      } catch {
        setSoloCapturing(false);
      }
    } else {
      setShareModalOpen(true);
    }
  };

  const handleCloseShareModal = () => {
    setShareModalOpen(false);
  };

  const handleSoloDownload = () => {
    if (!soloImageUrl) return;
    const a = document.createElement('a');
    a.href = soloImageUrl;
    a.download = '다시봄-리포트.png';
    a.click();
  };

  const handleSoloShare = async () => {
    if (!soloImageBlob) return;
    const file = new File([soloImageBlob], '다시봄-리포트.png', { type: 'image/png' });
    if (navigator.share && navigator.canShare?.({ files: [file] })) {
      try {
        await navigator.share({ files: [file], title: '다시봄 리포트' });
      } catch {}
    } else {
      handleSoloDownload();
    }
  };

  const handleConvertToCommunity = async (sId: string) => {
    setConvertLoading(true);
    try {
      const res = await api.get(`/api/sessions/${sId}/draft-for-community`);
      const draft = res.data;
      sessionStorage.setItem('community-draft', JSON.stringify(draft));
      router.push('/community/new?from=session');
    } catch (err) {
      console.error('Failed to load draft:', err);
      alert('사연 변환 초안을 불러오지 못했어요. 다시 시도해주세요.');
      setConvertLoading(false);
    }
  };

  if (loading || generating) {
    return (
      <PhoneFrame tone="P">
        <PhoneHeader title="우리의 오늘 리포트" tone="P" back={true} onBack={() => router.push('/')} />
        <div style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 24, padding: 32 }}>
          <div className="report-spinner" />
          <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 8 }}>
            <div
              className={generating ? 'report-generating-text' : undefined}
              style={{ fontSize: 15, color: 'var(--P-ink)', fontFamily: 'var(--font-serif)', textAlign: 'center' }}
            >
              {generating ? '리포트를 생성하고 있어요…' : '리포트를 열어보는 중…'}
            </div>
            <div style={{ fontSize: 13, color: 'var(--P-sub)', textAlign: 'center', lineHeight: 1.7 }}>
              {generating
                ? <>AI가 대화를 분석하고 있어요.<br />잠시만 기다려주세요.</>
                : '잠시만 기다려주세요.'}
            </div>
          </div>
        </div>
      </PhoneFrame>
    );
  }

  const isFailedReport = report?.llmProvider === 'error-fallback';

  if (error || !report || isFailedReport) {
    return (
      <PhoneFrame tone="P">
        <PhoneHeader title="우리의 오늘 리포트" tone="P" back={true} onBack={() => router.push('/')} />
        <div style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', padding: '40px 22px', gap: 12 }}>
          <div style={{ fontSize: 14, color: 'var(--P-ink)', marginBottom: 12, textAlign: 'center' }}>
            {isFailedReport
              ? '리포트 생성에 실패했어요.\n잠시 후 다시 시도해 주세요.'
              : error || '리포트를 찾지 못했어요'}
          </div>
          <button onClick={() => router.push('/history')} className="btn-P" style={{ width: '100%' }}>
            대화기록 보기
          </button>
          <button onClick={() => router.push('/')} className="btn-P ghost" style={{ width: '100%' }}>
            홈으로 돌아가기
          </button>
        </div>
      </PhoneFrame>
    );
  }

  return (
    <PhoneFrame tone="P" className="relative">
      <PhoneHeader
        title="우리의 오늘 리포트"
        tone="P"
        back={true}
        onBack={() => router.back()}
      />

      <div style={{ flex: 1, overflowY: 'auto', display: 'flex', flexDirection: 'column' }}>
        {report.isSoloMode ? (
          <SoloReport report={report} sessionId={sessionId} />
        ) : (
          <ReportLayout
            report={report}
            myRole={myRole}
            nameA={nameA}
            nameB={nameB}
            styleA={styleA}
            styleB={styleB}
            variant="card"
            sessionId={sessionId}
            onConvertToCommunity={handleConvertToCommunity}
            onInvite={() => router.push(`/session/chat/${sessionId}?invite=1`)}
          />
        )}
      </div>

      {/* Fixed bottom footer */}
      <div
        style={{
          padding: '16px 22px 12px',
          borderTop: '1px solid var(--P-border)',
          background: 'var(--P-bg)',
          fontSize: 11,
          color: 'var(--P-sub)',
          lineHeight: 1.5,
          textAlign: 'center',
        }}
      >
        <div style={{ marginBottom: 12 }}>
          <button
            onClick={handleShareClick}
            className="btn-P"
            style={{ width: '100%' }}
            disabled={soloCapturing}
          >
            {soloCapturing ? '이미지 생성 중…' : '리포트 공유하기'}
          </button>
        </div>
        <div>본 서비스는 심리 상담이나 법률 자문을 대체하지 않습니다. 위기 상황이라면 전문기관(1393/1366/132)에 연락해주세요.</div>
      </div>

      {/* Solo Share Modal */}
      {soloShareModalOpen && soloImageUrl && (
        <div
          style={{
            position: 'fixed',
            inset: 0,
            background: 'rgba(0,0,0,0.5)',
            display: 'flex',
            alignItems: 'flex-end',
            justifyContent: 'center',
            zIndex: 999,
          }}
          onClick={() => setSoloShareModalOpen(false)}
        >
          <div
            onClick={(e) => e.stopPropagation()}
            style={{
              background: 'var(--P-bg)',
              borderRadius: '20px 20px 0 0',
              padding: '24px',
              width: '100%',
              maxWidth: 480,
              maxHeight: '80vh',
              overflowY: 'auto',
            }}
          >
            <div style={{ fontSize: 14, fontWeight: 500, color: 'var(--P-ink)', marginBottom: 16 }}>
              리포트 이미지
            </div>
            <div
              style={{
                borderRadius: 12,
                overflow: 'hidden',
                marginBottom: 20,
                border: '1px solid var(--P-border)',
              }}
            >
              <img src={soloImageUrl} style={{ width: '100%', display: 'block' }} alt="리포트 캡처" />
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              <button className="btn-P" style={{ width: '100%' }} onClick={handleSoloDownload}>
                이미지 저장
              </button>
              <button className="btn-P" style={{ width: '100%' }} onClick={handleSoloShare}>
                공유하기
              </button>
              <button
                style={{
                  width: '100%',
                  padding: '12px',
                  background: 'transparent',
                  color: 'var(--P-sub)',
                  border: 'none',
                  fontSize: 14,
                  cursor: 'pointer',
                }}
                onClick={() => setSoloShareModalOpen(false)}
              >
                닫기
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Duo Share Modal */}
      {shareModalOpen && (
        <div
          style={{
            position: 'fixed',
            inset: 0,
            background: 'rgba(0,0,0,0.5)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            zIndex: 999,
          }}
          onClick={handleCloseShareModal}
        >
          <div
            onClick={(e) => e.stopPropagation()}
            style={{
              background: 'var(--P-bg)',
              borderRadius: 20,
              padding: '24px',
              maxWidth: 340,
              width: '90vw',
              maxHeight: '80vh',
              overflowY: 'auto',
            }}
          >
            <div style={{ fontSize: 14, fontWeight: 500, color: 'var(--P-ink)', marginBottom: 16 }}>
              리포트 공유하기
            </div>

            {/* Share variant tabs */}
            <div style={{ display: 'flex', gap: 6, marginBottom: 16 }}>
              {([
                { id: 'c', label: '비유' },
                { id: 'b', label: '4문장' },
                { id: 'd', label: '균형' },
              ] as const).map((v) => (
                <button
                  key={v.id}
                  onClick={() => setShareVariant(v.id)}
                  style={{
                    flex: 1,
                    padding: '8px 0',
                    fontSize: 12,
                    background: shareVariant === v.id ? 'var(--P-ink)' : 'transparent',
                    color: shareVariant === v.id ? 'var(--P-bg)' : 'var(--P-sub)',
                    border: `1px solid ${shareVariant === v.id ? 'var(--P-ink)' : 'var(--P-border)'}`,
                    borderRadius: 8,
                    cursor: 'pointer',
                  }}
                >
                  {v.label}
                </button>
              ))}
            </div>

            {/* Preview */}
            <div style={{ display: 'flex', justifyContent: 'center', marginBottom: 16 }}>
              <ShareImage
                variant={shareVariant}
                report={report}
                styleA={styleA}
                styleB={styleB}
                nameA={nameA}
                nameB={nameB}
              />
            </div>

            {/* Action buttons */}
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              <button className="btn-P" style={{ width: '100%' }} onClick={() => alert('이미지 저장 기능 (준비 중)')}>
                이미지 저장
              </button>
              <button className="btn-P" style={{ width: '100%' }} onClick={() => alert('링크 복사 기능 (준비 중)')}>
                링크 복사
              </button>
              <button
                className="btn-P ghost"
                style={{ width: '100%' }}
                onClick={() => alert('PDF로 보관 기능은 Premium에서 열려요')}
              >
                PDF로 보관
              </button>
              <button
                style={{
                  width: '100%',
                  padding: '12px',
                  background: 'transparent',
                  color: 'var(--P-sub)',
                  border: 'none',
                  fontSize: 14,
                  cursor: 'pointer',
                }}
                onClick={handleCloseShareModal}
              >
                닫기
              </button>
            </div>
          </div>
        </div>
      )}
    </PhoneFrame>
  );
}
