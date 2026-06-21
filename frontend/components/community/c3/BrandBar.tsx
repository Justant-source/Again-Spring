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
            <svg width="21" height="21" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
              <circle cx="11" cy="11" r="7" /><path d="M21 21l-4.3-4.3" />
            </svg>
          </button>
        )}
        <UserChip user={user} />
      </div>
    </div>
  );
}

