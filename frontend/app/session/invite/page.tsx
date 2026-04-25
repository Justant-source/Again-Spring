// ✅ MOCKUP APPLIED — source: design/handoff/tone-L-screens.jsx (InvitePick)

'use client';

import { useRouter } from 'next/navigation';
import { useState, useEffect } from 'react';
import { useSessionStore } from '@/lib/store/sessionStore';
import { PhoneFrame, PhoneHeader } from '@/components/shared';

const TONES = [
  {
    key: '부드럽게',
    message: `우리 얘기 좀 정리해보고 싶어서\n다시봄에 내 생각을 적어봤어.\n너 생각도 듣고 싶은데, 같이 해볼래?`,
  },
  {
    key: '가볍게',
    message: `요즘 관계 AI 중재자 같은 게 있더라고.\n내가 먼저 써봤어. 너도 해볼래?\n둘 다 입력해야 결과가 나온대.`,
  },
  {
    key: '진지하게',
    message: `우리가 최근에 부딪혔던 일에 대해\n서로 정리할 시간이 필요한 것 같아.\n중재자가 도와주는 앱인데, 같이 해줄 수 있어?`,
  },
];

export default function InvitePage() {
  const router = useRouter();
  const { inviteToken, inviteMessageTone, setInviteTone } = useSessionStore();
  const [toneIdx, setToneIdx] = useState(
    inviteMessageTone === 'soft' ? 0 : inviteMessageTone === 'light' ? 1 : 2,
  );
  const [message, setMessage] = useState(TONES[toneIdx].message);
  const [edited, setEdited] = useState(false);
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    if (!inviteToken) {
      router.push('/session/new');
    }
  }, [inviteToken, router]);

  if (!inviteToken) {
    return null;
  }

  const shareUrl = `http://100.99.33.127/session/join/${inviteToken}`;

  const handleToneChange = (idx: number) => {
    if (edited && !window.confirm('편집한 내용이 사라져요. 새 말투로 바꿀까요?')) {
      return;
    }
    setToneIdx(idx);
    setMessage(TONES[idx].message);
    setEdited(false);
    const tones: Array<'soft' | 'light' | 'serious'> = ['soft', 'light', 'serious'];
    setInviteTone(tones[idx]);
  };

  const handleMessageChange = (e: React.ChangeEvent<HTMLTextAreaElement>) => {
    setMessage(e.target.value);
    setEdited(true);
  };

  const handleResetMessage = () => {
    setMessage(TONES[toneIdx].message);
    setEdited(false);
  };

  const fullShareText = `${message}\n\n${shareUrl}`;

  const handleCopyLink = async () => {
    try {
      await navigator.clipboard.writeText(fullShareText);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch (err) {
      console.error('Failed to copy:', err);
    }
  };

  const handleKakaoShare = () => {
    alert('카카오톡 연동은 배포 후 연결돼요');
  };

  return (
    <PhoneFrame tone="L">
      <PhoneHeader title="상대에게 어떻게 보낼까요" onBack={() => router.push('/session/describe')} />
      <div style={{ padding: '8px 28px 28px', flex: 1, display: 'flex', flexDirection: 'column', overflow: 'auto' }}>
        <div className="serif" style={{ fontSize: 18, lineHeight: 1.5, marginBottom: 20 }}>
          말투 하나에도<br />마음이 실리니까요.
        </div>

        <div style={{ display: 'flex', gap: 6, marginBottom: 16 }}>
          {TONES.map((t, i) => (
            <button
              key={i}
              onClick={() => handleToneChange(i)}
              style={{
                flex: 1,
                padding: '8px 0',
                fontSize: 12,
                cursor: 'pointer',
                background: i === toneIdx ? 'var(--L-ink)' : 'transparent',
                color: i === toneIdx ? 'var(--L-bg)' : 'var(--L-sub)',
                border: `1px solid ${i === toneIdx ? 'var(--L-ink)' : 'var(--L-border)'}`,
                borderRadius: 3,
                transition: 'all 0.15s',
                fontWeight: i === toneIdx ? 500 : 400,
              }}
            >
              {t.key}
            </button>
          ))}
        </div>

        <div className="letter-card" style={{ padding: 22, marginBottom: 8 }}>
          <textarea
            value={message}
            onChange={handleMessageChange}
            rows={Math.max(4, message.split('\n').length)}
            placeholder="상대방에게 전할 메시지를 적어주세요"
            style={{
              width: '100%',
              fontSize: 14,
              lineHeight: 1.8,
              fontFamily: 'var(--font-serif)',
              color: 'var(--L-ink)',
              background: 'transparent',
              border: 'none',
              outline: 'none',
              resize: 'none',
              padding: 0,
            }}
          />
          <div style={{ marginTop: 14, fontSize: 12, color: 'var(--L-sub)', borderTop: '1px solid var(--L-border)', paddingTop: 10 }}>
            {shareUrl}
          </div>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 20, fontSize: 11, color: 'var(--L-sub)' }}>
          <span>{edited ? '✎ 직접 편집한 메시지예요' : '바로 클릭해서 편집할 수 있어요'}</span>
          {edited && (
            <button
              type="button"
              onClick={handleResetMessage}
              style={{ background: 'none', border: 'none', color: 'var(--L-ink)', textDecoration: 'underline', cursor: 'pointer', fontSize: 11, padding: 0 }}
            >
              원래대로
            </button>
          )}
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 10, marginBottom: 24 }}>
          <button
            className="btn-L"
            style={{ background: '#FEE500', color: '#3C1E1E', width: '100%' }}
            onClick={handleKakaoShare}
          >
            카카오톡으로 보내기
          </button>
          <button className="btn-L ghost" style={{ width: '100%' }} onClick={handleCopyLink}>
            {copied ? '링크를 복사했어요' : '문자 · 링크 복사'}
          </button>
        </div>

        <div style={{ textAlign: 'center', marginTop: 'auto', paddingTop: 16 }}>
          <button
            style={{
              background: 'none',
              border: 'none',
              color: 'var(--L-sub)',
              fontSize: 13,
              cursor: 'pointer',
              textDecoration: 'underline',
            }}
            onClick={() => router.push('/session/wait')}
          >
            다음: 대기 화면
          </button>
        </div>
      </div>
    </PhoneFrame>
  );
}
