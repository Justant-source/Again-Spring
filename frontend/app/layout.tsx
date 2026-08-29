import './globals.css';
import { Suspense } from 'react';
import type { Metadata, Viewport } from 'next';
import { MSWProvider } from '@/components/shared/MSWProvider';
import { AuthBootstrap } from '@/components/shared/AuthBootstrap';
import { AuthRedirectGuard } from '@/components/shared/AuthRedirectGuard';
import { DailyLimitModal } from '@/components/shared/DailyLimitModal';
import { FeedbackModal } from '@/components/feedback/FeedbackModal';
import { ForcePasswordChangeModal } from '@/components/auth/ForcePasswordChangeModal';
import { BetaBanner } from '@/components/shared/BetaBanner';
import { LegalFooter } from '@/components/shared/LegalFooter';
import { BottomNav } from '@/components/shared/BottomNav';
import { VisitTracker } from '@/components/VisitTracker';

export const metadata: Metadata = {
  // og:image / og:url 의 상대 경로 → 절대 URL 해소 기준점
  // NEXT_PUBLIC_APP_URL: docker-compose build arg (prod: https://againspring.net, dev: https://dev.againspring.net)
  metadataBase: new URL(process.env.NEXT_PUBLIC_APP_URL || 'https://againspring.net'),
  // 2026-08-29: 문구가 광장형 피벗 이전('AI 중재자')에 머물러 있어 실제 서비스와 달랐다.
  // 검색 이용자가 실제로 입력하는 말(사연·갈등·공감·투표)로 다시 썼다.
  title: {
    default: '다시봄 · 갈등 사연 공감 투표 커뮤니티',
    template: '%s · 다시봄',
  },
  description:
    '연인·부부·친구·가족·직장에서 생긴 갈등 사연을 올리면 사람들이 작성자와 상대방 중 '
    + '어느 쪽에 공감하는지 투표합니다. 한 사건을 양쪽 입장에서 함께 볼 수 있어요.',
  keywords: ['사연', '갈등', '공감', '커뮤니티', '연애 고민', '부부 갈등', '직장 갈등', '다시봄'],
  alternates: {
    canonical: '/',
  },
  openGraph: {
    title: '다시봄 · 갈등 사연 공감 투표 커뮤니티',
    description:
      '이 갈등, 당신은 어느 쪽에 공감하나요? 사연을 읽고 작성자와 상대방 중 한쪽에 투표해 보세요.',
    type: 'website',
    locale: 'ko_KR',
    siteName: '다시봄',
  },
  twitter: {
    card: 'summary_large_image',
    title: '다시봄 · 갈등 사연 공감 투표 커뮤니티',
    description: '이 갈등, 당신은 어느 쪽에 공감하나요?',
  },
  // 서치콘솔·서치어드바이저 소유 확인용. 값은 배포 환경변수로 주입한다.
  verification: {
    google: process.env.NEXT_PUBLIC_GOOGLE_SITE_VERIFICATION || undefined,
    other: process.env.NEXT_PUBLIC_NAVER_SITE_VERIFICATION
      ? { 'naver-site-verification': process.env.NEXT_PUBLIC_NAVER_SITE_VERIFICATION }
      : undefined,
  },
};

export const viewport: Viewport = {
  themeColor: '#F5EFE6',
  width: 'device-width',
  initialScale: 1,
  viewportFit: 'cover',
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="ko">
      <body>
        <MSWProvider>
          <AuthBootstrap />
          <AuthRedirectGuard />
          <DailyLimitModal />
          <BetaBanner />
          <FeedbackModal />
          <ForcePasswordChangeModal />
          <LegalFooter />
          <BottomNav />
          <Suspense fallback={null}>
            <VisitTracker />
          </Suspense>
          <div style={{ paddingTop: '30px', paddingBottom: '0px' }}>{children}</div>
        </MSWProvider>
      </body>
    </html>
  );
}
