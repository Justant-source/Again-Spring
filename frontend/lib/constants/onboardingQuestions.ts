export type OnboardingAxis =
  | 'withdrawal'
  | 'emotional_flooding'
  | 'logical_orientation'
  | 'empathy_priority'
  | 'expression_mode'
  | 'tone_sensitivity'
  | 'apology_style'
  | 'repair_receptivity'
  | 'channel_preference'
  | 'implicit_expectation';

export interface OnboardingQuestion {
  id: string;
  text: string;
  measures: OnboardingAxis;
}

export const ONBOARDING_QUESTIONS: OnboardingQuestion[] = [
  { id: 'q1', text: '나는 갈등이 생기면 일단 시간을 두고 싶어하는 편이다.', measures: 'withdrawal' },
  { id: 'q2', text: '상대방이 감정적으로 격해지면, 나도 같이 감정이 올라오는 편이다.', measures: 'emotional_flooding' },
  { id: 'q3', text: '문제가 생기면 "왜 그랬는지" 이유를 듣고 싶어하는 편이다.', measures: 'logical_orientation' },
  { id: 'q4', text: '상대가 내 감정을 먼저 알아주면, 문제는 저절로 풀린다고 느낀다.', measures: 'empathy_priority' },
  { id: 'q5', text: '나는 서운한 감정을 말로 표현하기보다 행동으로 보여주는 편이다.', measures: 'expression_mode' },
  { id: 'q6', text: '상대의 말투가 날카로우면, 내용보다 그 말투에 더 상처받는다.', measures: 'tone_sensitivity' },
  { id: 'q7', text: '사과할 때는 "내가 뭘 잘못했는지" 구체적으로 아는 게 중요하다.', measures: 'apology_style' },
  { id: 'q8', text: '갈등 중에 상대가 농담을 하면 분위기가 풀린다고 느낀다.', measures: 'repair_receptivity' },
  { id: 'q9', text: '중요한 이야기일수록 직접 만나서 해야 한다고 생각한다.', measures: 'channel_preference' },
  { id: 'q10', text: '관계에서 "말하지 않아도 아는 것"이 중요하다고 느낀다.', measures: 'implicit_expectation' },
];

export const LIKERT_LABELS = {
  min: '전혀 아니다',
  max: '매우 그렇다',
} as const;
