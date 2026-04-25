import type { Report } from '@/lib/types';

/** Curated reports keyed by scenario name (docs/MOCK_SCENARIOS.md). */
export const mockReports: Record<string, Report> = {
  factual: {
    id: 'rep_factual',
    sessionId: 'sess_factual',
    conflictType: 'factual',
    contributionRatio: {
      a: 25,
      b: 75,
      label: {
        a: '마음 열고 기다려주면 좋은 쪽',
        b: '먼저 다가가면 좋은 쪽',
      },
    },
    needsMap: {
      axisX: '연결 ↔ 자율',
      axisY: '안정 ↔ 변화',
      positionA: { x: -60, y: 0 },
      positionB: { x: 50, y: 0 },
      interpretation:
        'A님은 관계에서 연결감을 더 원하시고, B님은 자율성을 더 중요하게 여기세요.',
    },
    nvcScripts: {
      bToA: {
        observation: '지난 3주 동안 세 번 주말 약속이 당일에 바뀌었어',
        feeling: '내가 우선순위에서 밀리는 것 같아 서운하고 외로웠어',
        need: "나한테는 예측 가능한 시간과 '중요하게 여겨지는 느낌'이 필요해",
        request: '다음부터는 일정 변경이 예상되면 하루 전에 알려줄 수 있을까?',
      },
      aToB: {
        observation: '업무가 많아서 주말에 쉬고 싶었어',
        feeling: '체력적으로 많이 지쳤고, 내 상황을 충분히 전하지 못한 게 미안해',
        need: '나한테는 회복할 시간이 필요해, 하지만 너와의 시간도 소중해',
        request: '다음에는 힘든 상황을 미리 공유할게. 함께 조율해볼 수 있을까?',
      },
    },
    repairSuggestions: [
      '이번 주말은 짧게라도 만나서 얼굴 보자.',
      '내가 힘들 때 미리 얘기 못 해서 미안해.',
      '우리 같이 일정 공유 앱 써볼까?',
    ],
    isSoloMode: false,
    createdAt: new Date().toISOString(),
  },

  difference: {
    id: 'rep_difference',
    sessionId: 'sess_difference',
    conflictType: 'difference',
    contributionRatio: {
      a: 55,
      b: 45,
      label: {
        a: '먼저 다가가면 좋은 쪽',
        b: '마음 열고 기다려주면 좋은 쪽',
      },
    },
    needsMap: {
      axisX: '연결 ↔ 자율',
      axisY: '안정 ↔ 변화',
      positionA: { x: -70, y: 0 },
      positionB: { x: 60, y: 0 },
      interpretation:
        "두 분은 '연결 ↔ 자율' 축에서 서로 다른 자리에 계세요. 누가 맞고 틀린 게 아닙니다.",
    },
    nvcScripts: {
      aToB: {
        observation: '하루에 연락이 1–2번 정도 오고 있어',
        feeling: '가끔 혼자 남겨진 것 같고 불안해',
        need: "나한테는 '함께 있다는 느낌'이 중요해",
        request: '짧게라도 하루 몇 번 안부 나눌 수 있을까?',
      },
      bToA: {
        observation: '연락을 자주 나누고 싶다는 얘기를 들었어',
        feeling: '혼자만의 시간이 부족하면 에너지가 떨어져서 힘들어',
        need: "나한테는 '충전할 수 있는 혼자 시간'이 필요해",
        request: '저녁에 한 번 길게 연락하는 걸로 해보면 어떨까?',
      },
    },
    repairSuggestions: [
      '서로 다른 게 문제가 아니라는 걸 인정하자.',
      '아침과 저녁, 하루 두 번 안부 시간을 정해볼까?',
      '서로의 리듬을 존중하는 방법을 찾아보자.',
    ],
    isSoloMode: false,
    createdAt: new Date().toISOString(),
  },

  mixed: {
    id: 'rep_mixed',
    sessionId: 'sess_mixed',
    conflictType: 'mixed',
    contributionRatio: {
      a: 65,
      b: 35,
      label: {
        a: '먼저 다가가면 좋은 쪽',
        b: '마음 열고 기다려주면 좋은 쪽',
      },
    },
    needsMap: {
      axisX: '안정 ↔ 변화',
      axisY: '계획 ↔ 즉흥',
      positionA: { x: 40, y: -30 },
      positionB: { x: -60, y: 20 },
      interpretation:
        "재정 관리에서 '안정성'과 '도전'에 대한 우선순위가 서로 다르세요.",
    },
    nvcScripts: {
      aToB: {
        observation: '몇 달간 대출 사실을 공유받지 못했어',
        feeling: '예상치 못한 상황 앞에서 당황스럽고 신뢰가 흔들렸어',
        need: '재정에 대한 투명한 공유가 나한텐 안정감의 기반이야',
        request: '앞으로 큰 재정 결정은 함께 상의하는 규칙을 정할 수 있을까?',
      },
      bToA: {
        observation: '너를 실망시킬까 봐 말을 꺼내지 못했어',
        feeling: '미안함과 부끄러움이 겹쳐 미루게 됐어',
        need: '나에겐 실수해도 같이 풀어갈 수 있는 신뢰가 필요해',
        request: '정기적으로 재정 리뷰 자리를 만드는 건 어떨까?',
      },
    },
    repairSuggestions: [
      '재정에 대해 함께 정기적으로 대화하는 시간을 만들자.',
      '숨긴 건 정말 미안해, 신뢰 회복에 시간이 필요한 거 알아.',
      '우리 미래에 대한 계획을 함께 다시 세워볼까?',
    ],
    isSoloMode: false,
    createdAt: new Date().toISOString(),
  },

  solo: {
    id: 'rep_solo',
    sessionId: 'sess_solo',
    conflictType: null,
    contributionRatio: null,
    needsMap: {
      axisX: '연결 ↔ 자율',
      positionA: { x: -50, y: 0 },
      positionB: null,
      interpretation: 'B님의 입력이 있어야 완전한 분석이 가능해요.',
    },
    repairSuggestions: [],
    isSoloMode: true,
    aPatternFeedback:
      'A님은 이 상황에서 조금 성급하게 판단하셨을 수 있어요. 먼저 솔직하게 감정을 공유해보시는 건 어떨까요?',
    suggestedApproach:
      "다음에 상대를 만나실 때 이렇게 시작해보세요: '요즘 우리 사이 거리감이 생긴 것 같아서 얘기 좀 하고 싶어.'",
    inviteAgainCTA: '지금이라도 상대를 초대하면 완전한 리포트가 생성돼요.',
    createdAt: new Date().toISOString(),
  },

  conflict_intense: {
    id: 'rep_fourh',
    sessionId: 'sess_fourh',
    conflictType: 'mixed',
    contributionRatio: {
      a: 50,
      b: 50,
      label: {
        a: '함께 마주 걷는 쪽',
        b: '함께 마주 걷는 쪽',
      },
    },
    needsMap: {
      axisX: '연결 ↔ 자율',
      axisY: '표현 ↔ 수용',
      positionA: { x: -50, y: 55 },
      positionB: { x: 55, y: -50 },
      interpretation:
        '두 분 모두 지침이 깊어 보여요. 전문가와 함께 안전한 대화 공간을 마련해보는 것도 좋겠습니다.',
    },
    nvcScripts: {
      aToB: {
        observation: '최근 몇 주간 대화가 자주 중단되었어',
        feeling: '답답함과 외로움이 같이 올라왔어',
        need: '서로 존중 받는 느낌이 나한테 꼭 필요해',
        request: '전문 상담과 함께 이야기해보는 건 어떨까?',
      },
      bToA: {
        observation: '감정을 쏟은 뒤 대화를 끊은 적이 많아',
        feeling: '숨이 막히고 피하고 싶어져서 힘들었어',
        need: '나한텐 숨 돌릴 시간과 안전한 표현 방법이 필요해',
        request: '우리 둘 다 안전하게 말할 수 있는 규칙을 같이 만들까?',
      },
    },
    repairSuggestions: [
      '지금 상태에서는 전문가의 도움을 받는 것이 관계에 가장 도움이 돼요.',
      '대화가 격해지면 20분 Cooldown을 약속하자.',
      '상대의 감정을 먼저 짧게 비춰주고 나서 내 이야기를 꺼내자.',
    ],
    isSoloMode: false,
    createdAt: new Date().toISOString(),
  },
};

const ORDER = ['factual', 'difference', 'mixed', 'solo', 'conflict_intense'] as const;

/** Stable picker so the same sessionId always gets the same scenario. */
export function pickReport(sessionId: string) {
  const idx =
    Math.abs(
      sessionId.split('').reduce((acc, c) => acc + c.charCodeAt(0), 0),
    ) % ORDER.length;
  const key = ORDER[idx];
  return { ...mockReports[key], sessionId };
}
