'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { User } from '@/lib/types/user';
import { GuestInfoSheet } from '@/components/shared/GuestInfoSheet';

interface UserChipProps {
  user?: User | null;
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
