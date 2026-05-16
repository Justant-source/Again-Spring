import type { CommunicationStyle } from '@/lib/types';

export interface StyleDefinition {
  id: CommunicationStyle;
  label: string;
  motif:
    | 'wave'
    | 'mountain'
    | 'flame'
    | 'leaf'
    | 'moon'
    | 'star';
  description: string;
  strengths: string[];
  caution: string[];
  color: string;
}

export const COMMUNICATION_STYLES: Record<CommunicationStyle, StyleDefinition> = {
  wave: {
    id: 'wave',
    label: '파도형',
    motif: 'wave',
    description: '감정 표현이 풍부하고 즉각적인 스타일',
    strengths: ['진솔한 감정 표현', '따뜻한 공감 능력'],
    caution: ['감정 격앙 시 휴식 필요', '상대에게 숨 돌릴 시간 주기'],
    color: '#60A5FA',
  },
  mountain: {
    id: 'mountain',
    label: '산형',
    motif: 'mountain',
    description: '차분하고 거리를 두고 생각하는 스타일',
    strengths: ['평정심', '신중한 판단'],
    caution: ['표현 부족으로 오해 가능', '감정 공유 노력 필요'],
    color: '#78716C',
  },
  flame: {
    id: 'flame',
    label: '불꽃형',
    motif: 'flame',
    description: '직설적이고 명확함을 선호하는 스타일',
    strengths: ['명확한 의사 전달', '빠른 문제 해결'],
    caution: ['말투가 상처될 수 있음', '부드러운 시작 필요'],
    color: '#F87171',
  },
  leaf: {
    id: 'leaf',
    label: '이파리형',
    motif: 'leaf',
    description: '조화와 공감을 중시하는 스타일',
    strengths: ['뛰어난 공감력', '관계 조율 능력'],
    caution: ['자기 욕구 표현 부족', '솔직한 의사 표현 연습'],
    color: '#4ADE80',
  },
  moon: {
    id: 'moon',
    label: '달빛형',
    motif: 'moon',
    description: '말보다 분위기·행동으로 표현하는 스타일',
    strengths: ['세심한 배려', '행동을 통한 사랑'],
    caution: ['상대가 오해할 수 있음', '말로도 표현해주세요'],
    color: '#A78BFA',
  },
  star: {
    id: 'star',
    label: '별빛형',
    motif: 'star',
    description: '논리와 이유를 중시하는 스타일',
    strengths: ['구조적 사고', '근거 있는 대화'],
    caution: ['감정 인정 먼저 하기', '상대 감정 덮어쓰지 않기'],
    color: '#FBBF24',
  },
};

export interface StyleCombinationInsight {
  strength: string;
  challenge: string;
  advice: string;
}

export const STYLE_COMBINATION_INSIGHTS: Record<string, StyleCombinationInsight> = {
  'wave-mountain': {
    strength: '감정과 이성의 균형을 맞출 수 있는 조합',
    challenge: '파도형이 감정 표현할 때 산형이 거리 두면 서운함 발생',
    advice: '파도형은 감정 표출 후 회복 시간 주기, 산형은 감정 인정 먼저',
  },
  'wave-flame': {
    strength: '표현이 강한 두 사람, 소통이 활발함',
    challenge: '둘 다 감정적·직설적이라 충돌 시 격해지기 쉬움',
    advice: 'Cooldown 규칙 정하기: 격해지면 20분 휴식',
  },
  'mountain-flame': {
    strength: '논리적 사고의 두 사람',
    challenge: '불꽃형이 답답해하고 산형이 부담스러워할 수 있음',
    advice: '불꽃형은 기다려주기, 산형은 조금 더 표현하기',
  },
  'leaf-star': {
    strength: '배려와 논리의 균형',
    challenge: '이파리형은 감정 인정 원하는데 별빛형은 원인 분석 먼저',
    advice: '별빛형은 "그랬구나" 공감 먼저, 그 후 분석',
  },
  'moon-wave': {
    strength: '서로 다른 방식으로 표현하는 조합',
    challenge: '달빛형의 간접 표현이 파도형에게 답답함을 줌',
    advice: '달빛형은 중요한 건 말로, 파도형은 행동도 읽기',
  },
};

export function getStyleCombinationKey(
  a: CommunicationStyle,
  b: CommunicationStyle,
): string {
  return [a, b].sort().join('-');
}

