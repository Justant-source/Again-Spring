'use client';

import React from 'react';
import type { Report, CommunicationStyle } from '@/lib/types';
import { SafeHaven } from '@/components/icons/SafeHaven';
import { Phone } from '@/components/icons/Phone';
import { StatusDot } from '@/components/icons/StatusDot';
import { ContributionRatio } from './ContributionRatio';
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
  sessionId?: string;
  onConvertToCommunity?: (draft: any) => void;
}

function nextStepBtn(bg: string, color: string, border: string): React.CSSProperties {
  return {
    width: '100%',
    padding: '14px 16px',
    background: bg,
    color,
    border: `1px solid ${border}`,
    borderRadius: 10,
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    textAlign: 'center',
    cursor: 'pointer',
    transition: 'opacity 0.15s',
  };
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
  sessionId,
  onConvertToCommunity,
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
              두 분의 거리: <StatusDot level={distanceInfo.level} /> <strong style={{ color: 'var(--P-ink)' }}>{distanceInfo.label}</strong>
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
            두 분의 거리: <StatusDot level={distanceInfo.level} />{' '}
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
          <div style={{ fontSize: 14, fontWeight: 600, color: '#C0392B', marginBottom: 8, display: 'flex', alignItems: 'center', gap: 6 }}>
            <SafeHaven width={16} height={16} color="#C0392B" />
            중요한 안내
          </div>
          <div style={{ fontSize: 13, color: '#5A2D2D', lineHeight: 1.75, marginBottom: 12 }}>
            말씀해주신 상황에는 도움이 더 필요해 보여요. AI의 분석보다 전문가의 도움이 훨씬 안전하고 정확해요.
          </div>
          <div style={{ fontSize: 13, color: '#C0392B', lineHeight: 2.1 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}><Phone width={14} height={14} color="#C0392B" /> 여성긴급전화 1366 (24시간)</div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}><Phone width={14} height={14} color="#C0392B" /> 정신건강위기상담 1577-0199</div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}><Phone width={14} height={14} color="#C0392B" /> 자살예방상담전화 1393</div>
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

      {/* 5. Repair Suggestions */}
      {report.repairSuggestions && report.repairSuggestions.length > 0 && (
        <div className="p-card">
          <RepairSuggestions suggestions={report.repairSuggestions} />
        </div>
      )}

      {/* 다음 단계 선택 — 커뮤니티 / 배심원 / 상대방 초대 */}
      {sessionId && onConvertToCommunity && (
        <div style={{ marginTop: 16 }}>
          <div style={{ fontSize: 12, color: 'var(--P-sub)', marginBottom: 10, fontWeight: 600, letterSpacing: 0.5 }}>
            다음 단계를 선택하세요
          </div>

          {/* 비공개 AI 배심원 */}
          <button
            data-testid="convert-to-community-btn"
            onClick={() => {
              sessionStorage.setItem('community-draft-visibility', 'PRIVATE');
              onConvertToCommunity(sessionId);
            }}
            style={nextStepBtn('#FBF3EC', 'var(--P-ink, #5C4030)', 'var(--P-border, #EADFD0)')}
          >
            <div style={{ fontSize: 22, marginBottom: 4 }}>
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
                <circle cx="12" cy="12" r="9"/><path d="M12 8v4l3 3"/>
              </svg>
            </div>
            <div style={{ fontSize: 13, fontWeight: 600 }}>AI 배심원에게 물어보기</div>
            <div style={{ fontSize: 11, marginTop: 3, opacity: 0.7 }}>비공개 · 9인 다관점 의견 · 약 3분</div>
          </button>

          {/* 공개 투표 */}
          <button
            onClick={() => {
              sessionStorage.setItem('community-draft-visibility', 'PUBLIC');
              onConvertToCommunity(sessionId);
            }}
            style={{ ...nextStepBtn('white', 'var(--L-ink, #2B2B2B)', '#e7e3d8'), marginTop: 8 }}
          >
            <div style={{ fontSize: 22, marginBottom: 4 }}>
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
                <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/>
              </svg>
            </div>
            <div style={{ fontSize: 13, fontWeight: 600 }}>커뮤니티에 공개하기</div>
            <div style={{ fontSize: 11, marginTop: 3, opacity: 0.6 }}>공개 투표 · 실제 사람들의 반응</div>
          </button>

          {/* 상대방 초대 (Duo) */}
          {onInvite && (
            <button
              onClick={onInvite}
              style={{ ...nextStepBtn('var(--L-card, #FBF6EC)', 'var(--L-ink, #2B2B2B)', 'var(--L-border, #D9CFBD)'), marginTop: 8 }}
            >
              <div style={{ fontSize: 22, marginBottom: 4 }}>
                <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
                </svg>
              </div>
              <div style={{ fontSize: 13, fontWeight: 600 }}>상대방과 직접 대화하기</div>
              <div style={{ fontSize: 11, marginTop: 3, opacity: 0.6 }}>초대 링크 · AI 중재자 참여 · Duo 모드</div>
            </button>
          )}
        </div>
      )}
    </div>
  );
}
