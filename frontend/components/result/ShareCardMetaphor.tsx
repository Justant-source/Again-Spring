'use client';

import Image from 'next/image';
import { getMetaphorById, getMetaphorImagePath } from '@/lib/constants/metaphors';
import type { Report } from '@/lib/types';

interface Props {
  report: Report;
}

export function ShareCardMetaphor({ report }: Props) {
  const metaphorId = (report as any).metaphor?.id ?? 'half-open-letter';
  const metaphor = getMetaphorById(metaphorId);
  if (!metaphor) return null;

  return (
    <div
      style={{
        width: 270,
        height: 480,
        background: 'var(--P-bg)',
        border: '1px solid var(--P-border)',
        borderRadius: 18,
        padding: '36px 28px',
        position: 'relative',
        display: 'flex',
        flexDirection: 'column',
        textAlign: 'center',
        fontFamily: 'var(--font-sans)',
        color: 'var(--P-ink)',
      }}
    >
      <div style={{ fontSize: 11, color: 'var(--P-sub)' }}>다시봄</div>
      <div style={{ fontSize: 13, color: 'var(--P-sub)', marginTop: 16 }}>당신의 마음은</div>

      <div
        style={{
          flex: 1,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          padding: '12px 0',
        }}
      >
        <Image
          src={getMetaphorImagePath(metaphor.filename)}
          alt={metaphor.label}
          width={200}
          height={200}
          style={{ display: 'block' }}
        />
      </div>

      <div className="serif" style={{ fontSize: 22, lineHeight: 1.4 }}>
        <strong>{metaphor.label}</strong> 같아요
      </div>
      <div style={{ fontSize: 12, color: 'var(--P-sub)', marginTop: 10, lineHeight: 1.6 }}>
        {metaphor.meaning}
      </div>

      <div style={{ marginTop: 'auto', fontSize: 11, color: 'var(--P-sub)' }}>
        다시봄 · 마음을 옮겨 적어 보세요
      </div>
      <div style={{ fontSize: 10, color: 'var(--P-sub)', marginTop: 4 }}>
        againspring.net
      </div>
    </div>
  );
}
