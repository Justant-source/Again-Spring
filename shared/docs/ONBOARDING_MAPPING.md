# 온보딩 10문항 → 6가지 커뮤니케이션 스타일 매핑

---

## 10문항 전체 (5점 리커트 척도)

각 문항: `전혀 그렇지 않다 [1] [2] [3] [4] [5] 매우 그렇다`

```typescript
export const ONBOARDING_QUESTIONS = [
  {
    id: 'q1',
    text: '나는 갈등이 생기면 일단 시간을 두고 싶어하는 편이다.',
    measures: 'withdrawal', // 회피-대면 성향
  },
  {
    id: 'q2',
    text: '상대방이 감정적으로 격해지면, 나도 같이 감정이 올라오는 편이다.',
    measures: 'emotional_flooding', // 감정 조절
  },
  {
    id: 'q3',
    text: '문제가 생기면 "왜 그랬는지" 이유를 듣고 싶어하는 편이다.',
    measures: 'logical_orientation', // 논리형
  },
  {
    id: 'q4',
    text: '상대가 내 감정을 먼저 알아주면, 문제는 저절로 풀린다고 느낀다.',
    measures: 'empathy_priority', // 공감 선호
  },
  {
    id: 'q5',
    text: '나는 서운한 감정을 말로 표현하기보다 행동으로 보여주는 편이다.',
    measures: 'expression_mode', // 직접 vs 간접 표현
  },
  {
    id: 'q6',
    text: '상대의 말투가 날카로우면, 내용보다 그 말투에 더 상처받는다.',
    measures: 'tone_sensitivity', // 비판 감수성
  },
  {
    id: 'q7',
    text: '사과할 때는 "내가 뭘 잘못했는지" 구체적으로 아는 게 중요하다.',
    measures: 'apology_style', // 구체성 선호
  },
  {
    id: 'q8',
    text: '갈등 중에 상대가 농담을 하면 분위기가 풀린다고 느낀다.',
    measures: 'repair_receptivity', // Repair 수용성
  },
  {
    id: 'q9',
    text: '중요한 이야기일수록 직접 만나서 해야 한다고 생각한다.',
    measures: 'channel_preference', // 대면 vs 비대면
  },
  {
    id: 'q10',
    text: '관계에서 "말하지 않아도 아는 것"이 중요하다고 느낀다.',
    measures: 'implicit_expectation', // 암묵적 기대
  },
];
```

---

## 6가지 스타일

```typescript
export const COMMUNICATION_STYLES = {
  wave: {
    emoji: '🌊',
    label: '파도형',
    description: '감정 표현이 풍부하고 즉각적인 스타일',
    strengths: ['진솔한 감정 표현', '따뜻한 공감 능력'],
    caution: ['감정 격앙 시 휴식 필요', '상대에게 숨 돌릴 시간 주기'],
    color: '#60A5FA', // blue-400
  },
  mountain: {
    emoji: '🏔️',
    label: '산형',
    description: '차분하고 거리를 두고 생각하는 스타일',
    strengths: ['평정심', '신중한 판단'],
    caution: ['표현 부족으로 오해 가능', '감정 공유 노력 필요'],
    color: '#78716C', // stone-500
  },
  flame: {
    emoji: '🔥',
    label: '불꽃형',
    description: '직설적이고 명확함을 선호하는 스타일',
    strengths: ['명확한 의사 전달', '빠른 문제 해결'],
    caution: ['말투가 상처될 수 있음', '부드러운 시작 필요'],
    color: '#F87171', // red-400
  },
  leaf: {
    emoji: '🌿',
    label: '이파리형',
    description: '조화와 공감을 중시하는 스타일',
    strengths: ['뛰어난 공감력', '관계 조율 능력'],
    caution: ['자기 욕구 표현 부족', '솔직한 의사 표현 연습'],
    color: '#4ADE80', // green-400
  },
  moon: {
    emoji: '🌙',
    label: '달빛형',
    description: '말보다 분위기·행동으로 표현하는 스타일',
    strengths: ['세심한 배려', '행동을 통한 사랑'],
    caution: ['상대가 오해할 수 있음', '말로도 표현해주세요'],
    color: '#A78BFA', // violet-400
  },
  star: {
    emoji: '⭐',
    label: '별빛형',
    description: '논리와 이유를 중시하는 스타일',
    strengths: ['구조적 사고', '근거 있는 대화'],
    caution: ['감정 인정 먼저 하기', '상대 감정 덮어쓰지 않기'],
    color: '#FBBF24', // amber-400
  },
};
```

