// ✅ MOCKUP APPLIED — source: design/handoff/tone-P-screens.jsx (ReportCards, ReportStory)
'use client';

import React from 'react';
import type { Report, CommunicationStyle } from '@/lib/types';
import { ContributionRatio } from './ContributionRatio';
import { NVCScript } from './NVCScript';
import { RepairSuggestions } from './RepairSuggestions';
import { NeedsMap } from './NeedsMap';

interface ReportLayoutProps {
  report: Report;
  myRole: 'A' | 'B';
  nameA: string;
  nameB: string;
  styleA?: CommunicationStyle;
  styleB?: CommunicationStyle;
  variant: 'card' | 'story';
}

export function ReportLayout({
  report,
  myRole,
  nameA,
  nameB,
  styleA,
  styleB,
  variant,
}: ReportLayoutProps) {
  const myName = myRole === 'A' ? nameA : nameB;
  const partnerName = myRole === 'A' ? nameB : nameA;

  // Determine NVC script order: show my perspective first, then partner's
  const myScript = myRole === 'A' ? report.nvcScripts?.aToB : report.nvcScripts?.bToA;
  const partnerScript = myRole === 'A' ? report.nvcScripts?.bToA : report.nvcScripts?.aToB;

  if (variant === 'story') {
    return (
      <div style={{ padding: '40px 26px 28px', display: 'flex', flexDirection: 'column', gap: 24 }}>
        {/* Date & Headline */}
        <div>
          <div style={{ fontSize: 11, color: 'var(--P-sub)' }}>
            {new Date(report.createdAt).toLocaleDateString('ko-KR')}
          </div>
          <div className="serif" style={{ fontSize: 28, lineHeight: 1.35, marginTop: 10, fontWeight: 500, color: 'var(--P-ink)' }}>
            오늘,
            <br />
            두 분의 이야기는
            <br />
            따뜻하게 마무리되었어요.
          </div>
        </div>

        {/* Conflict type section */}
        {report.conflictType && (
          <div style={{ padding: '28px 26px', margin: '-20px -26px 0' }}>
            <div style={{ fontSize: 12, color: 'var(--P-sub)' }}>갈등 유형</div>
            <div className="serif" style={{ fontSize: 22, marginTop: 8, color: 'var(--P-ink)' }}>
              {report.conflictType === 'factual' && '사실 차이형'}
              {report.conflictType === 'difference' && '차이형'}
              {report.conflictType === 'mixed' && '혼합형'}
            </div>
            <div style={{ fontSize: 14, color: 'var(--P-ink)', lineHeight: 1.8, marginTop: 10 }}>
              누구의 잘못이라기보다, 두 분이 서로 다른 방식으로 필요로 하는 것들이 있었어요.
            </div>
          </div>
        )}

        {/* Needs Map */}
        <div style={{ padding: '20px 26px 40px', margin: '-20px -26px 0' }}>
          <NeedsMap
            positionA={report.needsMap.positionA}
            positionB={report.needsMap.positionB}
            axisX={report.needsMap.axisX}
            axisY={report.needsMap.axisY}
            labelA={nameA}
            labelB={nameB}
            size={260}
          />
        </div>
      </div>
    );
  }

  // Card variant (default)
  return (
    <div style={{ padding: '8px 22px 40px', display: 'flex', flexDirection: 'column', gap: 14 }}>
      {/* Contribution Ratio card */}
      {report.contributionRatio && (
        <div className="p-card">
          <ContributionRatio ratio={report.contributionRatio} nameA={nameA} nameB={nameB} />
        </div>
      )}

      {/* NVC Scripts cards */}
      {report.nvcScripts && (
        <>
          {myScript && (
            <div className="p-card">
              <NVCScript script={myScript} from={myName} to={partnerName} />
            </div>
          )}
          {partnerScript && (
            <div className="p-card">
              <NVCScript script={partnerScript} from={partnerName} to={myName} />
            </div>
          )}
        </>
      )}

      {/* Repair Suggestions card */}
      {report.repairSuggestions && report.repairSuggestions.length > 0 && (
        <div className="p-card">
          <RepairSuggestions suggestions={report.repairSuggestions} />
        </div>
      )}

      {/* Share CTA */}
      <button className="btn-P" style={{ marginTop: 4 }}>
        카톡으로 리포트 공유
      </button>
    </div>
  );
}
