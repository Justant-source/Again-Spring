'use client';

import { MbtiAxisSlider } from '@/components/onboarding/MbtiAxisSlider';

export interface MediatorStyle {
  x: number; // 팩트(0) ↔ 공감(100)
  y: number; // 경청(0) ↔ 능동(100)
}

export interface MediatorStylePickerProps {
  value: MediatorStyle;
  onChange: (v: MediatorStyle) => void;
}

/**
 * 중재자 성향 2D 슬라이더 선택 컴포넌트
 * X축: 팩트 기반 ↔ 공감 기반
 * Y축: 경청·반영 중심 ↔ 능동·질문 중심
 */
export function MediatorStylePicker({ value, onChange }: MediatorStylePickerProps) {
  const update = (axis: 'x' | 'y', val: number) => {
    onChange({ ...value, [axis]: val });
  };

  // 성향 조합 이름 결정
  const getStyleName = () => {
    const x = value.x;
    const y = value.y;

    if (x <= 30 && y <= 30) {
      return '공감하며 이야기를 부드럽게 받아주는 형';
    } else if (x >= 70 && y >= 70) {
      return '논리적으로 탐색 질문을 던지는 형';
    } else if (x <= 30 && y >= 70) {
      return '감정을 인식하며 적극적으로 질문하는 형';
    } else if (x >= 70 && y <= 30) {
      return '사실 중심으로 정리하며 듣는 형';
    } else {
      return '사실과 감정을 균형있게 다루는 형';
    }
  };

  const isCenter = value.x === 50 && value.y === 50;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 24 }}>
      <MbtiAxisSlider
        axisLabel="의사소통 방식"
        leftLetter="공"
        leftLabel="공감 기반"
        rightLetter="팩"
        rightLabel="팩트 기반"
        value={value.x}
        onChange={(v) => update('x', v)}
      />

      <MbtiAxisSlider
        axisLabel="중재 스타일"
        leftLetter="경"
        leftLabel="경청·반영"
        rightLetter="능"
        rightLabel="능동·질문"
        value={value.y}
        onChange={(v) => update('y', v)}
      />

      {/* Preview */}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          flexDirection: 'column',
          gap: 6,
          minHeight: 60,
          padding: '16px',
          borderRadius: 3,
          backgroundColor: 'var(--L-bkg)',
        }}
      >
        <div
          style={{
            fontSize: 14,
            fontWeight: 500,
            color: isCenter ? 'var(--L-sub)' : 'var(--L-ink)',
            textAlign: 'center',
            lineHeight: 1.5,
            transition: 'color 0.2s',
          }}
        >
          {getStyleName()}
        </div>
      </div>
    </div>
  );
}
