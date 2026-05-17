'use client';

import { useRouter } from 'next/navigation';
import { getMetaphorById, getMetaphorImagePath } from '@/lib/constants/metaphors';
import type { Report } from '@/lib/types';
import { SoloStageFlowSection } from './SoloStageFlow';
import { SoloFailedState } from './SoloFailedState';

interface SoloReportProps {
  report: Report;
  sessionId: string;
}

export function SoloReport({ report, sessionId }: SoloReportProps) {
  const router = useRouter();

  if (report.status === 'FAILED') {
    return <SoloFailedState sessionId={sessionId} />;
  }

  const metaphor = report.metaphorId ? getMetaphorById(report.metaphorId) : null;

  const hasNvc =
    report.nvcObservation ||
    report.nvcFeeling ||
    report.nvcNeed ||
    report.nvcRequest;

  const hasActions = report.recommendedActions && report.recommendedActions.length > 0;

  return (
    <div>
      {/* Shareable capture area — all cards, no buttons */}
      <div
        id="solo-report-shareable"
        style={{ padding: '8px 22px 20px', background: 'var(--P-bg)', display: 'flex', flexDirection: 'column', gap: 20 }}
      >
        {/* Watermark header */}
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            paddingTop: 6,
            paddingBottom: 14,
            borderBottom: '1px solid var(--P-border)',
          }}
        >
          <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
            <span
              className="serif"
              style={{ fontSize: 18, fontWeight: 700, color: 'var(--P-ink)', letterSpacing: '-0.01em' }}
            >
              다시봄
            </span>
            <span style={{ fontSize: 10, color: 'var(--P-sub)', letterSpacing: '0.06em' }}>
              againspring.net
            </span>
          </div>
          <span style={{ fontSize: 11, color: 'var(--P-sub)' }}>마음 정리 리포트</span>
        </div>

        {/* Core Summary */}
        {report.coreSummary && (
          <div
            className="p-card"
            style={{ padding: '18px 20px' }}
          >
            <div style={{ fontSize: 12, color: 'var(--P-sub)', marginBottom: 10 }}>
              핵심 정리
            </div>
            <div
              className="serif"
              style={{
                fontSize: 15,
                lineHeight: 1.8,
                color: 'var(--P-ink)',
              }}
            >
              {report.coreSummary}
            </div>
          </div>
        )}

        {/* 4-Stage Flow */}
        {report.fourStageFlow && report.fourStageFlow.length > 0 && (
          <div className="p-card" style={{ padding: '18px 20px' }}>
            <SoloStageFlowSection stages={report.fourStageFlow} />
          </div>
        )}

        {/* Metaphor */}
        {metaphor && (
          <div className="p-card" style={{ padding: '18px 20px' }}>
            <div style={{ fontSize: 12, color: 'var(--P-sub)', marginBottom: 14 }}>
              지금 마음은
            </div>
            <div
              style={{
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                gap: 14,
              }}
            >
              {/* Plain img so html2canvas can capture it reliably */}
              {/* eslint-disable-next-line @next/next/no-img-element */}
              <img
                src={getMetaphorImagePath(metaphor.filename)}
                alt={metaphor.label}
                width={160}
                height={160}
                style={{ display: 'block' }}
              />
              <div
                className="serif"
                style={{ fontSize: 18, textAlign: 'center', lineHeight: 1.4, color: 'var(--P-ink)' }}
              >
                <strong>{metaphor.label}</strong> 같아요
              </div>
              <div
                style={{
                  fontSize: 13,
                  color: 'var(--P-sub)',
                  textAlign: 'center',
                  lineHeight: 1.7,
                  maxWidth: 220,
                }}
              >
                {report.metaphorReason || metaphor.meaning}
              </div>
            </div>
          </div>
        )}

        {/* NVC Reflection */}
        {hasNvc && (
          <div className="p-card" style={{ padding: '18px 20px' }}>
            <div style={{ fontSize: 12, color: 'var(--P-sub)', marginBottom: 14 }}>
              마음의 언어로 정리하면
            </div>
            <div
              style={{
                display: 'flex',
                flexDirection: 'column',
                gap: 14,
                fontFamily: 'var(--font-serif)',
                fontSize: 14,
                lineHeight: 1.85,
              }}
            >
              {report.nvcObservation && (
                <NvcRow label="관찰" text={report.nvcObservation} />
              )}
              {report.nvcFeeling && (
                <NvcRow label="느낌" text={report.nvcFeeling} />
              )}
              {report.nvcNeed && (
                <NvcRow label="욕구" text={report.nvcNeed} />
              )}
              {report.nvcRequest && (
                <NvcRow label="부탁" text={report.nvcRequest} />
              )}
            </div>
          </div>
        )}

        {/* Recommended Actions */}
        {hasActions && (
          <div className="p-card" style={{ padding: '18px 20px' }}>
            <div style={{ fontSize: 12, color: 'var(--P-sub)', marginBottom: 14 }}>
              다음 행동
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
              {report.recommendedActions!.map((item, i) => (
                <div
                  key={i}
                  style={{
                    display: 'flex',
                    gap: 10,
                    alignItems: 'flex-start',
                  }}
                >
                  <div
                    style={{
                      width: 20,
                      height: 20,
                      borderRadius: 6,
                      border: item.isUserChosen
                        ? '2px solid var(--P-ink)'
                        : '1px solid var(--P-border)',
                      background: item.isUserChosen ? 'var(--P-ink)' : 'transparent',
                      flexShrink: 0,
                      marginTop: 2,
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                    }}
                  >
                    {item.isUserChosen && (
                      <svg width="11" height="9" viewBox="0 0 11 9" fill="none">
                        <path
                          d="M1 4L4 7.5L10 1"
                          stroke="var(--P-card)"
                          strokeWidth="1.8"
                          strokeLinecap="round"
                          strokeLinejoin="round"
                        />
                      </svg>
                    )}
                  </div>
                  <div>
                    <div
                      style={{
                        fontSize: 13,
                        color: 'var(--P-ink)',
                        lineHeight: 1.6,
                        fontWeight: item.isUserChosen ? 500 : 400,
                      }}
                    >
                      {item.action}
                    </div>
                    {item.rationale && (
                      <div style={{ fontSize: 11, color: 'var(--P-sub)', marginTop: 2, lineHeight: 1.5 }}>
                        {item.rationale}
                      </div>
                    )}
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* External Resource Guidance */}
        {report.externalResourceGuidance && (
          <div
            style={{
              padding: '14px 16px',
              background: 'var(--P-card)',
              border: '1px solid var(--P-border)',
              borderRadius: 12,
            }}
          >
            <div style={{ fontSize: 11, color: 'var(--P-sub)', marginBottom: 6 }}>
              전문 도움
            </div>
            <div style={{ fontSize: 13, color: 'var(--P-ink)', lineHeight: 1.6 }}>
              {report.externalResourceGuidance.resource}
            </div>
            {report.externalResourceGuidance.rationale && (
              <div style={{ fontSize: 11, color: 'var(--P-sub)', marginTop: 4, lineHeight: 1.5 }}>
                {report.externalResourceGuidance.rationale}
              </div>
            )}
          </div>
        )}
      </div>

      {/* 법적 안내 박스 — 절대 불변 규칙 #5: 항상 표시, 숨기거나 조건부 처리 금지 */}
      <div
        data-testid="ratio-legal-notice"
        style={{
          margin: '0 22px 20px',
          background: 'color-mix(in srgb, var(--P-sub) 6%, transparent)',
          border: '1px solid color-mix(in srgb, var(--P-sub) 15%, transparent)',
          borderRadius: 10,
          padding: '12px 14px',
          fontSize: 12,
          color: 'var(--P-sub)',
          lineHeight: 1.7,
        }}
      >
        이 리포트는 한 분의 관점을 바탕으로 한 참고용이에요.
        법적 판단이나 과실 비율과는 무관하며, AI 분석에는 한계가 있어요.
        깊은 갈등은 전문 상담을 권해드려요.
      </div>

      {/* Footer: restart — outside shareable area */}
      <div style={{ padding: '8px 22px 36px', textAlign: 'center' }}>
        <button
          onClick={() => router.push('/session/new')}
          style={{
            background: 'transparent',
            border: '1px solid var(--P-border)',
            borderRadius: 10,
            padding: '10px 20px',
            fontSize: 13,
            color: 'var(--P-sub)',
            cursor: 'pointer',
          }}
        >
          다시 정리하기
        </button>
      </div>
    </div>
  );
}

function NvcRow({ label, text }: { label: string; text: string }) {
  return (
    <div style={{ display: 'flex', gap: 10, alignItems: 'flex-start' }}>
      <div
        style={{
          fontSize: 11,
          fontWeight: 600,
          color: 'var(--P-sub)',
          width: 28,
          flexShrink: 0,
          paddingTop: 3,
        }}
      >
        {label}
      </div>
      <div style={{ color: 'var(--P-ink)', flex: 1 }}>{text}</div>
    </div>
  );
}
