
'use client';

/**
 * V47~: 카테고리 중·소분류 선택 제거.
 * 대분류는 /session/new에서 선택 완료.
 * 이 화면은 중재자 성향 슬라이더 단일 화면으로 대체됨 (회원·게스트 공통).
 *
 * 흐름: /session/new(대분류 선택, step 1) → /session/category(중재자 성향, step 2) → 세션 생성
 */

export const dynamic = 'force-dynamic';

import { useRouter } from 'next/navigation';
import { useState, useEffect } from 'react';
import { useSessionStore } from '@/lib/store/sessionStore';
import { useUserStore } from '@/lib/store/userStore';
import { PhoneFrame, PhoneHeader, Dashes } from '@/components/shared';
import { MediatorStylePicker } from '@/components/session/MediatorStylePicker';
import { api } from '@/lib/api/client';

export default function CategoryPage() {
  const router = useRouter();
  const { relationType } = useSessionStore();
  const user = useUserStore((s) => s.user);

  // 회원: 프로필 저장값 프리필, 게스트/미설정: 50/50
  const [styleX, setStyleX] = useState<number>(user?.mediatorDefaultX ?? 50);
  const [styleY, setStyleY] = useState<number>(user?.mediatorDefaultY ?? 50);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  // user 로드 후 mediatorDefault 값으로 동기화 (초기 렌더 직후)
  useEffect(() => {
    if (user?.mediatorDefaultX != null) setStyleX(user.mediatorDefaultX);
    if (user?.mediatorDefaultY != null) setStyleY(user.mediatorDefaultY);
  }, [user?.mediatorDefaultX, user?.mediatorDefaultY]);

  if (!relationType) {
    router.push('/session/new');
    return null;
  }

  const handleBack = () => {
    router.push('/session/new');
  };

  const handleStart = async () => {
    setLoading(true);
    setErrorMessage(null);
    try {
      const response = await api.post('/api/sessions', {
        relationType,
        mediatorStyleX: styleX,
        mediatorStyleY: styleY,
      });
      const { id } = response.data;
      router.push(`/session/chat/${id}`);
    } catch (error: unknown) {
      const err = error as {
        response?: { status?: number; data?: { error?: { message?: string; code?: string } } };
      };
      const status = err?.response?.status;
      if (status === 401 || status === 403) return;

      console.error('Failed to create session:', error);
      const beMessage = err?.response?.data?.error?.message;
      setErrorMessage(beMessage || '대화를 시작할 수 없어요. 잠시 후 다시 시도해 주세요.');
      setLoading(false);
    }
  };

  const hasProfileDefault = user?.mediatorDefaultX != null || user?.mediatorDefaultY != null;

  return (
    <PhoneFrame tone="L">
      <PhoneHeader title="중재자 성향" onBack={handleBack} />
      <div style={{ padding: '8px 28px 28px', flex: 1, display: 'flex', flexDirection: 'column' }}>
        <div style={{ marginBottom: 28 }}>
          <Dashes n={2} done={2} />
        </div>

        <div className="serif" style={{ fontSize: 18, lineHeight: 1.5, marginBottom: 8 }}>
          이번 대화의 중재자 톤을<br />정해주세요.
        </div>
        <div style={{ fontSize: 12, color: 'var(--L-sub)', marginBottom: 28, lineHeight: 1.6 }}>
          {hasProfileDefault
            ? '프로필에 저장한 기본값을 불러왔어요.'
            : '프로필에서 기본값을 저장할 수도 있어요.'}
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
          <button className="btn-L" style={{ flex: 1 }} onClick={handleStart} disabled={loading}>
            {loading ? '생성 중...' : '대화 시작'}
          </button>
        </div>
      </div>
    </PhoneFrame>
  );
}
