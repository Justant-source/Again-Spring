
'use client';

import { useRouter } from 'next/navigation';
import { useState } from 'react';
import { useSessionStore } from '@/lib/store/sessionStore';
import { useUserStore } from '@/lib/store/userStore';
import { CATEGORIES } from '@/lib/constants/categories';
import { permissionsFor } from '@/lib/constants/userPermissions';
import { PhoneFrame, PhoneHeader, Dashes } from '@/components/shared';
import { MediatorStylePicker } from '@/components/session/MediatorStylePicker';
import { api } from '@/lib/api/client';

export default function CategoryPage() {
  const router = useRouter();
  const { relationType, setCategory } = useSessionStore();
  const user = useUserStore((s) => s.user);
  const perms = permissionsFor(user);
  const stylePicksPerSession = perms.mediator.styleSource === 'per_session';

  const totalSteps = stylePicksPerSession ? 4 : 3;
  const [stage, setStage] = useState<1 | 2 | 3>(1);
  const [selectedMiddleId, setSelectedMiddleId] = useState<string | null>(null);
  const [customText, setCustomText] = useState('');
  const [pendingCategory, setPendingCategory] = useState<{
    majorId: string;
    middleId: string;
    minorId: string;
    customText?: string;
  } | null>(null);
  // 프로필에 저장한 중재자 톤 기본값 우선, 없으면 정책 default(50/50)
  const [styleX, setStyleX] = useState<number>(
    user?.mediatorDefaultX ?? perms.mediator.defaultStyleX
  );
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  if (!relationType) {
    router.push('/session/new');
    return null;
  }

  const major = CATEGORIES.find((m) => m.relationType === relationType);
  if (!major) {
    router.push('/session/new');
    return null;
  }

  const createSession = async (cat: typeof pendingCategory, x: number, y: number) => {
    if (!cat) return;
    setLoading(true);
    setErrorMessage(null);
    try {
      const response = await api.post('/api/sessions', {
        relationType,
        category: cat,
        mediatorStyleX: x,
        mediatorStyleY: y,
      });
      const { id } = response.data;
      router.push(`/session/chat/${id}`);
    } catch (error: unknown) {
      const err = error as {
        response?: { status?: number; data?: { error?: { message?: string; code?: string } } };
      };
      const status = err?.response?.status;
      // 401/403 is handled globally by the API interceptor (redirects to /guest or /login)
      if (status === 401 || status === 403) return;

      console.error('Failed to create session:', error);
      const beMessage = err?.response?.data?.error?.message;
      setErrorMessage(beMessage || '대화를 시작할 수 없어요. 잠시 후 다시 시도해 주세요.');
      setLoading(false);
    }
  };

  const handleMiddleSelect = (middleId: string) => {
    setSelectedMiddleId(middleId);
    setStage(2);
  };

  const handleMinorSelect = async (minorId: string, allowsCustom: boolean) => {
    if (allowsCustom && !customText.trim()) return;

    const cat = {
      majorId: major.id,
      middleId: selectedMiddleId!,
      minorId,
      customText: customText.trim() || undefined,
    };
    setCategory(cat);
    setPendingCategory(cat);

    if (stylePicksPerSession) {
      // 게스트: 중재자 성향 선택 단계로 이동
      setStage(3);
    } else {
      // 회원: 프로필 기반 성향 (BE는 기본값 50/50로 저장, 향후 프로필 연동)
      await createSession(cat, perms.mediator.defaultStyleX, perms.mediator.defaultStyleY);
    }
  };

  const handleConfirmStyle = async () => {
    if (!pendingCategory) return;
    await createSession(pendingCategory, styleX, perms.mediator.defaultStyleY);
  };

  const handleBack = () => {
    if (stage === 3) {
      setStage(2);
    } else if (stage === 2) {
      setStage(1);
      setSelectedMiddleId(null);
      setCustomText('');
      setPendingCategory(null);
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
            <Dashes n={totalSteps} done={2} />
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

  // Stage 3: 게스트 전용 — 중재자 성향 선택
  if (stage === 3) {
    return (
      <PhoneFrame tone="L">
        <PhoneHeader title="중재자 성향" onBack={handleBack} />
        <div style={{ padding: '8px 28px 28px', flex: 1, display: 'flex', flexDirection: 'column' }}>
          <div style={{ marginBottom: 28 }}>
            <Dashes n={totalSteps} done={4} />
          </div>

          <div className="serif" style={{ fontSize: 18, lineHeight: 1.5, marginBottom: 8 }}>
            이번 대화의 중재자 톤을 정해주세요.
          </div>
          <div style={{ fontSize: 12, color: 'var(--L-sub)', marginBottom: 28, lineHeight: 1.6 }}>
            {user?.mediatorDefaultX != null
              ? '프로필에 저장한 기본값을 불러왔어요. 이번 대화에는 다르게 조정해도 좋아요.'
              : '매 대화마다 다르게 정할 수 있어요. 이번 대화에만 적용돼요.'}
          </div>

          <div style={{ flex: 1, display: 'flex', flexDirection: 'column', justifyContent: 'center' }}>
            <MediatorStylePicker value={styleX} onChange={setStyleX} />
          </div>

          {errorMessage && (
            <div
              role="alert"
              style={{
                marginTop: 16,
                padding: '12px 14px',
                background: '#FBEAEA',
                border: '1px solid #E0B4B4',
                borderRadius: 6,
                fontSize: 13,
                color: '#9B2C2C',
                lineHeight: 1.6,
              }}
            >
              {errorMessage}
              <button
                type="button"
                onClick={() => router.push('/history')}
                style={{
                  display: 'block',
                  marginTop: 8,
                  background: 'transparent',
                  border: 'none',
                  padding: 0,
                  color: '#9B2C2C',
                  textDecoration: 'underline',
                  fontSize: 13,
                  cursor: 'pointer',
                }}
              >
                지난 대화 정리하러 가기 →
              </button>
            </div>
          )}

          <div style={{ display: 'flex', gap: 8, marginTop: 28 }}>
            <button className="btn-L ghost" style={{ flex: 1 }} onClick={handleBack} disabled={loading}>
              이전
            </button>
            <button className="btn-L" style={{ flex: 1 }} onClick={handleConfirmStyle} disabled={loading}>
              {loading ? '생성 중...' : '대화 시작'}
            </button>
          </div>
        </div>
      </PhoneFrame>
    );
  }

  // Stage 2: Minor selection
  const selectedMiddle = major.middles.find((m) => m.id === selectedMiddleId);
  if (!selectedMiddle) return null;

  const hasCustom = selectedMiddle.minors.some((m) => m.allowCustomInput);
  const stage2BtnLabel = stylePicksPerSession ? '다음' : '대화 시작';

  return (
    <PhoneFrame tone="L">
      <PhoneHeader title="조금 더 구체적으로" onBack={handleBack} />
      <div style={{ padding: '8px 28px 28px', flex: 1, display: 'flex', flexDirection: 'column' }}>
        <div style={{ marginBottom: 28 }}>
          <Dashes n={totalSteps} done={3} />
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
              disabled={minor.allowCustomInput || loading}
              style={{
                padding: '14px 16px',
                border: '1px solid',
                borderColor: minor.allowCustomInput && customText.trim() ? 'var(--L-ink)' : 'var(--L-border)',
                borderRadius: 3,
                fontSize: 14,
                background: 'transparent',
                cursor: minor.allowCustomInput || loading ? 'default' : 'pointer',
                textAlign: 'left',
                transition: 'all 0.15s',
                opacity: (minor.allowCustomInput && !customText.trim()) || loading ? 0.5 : 1,
                ...(minor.allowCustomInput && customText.trim() ? {
                  display: '-webkit-box',
                  WebkitLineClamp: 2,
                  WebkitBoxOrient: 'vertical' as const,
                  overflow: 'hidden',
                } : {}),
              }}
            >
              {minor.allowCustomInput && customText.trim() ? customText : minor.label}
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
              <button className="btn-L ghost" style={{ flex: 1 }} onClick={handleBack} disabled={loading}>
                이전
              </button>
              <button
                className="btn-L"
                style={{ flex: 1 }}
                disabled={!customText.trim() || loading}
                onClick={() => handleMinorSelect('custom', true)}
              >
                {loading ? '생성 중...' : stage2BtnLabel}
              </button>
            </div>
          </div>
        )}
      </div>
    </PhoneFrame>
  );
}
