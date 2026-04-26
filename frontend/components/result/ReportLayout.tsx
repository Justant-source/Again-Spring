// ✅ MOCKUP APPLIED — source: design/handoff/tone-P-screens.jsx (ReportCards, ReportStory)
'use client';

import React from 'react';
import type { Report, CommunicationStyle } from '@/lib/types';
import { ContributionRatio } from './ContributionRatio';
import { FourHorsemenObservation } from './FourHorsemenObservation';
import { NVCScript } from './NVCScript';
import { RepairSuggestions } from './RepairSuggestions';
import { NeedsMap } from './NeedsMap';
import { MetaphorCards } from './MetaphorCards';
import { calculateDistanceLabel } from '@/lib/utils/needsMapDistance';

interface ReportLayoutProps {
  report: Report;
  myRole: 'A' | 'B';
  nameA: string;
  nameB: string;
  styleA?: CommunicationStyle;
  styleB?: CommunicationStyle;
  variant: 'card' | 'story';
  onInvite?: () => void;
}

export function ReportLayout({
  report,
  myRole,
  nameA,
  nameB,
  styleA,
  styleB,
  variant,
  onInvite,
}: ReportLayoutProps) {
  const myName = myRole === 'A' ? nameA : nameB;
  const partnerName = myRole === 'A' ? nameB : nameA;

  const myScript = myRole === 'A' ? report.nvcScripts?.aToB : report.nvcScripts?.bToA;
  const partnerScript = myRole === 'A' ? report.nvcScripts?.bToA : report.nvcScripts?.aToB;

  const distanceInfo =
    report.needsMap.positionB
      ? calculateDistanceLabel(report.needsMap.positionA, report.needsMap.positionB)
      : null;

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
          {distanceInfo && (
            <div style={{ textAlign: 'center', marginTop: 18, fontSize: 13, color: 'var(--P-sub)' }}>
              두 분의 거리: {distanceInfo.emoji} <strong style={{ color: 'var(--P-ink)' }}>{distanceInfo.label}</strong>
            </div>
          )}
        </div>
      </div>
    );
  }

  // Card variant
  return (
    <div style={{ padding: '8px 22px 40px', display: 'flex', flexDirection: 'column', gap: 14 }}>
      {/* 1. Needs Map + distance label */}
      <div className="p-card">
        <div style={{ fontSize: 12, color: 'var(--P-sub)', marginBottom: 12 }}>욕구 차이 지도</div>
        <NeedsMap
          positionA={report.needsMap.positionA}
          positionB={report.needsMap.positionB}
          axisX={report.needsMap.axisX}
          axisY={report.needsMap.axisY}
          labelA={nameA}
          labelB={nameB}
          size={240}
        />
        {distanceInfo && (
          <div
            style={{
              marginTop: 16,
              textAlign: 'center',
              fontSize: 13,
              color: 'var(--P-sub)',
            }}
          >
            두 분의 거리: {distanceInfo.emoji}{' '}
            <strong style={{ color: 'var(--P-ink)' }}>{distanceInfo.label}</strong>
          </div>
        )}
        {report.needsMap.interpretation && (
          <div
            style={{
              marginTop: 10,
              fontSize: 12,
              color: 'var(--P-sub)',
              lineHeight: 1.6,
              textAlign: 'center',
            }}
          >
            {report.needsMap.interpretation}
          </div>
        )}
      </div>

      {/* 2. Metaphor Cards */}
      {(report.metaphorId || (report.metaphorCards && report.metaphorCards.length > 0)) && (
        <div className="p-card">
          <MetaphorCards
            metaphorId={report.metaphorId}
            cards={report.metaphorCards}
            mode={report.isSoloMode ? 'solo' : 'pair'}
          />
        </div>
      )}

      {/* 3. Contribution Ratio */}
      {!report.powerImbalanceDetected && (
        <div className="p-card">
          <ContributionRatio
            ratio={report.contributionRatio}
            nameA={nameA}
            nameB={nameB}
            conflictType={report.conflictType}
            isSoloMode={report.isSoloMode}
            onInvite={onInvite}
          />
        </div>
      )}

      {/* Power imbalance crisis resource */}
      {report.powerImbalanceDetected && (
        <div
          style={{
            background: '#FFF3F3',
            border: '1px solid #FFBBBB',
            borderRadius: 12,
            padding: '18px 16px',
          }}
        >
          <div style={{ fontSize: 14, fontWeight: 600, color: '#C0392B', marginBottom: 8 }}>
            ⚠️ 중요한 안내
          </div>
          <div style={{ fontSize: 13, color: '#5A2D2D', lineHeight: 1.75, marginBottom: 12 }}>
            말씀해주신 상황에는 도움이 더 필요해 보여요. AI의 분석보다 전문가의 도움이 훨씬 안전하고 정확해요.
          </div>
          <div style={{ fontSize: 13, color: '#C0392B', lineHeight: 1.9 }}>
            📞 여성긴급전화 1366 (24시간)<br />
            📞 정신건강위기상담 1577-0199<br />
            📞 자살예방상담전화 1393
          </div>
        </div>
      )}

      {/* 4. NVC Scripts */}
      {report.nvcScripts && (
        <>
          {myScript && (
            <div className="p-card">
              <NVCScript script={myScript} from={myName} to={partnerName} />
            </div>
          )}
          {!report.isSoloMode && partnerScript && (
            <div className="p-card">
              <NVCScript script={partnerScript} from={partnerName} to={myName} />
            </div>
          )}
        </>
      )}

      {/* 5. 4 Horsemen Observation */}
      {report.horsemenObservation && (
        <div className="p-card">
          <FourHorsemenObservation horsemen={report.horsemenObservation} />
        </div>
      )}

      {/* 6. Repair Suggestions */}
      {report.repairSuggestions && report.repairSuggestions.length > 0 && (
        <div className="p-card">
          <RepairSuggestions suggestions={report.repairSuggestions} />
        </div>
      )}
    </div>
  );
}
