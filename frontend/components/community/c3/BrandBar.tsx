'use client';

import { User } from '@/lib/types/user';
import { UserChip } from './UserChip';

interface BrandBarProps {
  title?: string;
  user?: User | null;
}

export function BrandBar({ title = '다시봄', user }: BrandBarProps) {
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
      <UserChip user={user} />
    </div>
  );
}
