'use client';

import { User } from '@/lib/types/user';
import { UserChip } from './UserChip';

interface BrandBarProps {
  title?: string;
  user?: User | null;
  onSearchOpen?: () => void;
}

export function BrandBar({ title = '다시봄', user, onSearchOpen }: BrandBarProps) {
  return (
    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
      <span
        style={{
          fontFamily: 'var(--font-serif)',
          fontSize: 17,
          fontWeight: 500,
          color: 'var(--L-ink)',
        }}
      >
        {title}
      </span>
      <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
        {onSearchOpen && (
          <button
            onClick={onSearchOpen}
            aria-label="검색"
            style={{ background: 'none', border: 'none', cursor: 'pointer', padding: 0, display: 'flex', alignItems: 'center', color: 'var(--L-ink)' }}
          >
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <circle cx="11" cy="11" r="8" /><line x1="21" y1="21" x2="16.65" y2="16.65" />
            </svg>
          </button>
        )}
        <UserChip user={user} />
      </div>
    </div>
  );
}

