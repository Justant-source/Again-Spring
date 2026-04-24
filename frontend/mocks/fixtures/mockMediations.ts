export interface MediatorTurn {
  turnNumber: number;
  forRole: 'A' | 'B';
  isPerspectiveTaking?: boolean;
  mediatorMessage: string;
  questions?: string[];
}

/**
 * Fixed mediator prompts for a 6-turn arc. The Mock API returns these in
 * order regardless of user input — good enough for UX validation.
 */
export const MEDIATOR_TURNS: MediatorTurn[] = [
  {
    turnNumber: 1,
    forRole: 'A',
    mediatorMessage:
      '먼저 A님의 이야기를 듣고 있어요. 조금 더 적어주셔도 좋고, 지금 상태 그대로 시작해도 괜찮아요.',
  },
  {
    turnNumber: 2,
    forRole: 'B',
    mediatorMessage:
      'A님이 적어주신 내용을 정돈해 전해드렸어요. 이제 B님의 이야기를 편한 말로 들려주세요.',
  },
  {
    turnNumber: 3,
    forRole: 'A',
    isPerspectiveTaking: true,
    mediatorMessage:
      "A님, 솔직하게 써주셔서 감사해요. 두 가지만 여쭤볼게요. 상대의 상황을 혹시 어느 정도 느끼고 계셨을까요? 그리고, 이 기간에 A님께 가장 필요했던 건 무엇이었을까요?",
    questions: [
      '상대의 상황을 어느 정도 알고 계셨나요?',
      '이 기간에 A님께 가장 필요했던 건 무엇이었을까요?',
    ],
  },
  {
    turnNumber: 4,
    forRole: 'B',
    isPerspectiveTaking: true,
    mediatorMessage:
      'B님, 답장 고마워요. 두 가지만 더 여쭤볼게요. 지금의 선택이 있기 전, 혹시 마음에 걸리던 장면이 있었을까요? 그리고 A님께 가장 전하고 싶은 마음은 무엇인가요?',
    questions: [
      '지금 방식을 선택하기 전 마음에 걸리던 장면이 있으셨나요?',
      'A님께 가장 전하고 싶은 한 문장이 있다면요?',
    ],
  },
  {
    turnNumber: 5,
    forRole: 'A',
    mediatorMessage:
      '이번 턴은 선택이에요. B님의 이야기를 잠깐 들어본 지금, 다시 한 번 덧붙이고 싶은 말이 있다면 남겨주세요. 없어도 괜찮아요.',
  },
  {
    turnNumber: 6,
    forRole: 'B',
    mediatorMessage:
      '마지막 턴도 선택이에요. 오늘의 대화를 A님께 어떻게 마무리하고 싶으신가요. 가볍게 한 문장이어도 좋아요.',
  },
];

export function getMediatorTurn(turnNumber: number) {
  return MEDIATOR_TURNS.find((t) => t.turnNumber === turnNumber);
}
