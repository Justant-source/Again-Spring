
'use client';

import { useEffect, useRef, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useUserStore, useHasHydrated } from '@/lib/store/userStore';
import { DeleteAccountModal } from '@/components/profile/DeleteAccountModal';
import { ChangePasswordSection } from '@/components/profile/ChangePasswordSection';
import { permissionsFor } from '@/lib/constants/userPermissions';
import { PhoneFrame, PhoneHeader } from '@/components/shared/PhoneFrame';
import { STYLE_MOTIF } from '@/components/shared/Motif';
import { COMMUNICATION_STYLES } from '@/lib/constants/communicationStyles';
import { MediatorStylePicker } from '@/components/session/MediatorStylePicker';
import { api } from '@/lib/api/client';
import type { User } from '@/lib/types';

export default function ProfilePage() {
  const router = useRouter();
  const user = useUserStore((s) => s.user);
  const setUser = useUserStore((s) => s.setUser);
  const clearUser = useUserStore((s) => s.clear);
  const hasHydrated = useHasHydrated();
  const [showDeleteModal, setShowDeleteModal] = useState(false);

  // V47~: 중재자 톤 슬라이더 — X축(팩트↔공감), Y축은 기본값 50 유지.
  // 변경 시 debounce 후 PATCH /api/users/me/mediator-style 로 저장.
  const [mediatorX, setMediatorX] = useState<number>(user?.mediatorDefaultX ?? 50);
  const mediatorTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    setMediatorX(user?.mediatorDefaultX ?? 50);
  }, [user?.mediatorDefaultX]);

  useEffect(() => {
    if (hasHydrated && !user) {
      router.push('/login');
    }
  }, [hasHydrated, user, router]);

  if (!hasHydrated || !user) {
    return null;
  }

  const showStyleSection = permissionsFor(user).ui.showCommunicationStyleSection;

  const style = user.communicationStyle
    ? COMMUNICATION_STYLES[user.communicationStyle]
    : null;

  const MotifComponent = user.communicationStyle
    ? STYLE_MOTIF[user.communicationStyle]
    : null;

  const onMediatorChange = (x: number) => {
    setMediatorX(x);
    if (mediatorTimerRef.current) clearTimeout(mediatorTimerRef.current);
    mediatorTimerRef.current = setTimeout(() => {
      if (x === user.mediatorDefaultX) return;
      // V47~: 새 전용 엔드포인트 사용
      api.patch('/api/users/me/mediator-style', { mediatorStyleX: x })
        .then(() => {
          setUser({ ...user, mediatorDefaultX: x });
        })
        .catch((e) => console.error('Mediator update failed:', e));
    }, 500);
  };

  const handleLogout = async () => {
    clearUser();
    router.push('/');
  };

  return (
    <PhoneFrame tone="L">
      <PhoneHeader title="내정보" tone="L" back={false} />
      <div
        style={{
          padding: '8px 28px 40px',
          display: 'flex',
          flexDirection: 'column',
          gap: 16,
        }}
      >
        {/* User info card */}
        <div
          className="letter-card"
          style={{
            padding: '18px 16px',
            marginTop: 8,
          }}
        >
          <div style={{ fontSize: 12, color: 'var(--L-sub)', marginBottom: 4 }}>
            닉네임
          </div>
          <div
            className="serif"
            style={{
              fontSize: 16,
              color: 'var(--L-ink)',
              fontWeight: 500,
              marginBottom: 12,
            }}
          >
            {user.nickname}
          </div>

          {user.email && (
            <>
              <div style={{ fontSize: 12, color: 'var(--L-sub)', marginBottom: 4 }}>
                이메일
              </div>
              <div
                style={{
                  fontSize: 13,
                  color: 'var(--L-ink)',
                  marginBottom: 12,
                  wordBreak: 'break-all',
                }}
              >
                {user.email}
              </div>
            </>
          )}

          {user.isGuest && (
            <div
              style={{
                padding: '10px 12px',
                background: 'var(--L-bg)',
                border: '1px solid var(--L-border)',
                borderRadius: '3px',
                fontSize: '12px',
                color: 'var(--L-sub)',
              }}
            >
              게스트 모드
            </div>
          )}
        </div>

        {/* Communication style card — admin은 정책으로 노출 안 함 */}
        {showStyleSection && (
        <div
          className="letter-card"
          style={{
            padding: '18px 16px',
          }}
        >
          <div style={{ fontSize: 12, color: 'var(--L-sub)', marginBottom: 10 }}>
            당신의 대화 스타일
          </div>

          {style && MotifComponent ? (
            <>
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 12,
                  marginBottom: 14,
                }}
              >
                <div
                  style={{
                    width: 56,
                    height: 56,
                    borderRadius: '50%',
                    background: style.color,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    color: 'white',
                  }}
                >
                  <MotifComponent size={28} color="white" />
                </div>
                <div>
                  <div
                    className="serif"
                    style={{
                      fontSize: 16,
                      color: 'var(--L-ink)',
                      fontWeight: 500,
                    }}
                  >
                    {style.label}
                  </div>
                  <div style={{ fontSize: 12, color: 'var(--L-sub)', marginTop: 2 }}>
                    {style.description}
                  </div>
                </div>
              </div>

              <div style={{ fontSize: 13, lineHeight: 1.6, color: 'var(--L-sub)', marginBottom: 16 }}>
                <div style={{ fontWeight: 500, marginBottom: 8 }}>강점</div>
                <ul style={{ paddingLeft: '16px', marginBottom: 12 }}>
                  {style.strengths.map((s: string, i: number) => (
                    <li key={i}>{s}</li>
                  ))}
                </ul>

                <div style={{ fontWeight: 500, marginBottom: 8 }}>유의할 점</div>
                <ul style={{ paddingLeft: '16px' }}>
                  {style.caution.map((c: string, i: number) => (
                    <li key={i}>{c}</li>
                  ))}
                </ul>
              </div>

              <div
                style={{
                  border: '1px solid var(--L-rule)',
                  borderRadius: 8,
                  overflow: 'hidden',
                }}
              >
                <div
                  style={{
                    padding: '10px 14px 8px',
                    fontSize: 11,
                    color: 'var(--L-sub)',
                    borderBottom: '1px solid var(--L-rule)',
                  }}
                >
                  스타일 다시 등록하기
                </div>
                {[
                  {
                    label: '10문항 다시 하기',
                    desc: '갈등 상황 기반 검사 · 약 2분',
                    href: '/onboarding?next=/profile',
                  },
                  {
                    label: 'MBTI 수정하기',
                    desc: '직접 입력으로 변경',
                    href: '/onboarding/mbti-input?next=/profile',
                  },
                ].map((opt, i) => (
                  <button
                    key={opt.label}
                    onClick={() => router.push(opt.href)}
                    style={{
                      width: '100%',
                      background: 'transparent',
                      border: 'none',
                      borderTop: i === 0 ? 'none' : '1px solid var(--L-rule)',
                      padding: '12px 14px',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                      cursor: 'pointer',
                      textAlign: 'left',
                    }}
                  >
                    <div>
                      <div style={{ fontSize: 13, color: 'var(--L-ink)', marginBottom: 2 }}>
                        {opt.label}
                      </div>
                      <div style={{ fontSize: 11, color: 'var(--L-sub)' }}>{opt.desc}</div>
                    </div>
                    <span style={{ color: 'var(--L-sub)', fontSize: 16, marginLeft: 12, flexShrink: 0 }}>›</span>
                  </button>
                ))}
              </div>
            </>
          ) : (
            <>
              <div style={{ fontSize: 13, color: 'var(--L-sub)', lineHeight: 1.6, marginBottom: 14 }}>
                아직 대화 스타일이 등록되지 않았어요.
                <br />10문항으로 내 스타일을 파악해보세요.
              </div>
              <button
                className="btn-L"
                style={{ width: '100%' }}
                onClick={() => router.push('/onboarding/intro?next=/profile')}
              >
                10문항 시작하기
              </button>
            </>
          )}
        </div>
        )}

        {/* 중재자 대화 스타일 — 새 대화 시작 시 picker 기본값. 매 세션에서 다시 조정 가능. */}
        <div className="letter-card" style={{ padding: '18px 16px' }}>
          <div style={{ fontSize: 12, color: 'var(--L-sub)', marginBottom: 10 }}>
            중재자 대화 스타일
          </div>
          <MediatorStylePicker value={mediatorX} onChange={onMediatorChange} showHeader={false} />
        </div>

        {/* 비밀번호 변경 (이메일 가입자만) */}
        <ChangePasswordSection />

        {/* 관리자 진입 카드 — showAdminEntryButton 조건 */}
        {permissionsFor(user).ui.showAdminEntryButton && (
          <div className="letter-card" style={{ padding: '4px 0' }}>
            <button
              onClick={() => router.push('/admin')}
              style={{ width: '100%', background: 'none', border: 'none', padding: '14px 16px', display: 'flex', alignItems: 'center', justifyContent: 'space-between', cursor: 'pointer', textAlign: 'left' }}
            >
              <div style={{ fontSize: 14, color: 'var(--L-ink)', fontWeight: 500 }}>관리자 대시보드</div>
              <span style={{ color: 'var(--L-sub)', fontSize: 16 }}>›</span>
            </button>
            {permissionsFor(user).admin.canAccessMarketing && (
              <button
                onClick={() => router.push('/admin/marketing')}
                style={{ width: '100%', background: 'none', border: 'none', borderTop: '1px solid var(--L-border)', padding: '14px 16px', display: 'flex', alignItems: 'center', justifyContent: 'space-between', cursor: 'pointer', textAlign: 'left' }}
              >
                <div style={{ fontSize: 14, color: 'var(--L-ink)', fontWeight: 500 }}>마케팅 관리</div>
                <span style={{ color: 'var(--L-sub)', fontSize: 16 }}>›</span>
              </button>
            )}
          </div>
        )}

        {/* Actions */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 8, marginTop: 12 }}>
          <button onClick={handleLogout} className="btn-L" style={{ width: '100%' }}>
            로그아웃
          </button>
          <button
            onClick={() => setShowDeleteModal(true)}
            style={{
              width: '100%',
              padding: '12px 16px',
              background: 'transparent',
              color: '#B94040',
              border: '1px solid #B94040',
              borderRadius: '3px',
              fontSize: '14px',
              fontWeight: 500,
              cursor: 'pointer',
            }}
          >
            계정 삭제
          </button>
        </div>

        {/* 법적 링크 + 위기 핫라인 */}
        <div style={{ marginTop: 24, paddingTop: 16, borderTop: '1px solid var(--L-border)', display: 'flex', justifyContent: 'center', gap: 16, flexWrap: 'wrap' }}>
          {[
            { href: '/terms', label: '이용약관' },
            { href: '/privacy', label: '개인정보처리방침' },
          ].map((link) => (
            <a key={link.href} href={link.href} style={{ fontSize: 12, color: 'var(--L-sub)', textDecoration: 'none' }}>
              {link.label}
            </a>
          ))}
          <a href="tel:1393" style={{ fontSize: 12, color: 'var(--L-sub)', textDecoration: 'none' }}>
            위기 상담 1393
          </a>
        </div>
      </div>
      <DeleteAccountModal
        open={showDeleteModal}
        user={user}
        onClose={() => setShowDeleteModal(false)}
        onDeleted={() => {
          clearUser();
          router.push('/');
        }}
      />
    </PhoneFrame>
  );
}
