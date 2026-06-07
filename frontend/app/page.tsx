'use client';

import { useRouter } from 'next/navigation';
import { useEffect, useState } from 'react';
import { Footer } from '@/components/shared/Footer';
import { BrandBar } from '@/components/community/c3';
import { postApi, PostSummary } from '@/lib/api/community/postApi';
import { useUserStore } from '@/lib/store/userStore';
import { useGuestInit } from '@/lib/hooks/useGuestInit';
import { permissionsFor } from '@/lib/constants/userPermissions';

// BE PostCategory → 한글 라벨
const CAT_LABEL: Record<string, string> = {
  COUPLE: '연인', MARRIED: '부부', FRIEND: '친구',
  FAMILY: '가족', WORK: '직장', OTHER: '기타',
};
function getCatLabel(id: string) {
  return CAT_LABEL[(id || '').toUpperCase()] ?? '기타';
}

// ISO 날짜 → KST YYYY-MM-DD
function kstDate(iso: string) {
  return new Date(iso).toLocaleDateString('en-CA', { timeZone: 'Asia/Seoul' });
}

export default function LandingPage() {
  const router = useRouter();
  const user = useUserStore((s) => s.user);
  const [mounted, setMounted] = useState(false);
  const [latestPost, setLatestPost] = useState<PostSummary | null>(null);
  const [todayPost, setTodayPost]   = useState<PostSummary | null>(null);
  useGuestInit();

  useEffect(() => {
    setMounted(true);

    // 방금 올라온 사연 = 최신순 1건
    postApi.list({ sort: 'latest', size: 1 })
      .then(r => setLatestPost(r.content[0] ?? null))
      .catch(() => {});

    // 오늘의 사연 = 추천순 목록에서 오늘(KST) 작성 첫 글 / 없으면 전체 추천 1위
    postApi.list({ sort: 'recommended', size: 20 })
      .then(r => {
        const today = new Date().toLocaleDateString('en-CA', { timeZone: 'Asia/Seoul' });
        const pick = r.content.find(p => kstDate(p.createdAt) === today)
                  ?? r.content[0]
                  ?? null;
        setTodayPost(pick);
      })
      .catch(() => {});
  }, []);

  if (!mounted) return null;

  const perms = permissionsFor(user);
  const showAdminEntry = perms.ui.showAdminEntryButton;
  const showMarketingEntry = perms.admin.canAccessMarketing;

  return (
    <div style={{ background: 'var(--L-bg)', minHeight: '100dvh', display: 'flex', flexDirection: 'column' }}>
      <div className="flex flex-col flex-1 px-7 pt-6 pb-5" style={{ maxWidth: 640, margin: '0 auto', width: '100%' }}>
        {/* 헤더: 다시봄 + 우측 유저 칩 */}
        <BrandBar title="다시봄" user={user} />

        {/* 관리자 모드 진입 카드 — user-permissions.json의 ui.showAdminEntryButton */}
        {showAdminEntry && (
          <button
            onClick={() => router.push('/admin')}
            style={{
              marginTop: 16,
              width: '100%',
              padding: '14px 18px',
              background: 'var(--L-ink)',
              color: 'var(--L-card)',
              border: 'none',
              borderRadius: 8,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              cursor: 'pointer',
              textAlign: 'left',
            }}
          >
            <div>
              <div style={{ fontSize: 13, fontWeight: 600, letterSpacing: 0.3 }}>
                관리자 모드
              </div>
              <div style={{ fontSize: 11, marginTop: 3, opacity: 0.75 }}>
                대시보드 · 의견함 · 사용자 · 위기 모니터링
              </div>
            </div>
            <span style={{ fontSize: 18, opacity: 0.85 }}>›</span>
          </button>
        )}

        {/* 마케팅 모드 진입 카드 — admin.canAccessMarketing (dev 전용) */}
        {showMarketingEntry && (
          <button
            onClick={() => router.push('/admin/marketing')}
            style={{
              marginTop: 8,
              width: '100%',
              padding: '14px 18px',
              background: '#2d4a7a',
              color: 'white',
              border: 'none',
              borderRadius: 8,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              cursor: 'pointer',
              textAlign: 'left',
            }}
          >
            <div>
              <div style={{ fontSize: 13, fontWeight: 600, letterSpacing: 0.3 }}>
                마케팅 모드
              </div>
              <div style={{ fontSize: 11, marginTop: 3, opacity: 0.75 }}>
                사연 · 시뮬레이션 · 콘텐츠 생성 · 비용
              </div>
            </div>
            <span style={{ fontSize: 18, opacity: 0.85 }}>›</span>
          </button>
        )}

        {/* C3 광장형 랜딩 본문 */}
        <div className="mt-8 flex flex-col flex-1">
          <div className="text-[12px] mb-2.5" style={{ color: 'var(--L-sub)' }}>
            혼자 끙끙 앓지 마세요
          </div>
          <h1
            className="serif"
            style={{
              fontSize: 30,
              lineHeight: 1.35,
              letterSpacing: '-0.01em',
              marginBottom: 16,
            }}
          >
            나의 갈등,<br />혼자 판단하기<br />어려울 때.
          </h1>
          {/* 방금 올라온 사연 — 최신글 제목 1줄 알약 */}
          {latestPost && (
            <button
              data-testid="landing-latest-pill"
              onClick={() => router.push('/community/' + latestPost.id)}
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: 8,
                alignSelf: 'flex-start',
                maxWidth: '100%',
                padding: '9px 14px',
                borderRadius: 999,
                background: 'var(--L-card)',
                border: '1px solid var(--L-border)',
                cursor: 'pointer',
                marginBottom: 22,
                textAlign: 'left',
              }}
            >
              <span style={{ width: 6, height: 6, borderRadius: '50%', background: 'var(--faction-author)', flexShrink: 0 }} />
              <span style={{ fontSize: 12.5, color: 'var(--L-ink)', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                방금 올라온 사연 ·{' '}
                <span style={{ color: 'var(--L-sub)' }}>〈{latestPost.title}〉</span>
              </span>
            </button>
          )}

          {/* 오늘의 사연 — 추천순 1위 광장형 박스 */}
          {todayPost && (
            <>
              <div style={{ fontSize: 12.5, color: 'var(--L-sub)', marginBottom: 10 }}>오늘의 사연</div>
              <button
                data-testid="landing-today-card"
                onClick={() => router.push('/community/' + todayPost.id)}
                style={{
                  display: 'block',
                  width: '100%',
                  background: 'var(--L-card)',
                  border: '1px solid var(--L-border)',
                  borderRadius: 14,
                  padding: '17px 18px',
                  cursor: 'pointer',
                  textAlign: 'left',
                  marginBottom: 22,
                }}
              >
                <span style={{ display: 'inline-block', fontSize: 11.5, color: 'var(--L-bg)', background: 'var(--L-ink)', borderRadius: 999, padding: '3px 11px' }}>
                  {getCatLabel(todayPost.category)}
                </span>
                <div className="serif" style={{ fontSize: 17, fontWeight: 500, color: 'var(--L-ink)', marginTop: 12, lineHeight: 1.4 }}>
                  {todayPost.title}
                </div>
                {todayPost.bodyPublished && (
                  <div style={{ fontSize: 13, color: 'var(--L-sub)', lineHeight: 1.6, marginTop: 6, display: '-webkit-box', WebkitLineClamp: 1, WebkitBoxOrient: 'vertical', overflow: 'hidden' }}>
                    {todayPost.bodyPublished}
                  </div>
                )}
                <div style={{ display: 'flex', height: 9, borderRadius: 999, overflow: 'hidden', marginTop: 16 }}>
                  <div style={{ width: (todayPost.authorPct ?? 50) + '%', background: 'var(--faction-author)' }} />
                  <div style={{ flex: 1, background: 'var(--faction-partner)' }} />
                </div>
                <div style={{ fontSize: 12, color: 'var(--L-sub)', marginTop: 10 }}>
                  {todayPost.viewCount ? todayPost.viewCount.toLocaleString('ko-KR') : 0}명이 함께 봤어요
                </div>
              </button>
            </>
          )}

          {/* 스페이서 */}
          <div style={{ flex: '1 0 0', maxHeight: 24 }} />

          {/* 권유 카피 */}
          <p
            className="text-[13.5px]"
            style={{
              color: 'var(--L-sub)',
              lineHeight: 1.7,
              marginBottom: 16,
            }}
          >
            비슷한 일, 당신도 있죠?<br />
            혼자 묻어두지 말고 올려보세요.
          </p>

          {/* 하단 버튼 */}
          <button
            data-testid="landing-cta"
            onClick={() => router.push('/community')}
            style={{
              width: '100%',
              padding: '12px 0',
              background: 'var(--L-ink)',
              color: 'var(--L-card)',
              border: 'none',
              borderRadius: 6,
              fontSize: 14,
              fontWeight: 600,
              cursor: 'pointer',
            }}
          >
            다시봄 광장
          </button>
        </div>
      </div>
      <Footer />
    </div>
  );
}
