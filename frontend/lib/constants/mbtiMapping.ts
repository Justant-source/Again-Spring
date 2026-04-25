import type { CommunicationStyle } from '@/lib/types';

// 16 MBTI types → 6 communication styles
export const MBTI_TO_STYLE: Record<string, CommunicationStyle> = {
  // wave: 감정 표현 풍부, 외향적 공감
  ENFP: 'wave', ENFJ: 'wave', ESFP: 'wave',
  // leaf: 조화·배려·공감 우선
  ESFJ: 'leaf', INFP: 'leaf', ISFJ: 'leaf',
  // moon: 행동으로 표현, 암묵적 이해
  INFJ: 'moon', ISFP: 'moon',
  // flame: 직설적, 목표·논리 지향
  ENTJ: 'flame', ENTP: 'flame', ESTP: 'flame',
  // star: 논리·분석 중시
  ESTJ: 'star', INTP: 'star',
  // mountain: 차분·거리 두기
  INTJ: 'mountain', ISTJ: 'mountain', ISTP: 'mountain',
};

export interface MbtiQuestion {
  id: string;
  dimension: 'EI' | 'SN' | 'TF' | 'JP';
  text: string;
  optionA: { label: string; letter: string };
  optionB: { label: string; letter: string };
}

// 8 questions (2 per dimension), A/B choice
export const MBTI_TEST_QUESTIONS: MbtiQuestion[] = [
  {
    id: 'ei1',
    dimension: 'EI',
    text: '여러 사람이 모이는 자리는',
    optionA: { label: '활기차고 즐겁다', letter: 'E' },
    optionB: { label: '피곤하고 부담스럽다', letter: 'I' },
  },
  {
    id: 'ei2',
    dimension: 'EI',
    text: '갈등 상황이 생기면',
    optionA: { label: '바로 표현하고 싶다', letter: 'E' },
    optionB: { label: '혼자 생각할 시간이 필요하다', letter: 'I' },
  },
  {
    id: 'sn1',
    dimension: 'SN',
    text: '문제를 해결할 때',
    optionA: { label: '검증된 방법을 따른다', letter: 'S' },
    optionB: { label: '새로운 접근을 시도한다', letter: 'N' },
  },
  {
    id: 'sn2',
    dimension: 'SN',
    text: '나는 주로',
    optionA: { label: '현재와 구체적 사실에 집중한다', letter: 'S' },
    optionB: { label: '미래와 가능성을 상상한다', letter: 'N' },
  },
  {
    id: 'tf1',
    dimension: 'TF',
    text: '결정을 내릴 때',
    optionA: { label: '논리와 원칙이 더 중요하다', letter: 'T' },
    optionB: { label: '사람들의 감정이 더 중요하다', letter: 'F' },
  },
  {
    id: 'tf2',
    dimension: 'TF',
    text: '누군가 화가 났을 때',
    optionA: { label: '원인을 파악하고 해결책을 찾는다', letter: 'T' },
    optionB: { label: '먼저 감정에 공감하고 싶다', letter: 'F' },
  },
  {
    id: 'jp1',
    dimension: 'JP',
    text: '나는 계획이',
    optionA: { label: '있어야 마음이 편하다', letter: 'J' },
    optionB: { label: '없어도 상황에 맞게 잘 된다', letter: 'P' },
  },
  {
    id: 'jp2',
    dimension: 'JP',
    text: '일정이 갑자기 바뀌면',
    optionA: { label: '불편하고 다시 조정하고 싶다', letter: 'J' },
    optionB: { label: '오히려 자유롭고 좋다', letter: 'P' },
  },
];

// Derive MBTI type string from per-dimension tallies
export function deriveMbtiType(answers: Record<string, string>): string {
  const tally = (a: string, b: string) => {
    const votes = MBTI_TEST_QUESTIONS.filter((q) => q.dimension === (a + b) as 'EI' | 'SN' | 'TF' | 'JP')
      .map((q) => answers[q.id]);
    const countA = votes.filter((v) => v === a).length;
    const countB = votes.filter((v) => v === b).length;
    return countA >= countB ? a : b;
  };
  return tally('E', 'I') + tally('S', 'N') + tally('T', 'F') + tally('J', 'P');
}
