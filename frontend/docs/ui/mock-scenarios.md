# Mock API 시나리오 샘플

Mock 단계에서 사용할 3가지 기본 시나리오와 확장 시나리오.
`mocks/fixtures/mockReports.ts`에 구현.

---

## 시나리오 1: 사실형 (약속 파기)

### 세션 정보
- **관계**: 연인
- **카테고리**: 연인 > 약속·신뢰 문제 > 약속을 반복적으로 어김
- **A**: 약속 반복 취소당한 쪽
- **B**: 약속 반복 취소한 쪽

### 리포트

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
  "nvcScripts": {
    "aToB": {
      "observation": "아이 교육비 때문에 몰래 대출을 받았어",
      "feeling": "결정을 혼자 했다는 죄책감이 있고, 신뢰를 잃을까봐 두려워",
      "need": "나한테는 가족을 지키려는 마음과 그 불안감을 나눌 안전한 공간이 필요해",
      "request": "내 선택이 잘못됐다고 생각하니까, 앞으로 어떻게 함께할지 얘기해볼 수 있을까?"
    },
    "bToA": {
      "observation": "몰래 대출이 있었다는 걸 나중에 알게 됐어",
      "feeling": "신뢰가 깨지고, 함께 결정하지 못했다는 게 너무 서운해",
      "need": "우리는 함께라는 느낌이 필요해. 앞으로 투명하게 의사결정해야 해",
      "request": "지금부터 큰 결정들은 미리 나눌 수 있을까?"
    }
  },
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
  "aPatternFeedback": "A님은 이 상황에서 조금 성급하게 판단하셨을 수 있어요. B에게 먼저 솔직하게 감정을 공유해보시는 건 어떨까요?",
  "suggestedApproach": "다음에 B님을 만나실 때 이렇게 시작해보세요: '요즘 우리 거리감이 생긴 것 같아서 얘기 좀 하고 싶어.'",
  "inviteAgainCTA": "지금이라도 B님을 초대하면 완전한 리포트가 생성돼요"
}
```

---

## Mock 시나리오 5: 심각한 갈등 (권태기)

### 세션 정보
- **관계**: 부부
- **카테고리**: 부부 > 부부 관계·애정 문제 > 권태기
- 양쪽 모두 감정적으로 격앙된 입력

### 리포트 특징
- 리포트 상단에 **"관계 회복에 시간과 노력이 필요해 보여요. 전문 상담을 권해드려요."** 안내
- 유료 상담 연결 CTA (제휴 준비 전까지는 정보 제공만)

---

## MSW Handler 구조

현재 리포트 생성은 `mocks/handlers/mediation.ts`에서 `pickReport()`를 통해 시나리오별 리포트를 반환합니다.

```typescript
// mocks/handlers/mediation.ts

export const mediationHandlers = [
  // 리포트 POST 요청
  http.post('/api/sessions/:id/report', async ({ params }) => {
    await delay(2400);
    const report = pickReport(String(params.id));
    return HttpResponse.json(report);
  }),

  // 리포트 GET 요청
  http.get('/api/sessions/:id/report', async ({ params }) => {
    await delay(200);
    const report = pickReport(String(params.id));
    return HttpResponse.json(report);
  }),

  // 개발자 오버라이드: 특정 시나리오 강제 선택
  http.get('/api/mock/report', async ({ request }) => {
    const url = new URL(request.url);
    const scenario = url.searchParams.get('scenario') ?? 'difference';
    await delay(200);
    return HttpResponse.json(pickReport(`force_${scenario}`));
  }),
];
```

---

## 개발 단계 디버깅 팁

프론트 개발 중 리포트 시나리오를 강제로 선택하려면:
- 개발자 오버라이드: `/api/mock/report?scenario=factual|difference|mixed`
- 예: `http://localhost:3000/api/mock/report?scenario=factual`로 GET 요청하면 해당 시나리오의 리포트가 반환됨

---

## MSW 자동 생성 파일

```bash
npx msw init ./public
# public/mockServiceWorker.js 생성 (자동으로 .gitignored)
```

`mocks/browser.ts`에서 worker를 등록하면, dev 모드에서 모든 API 요청이 자동 가로채집니다.

