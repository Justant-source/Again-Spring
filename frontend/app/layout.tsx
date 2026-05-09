import './globals.css';
import type { Metadata, Viewport } from 'next';
import { MSWProvider } from '@/components/shared/MSWProvider';
import { AuthBootstrap } from '@/components/shared/AuthBootstrap';
import { DailyLimitModal } from '@/components/shared/DailyLimitModal';
import { FeedbackModal } from '@/components/feedback/FeedbackModal';
import { ConsentReconfirmModal } from '@/components/legal/ConsentReconfirmModal';
import { BetaBanner } from '@/components/shared/BetaBanner';
import { LegalFooter } from '@/components/shared/LegalFooter';

export const metadata: Metadata = {
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
          <DailyLimitModal />
          <BetaBanner />
          <FeedbackModal />
          <ConsentReconfirmModal />
          <LegalFooter />
          <div style={{ paddingTop: '30px' }}>{children}</div>
        </MSWProvider>
      </body>
    </html>
  );
}
