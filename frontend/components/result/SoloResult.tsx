
'use client';

import { useState } from 'react';
import { useUserStore } from '@/lib/store/userStore';
import { useSessionStore } from '@/lib/store/sessionStore';
import { api } from '@/lib/api/client';
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

export function SoloResult({ report, sessionId: sessionIdProp }: { report: Report; sessionId?: string }) {
  const user = useUserStore((s) => s.user);
  const sessionIdFromStore = useSessionStore((s) => s.sessionId);
  const sessionId = sessionIdProp || sessionIdFromStore;
  const partnerNickname = useSessionStore((s) => s.partnerNickname);
  const [copied, setCopied] = useState(false);
  const [inviteCopied, setInviteCopied] = useState(false);
  const [inviteLoading, setInviteLoading] = useState(false);

  const nvcDraft = (() => {
    if (!report.isSoloMode) return null;
    const nvc = report.nvcScripts?.aToB;
    if (!nvc) return null;
    return report.nvcSuggestion?.fourSentenceDraft
      ?? [nvc.observation, nvc.feeling, nvc.need, nvc.request].filter(Boolean).join('\n');
  })();

  const handleCopyNvcMessage = async () => {
    if (!nvcDraft) return;
    try {
      await navigator.clipboard.writeText(nvcDraft);
      setCopied(true);
      setTimeout(() => setCopied(false), 2500);
    } catch (err) {
      console.error('Failed to copy:', err);
    }
  };

  if (!report.isSoloMode) return null;

  // Get user's communication style
  const style = user?.communicationStyle
    ? COMMUNICATION_STYLES[user.communicationStyle]
    : null;

  const MotifComponent = user?.communicationStyle
    ? STYLE_MOTIF[user.communicationStyle]
    : null;

  const handleInviteClick = async () => {
    if (!sessionId || inviteLoading) return;
    setInviteLoading(true);
    try {
      const res = await api.post(`/api/sessions/${sessionId}/invite`);
      const token = res.data?.token || res.data?.inviteToken;
      if (!token) throw new Error('no token');
      const link = `${window.location.origin}/session/join/${token}`;
      await navigator.clipboard.writeText(link);
      setInviteCopied(true);
      setTimeout(() => setInviteCopied(false), 3000);
    } catch {
      alert('링크 생성에 실패했어요. 잠시 후 다시 시도해주세요.');
    } finally {
      setInviteLoading(false);
    }
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
          {user?.nickname ? `${user.nickname}님 입장에서의 정리` : '당신 입장에서의 정리'}
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
              <b style={{ fontWeight: 500 }}>관찰</b>{' '}·{' '}
              <span>{report.nvcScripts?.aToB?.observation ||
                '상황을 객관적으로 보셨어요'}</span>
            </div>
          </div>

          {/* Feeling */}
          <div style={{ display: 'flex', gap: 10, alignItems: 'flex-start' }}>
            <span style={{ color: 'var(--P-sub)', marginTop: 2 }}>
              <IconDrop size={15} />
            </span>
            <div>
              <b style={{ fontWeight: 500 }}>느낌</b>{' '}·{' '}
              <span>{report.nvcScripts?.aToB?.feeling ||
                '그때의 감정이 잘 정리되었어요'}</span>
            </div>
          </div>

          {/* Need */}
          <div style={{ display: 'flex', gap: 10, alignItems: 'flex-start' }}>
            <span style={{ color: 'var(--P-sub)', marginTop: 2 }}>
              <IconNeed size={15} />
            </span>
            <div>
              <b style={{ fontWeight: 500 }}>욕구</b>{' '}·{' '}
              <span>{report.nvcScripts?.aToB?.need ||
                '진정한 필요가 드러났어요'}</span>
            </div>
          </div>

          {/* Request */}
          <div style={{ display: 'flex', gap: 10, alignItems: 'flex-start' }}>
            <span style={{ color: 'var(--P-sub)', marginTop: 2 }}>
              <IconAsk size={15} />
            </span>
            <div>
              <b style={{ fontWeight: 500 }}>부탁</b>{' '}·{' '}
              <span>{report.nvcScripts?.aToB?.request ||
                '건설적인 요청이 있으셨어요'}</span>
            </div>
          </div>
        </div>
      </div>

      {/* NVC 4문장 카톡 복사 */}
      {nvcDraft && (
        <div
          style={{
            padding: '16px 18px',
            background: 'var(--P-card)',
            border: '1px solid var(--P-border)',
            borderRadius: 12,
          }}
        >
          <div style={{ fontSize: 12, color: 'var(--P-sub)', marginBottom: 8 }}>
            상대에게 보낼 수 있는 4문장 초안
          </div>
          <div
            className="serif"
            style={{
              fontSize: 14,
              lineHeight: 1.8,
              color: 'var(--P-ink)',
              whiteSpace: 'pre-line',
              marginBottom: 14,
            }}
          >
            {nvcDraft}
          </div>
          <button
            onClick={handleCopyNvcMessage}
            className="btn-P"
            style={{ width: '100%', fontSize: 13 }}
          >
            {copied ? '복사됐어요. 카톡에 붙여넣기 하세요.' : '카톡으로 보내기 (복사)'}
          </button>
          <div style={{ marginTop: 8, fontSize: 11, color: 'var(--P-sub)', textAlign: 'center' }}>
            그대로 보내도, 일부만 다듬어 보내도 좋아요.
          </div>
        </div>
      )}

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
          지금이라도 {partnerNickname || '상대'}분을 초대하면<br />
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
          {inviteLoading ? '링크 생성 중...' : inviteCopied ? '링크 복사됐어요. 카톡에 붙여넣기 하세요.' : '초대 링크 다시 보내기'}
        </button>
      </div>
    </div>
  );
}