---

## 매핑 알고리즘

각 문항 점수(1~5)에서 6가지 축으로 변환 후, 가장 강한 축을 선택.

### 6가지 축 계산

```typescript
function calculateStyleAxes(answers: number[]): Record<string, number> {
  const [q1, q2, q3, q4, q5, q6, q7, q8, q9, q10] = answers;
  
  // 각 축을 0-10 범위로 정규화
  const axes = {
    // 파도형: 감정 표현 강함 + 빠른 감정 반응
    wave: ((6 - q1) + q2 + (6 - q5)) / 3 * 2, // q1 역방향 (회피 낮음), q5 역방향 (직접 표현)
    
    // 산형: 신중하고 거리를 두는 경향
    mountain: (q1 + (6 - q2)) / 2 * 2, // q1 순방향, q2 역방향 (감정 조절 잘함)
    
    // 불꽃형: 직설적이고 논리 강함
    flame: (q3 + (6 - q5) + (6 - q6)) / 3 * 2, // 논리+직접 표현+비판에 둔감
    
    // 이파리형: 공감 중심, 조화 추구
    leaf: (q4 + q6) / 2 * 2, // 공감 선호 + 말투 민감
    
    // 달빛형: 간접 표현, 암묵적 기대
    moon: (q5 + q10) / 2 * 2, // 행동 표현 + 암묵 기대
    
    // 별빛형: 구체성 선호, 논리
    star: (q3 + q7) / 2 * 2, // 이유 선호 + 구체적 사과
  };
  
  return axes;
}

function determineStyle(answers: number[]): CommunicationStyle {
  const axes = calculateStyleAxes(answers);
  
  // 가장 높은 축 선택
  const topStyle = Object.entries(axes).sort((a, b) => b[1] - a[1])[0][0];
  
  return topStyle as CommunicationStyle;
}
```

---

## 결과 해석 예시

### 예시 1: 파도형 결과

```
답변: [2, 5, 3, 4, 1, 5, 3, 5, 4, 2]

계산:
- wave: ((6-2) + 5 + (6-1)) / 3 * 2 = (4+5+5)/3*2 = 9.33 ← 가장 높음
- mountain: (2 + (6-5)) / 2 * 2 = (2+1)/2*2 = 3.0
- flame: (3 + (6-1) + (6-5)) / 3 * 2 = (3+5+1)/3*2 = 6.0
- leaf: (4 + 5) / 2 * 2 = 9.0
- moon: (1 + 2) / 2 * 2 = 3.0
- star: (3 + 3) / 2 * 2 = 6.0

→ 결과: 🌊 파도형
```

---

## 조합 해석 (양쪽 스타일 조합)

두 사람이 각자 스타일을 받은 후, **조합**별로 해석 문구 생성:

```typescript
export const STYLE_COMBINATION_INSIGHTS = {
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
  // ... 6x6 = 36가지 조합 (동일 스타일 조합 포함)
};
```

---

## Claude Code 작업 지침

1. `lib/constants/onboardingQuestions.ts`로 문항 배열 만들기
2. `lib/constants/communicationStyles.ts`로 스타일 정의 옮기기
3. `lib/utils/styleCalculator.ts`로 매핑 알고리즘 구현
4. 온보딩 결과 페이지에서 스타일 카드 애니메이션 공개 UX 구현
5. 조합 해석은 세션 결과 리포트에서 활용 (A-B 조합)
