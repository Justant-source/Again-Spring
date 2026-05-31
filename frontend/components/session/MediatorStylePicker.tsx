'use client';

/**
 * 중재자 성향(공감↔팩트) 단일 축 슬라이더.
 * - x = 0: 팩트 중심 (객관적 정리)
 * - x = 50: 균형 (기본값)
 * - x = 100: 공감 중심 (감정 인정)
 * - y는 본 picker에서 노출하지 않으며, 호출 측에서 50 고정으로 전송한다.
 *
 * 사용 등급(user-permissions.json):
 *   - 모든 tier가 styleSource = 'per_session' → 매 세션 진입 시 본 컴포넌트로 다시 선택
 */
export interface MediatorStylePickerProps {
  value: number; // 0 ~ 100
  onChange: (x: number) => void;
  /** 슬라이더 위 헤더("이번 대화의 중재자 톤" 라벨) 노출 여부. 기본 true. */
  showHeader?: boolean;
}

export function MediatorStylePicker({ value, onChange, showHeader = true }: MediatorStylePickerProps) {
  const label = describe(value);

  return (
    <div style={{ width: '100%' }}>
      <div style={{ marginBottom: 14 }}>
        <div className="serif" style={{ fontSize: 16, color: 'var(--L-ink)' }}>
          {label.title}
        </div>
        <div style={{ fontSize: 12, color: 'var(--L-sub)', marginTop: 4, lineHeight: 1.6 }}>
          {label.description}
        </div>
      </div>

      <input
        type="range"
        min={0}
        max={100}
        step={5}
        value={value}
        onChange={(e) => onChange(Number(e.target.value))}
        aria-label="중재자의 공감-팩트 비율 (0=팩트 중심, 100=공감 중심)"
        style={{
          width: '100%',
          accentColor: 'var(--L-ink)',
          marginBottom: 6,
        }}
      />

      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          fontSize: 11,
          color: 'var(--L-sub)',
          marginTop: 2,
        }}
      >
        <span>← 팩트 중심</span>
        <span style={{ fontWeight: 500, color: 'var(--L-ink)' }}>{value} / 100</span>
        <span>공감 중심 →</span>
      </div>
    </div>
  );
}

function describe(x: number): { title: string; description: string } {
  if (x <= 25) {
    return {
      title: '팩트 중심',
      description: '상황을 객관적으로 정리하고 사실 관계를 짚어드릴게요.',
    };
  }
  if (x <= 45) {
    return {
      title: '약간 팩트 우세',
      description: '감정도 듣지만, 상황 정리에 좀 더 무게를 둘게요.',
    };
  }
  if (x <= 55) {
    return {
      title: '균형',
      description: '감정 인정과 상황 정리를 같은 비중으로 다룰게요.',
    };
  }
  if (x <= 75) {
    return {
      title: '약간 공감 우세',
      description: '먼저 마음을 충분히 들어드린 뒤 정리에 들어갈게요.',
    };
  }
  return {
    title: '공감 중심',
    description: '감정을 충분히 받아주고, 정리는 부드럽게 곁들일게요.',
  };
}
