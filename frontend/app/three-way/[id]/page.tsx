'use client';

import { useState, useEffect, useRef } from 'react';
import { useParams } from 'next/navigation';
import { threeWayApi, ThreeWayMessage } from '@/lib/api/community/threeWayApi';
import { checkKeywords } from '@/lib/utils/keywordGuard';
import { CrisisResourceModal } from '@/components/shared/CrisisResourceModal';

const ROLE_LABELS: Record<string, string> = {
  PARTY_A: 'A님',
  PARTY_B: 'B님',
  MEDIATOR: 'AI 중재자',
};

export default function ThreeWayChatPage() {
  const params = useParams();
  const twsId = params.id as string;

  const [messages, setMessages] = useState<ThreeWayMessage[]>([]);
  const [inputText, setInputText] = useState('');
  const [myRole, setMyRole] = useState<'PARTY_A' | 'PARTY_B'>('PARTY_A');
  const [crisisOpen, setCrisisOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const [inviteUrl, setInviteUrl] = useState<string | null>(null);
  const [sessionStatus, setSessionStatus] = useState<string>('ACTIVE');
  const pollRef = useRef<NodeJS.Timeout | null>(null);

  // 메시지 폴링 (3초)
  const fetchMessages = async () => {
    try {
      const msgs = await threeWayApi.getMessages(twsId);
      setMessages(msgs);
    } catch {}
  };

  useEffect(() => {
    fetchMessages();
    pollRef.current = setInterval(fetchMessages, 3000);
    return () => { if (pollRef.current) clearInterval(pollRef.current); };
  }, [twsId]);

  // 초대링크 (WAITING 상태)
  useEffect(() => {
    threeWayApi.getInviteUrl(twsId).then(r => setInviteUrl(r.url)).catch(() => {});
  }, [twsId]);

  const handleSend = async () => {
    if (!inputText.trim()) return;
    const kw = checkKeywords(inputText);
    if (kw.level === 1) { setCrisisOpen(true); return; }

    try {
      setLoading(true);
      await threeWayApi.sendMessage(twsId, inputText.trim(), myRole);
      setInputText('');
      await fetchMessages();
    } catch {} finally { setLoading(false); }
  };

  const getMessageStyle = (role: string) => {
    if (role === 'MEDIATOR') return {
      background: 'var(--P-card, #FFF8F0)',
      border: '1px solid var(--P-border, #EADFD0)',
      borderRadius: 10,
      padding: '10px 14px',
      margin: '8px auto',
      maxWidth: '85%',
      textAlign: 'center' as const,
    };
    if (role === 'PARTY_A') return {
      background: 'var(--P-a, #F4A896)',
      borderRadius: 12,
      padding: '10px 14px',
      margin: '4px 0 4px auto',
      maxWidth: '75%',
    };
    return {
      background: 'var(--P-b, #A8C8B4)',
      borderRadius: 12,
      padding: '10px 14px',
      margin: '4px auto 4px 0',
      maxWidth: '75%',
    };
  };

  return (
    <div style={{ maxWidth: 640, margin: '0 auto', padding: '0 0 120px', fontFamily: 'inherit' }}>
      {/* 헤더 */}
      <div style={{ position: 'sticky', top: 0, background: 'white', borderBottom: '1px solid #e7e3d8', padding: '12px 16px', zIndex: 10 }}>
        <div style={{ fontSize: 15, fontWeight: 600, color: '#1A1A2E' }}>3자 대화</div>
        {inviteUrl && sessionStatus === 'WAITING' && (
          <div style={{ marginTop: 8, fontSize: 12, color: '#888' }}>
            초대링크: <a href={inviteUrl} style={{ color: '#D4A5A5' }}>{inviteUrl}</a>
          </div>
        )}
      </div>

      {/* 메시지 목록 */}
      <div style={{ padding: '16px' }}>
        {messages.length === 0 && (
          <div style={{ textAlign: 'center', color: '#aaa', padding: 40, fontSize: 13 }}>
            메시지가 없습니다
          </div>
        )}
        {messages.map((msg) => (
          <div key={msg.id}>
            {msg.authorRole === 'MEDIATOR' ? (
              <div
                data-testid="three-way-mediator-msg"
                style={getMessageStyle('MEDIATOR')}
              >
                <div
                  data-testid="three-way-mediator-label"
                  style={{ fontSize: 10, color: '#A08670', marginBottom: 4, fontWeight: 600 }}
                >
                  [AI 중재자]
                </div>
                <div style={{ fontSize: 13, color: '#5C4030' }}>{msg.content}</div>
              </div>
            ) : (
              <div
                data-testid={`three-way-${msg.authorRole === 'PARTY_A' ? 'party-a' : 'party-b'}-msg`}
                style={getMessageStyle(msg.authorRole)}
              >
                <div style={{ fontSize: 10, color: 'rgba(92,64,48,0.6)', marginBottom: 2 }}>
                  {ROLE_LABELS[msg.authorRole]}
                </div>
                <div style={{ fontSize: 13 }}>{msg.content}</div>
              </div>
            )}
          </div>
        ))}
      </div>

      {/* 입력창 */}
      <div style={{ position: 'fixed', bottom: 0, left: 0, right: 0, background: 'white', borderTop: '1px solid #e7e3d8', padding: 12 }}>
        <div style={{ maxWidth: 640, margin: '0 auto', display: 'flex', gap: 8 }}>
          <select
            value={myRole}
            onChange={(e) => setMyRole(e.target.value as 'PARTY_A' | 'PARTY_B')}
            style={{ padding: '8px', fontSize: 12, border: '1px solid #e7e3d8', borderRadius: 6 }}
          >
            <option value="PARTY_A">A님</option>
            <option value="PARTY_B">B님</option>
          </select>
          <input
            value={inputText}
            onChange={(e) => {
              setInputText(e.target.value);
              const kw = checkKeywords(e.target.value);
              if (kw.level === 1) setCrisisOpen(true);
            }}
            placeholder="메시지 입력..."
            style={{ flex: 1, padding: '8px 12px', fontSize: 13, border: '1px solid #e7e3d8', borderRadius: 6, outline: 'none' }}
            onKeyDown={(e) => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); handleSend(); } }}
          />
          <button
            onClick={handleSend}
            disabled={loading || !inputText.trim()}
            style={{ padding: '8px 16px', background: '#D4A5A5', color: 'white', border: 'none', borderRadius: 6, fontSize: 13, cursor: 'pointer', opacity: loading ? 0.6 : 1 }}
          >
            전송
          </button>
        </div>
      </div>

      <CrisisResourceModal open={crisisOpen} onClose={() => setCrisisOpen(false)} />
    </div>
  );
}
