# Mock API 시나리오 샘플

Mock 단계에서 사용할 3가지 기본 시나리오.
`mocks/fixtures/mockMediations.ts`와 `mocks/fixtures/mockReports.ts`에 구현.

---

## 시나리오 1: 사실형 (약속 파기)

### 세션 정보
- **관계**: 연인
- **카테고리**: 연인 > 약속·신뢰 문제 > 약속을 반복적으로 어김
- **A**: 약속 반복 취소당한 쪽
- **B**: 약속 반복 취소한 쪽

### 턴별 입력/응답

**Turn 1 (A 입력)**
```
3주 연속 주말 데이트 약속을 당일에 취소했어요.
매번 "피곤해서", "갑자기 일이 생겨서"라는 핑계로요.
그런데 같은 날 친구랑은 만났더라고요.
저를 중요하게 생각 안 하는 것 같아서 너무 서운해요.
```

**Turn 1 AI (B를 위한 요약)**
```
A님은 최근 반복된 일정 변경 때문에 관계에서의 우선순위에 
대해 깊이 속상해하고 계세요. 자세한 맥락은 B님 답변 후 
함께 공개됩니다.
```

**Turn 2 (B 입력)**
```
요즘 회사에서 프로젝트 마감이 있어서 정말 힘들었어요.
친구 만난 날은 이미 몇 달 전부터 잡혔던 거라 못 미룬 거고,
A한테는 주말에 쉬고 싶어서 취소한 건데...
그 얘기를 이렇게 들으니 제가 나쁜 사람 같네요.
```

**Turn 2 AI (A를 위한 요약)**
```
B님은 최근 업무적 어려움과 체력적 한계에 대해 설명하셨어요.
다만 A님께 그 상황을 충분히 공유하지 못한 점이 있을 수 있어요.
```

**Turn 3 (A에게 심화 질문)**
```
A님, 여기까지 솔직하게 써주셔서 감사해요.

B님이 업무로 힘드셨다는 얘기를 잠깐 들었는데, 
두 가지만 여쭤볼게요:

Q1. B님이 그렇게 힘든 상황이었다는 걸 어느 정도 알고 계셨나요?
Q2. 그 3주 동안 A님이 가장 필요했던 건 뭐였을까요?
```

**Turn 4 (B에게 심화 질문)**
```
B님, 답변 주셔서 감사해요.

A님 얘기도 잠깐 들어봤는데, 두 가지 더 여쭐게요:

Q1. 약속 취소하실 때 미리 알릴 수 있었을 상황이 있으셨나요?
Q2. 친구 약속은 우선순위로 두셨던 이유가 있을까요?
```

### 최종 리포트

```json
{
  "conflictType": "factual",
  "contributionRatio": {
    "a": 25,
    "b": 75,
    "label": {
      "a": "마음 열고 기다려주면 좋은 쪽",
      "b": "먼저 다가가면 좋은 쪽"
    }
  },
  "needsMap": {
    "axisX": "연결성-자율성",
    "positionA": { "x": -60, "y": 0 },
    "positionB": { "x": 50, "y": 0 },
    "interpretation": "A님은 관계에서 '연결감'을 더 원하시고, B님은 '자율성'을 더 중요하게 여기세요"
  },
  "temperature": 35.6,
  "fourHorsemen": {
    "criticism": { "detected": true, "examples": ["저를 중요하게 생각 안 하는 것 같아요"] },
    "defensiveness": { "detected": true, "examples": ["친구 약속은 이미 몇 달 전부터..."] },
    "contempt": { "detected": false },
    "stonewalling": { "detected": false }
  },
  "nvcScripts": {
    "bToA": {
      "observation": "지난 3주 동안 세 번 주말 약속이 당일에 바뀌었어",
      "feeling": "내가 우선순위에서 밀리는 것 같아 서운하고 외로웠어",
      "need": "나한테는 예측 가능한 시간과 '중요하게 여겨지는 느낌'이 필요해",
      "request": "다음부터는 일정 변경이 예상되면 하루 전에 알려줄 수 있을까?"
    },
    "aToB": {
      "observation": "업무가 많아서 주말에 쉬고 싶었어",
      "feeling": "체력적으로 많이 지쳤고, 내 상황을 충분히 전하지 못한 게 미안해",
      "need": "나한테는 회복할 시간이 필요해, 하지만 너와의 시간도 소중해",
      "request": "다음에는 힘든 상황을 미리 공유할게. 함께 조율해볼 수 있을까?"
    }
  },
  "repairSuggestions": [
    "이번 주말은 짧게라도 만나서 얼굴 보자.",
    "내가 힘들 때 미리 얘기 못 해서 미안해.",
    "우리 같이 일정 공유 앱 써볼까?"
  ]
}
```

