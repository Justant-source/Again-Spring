// ✅ MOCKUP APPLIED — source: design/handoff/tone-L-screens.jsx (TreeMid, TreeSmall)

'use client';

import { useRouter } from 'next/navigation';
import { useState } from 'react';
import { useSessionStore } from '@/lib/store/sessionStore';
import { CATEGORIES } from '@/lib/constants/categories';
import { PhoneFrame, PhoneHeader, Dashes } from '@/components/shared';
import { api } from '@/lib/api/client';

export default function CategoryPage() {
  const router = useRouter();
  const { relationType, category, setCategory } = useSessionStore();
  const [stage, setStage] = useState<1 | 2>(1); // 1 = middle selection, 2 = minor selection
  const [selectedMiddleId, setSelectedMiddleId] = useState<string | null>(null);
  const [customText, setCustomText] = useState('');

  // Redirect if no relationship type selected
  if (!relationType) {
    router.push('/session/new');
    return null;
  }

  const major = CATEGORIES.find((m) => m.relationType === relationType);
  if (!major) {
    router.push('/session/new');
    return null;
  }

  const handleMiddleSelect = (middleId: string) => {
    setSelectedMiddleId(middleId);
    setStage(2);
  };

  const handleMinorSelect = async (minorId: string, allowsCustom: boolean) => {
    if (allowsCustom && !customText.trim()) {
      // Require custom text for "direct input" options
      return;
    }
    setCategory({
      majorId: major.id,
      middleId: selectedMiddleId!,
      minorId,
      customText: customText.trim() || undefined,
    });

    // Create session and navigate to chat page
    try {
      const response = await api.post('/api/sessions', {
        relationType,
        category: {
          majorId: major.id,
          middleId: selectedMiddleId!,
          minorId,
          customText: customText.trim() || undefined,
        },
      });
      const { id } = response.data;
      router.push(`/session/chat/${id}`);
    } catch (error) {
      console.error('Failed to create session:', error);
      router.push('/session/new');
    }
  };

  const handleBack = () => {
    if (stage === 2) {
      setStage(1);
      setSelectedMiddleId(null);
      setCustomText('');
    } else {
      router.push('/session/new');
    }
  };

  if (stage === 1) {
    return (
      <PhoneFrame tone="L">
        <PhoneHeader title="어떤 일로 마음이 무거우신가요" onBack={handleBack} />
        <div style={{ padding: '8px 28px 28px', flex: 1, display: 'flex', flexDirection: 'column' }}>
          <div style={{ marginBottom: 28 }}>
            <Dashes n={4} done={2} />
          </div>

          <div style={{ fontSize: 12, color: 'var(--L-sub)', marginBottom: 10 }}>
            {major.label} →
          </div>
          <div className="serif" style={{ fontSize: 19, lineHeight: 1.5, marginBottom: 22 }}>
            마음에 걸리시는 일의<br />큰 갈래를 골라주세요.
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: 0 }}>
            {major.middles.map((middle) => (
              <button
                key={middle.id}
                onClick={() => handleMiddleSelect(middle.id)}
                style={{
                  padding: '14px 16px',
                  fontSize: 14,
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  color: selectedMiddleId === middle.id ? 'var(--L-ink)' : 'var(--L-sub)',
                  fontWeight: selectedMiddleId === middle.id ? 500 : 400,
                  background: 'transparent',
                  border: 'none',
                  borderBottom: '1px solid var(--L-border)',
                  cursor: 'pointer',
                  textAlign: 'left',
                  transition: 'all 0.15s',
                }}
                onMouseEnter={(e) => {
                  e.currentTarget.style.color = 'var(--L-ink)';
                  e.currentTarget.style.fontWeight = '500';
                }}
                onMouseLeave={(e) => {
                  e.currentTarget.style.color = selectedMiddleId === middle.id ? 'var(--L-ink)' : 'var(--L-sub)';
                  e.currentTarget.style.fontWeight = selectedMiddleId === middle.id ? '500' : '400';
                }}
              >
                <span>{middle.label}</span>
                <span style={{ fontSize: 14, color: 'var(--L-sub)' }}>›</span>
              </button>
            ))}
          </div>
        </div>
      </PhoneFrame>
    );
  }

  // Stage 2: Minor selection
  const selectedMiddle = major.middles.find((m) => m.id === selectedMiddleId);
  if (!selectedMiddle) {
    return null;
  }

  const selectedMinor = selectedMiddle.minors.find((m) => m.id === 'custom');
  const hasCustom = selectedMiddle.minors.some((m) => m.allowCustomInput);

  return (
    <PhoneFrame tone="L">
      <PhoneHeader title="조금 더 구체적으로" onBack={handleBack} />
      <div style={{ padding: '8px 28px 28px', flex: 1, display: 'flex', flexDirection: 'column' }}>
        <div style={{ marginBottom: 28 }}>
          <Dashes n={4} done={3} />
        </div>

        <div style={{ fontSize: 12, color: 'var(--L-sub)', marginBottom: 10 }}>
          {major.label} · {selectedMiddle.label} →
        </div>
        <div className="serif" style={{ fontSize: 18, lineHeight: 1.5, marginBottom: 22 }}>
          가장 가까운 상황을<br />골라주세요.
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 8, marginBottom: hasCustom ? 16 : 0 }}>
          {selectedMiddle.minors.map((minor) => (
            <button
              key={minor.id}
              onClick={() => !minor.allowCustomInput && handleMinorSelect(minor.id, false)}
              disabled={minor.allowCustomInput}
              style={{
                padding: '14px 16px',
                border: '1px solid',
                borderColor: 'var(--L-border)',
                borderRadius: 3,
                fontSize: 14,
                background: 'transparent',
                cursor: minor.allowCustomInput ? 'default' : 'pointer',
                textAlign: 'left',
                transition: 'all 0.15s',
                opacity: minor.allowCustomInput ? 0.5 : 1,
              }}
            >
              {minor.label}
            </button>
          ))}
        </div>

        {hasCustom && (
          <div style={{ marginTop: 8 }}>
            <textarea
              className="ta-L"
              placeholder="직접 입력해주세요"
              value={customText}
              onChange={(e) => setCustomText(e.target.value)}
              style={{ minHeight: 100 }}
            />
            <div style={{ marginTop: 12, display: 'flex', gap: 8 }}>
              <button className="btn-L ghost" style={{ flex: 1 }} onClick={handleBack}>
                이전
              </button>
              <button
                className="btn-L"
                style={{ flex: 1 }}
                disabled={!customText.trim()}
                onClick={() => handleMinorSelect('custom', true)}
              >
                다음
              </button>
            </div>
          </div>
        )}
      </div>
    </PhoneFrame>
  );
}
