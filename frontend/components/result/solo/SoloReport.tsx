'use client';

import Image from 'next/image';
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
    <div style={{ padding: '8px 22px 36px', display: 'flex', flexDirection: 'column', gap: 20 }}>

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
            <Image
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

      {/* Footer: restart */}
      <div style={{ paddingTop: 8, textAlign: 'center' }}>
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
