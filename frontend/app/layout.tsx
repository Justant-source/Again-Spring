import './globals.css';
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

export const metadata: Metadata = {
  // og:image / og:url 의 상대 경로 → 절대 URL 해소 기준점
  // NEXT_PUBLIC_APP_URL: docker-compose build arg (prod: https://againspring.net, dev: https://dev.againspring.net)
  metadataBase: new URL(process.env.NEXT_PUBLIC_APP_URL || 'https://againspring.net'),
  title: '다시봄 · Again Spring',
  description:
    '싸운 두 사람 사이에 조용히 앉는 AI 중재자. 판결이 아니라 중재입니다.',
  openGraph: {
    title: '다시봄 · Again Spring',
    description:
      '다시 봄. 다시 바라봄. 관계 회복을 돕는 AI 중재자.',
    type: 'website',
    locale: 'ko_KR',
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
          <div style={{ paddingTop: '30px', paddingBottom: '0px' }}>{children}</div>
        </MSWProvider>
      </body>
    </html>
  );
}
