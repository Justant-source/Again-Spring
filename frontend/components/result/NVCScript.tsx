'use client';

import type { NVCScript as NVCScriptType } from '@/lib/types';
import { IconEye, IconDrop, IconNeed, IconAsk } from '@/components/shared/Motif';

interface NVCScriptProps {
  script: NVCScriptType;
  from: string;
  to: string;
}

const NVC_STEPS = [
  { key: 'observation', label: '관찰', icon: IconEye },
  { key: 'feeling', label: '느낌', icon: IconDrop },
  { key: 'need', label: '욕구', icon: IconNeed },
  { key: 'request', label: '부탁', icon: IconAsk },
];

export function NVCScript({ script, from, to }: NVCScriptProps) {
  return (
    <div>
      <div style={{ fontSize: 11, color: 'var(--P-sub)', marginBottom: 10 }}>
        <span className="chip-P" style={{ display: 'inline-flex' }}>
          {from} → {to}
        </span>
      </div>

      <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
        {NVC_STEPS.map(({ key, label, icon: Icon }) => {
          const text = script[key as keyof NVCScriptType];
          return (
            <div key={key} style={{ display: 'flex', gap: 10, alignItems: 'flex-start' }}>
              <span style={{ color: 'var(--P-sub)', marginTop: 2, flexShrink: 0 }}>
                <Icon size={15} />
              </span>
              <div>
                <div style={{ fontWeight: 500, fontSize: 12, color: 'var(--P-ink)', marginBottom: 2 }}>{label}</div>
                <div style={{ fontFamily: 'var(--font-serif)', fontSize: 13, lineHeight: 1.6, color: 'var(--P-ink)' }}>
                  {text}
                </div>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
