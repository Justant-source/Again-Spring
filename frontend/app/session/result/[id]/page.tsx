// ✅ MOCKUP APPLIED — source: design/handoff/tone-P-screens.jsx (ReportCards, ReportStory)
'use client';

import { useEffect, useState } from 'react';
import { useParams, useRouter, useSearchParams } from 'next/navigation';
import { useSessionStore } from '@/lib/store/sessionStore';
import { useUserStore } from '@/lib/store/userStore';
import { api } from '@/lib/api/client';
import type { Report, CommunicationStyle } from '@/lib/types';
import { PhoneFrame, PhoneHeader } from '@/components/shared/PhoneFrame';
import { ReportLayout } from '@/components/result/ReportLayout';
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
  const [error, setError] = useState<string | null>(null);
  const [variant, setVariant] = useState<'card' | 'story'>('card');
  const [shareModalOpen, setShareModalOpen] = useState(false);
  const [shareVariant, setShareVariant] = useState<'map' | 'temp' | 'style'>('map');

  // Determine names
  const myRole = sessionStore.role || 'A';
  const nameA = myRole === 'A' ? (userStore.user?.nickname || '서현') : (sessionStore.partnerNickname || '준호');
  const nameB = myRole === 'A' ? (sessionStore.partnerNickname || '준호') : (userStore.user?.nickname || '서현');

  // Determine styles
  const styleA = myRole === 'A' ? userStore.user?.communicationStyle : undefined;
  const styleB = myRole === 'B' ? userStore.user?.communicationStyle : undefined;

  useEffect(() => {
    const fetchReport = async () => {
      setLoading(true);
      setError(null);

      try {
        let res;
        const scenario = searchParams.get('scenario');

        if (scenario) {
          // Dev mode: use mock endpoint
          res = await api.get(`/mock/report?scenario=${scenario}`);
        } else {
          // Prod mode: fetch actual report
          res = await api.get(`/sessions/${sessionId}/report`);
        }

        setReport(res.data);
      } catch (err) {
        console.error('Failed to fetch report:', err);
        setError('리포트를 찾지 못했어요');
      } finally {
        setLoading(false);
      }
    };

    if (sessionId) {
      fetchReport();
    }
  }, [sessionId, searchParams]);

  const handleShareClick = () => {
    setShareModalOpen(true);
  };

  const handleCloseShareModal = () => {
    setShareModalOpen(false);
  };

  if (loading) {
    return (
      <PhoneFrame tone="P">
        <PhoneHeader title="우리의 오늘 리포트" tone="P" back={false} />
        <div style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          <div style={{ textAlign: 'center', fontSize: 14, color: 'var(--P-ink)', fontFamily: 'var(--font-serif)' }}>
            리포트를 열어보는 중…
          </div>
        </div>
      </PhoneFrame>
    );
  }

  if (error || !report) {
    return (
      <PhoneFrame tone="P">
        <PhoneHeader title="우리의 오늘 리포트" tone="P" back={false} />
        <div style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', padding: '40px 22px' }}>
          <div style={{ fontSize: 14, color: 'var(--P-ink)', marginBottom: 24, textAlign: 'center' }}>
            {error || '리포트를 찾지 못했어요'}
          </div>
          <button
            onClick={() => router.push('/')}
            className="btn-P"
          >
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
        right={
          <div style={{ display: 'flex', gap: 8, fontSize: 11 }}>
            <button
              onClick={() => setVariant('card')}
              style={{
                background: variant === 'card' ? 'var(--P-ink)' : 'transparent',
                color: variant === 'card' ? 'var(--P-card)' : 'var(--P-sub)',
                border: `1px solid ${variant === 'card' ? 'var(--P-ink)' : 'var(--P-border)'}`,
                borderRadius: 4,
                padding: '4px 8px',
                cursor: 'pointer',
                fontSize: 11,
              }}
            >
              카드
            </button>
            <button
              onClick={() => setVariant('story')}
              style={{
                background: variant === 'story' ? 'var(--P-ink)' : 'transparent',
                color: variant === 'story' ? 'var(--P-card)' : 'var(--P-sub)',
                border: `1px solid ${variant === 'story' ? 'var(--P-ink)' : 'var(--P-border)'}`,
                borderRadius: 4,
                padding: '4px 8px',
                cursor: 'pointer',
                fontSize: 11,
              }}
            >
              스토리
            </button>
          </div>
        }
      />

      <div style={{ flex: 1, overflowY: 'auto', display: 'flex', flexDirection: 'column' }}>
        <ReportLayout
          report={report}
          myRole={myRole}
          nameA={nameA}
          nameB={nameB}
          styleA={styleA}
          styleB={styleB}
          variant={variant}
        />
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
          <button onClick={handleShareClick} className="btn-P" style={{ width: '100%' }}>
            카톡으로 리포트 공유
          </button>
        </div>
        <div>본 서비스는 심리 상담이나 법률 자문을 대체하지 않습니다. 위기 상황이라면 전문기관(1393/1366/132)에 연락해주세요.</div>
      </div>

      {/* Share Modal */}
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
            <div style={{ display: 'flex', gap: 8, marginBottom: 16 }}>
              {(['map', 'temp', 'style'] as const).map((v) => (
                <button
                  key={v}
                  onClick={() => setShareVariant(v)}
                  style={{
                    flex: 1,
                    padding: '8px 12px',
                    background: shareVariant === v ? 'var(--P-ink)' : 'var(--P-card)',
                    color: shareVariant === v ? 'var(--P-card)' : 'var(--P-ink)',
                    border: `1px solid ${shareVariant === v ? 'var(--P-ink)' : 'var(--P-border)'}`,
                    borderRadius: 8,
                    fontSize: 12,
                    cursor: 'pointer',
                    fontWeight: 500,
                  }}
                >
                  {v === 'map' && '지도'}
                  {v === 'temp' && '온도'}
                  {v === 'style' && '스타일'}
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
