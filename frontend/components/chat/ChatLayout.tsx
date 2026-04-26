'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { ChatPanel } from './ChatPanel';
import { PartnerPanel } from './PartnerPanel';
import { SwipeContainer } from './SwipeContainer';
import { PartnerStatusBar } from './PartnerStatusBar';
import { InviteModal } from './InviteModal';
import { PartnerJoinedToast } from './PartnerJoinedToast';
import { api } from '@/lib/api/client';
import { usePolling } from '@/lib/hooks/usePolling';

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
      {/* 상단 상태바 (Duo 전환 시 자동 표시) */}
      {isDuo && <PartnerStatusBar sessionId={sessionId} myRole={myRole} />}

      {!isDuo ? (
        // Solo: 단일 패널
        <ChatPanel
          sessionId={sessionId}
          session={session}
          currentUserSender={myRole}
          isDuo={false}
          onOpenInvite={() => setShowInviteModal(true)}
        />
      ) : (
        // Duo: 스와이프 분할
        <SwipeContainer hint="← 스와이프하면 상대 진행도 볼 수 있어요">
          <ChatPanel
            sessionId={sessionId}
            session={session}
            currentUserSender={myRole}
            isDuo={true}
          />
          <PartnerPanel sessionId={sessionId} myRole={myRole} />
        </SwipeContainer>
      )}

      {/* 초대 모달 */}
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
    </>
  );
}
