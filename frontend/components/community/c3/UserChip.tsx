'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { User } from '@/lib/types/user';

const PARTNER_COLOR = '#5F8F76';

interface UserChipProps {
  user?: User | null;
}

function GuestInfoSheet({ user, onClose }: { user: User; onClose: () => void }) {
  const router = useRouter();
  const nickname = user.nickname || '게스트';

  return (
    <>
      {/* 오버레이 */}
      <div
        onClick={onClose}
        style={{
          position: 'fixed', inset: 0, zIndex: 400,
          background: 'rgba(0,0,0,0.25)',
        }}
      />
      {/* 바텀시트 */}
      <div
        data-testid="guest-info-sheet"
        style={{
          position: 'fixed', left: 0, right: 0, bottom: 0, zIndex: 401, maxWidth: 640, marginLeft: 'auto', marginRight: 'auto',
          background: 'var(--L-bg)',
          borderRadius: '20px 20px 0 0',
          boxShadow: '0 -8px 30px rgba(60,40,20,.12)',
          padding: '22px 24px max(26px, env(safe-area-inset-bottom, 26px))',
        }}>
        {/* 드래그 핸들 */}
        <div style={{ width: 36, height: 4, borderRadius: 2, background: 'var(--L-border)', margin: '0 auto 18px' }} />

        {/* 게스트 배지 + 닉네임 */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 14 }}>
          <span style={{ fontSize: 11, color: 'var(--L-sub)', border: '1px solid var(--L-border)', borderRadius: 999, padding: '3px 10px' }}>게스트</span>
          <span style={{ fontSize: 13, fontWeight: 500, color: 'var(--L-ink)' }}>{nickname}</span>
        </div>

        {/* 제목 */}
        <div className="serif" style={{ fontSize: 20, lineHeight: 1.45 }}>
          게스트로는<br />이런 게 안 돼요
        </div>

        {/* 제약 목록 */}
        <div style={{ marginTop: 16, display: 'flex', flexDirection: 'column', gap: 11 }}>
          {[
            '올린 뒤 수정·삭제할 수 없어요',
            '내 사연 목록에 저장되지 않아요',
            '상대 초대·결과 알림을 받을 수 없어요',
          ].map((text) => (
            <div key={text} style={{ display: 'flex', gap: 10, alignItems: 'flex-start' }}>
              <span style={{ color: PARTNER_COLOR, fontSize: 14, marginTop: 1, flexShrink: 0 }}>✕</span>
              <span style={{ fontSize: 13.5, color: 'var(--L-ink)', lineHeight: 1.5 }}>{text}</span>
            </div>
          ))}
        </div>

        {/* 안내 */}
        <div style={{ marginTop: 14, fontSize: 12, color: 'var(--L-sub)', lineHeight: 1.6 }}>
          투표와 댓글은 게스트도 자유롭게 할 수 있어요
        </div>

        {/* 버튼 */}
        <div style={{ marginTop: 20, display: 'flex', flexDirection: 'column', gap: 9 }}>
          <button
            onClick={() => { onClose(); router.push('/signup'); }}
            style={{
              width: '100%', padding: '15px 0', borderRadius: 4, border: 'none',
              background: 'var(--L-ink)', color: 'var(--L-bg)',
              fontSize: 15, fontWeight: 500, fontFamily: 'inherit', cursor: 'pointer',
            }}
          >
            회원가입하기
          </button>
          <button
            onClick={onClose}
            style={{
              width: '100%', padding: '14px 0', borderRadius: 4,
              border: '1px solid var(--L-border)', background: 'transparent',
              color: 'var(--L-ink)', fontSize: 15, fontWeight: 400,
              fontFamily: 'inherit', cursor: 'pointer',
            }}
          >
            게스트로 계속하기
          </button>
        </div>
      </div>
    </>
  );
}

export function UserChip({ user }: UserChipProps) {
  const [sheetOpen, setSheetOpen] = useState(false);
  const router = useRouter();

  const isGuest = user?.isGuest ?? true;
  const initial = isGuest ? '?' : (user?.nickname?.charAt(0) || '?');
  const nickname = user?.nickname || '';

  const handleClick = () => {
    if (isGuest && user) {
      setSheetOpen(true);
    } else if (!isGuest) {
      router.push('/profile/info');
    }
  };

  return (
    <>
      <div
        data-testid="user-chip"
        onClick={handleClick}
        role="button"
        tabIndex={0}
        onKeyDown={(e) => { if (e.key === 'Enter') handleClick(); }}
        style={{ display: 'inline-flex', alignItems: 'center', gap: 7, cursor: 'pointer' }}
      >
        <span style={{
          width: 22, height: 22, borderRadius: '50%',
          background: isGuest ? 'transparent' : 'var(--P-a)',
          border: isGuest ? '1.5px dashed var(--L-sub)' : 'none',
          color: isGuest ? 'var(--L-sub)' : 'white',
          display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
          fontSize: 11, fontWeight: 500, flexShrink: 0,
        }}>
          {initial}
        </span>
        {nickname && (
          <span style={{
            fontSize: 12.5,
            color: isGuest ? 'var(--L-sub)' : 'var(--L-ink)',
            fontWeight: 500,
            maxWidth: 80, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
          }}>
            {nickname}
          </span>
        )}
      </div>

      {sheetOpen && user && (
        <GuestInfoSheet user={user} onClose={() => setSheetOpen(false)} />
      )}
    </>
  );
}
