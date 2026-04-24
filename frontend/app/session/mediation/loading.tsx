// ✅ MOCKUP APPLIED — Next.js loading state for mediation page

import { PhoneFrame, PhoneHeader } from '@/components/shared/PhoneFrame';

export default function MediationLoading() {
  return (
    <PhoneFrame tone="L">
      <PhoneHeader title="중재 진행 중…" back={false} />
      <div className="flex flex-1 items-center justify-center flex-col gap-3">
        <div style={{ fontSize: '14px', color: 'var(--L-sub)' }}>
          중재자가 마음을 정리 중이에요
        </div>
        <div
          style={{
            display: 'flex',
            gap: '4px',
          }}
        >
          {[0, 1, 2].map((i) => (
            <div
              key={i}
              style={{
                width: '6px',
                height: '6px',
                borderRadius: '50%',
                background: 'var(--L-sub)',
                animation: `blink 1.4s infinite ${i * 0.2}s`,
              }}
            />
          ))}
        </div>
      </div>
    </PhoneFrame>
  );
}
