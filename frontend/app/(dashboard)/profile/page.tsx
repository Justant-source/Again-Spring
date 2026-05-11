// ⚠️ MOCKUP PENDING — design/mockups/10-profile/ not yet provided; baseline Tone L layout used

'use client';

import { useEffect, useRef, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useUserStore, useHasHydrated } from '@/lib/store/userStore';
import { DeleteAccountModal } from '@/components/profile/DeleteAccountModal';
import { ChangePasswordSection } from '@/components/profile/ChangePasswordSection';
import { permissionsFor } from '@/lib/constants/userPermissions';
import { PhoneFrame, PhoneHeader } from '@/components/shared/PhoneFrame';
import { STYLE_MOTIF } from '@/components/shared/Motif';
import {
  COMMUNICATION_STYLES,
  defaultMediatorXFor,
  closestStyleFor,
} from '@/lib/constants/communicationStyles';
import { api } from '@/lib/api/client';
import type { CommunicationStyle, User } from '@/lib/types';

export default function ProfilePage() {
  const router = useRouter();
  const user = useUserStore((s) => s.user);
  const setUser = useUserStore((s) => s.setUser);
  const clearUser = useUserStore((s) => s.clear);
  const hasHydrated = useHasHydrated();
  const [showDeleteModal, setShowDeleteModal] = useState(false);

  // 슬라이더 로컬 상태 — 사용자 입력 → debounce → PATCH /api/users/me
  const initialStyleX = user
    ? defaultMediatorXFor(user.communicationStyle, 50)
    : 50;
  const initialMediatorX = user?.mediatorDefaultX ?? initialStyleX;
  const [styleX, setStyleX] = useState<number>(initialStyleX);
  const [mediatorX, setMediatorX] = useState<number>(initialMediatorX);
  const [saving, setSaving] = useState<'style' | 'mediator' | null>(null);
  const styleTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const mediatorTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  // user 변경 시(다른 디바이스 동기화 등) 슬라이더 값 동기화
  useEffect(() => {
    if (!user) return;
    setStyleX(defaultMediatorXFor(user.communicationStyle, 50));
    setMediatorX(user.mediatorDefaultX ?? defaultMediatorXFor(user.communicationStyle, 50));
  }, [user?.communicationStyle, user?.mediatorDefaultX]);

  useEffect(() => {
    if (hasHydrated && !user) {
      router.push('/login');
    }
  }, [hasHydrated, user, router]);

  if (!hasHydrated || !user) {
    return null;
  }

  const showStyleSection = permissionsFor(user).ui.showCommunicationStyleSection;

  // 슬라이더 값으로부터 enum motif 유도 (사용자가 드래그 중이면 실시간 반영)
  const effectiveStyle: CommunicationStyle = closestStyleFor(styleX);
  const style = COMMUNICATION_STYLES[effectiveStyle];
  const MotifComponent = STYLE_MOTIF[effectiveStyle];

  const persistStyle = (x: number) => {
    const nextEnum = closestStyleFor(x);
    if (nextEnum === user.communicationStyle) return;
    setSaving('style');
    api.patch<User>('/api/users/me', { communicationStyle: nextEnum })
      .then((res) => setUser(res.data))
      .catch((e) => console.error('Style update failed:', e))
      .finally(() => setSaving(null));
  };

  const persistMediator = (x: number) => {
    if (x === user.mediatorDefaultX) return;
    setSaving('mediator');
    api.patch<User>('/api/users/me', { mediatorDefaultX: x })
      .then((res) => setUser(res.data))
      .catch((e) => console.error('Mediator update failed:', e))
      .finally(() => setSaving(null));
  };

  const onStyleChange = (x: number) => {
    setStyleX(x);
    if (styleTimerRef.current) clearTimeout(styleTimerRef.current);
    styleTimerRef.current = setTimeout(() => persistStyle(x), 500);
  };

  const onMediatorChange = (x: number) => {
    setMediatorX(x);
    if (mediatorTimerRef.current) clearTimeout(mediatorTimerRef.current);
    mediatorTimerRef.current = setTimeout(() => persistMediator(x), 500);
  };

  const handleLogout = async () => {
    clearUser();
    router.push('/');
  };

  return (
    <PhoneFrame tone="L">
      <PhoneHeader title={showStyleSection ? '내 대화 성향' : '프로필'} tone="L" onBack={() => router.back()} />
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

          {user.communicationStyle ? (
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

              {/* 슬라이더 1: 나의 대화 성향 — 슬라이더 X → 가장 가까운 enum으로 자동 저장 */}
              <div style={{ marginBottom: 18 }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', marginBottom: 6 }}>
                  <div style={{ fontSize: 13, color: 'var(--L-ink)', fontWeight: 500 }}>
                    내 대화 성향 직접 조정
                  </div>
                  <div style={{ fontSize: 11, color: 'var(--L-sub)' }}>
                    {saving === 'style' ? '저장 중…' : `→ ${style.label}`}
                  </div>
                </div>
                <input
                  type="range"
                  min={0}
                  max={100}
                  step={5}
                  value={styleX}
                  onChange={(e) => onStyleChange(Number(e.target.value))}
                  aria-label="나의 대화 성향(0=팩트 중심, 100=공감 중심)"
                  style={{ width: '100%', accentColor: 'var(--L-ink)' }}
                />
                <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 11, color: 'var(--L-sub)', marginTop: 2 }}>
                  <span>← 팩트 중심</span>
                  <span style={{ color: 'var(--L-ink)', fontWeight: 500 }}>{styleX}/100</span>
                  <span>공감 중심 →</span>
                </div>
              </div>

              {/* 슬라이더 2: 중재자 톤 기본값 — User.mediator_default_x로 저장 */}
              <div style={{ marginBottom: 18 }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', marginBottom: 6 }}>
                  <div style={{ fontSize: 13, color: 'var(--L-ink)', fontWeight: 500 }}>
                    중재자 톤 기본값
                  </div>
                  <div style={{ fontSize: 11, color: 'var(--L-sub)' }}>
                    {saving === 'mediator'
                      ? '저장 중…'
                      : user.mediatorDefaultX != null
                        ? '맞춤 값 저장됨'
                        : '프로필 성향 기반'}
                  </div>
                </div>
                <input
                  type="range"
                  min={0}
                  max={100}
                  step={5}
                  value={mediatorX}
                  onChange={(e) => onMediatorChange(Number(e.target.value))}
                  aria-label="새 대화 시작 시 중재자 톤 기본값(0=팩트, 100=공감)"
                  style={{ width: '100%', accentColor: 'var(--L-ink)' }}
                />
                <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 11, color: 'var(--L-sub)', marginTop: 2 }}>
                  <span>← 팩트 중심</span>
                  <span style={{ color: 'var(--L-ink)', fontWeight: 500 }}>{mediatorX}/100</span>
                  <span>공감 중심 →</span>
                </div>
                <div style={{ fontSize: 11, color: 'var(--L-sub)', marginTop: 6, lineHeight: 1.5 }}>
                  새 대화 시작 시 중재자 톤 picker의 시작값으로 쓰여요. 매 세션에서 다시 조정 가능.
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

        {/* 비밀번호 변경 (이메일 가입자만) */}
        <ChangePasswordSection />

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
