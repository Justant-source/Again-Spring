// ✅ MOCKUP APPLIED — source: design/handoff/tone-L-screens.jsx (InputDescribe)

'use client';

import { useRouter, useSearchParams } from 'next/navigation';
import { useState } from 'react';
import { useSessionStore } from '@/lib/store/sessionStore';
import { CATEGORIES } from '@/lib/constants/categories';
import { checkKeywords } from '@/lib/utils/keywordGuard';
import { CRISIS_RESOURCES_IMMEDIATE, CRISIS_RESOURCES_LEGAL } from '@/lib/constants/crisisResources';
import { api } from '@/lib/api/client';
import { PhoneFrame, PhoneHeader, Dashes } from '@/components/shared';
import { getDescribePlaceholder } from '@/lib/utils/describePlaceholder';

export default function DescribePage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const { category, relationType, description, setDescription, setSession, setRole, setPartnerNickname } =
    useSessionStore();

  const [text, setText] = useState(description);
  const [crisisLevel, setCrisisLevel] = useState<1 | 2 | null>(null);
  const [warningBannerDismissed, setWarningBannerDismissed] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState(false);

  const role = searchParams.get('role') as 'A' | 'B' | null;
  const isBMode = role === 'B';

  const handleBack = () => {
    if (isBMode) {
      router.push('/session/invite');
    } else {
      router.push('/session/category');
    }
  };

  // Redirect if no category selected and not in B-mode
  if (!isBMode && !category) {
    router.push('/session/category');
    return null;
  }

  // Get category labels
  let breadcrumb = '';
  if (category && !isBMode) {
    const major = CATEGORIES.find((m) => m.id === category.majorId);
    const middle = major?.middles.find((m) => m.id === category.middleId);
    const minor = middle?.minors.find((m) => m.id === category.minorId);
    breadcrumb = `${major?.label} · ${middle?.label} · ${minor?.label}`;
  } else if (isBMode) {
    breadcrumb = '상대의 입장에서 본 상황';
  }

  const handleTextChange = (value: string) => {
    setText(value);

    // Check keywords on every change
    const check = checkKeywords(value);
    if (check.level === 1) {
      setCrisisLevel(1);
    } else if (check.level === 2) {
      setCrisisLevel(2);
      setWarningBannerDismissed(false);
    } else {
      setCrisisLevel(null);
      setWarningBannerDismissed(false);
    }
  };

  const handleSubmit = async () => {
    if (text.length < 20) return; // Button disabled anyway
    if (crisisLevel === 1) return; // Should not be able to submit

    setIsSubmitting(true);
    setSubmitError(false);

    try {
      if (isBMode) {
        setDescription(text);
        setRole('B');
        router.push('/session/mediation?role=B');
      } else {
        const response = await api.post('/api/sessions', {
          relationType,
          category,
          description: text,
        });

        const { id, inviteToken } = response.data;
        setDescription(text);
        setSession({ id, inviteToken });
        setRole('A');
        router.push('/session/invite');
      }
    } catch (error) {
      console.error('Error submitting description:', error);
      setSubmitError(true);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <PhoneFrame tone="L">
      <PhoneHeader title={isBMode ? '당신의 마음을 들려주세요' : '당신의 마음을 들려주세요'} onBack={handleBack} />
      <div style={{ padding: '8px 28px 28px', flex: 1, display: 'flex', flexDirection: 'column', overflow: 'auto' }}>
        <div style={{ marginBottom: 28 }}>
          <Dashes n={4} done={4} />
        </div>

        {breadcrumb && (
          <div style={{ fontSize: 12, color: 'var(--L-sub)', marginBottom: 12 }}>
            {breadcrumb}
          </div>
        )}

        <div className="serif" style={{ fontSize: 19, lineHeight: 1.5, marginBottom: 18 }}>
          {isBMode
            ? <>이번엔 당신의 이야기를<br />들려주세요.</>
            : <>어떤 일이 있었는지<br />편한 말로 적어주세요.</>}
        </div>

        {/* Crisis Modal - Level 1 */}
        {crisisLevel === 1 && (
          <div
            style={{
              position: 'fixed',
              inset: 0,
              background: 'rgba(0, 0, 0, 0.5)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              zIndex: 1000,
            }}
          >
            <div
              className="letter-card"
              style={{
                width: '90%',
                maxWidth: 340,
                padding: 24,
                textAlign: 'center',
              }}
            >
              <div className="serif" style={{ fontSize: 18, marginBottom: 16 }}>
                🚨 중요한 안내
              </div>
              <div style={{ fontSize: 13, color: 'var(--L-sub)', lineHeight: 1.7, marginBottom: 24 }}>
                말씀해주신 상황은 저희 서비스의 범위를 넘어서는 매우 중요한 문제예요. 지금 바로 전문 기관의 도움을
                받아주세요.
              </div>

              <div style={{ display: 'flex', flexDirection: 'column', gap: 8, marginBottom: 16 }}>
                <div style={{ fontSize: 11, fontWeight: 600, color: 'var(--L-ink)', marginBottom: 4, textAlign: 'left' }}>즉각적 위기</div>
                {CRISIS_RESOURCES_IMMEDIATE.map((res) => (
                  <div
                    key={res.phone}
                    style={{
                      padding: 10,
                      border: '1px solid var(--L-border)',
                      borderRadius: 3,
                      fontSize: 12,
                      textAlign: 'left',
                    }}
                  >
                    <div style={{ fontWeight: 500, marginBottom: 2 }}>
                      {res.label} · {res.phone}
                    </div>
                    <div style={{ color: 'var(--L-sub)', fontSize: 11 }}>{res.hours}</div>
                  </div>
                ))}
              </div>

              <button className="btn-L" style={{ width: '100%' }} onClick={() => setCrisisLevel(null)}>
                닫기
              </button>
            </div>
          </div>
        )}

        {/* Warning Banner - Level 2 */}
        {crisisLevel === 2 && !warningBannerDismissed && (
          <div
            style={{
              marginBottom: 16,
              padding: 12,
              background: 'rgba(255, 200, 50, 0.1)',
              border: '1px solid var(--L-border)',
              borderRadius: 3,
              fontSize: 12,
            }}
          >
            <div style={{ marginBottom: 8 }}>
              <span style={{ fontWeight: 500 }}>⚠️ 안내</span>
            </div>
            <div style={{ color: 'var(--L-sub)', lineHeight: 1.6, marginBottom: 8 }}>
              법적 결정사항들(이혼, 소송 등)은 저희 서비스 범위를 벗어나요. 법적 조언이 필요하시면
              대한법률구조공단(132)을 이용해주세요.
            </div>
            <button
              className="btn-L ghost"
              style={{ fontSize: 11, padding: '6px 10px' }}
              onClick={() => setWarningBannerDismissed(true)}
            >
              알겠습니다
            </button>
          </div>
        )}

        <div style={{ position: 'relative', marginBottom: 28 }}>
          <textarea
            className="ta-L"
            placeholder={isBMode ? '' : getDescribePlaceholder(category?.majorId ?? '', category?.middleId ?? '')}
            value={text}
            onChange={(e) => handleTextChange(e.target.value)}
            style={{ minHeight: 160, maxHeight: 400 }}
          />
          <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 8, fontSize: 11, color: 'var(--L-sub)' }}>
            <span>{!isBMode && '상대의 이름은 쓰지 않으셔도 괜찮아요'}</span>
            <span>
              {text.length} / 600
            </span>
          </div>
        </div>

        <div style={{ fontSize: 12, color: 'var(--L-sub)', lineHeight: 1.6, marginBottom: 28 }}>
          · 적으신 내용은 상대방에게 그대로 보이지 않아요.
          <br />· 중재자가 따뜻한 언어로 정리해 전달합니다.
        </div>

        <button
          className="btn-L"
          style={{ width: '100%' }}
          disabled={text.length < 20 || crisisLevel === 1 || isSubmitting}
          onClick={handleSubmit}
        >
          {isSubmitting ? '처리 중...' : isBMode ? '완료' : '다음 — 상대에게 초대 보내기'}
        </button>
        {submitError && (
          <div style={{ marginTop: 10, fontSize: 12, color: '#C0392B', textAlign: 'center' }}>
            오류가 발생했어요. 잠시 후 다시 시도해주세요.
          </div>
        )}
      </div>
    </PhoneFrame>
  );
}