---

## 시나리오 2: 차이형 (연락 빈도)

### 세션 정보
- **관계**: 연인
- **카테고리**: 연인 > 연락·관심 문제 > 연락이 너무 적어서 서운함
- **A**: 연락 자주 원하는 쪽
- **B**: 연락 부담스러워하는 쪽

### 최종 리포트

```json
{
  "conflictType": "difference",
  "contributionRatio": {
    "a": 55,
    "b": 45,
    "label": {
      "a": "먼저 다가가면 좋은 쪽",
      "b": "마음 열고 기다려주면 좋은 쪽"
    }
  },
  "needsMap": {
    "axisX": "연결성-자율성",
    "positionA": { "x": -70, "y": 0 },
    "positionB": { "x": 60, "y": 0 },
    "interpretation": "두 분은 '연결성-자율성' 축에서 상당한 거리를 보여요. 누가 맞고 틀린 게 아니에요."
  },
  "temperature": 36.2,
  "fourHorsemen": {
    "criticism": { "detected": false },
    "defensiveness": { "detected": true, "examples": ["나는 원래 그래"] },
    "contempt": { "detected": false },
    "stonewalling": { "detected": true, "examples": ["그냥 쉬고 싶어"] }
  },
  "nvcScripts": {
    "aToB": {
      "observation": "하루에 연락이 1-2번 정도 오고 있어",
      "feeling": "가끔 혼자 남겨진 것 같고 불안해",
      "need": "나한테는 '함께 있다는 느낌'이 중요해",
      "request": "짧게라도 하루 몇 번 안부 나눌 수 있을까?"
    },
    "bToA": {
      "observation": "연락을 자주 나누고 싶다는 얘기를 들었어",
      "feeling": "혼자만의 시간이 부족하면 에너지가 떨어져서 힘들어",
      "need": "나한테는 '충전할 수 있는 혼자 시간'이 필요해",
      "request": "저녁에 한 번 길게 연락하는 걸로 해보면 어떨까?"
    }
  },
  "repairSuggestions": [
    "우리 서로 다른 게 문제가 아니라는 걸 인정하자.",
    "아침과 저녁, 하루 두 번 '안부 시간'을 정해볼까?",
    "서로의 리듬을 존중하는 방법을 찾아보자."
  ]
}
```

---

## 시나리오 3: 혼합형 (돈 + 신뢰)

### 세션 정보
- **관계**: 부부
- **카테고리**: 부부 > 돈·재정 문제 > 재정 투명성 문제
- **A**: 몰래 대출받은 쪽
- **B**: 이를 뒤늦게 안 쪽

### 최종 리포트

```json
{
  "conflictType": "mixed",
  "contributionRatio": {
    "a": 65,
    "b": 35,
    "label": {
      "a": "먼저 다가가면 좋은 쪽",
      "b": "마음 열고 기다려주면 좋은 쪽"
    }
  },
  "needsMap": {
    "axisX": "안정성-변화",
    "positionA": { "x": 40, "y": -30 },
    "positionB": { "x": -60, "y": 20 },
    "interpretation": "재정 관리에서 '안정성'과 '도전'에 대한 우선순위가 서로 다르세요"
  },
  "temperature": 35.2,
  "fourHorsemen": {
    "criticism": { "detected": true, "examples": ["왜 이런 것도 안 말해"] },
    "defensiveness": { "detected": true, "examples": ["나도 너 생각해서 한 거야"] },
    "contempt": { "detected": false },
    "stonewalling": { "detected": false }
  },
  "nvcScripts": { /* ... */ },
  "repairSuggestions": [
    "재정에 대해 함께 정기적으로 대화하는 시간을 만들자.",
    "숨긴 건 정말 미안해, 신뢰 회복에 시간이 필요한 거 알아.",
    "우리 미래에 대한 계획을 함께 다시 세워볼까?"
  ]
}
```

