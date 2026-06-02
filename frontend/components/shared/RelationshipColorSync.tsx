'use client';

import { useEffect } from 'react';

const MAP: Record<string, { a: string; b: string }> = {
  couple: { a: '#F4A8B0', b: '#A8B8C8' },
  marriage: { a: '#F4A896', b: '#A8C8B4' },
  friend: { a: '#F4C896', b: '#B0C8D8' },
  family: { a: '#E8B896', b: '#B8C4A8' },
  parent_child: { a: '#D8A8A8', b: '#A8B8A8' },
  korean_specific: { a: '#E8C8A8', b: '#A8C0B8' },
  work: { a: '#B8C8E8', b: '#C8B8D8' },
};

/** Applies the relationship-tinted --P-a / --P-b CSS vars to :root. */
export function RelationshipColorSync({ type }: { type: string | null }) {
  useEffect(() => {
    if (!type) return;
    const { a, b } = MAP[type];
    document.documentElement.style.setProperty('--P-a', a);
    document.documentElement.style.setProperty('--P-b', b);
  }, [type]);
  return null;
}
