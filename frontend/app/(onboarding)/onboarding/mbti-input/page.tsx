'use client';

import { useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { PhoneFrame, PhoneHeader } from '@/components/shared/PhoneFrame';
import { MbtiAxisSlider } from '@/components/onboarding/MbtiAxisSlider';
import { MBTI_TO_STYLE } from '@/lib/constants/mbtiMapping';
import { COMMUNICATION_STYLES } from '@/lib/constants/communicationStyles';
import { useUserStore } from '@/lib/store/userStore';
import type { MbtiProfile } from '@/lib/types';

const AXES = [
  { axisLabel: '에너지 방향', leftLetter: 'E', leftLabel: '외향', rightLetter: 'I', rightLabel: '내향', key: 'e_i' as const },
  { axisLabel: '정보 수집',   leftLetter: 'S', leftLabel: '감각', rightLetter: 'N', rightLabel: '직관', key: 's_n' as const },
  { axisLabel: '의사 결정',   leftLetter: 'T', leftLabel: '사고', rightLetter: 'F', rightLabel: '감정', key: 't_f' as const },
  { axisLabel: '생활 양식',   leftLetter: 'J', leftLabel: '판단', rightLetter: 'P', rightLabel: '인식', key: 'j_p' as const },
];

function calcType(profile: MbtiProfile): string {
  return (
    (profile.e_i < 50 ? 'E' : 'I') +
    (profile.s_n < 50 ? 'S' : 'N') +
    (profile.t_f < 50 ? 'T' : 'F') +
    (profile.j_p < 50 ? 'J' : 'P')
  );
}

export default function MbtiInputPage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const setStyle = useUserStore((s) => s.setStyle);
  const setMbtiType = useUserStore((s) => s.setMbtiType);

  const nextPath = searchParams.get('next') ?? '/session/new';

  const [profile, setProfile] = useState<MbtiProfile>({ e_i: 50, s_n: 50, t_f: 50, j_p: 50 });

  const mbtiType = calcType(profile);
  const mappedStyle = MBTI_TO_STYLE[mbtiType];

  const update = (key: keyof MbtiProfile, val: number) =>
    setProfile((p) => ({ ...p, [key]: val }));

  const handleConfirm = () => {
    setStyle(mappedStyle);
    setMbtiType(mbtiType);
    router.push(`/onboarding/result?next=${encodeURIComponent(nextPath)}`);
  };

  const isCenter = Object.values(profile).every((v) => v === 50);

  return (
    <PhoneFrame tone="L">
      <PhoneHeader title="MBTI 슬라이더 입력" back={true} onBack={() => router.back()} />
      <div style={{ padding: '20px 28px 32px', flex: 1, display: 'flex', flexDirection: 'column' }}>
        <div className="serif" style={{ fontSize: 18, lineHeight: 1.55, marginBottom: 6, textAlign: 'center' }}>
          각 축의 비율을<br />자유롭게 조절해주세요
        </div>
        <div style={{ fontSize: 12, color: 'var(--L-sub)', textAlign: 'center', marginBottom: 28 }}>
          슬라이더를 움직여 나에게 가까운 쪽으로
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 24, marginBottom: 28 }}>
          {AXES.map((axis) => (
            <MbtiAxisSlider
              key={axis.key}
              axisLabel={axis.axisLabel}
              leftLetter={axis.leftLetter}
              leftLabel={axis.leftLabel}
              rightLetter={axis.rightLetter}
              rightLabel={axis.rightLabel}
              value={profile[axis.key]}
              onChange={(v) => update(axis.key, v)}
            />
          ))}
        </div>

        {/* Preview */}
        <div
          style={{
            flex: 1,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            flexDirection: 'column',
            gap: 6,
            minHeight: 70,
          }}
        >
          <div
            style={{
              fontSize: 34,
              fontWeight: 700,
              letterSpacing: 6,
              color: isCenter ? 'var(--L-rule)' : 'var(--L-accent)',
              transition: 'color 0.2s',
            }}
          >
            {mbtiType}
          </div>
          {!isCenter && (
            <div style={{ fontSize: 13, color: 'var(--L-sub)' }}>
              {COMMUNICATION_STYLES[mappedStyle].emoji} {COMMUNICATION_STYLES[mappedStyle].label} 성향과 연결돼요
            </div>
          )}
          {isCenter && (
            <div style={{ fontSize: 12, color: 'var(--L-sub)' }}>슬라이더를 움직여 유형을 결정해주세요</div>
          )}
        </div>

        <button
          className="btn-L"
          onClick={handleConfirm}
          disabled={isCenter}
          style={{ marginTop: 12 }}
        >
          이 유형으로 완료하기
        </button>
        <button
          className="btn-L ghost"
          style={{ marginTop: 8, fontSize: 12 }}
          onClick={() => router.push(`/onboarding/mbti-test${searchParams.get('next') ? `?next=${searchParams.get('next')}` : ''}`)}
        >
          📋 8문항 간이 검사로 알아보기
        </button>
      </div>
    </PhoneFrame>
  );
}