---

## Mock 시나리오 4: Solo 모드

### 입력
- A만 참여, B는 24시간 내 미참여
- 카테고리: 친구 > 연락 소홀·거리감 문제 > 거리감 느껴짐

### 최종 리포트 (워터마크)

```json
{
  "isSoloMode": true,
  "conflictType": null,
  "contributionRatio": null,
  "needsMap": {
    "axisX": "연결성-자율성",
    "positionA": { "x": -50, "y": 0 },
    "positionB": null,
    "interpretation": "B님의 입력이 있어야 완전한 분석이 가능해요"
  },
  "temperature": null,
  "fourHorsemen": {
    "criticism": { "detected": false },
    "defensiveness": { "detected": false },
    "contempt": { "detected": false },
    "stonewalling": { "detected": false }
  },
  "aPatternFeedback": "A님은 이 상황에서 조금 성급하게 판단하셨을 수 있어요. B에게 먼저 솔직하게 감정을 공유해보시는 건 어떨까요?",
  "suggestedApproach": "다음에 B님을 만나실 때 이렇게 시작해보세요: '요즘 우리 거리감이 생긴 것 같아서 얘기 좀 하고 싶어.'",
  "inviteAgainCTA": "지금이라도 B님을 초대하면 완전한 리포트가 생성돼요"
}
```

---

## Mock 시나리오 5: Four Horsemen 모두 탐지 (경고형)

### 입력
- 관계: 부부
- 카테고리: 부부 > 부부 관계·애정 문제 > 권태기
- 양쪽 모두 감정적으로 격앙된 입력

### 리포트 특징
- 관계 온도: 35.0°C (경고 수준)
- 모든 Four Horsemen 탐지됨
- 리포트 상단에 **"관계 회복에 시간과 노력이 필요해 보여요. 전문 상담을 권해드려요."** 안내
- 유료 상담 연결 CTA (제휴 준비 전까지는 정보 제공만)

---

## MSW Handler 구조

```typescript
// mocks/handlers/mediation.ts

import { http, HttpResponse, delay } from 'msw';
import { mockReports } from '../fixtures/mockReports';

export const mediationHandlers = [
  // 세션 생성
  http.post('/api/sessions', async ({ request }) => {
    await delay(800);
    const body = await request.json();
    return HttpResponse.json({
      id: `session_${Date.now()}`,
      inviteToken: `tok_${Math.random().toString(36).slice(2, 10)}`,
      status: 'waiting_b',
      ...body,
    });
  }),
  
  // 턴 진행
  http.post('/api/sessions/:id/turns', async ({ params, request }) => {
    await delay(1500); // AI 응답 시뮬레이션
    const body = await request.json();
    const turnNumber = body.turnNumber;
    
    // 턴별 고정 응답 반환
    const mockResponse = getMockTurnResponse(turnNumber, body);
    return HttpResponse.json(mockResponse);
  }),
  
  // 리포트 생성 요청
  http.post('/api/sessions/:id/report', async ({ params }) => {
    await delay(3000); // 리포트 생성 로딩감
    
    // 시나리오별로 하나 선택 (랜덤 또는 세션 기반)
    const report = selectMockReport(params.id as string);
    return HttpResponse.json(report);
  }),
];

function selectMockReport(sessionId: string) {
  const scenarios = ['factual', 'difference', 'mixed'];
  const index = sessionId.length % scenarios.length;
  return mockReports[scenarios[index]];
}
```

---

## 개발 단계 디버깅 팁

프론트 개발 중 시나리오 전환이 필요하면:
- URL 쿼리로 시나리오 지정: `?mockScenario=factual`
- 개발 모드에서만 "시나리오 선택" 디버그 패널 노출
