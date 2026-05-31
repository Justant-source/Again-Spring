'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { ChatPanel } from './ChatPanel';
import { PartnerPanel } from './PartnerPanel';
import { SwipeContainer } from './SwipeContainer';
import { PartnerStatusBar } from './PartnerStatusBar';
import { InviteModal } from './InviteModal';
import { PartnerJoinedToast } from './PartnerJoinedToast';
import { GuestUpgradeModal } from '@/components/auth/GuestUpgradeModal';
import { api } from '@/lib/api/client';
import { usePolling } from '@/lib/hooks/usePolling';
import { useUserStore } from '@/lib/store/userStore';

interface Props {
  sessionId: string;
  session: any;
}

type Status =
  | 'chatting_solo'
  | 'chatting_duo'
  | 'awaiting_finalization'
  | 'completed'
  | 'terminated';

export function ChatLayout({ sessionId, session: initialSession }: Props) {
  const router = useRouter();
  const [session, setSession] = useState(initialSession);
  // V13 Phase 2: Duo UI는 TESTER 역할 보유자만 표시
  const currentUser = useUserStore((s) => s.user);
  const isTester = currentUser?.roles?.includes('TESTER') ?? false;
  const [showInviteModal, setShowInviteModal] = useState(false);
  const [showJoinedToast, setShowJoinedToast] = useState(false);
  const [myRole] = useState<'USER_A' | 'USER_B'>(() => {
    const r = initialSession.myRole;
    if (r === 'B' || r === 'USER_B') return 'USER_B';
    return 'USER_A';
  });

  const refreshSession = async () => {
    try {
      const r = await api.get(`/api/sessions/${sessionId}`);
      const newSession = r.data;

      // Solo→Duo 전이 감지
      if (
        session.status === 'chatting_solo' &&
        newSession.status === 'chatting_duo'
      ) {
        setShowJoinedToast(true);
        setTimeout(() => setShowJoinedToast(false), 5000);
      }

      // 종료 감지
      if (newSession.status === 'completed') {
        router.push(`/session/result/${sessionId}`);
        return;
      }

      setSession(newSession);
    } catch (e) {
      console.debug('Session refresh error:', e);
    }
  };

  usePolling(refreshSession, 5000);

  const isDuo =
    session.status === 'chatting_duo' ||
    session.status === 'awaiting_finalization';

  return (
    <>
      <div style={{ position: 'fixed', inset: 0, display: 'flex', flexDirection: 'column' }}>
        {/* 상단 상태바 — 자연스러운 플로우로 ChatHeader 위에 겹치지 않음 */}
        {isDuo && <PartnerStatusBar sessionId={sessionId} myRole={myRole} />}

        <div style={{ flex: 1, overflow: 'hidden', position: 'relative' }}>
          {!isDuo ? (
            // Solo: 단일 패널 (V17: 초대 버튼은 모든 사용자에게 노출)
            <ChatPanel
              sessionId={sessionId}
              session={session}
              currentUserSender={myRole}
              isDuo={false}
              onOpenInvite={() => setShowInviteModal(true)}
            />
          ) : isTester ? (
            // Duo: 스와이프 분할 (TESTER만)
            <SwipeContainer hint="← 스와이프하면 상대 진행도 볼 수 있어요">
              <ChatPanel
                sessionId={sessionId}
                session={session}
                currentUserSender={myRole}
                isDuo={true}
              />
              <PartnerPanel sessionId={sessionId} myRole={myRole} />
            </SwipeContainer>
          ) : (
            // Duo 세션이지만 비TESTER — Solo 패널로 fallback
            <ChatPanel
              sessionId={sessionId}
              session={session}
              currentUserSender={myRole}
              isDuo={false}
            />
          )}
        </div>
      </div>

      {/* 초대 모달 — V17: 모든 사용자 (TESTER 게이트 해제) */}
      {showInviteModal && (
        <InviteModal
          sessionId={sessionId}
          onClose={() => setShowInviteModal(false)}
        />
      )}

      {/* 상대 합류 알림 */}
      {showJoinedToast && (
        <PartnerJoinedToast onClose={() => setShowJoinedToast(false)} />
      )}

      {/* 게스트 3턴 한도 도달 모달 */}
      <GuestUpgradeModal />
    </>
  );
}
