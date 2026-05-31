
'use client';

import { useRouter } from 'next/navigation';
import { useSessionStore } from '@/lib/store/sessionStore';
import { CATEGORIES } from '@/lib/constants/categories';
import { PhoneFrame, PhoneHeader, Dashes, RelationshipColorSync } from '@/components/shared';

export default function NewSessionPage() {
  const router = useRouter();
  const { relationType, setRelationType } = useSessionStore();

  const handleSelect = (majorId: string) => {
    const major = CATEGORIES.find((m) => m.id === majorId);
    if (major) {
      setRelationType(major.relationType);
      router.push('/session/category');
    }
  };

  const options = [
    { id: 'couple', name: '연인 · 썸', desc: '함께 알아가는 사이' },
    { id: 'marriage', name: '부부', desc: '결혼으로 맺어진 사이' },
    { id: 'friend', name: '친구 · 지인', desc: '친구 혹은 지인과의 갈등' },
    { id: 'family', name: '가족', desc: '형제자매 · 친척' },
    { id: 'parent_child', name: '부모 · 자식', desc: '키우고 자란 사이' },
    { id: 'work', name: '직장', desc: '직장 생활에서의 갈등' },
  ];

  return (
    <PhoneFrame tone="L">
      <RelationshipColorSync type={relationType} />
      <PhoneHeader title="어떤 관계인가요" onBack={() => router.push('/')} />
      <div style={{ padding: '8px 28px 28px', flex: 1, display: 'flex', flexDirection: 'column' }}>
        <div style={{ marginBottom: 28 }}>
          <Dashes n={2} done={1} />
        </div>

        <div className="serif" style={{ fontSize: 22, lineHeight: 1.5, marginBottom: 28 }}>
          어떤 분과의 이야기인가요?
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
          {options.map((opt) => (
            <button
              key={opt.id}
              onClick={() => handleSelect(opt.id)}
              className="letter-card"
              style={{
                padding: '14px 18px',
                border: '1px solid var(--L-border)',
                borderColor: relationType && opt.id.startsWith(relationType.split('_')[0]) ? 'var(--L-ink)' : 'var(--L-border)',
                background: relationType && opt.id.startsWith(relationType.split('_')[0]) ? 'var(--L-card)' : 'transparent',
                cursor: 'pointer',
                textAlign: 'left',
              }}
            >
              <div style={{ fontSize: 15, fontWeight: 500 }}>{opt.name}</div>
              <div style={{ fontSize: 12, color: 'var(--L-sub)', marginTop: 2 }}>{opt.desc}</div>
            </button>
          ))}
        </div>
      </div>
    </PhoneFrame>
  );
}
