// ✅ MOCKUP APPLIED — source: design/handoff/tone-P-screens.jsx (SoloResult)

'use client';

import { useRouter } from 'next/navigation';
import { useUserStore } from '@/lib/store/userStore';
import { useSessionStore } from '@/lib/store/sessionStore';
import {
  IconEye,
  IconDrop,
  IconNeed,
  IconAsk,
  IconMap,
  STYLE_MOTIF,
} from '@/components/shared/Motif';
import { COMMUNICATION_STYLES } from '@/lib/constants/communicationStyles';
import type { Report } from '@/lib/types';

export function SoloResult({ report }: { report: Report }) {
  const router = useRouter();
  const user = useUserStore((s) => s.user);
  const sessionId = useSessionStore((s) => s.sessionId);
  const partnerNickname = useSessionStore((s) => s.partnerNickname);

  if (!report.isSoloMode) return null;

  // Get user's communication style
  const style = user?.communicationStyle
    ? COMMUNICATION_STYLES[user.communicationStyle]
    : null;

  const MotifComponent = user?.communicationStyle
    ? STYLE_MOTIF[user.communicationStyle]
    : null;

  const handleInviteClick = () => {
    router.push(`/session/invite`);
  };

  return (
    <div style={{ padding: '8px 22px 28px', display: 'flex', flexDirection: 'column', gap: 14 }}>
      {/* Banner: one-sided analysis */}
      <div
        style={{
          padding: '10px 14px',
          background: 'var(--P-card)',
          border: '1px solid var(--P-border)',
          borderRadius: 12,
          fontSize: 12,
          color: 'var(--P-sub)',
          display: 'flex',
          alignItems: 'center',
          gap: 8,
        }}
      >
        <span
          style={{
            width: 6,
            height: 6,
            borderRadius: '50%',
            background: 'var(--P-a)',
          }}
        />
        한쪽 분석 · 완전한 리포트는 상대가 참여하면 완성돼요
      </div>

      {/* NVC Breakdown Card */}
      <div
        className="p-card"
        style={{
          padding: '20px',
        }}
      >
        <div style={{ fontSize: 12, color: 'var(--P-sub)', marginBottom: 12 }}>
          {user?.nickname || '님'}님 입장에서의 정리
        </div>
        <div
          style={{
            fontFamily: 'var(--font-serif)',
            fontSize: 14,
            lineHeight: 1.9,
            display: 'flex',
            flexDirection: 'column',
            gap: 14,
          }}
        >
          {/* Observation */}
          <div style={{ display: 'flex', gap: 10, alignItems: 'flex-start' }}>
            <span style={{ color: 'var(--P-sub)', marginTop: 2 }}>
              <IconEye size={15} />
            </span>
            <div>
              <b style={{ fontWeight: 500 }}>관찰</b> ·{' '}
              {report.nvcScripts?.aToB?.observation ||
                '상황을 객관적으로 보셨어요'}
            </div>
          </div>

          {/* Feeling */}
          <div style={{ display: 'flex', gap: 10, alignItems: 'flex-start' }}>
            <span style={{ color: 'var(--P-sub)', marginTop: 2 }}>
              <IconDrop size={15} />
            </span>
            <div>
              <b style={{ fontWeight: 500 }}>느낌</b> ·{' '}
              {report.nvcScripts?.aToB?.feeling ||
                '그때의 감정이 잘 정리되었어요'}
            </div>
          </div>

          {/* Need */}
          <div style={{ display: 'flex', gap: 10, alignItems: 'flex-start' }}>
            <span style={{ color: 'var(--P-sub)', marginTop: 2 }}>
              <IconNeed size={15} />
            </span>
            <div>
              <b style={{ fontWeight: 500 }}>욕구</b> ·{' '}
              {report.nvcScripts?.aToB?.need ||
                '진정한 필요가 드러났어요'}
            </div>
          </div>

          {/* Request */}
          <div style={{ display: 'flex', gap: 10, alignItems: 'flex-start' }}>
            <span style={{ color: 'var(--P-sub)', marginTop: 2 }}>
              <IconAsk size={15} />
            </span>
            <div>
              <b style={{ fontWeight: 500 }}>부탁</b> ·{' '}
              {report.nvcScripts?.aToB?.request ||
                '건설적인 요청이 있으셨어요'}
            </div>
          </div>
        </div>
      </div>

      {/* Style Card */}
      {style && MotifComponent && (
        <div className="p-card">
          <div style={{ fontSize: 12, color: 'var(--P-sub)', marginBottom: 8 }}>
            당신의 대화 스타일
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            <div
              style={{
                width: 48,
                height: 48,
                borderRadius: '50%',
                background: 'var(--P-a)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                color: '#5C4030',
              }}
            >
              <MotifComponent size={24} />
            </div>
            <div>
              <div
                className="serif"
                style={{ fontSize: 16, color: 'var(--P-ink)', fontWeight: 500 }}
              >
                {style.label}
              </div>
              <div style={{ fontSize: 12, color: 'var(--P-sub)' }}>
                {style.description}
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Needs Map Placeholder */}
      <div
        className="p-card"
        style={{
          opacity: 0.55,
          padding: '20px',
        }}
      >
        <div
          style={{
            fontSize: 12,
            color: 'var(--P-sub)',
            display: 'flex',
            alignItems: 'center',
            gap: 6,
            marginBottom: 12,
          }}
        >
          <IconMap size={13} /> 욕구 차이 지도
        </div>
        <div
          style={{
            textAlign: 'center',
            padding: '36px 0',
            fontFamily: 'var(--font-serif)',
            fontSize: 13,
            color: 'var(--P-sub)',
          }}
        >
          두 분이 함께 해야 그려져요
        </div>
      </div>

      {/* CTA Card */}
      <div
        style={{
          padding: '18px',
          background: 'var(--P-a)',
          color: 'var(--P-ink)',
          borderRadius: 14,
          textAlign: 'center',
        }}
      >
        <div
          style={{
            fontSize: 13,
            lineHeight: 1.6,
            fontFamily: 'var(--font-serif)',
            marginBottom: 12,
          }}
        >
          지금이라도 {partnerNickname || '상대'}님을 초대하면<br />
          두 분의 리포트가 완성돼요.
        </div>
        <button
          onClick={handleInviteClick}
          style={{
            background: 'var(--P-ink)',
            color: 'var(--P-card)',
            border: 'none',
            borderRadius: 10,
            padding: '12px 22px',
            fontSize: 14,
            fontWeight: 500,
            cursor: 'pointer',
          }}
        >
          초대 링크 다시 보내기
        </button>
      </div>
    </div>
  );
}
