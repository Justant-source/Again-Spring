// Backup standalone Solo result page (in case Phase 8 ReportLayout doesn't wire branching)

'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { PhoneFrame, PhoneHeader } from '@/components/shared/PhoneFrame';
import { SoloReport } from '@/components/result/solo/SoloReport';
import { LegalFooter } from '@/components/shared/LegalFooter';
import { api } from '@/lib/api/client';
import type { Report } from '@/lib/types';

export default function SoloResultPage({ params }: { params: { id: string } }) {
  const router = useRouter();
  const [report, setReport] = useState<Report | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchReport = async () => {
      try {
        const res = await api.get(`/api/sessions/${params.id}/report`);
        const data = res.data as Report;

        // Only render if solo mode
        if (!data.isSoloMode) {
          router.push(`/session/result/${params.id}`);
          return;
        }

        setReport(data);
      } catch (err) {
        console.error('Failed to fetch report:', err);
        router.push('/');
      } finally {
        setLoading(false);
      }
    };

    fetchReport();
  }, [params.id, router]);

  if (loading) {
    return (
      <PhoneFrame tone="P">
        <PhoneHeader title="혼자 정리한 이야기" tone="P" onBack={() => router.back()} />
        <div style={{ padding: '28px', textAlign: 'center', color: 'var(--P-sub)' }}>
          로딩 중...
        </div>
      </PhoneFrame>
    );
  }

  if (!report || !report.isSoloMode) {
    return null;
  }

  return (
    <PhoneFrame tone="P">
      <PhoneHeader title="혼자 정리한 이야기" tone="P" onBack={() => router.back()} />
      <SoloReport report={report} sessionId={params.id} />
      <LegalFooter />
    </PhoneFrame>
  );
}
