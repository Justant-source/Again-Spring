# 화해 기여도 계산 알고리즘

**중요**: UI에서 "과실비율"이라는 단어를 절대 사용하지 않습니다.
내부 코드 변수명은 `contributionRatio` 또는 `reconciliationRatio`.

---

## 계산 흐름

```
[Input] A의 입력 + B의 입력 + 카테고리 정보
   ↓
[Step 1] 갈등 유형 분류 (factual / difference / mixed)
   ↓
[Step 2] 각 요소별 스코어링 (0-10점씩 5개 요소)
   ↓
[Step 3] 초기 비율 산출 (A, B 각각 0-100)
   ↓
[Step 4] 유형별 클리핑 (사실형 최대 100:0, 차이형 최대 70:30)
   ↓
[Step 5] 5 단위 반올림
   ↓
[Step 6] 긍정 라벨 부여
   ↓
[Output] { a: number, b: number, labels: {...} }
```

---

## Step 1: 갈등 유형 분류

LLM이 분류. 다음 프롬프트 사용:

```
다음 갈등이 세 가지 중 어디에 해당하는지 분류하세요:

1. 사실형 (factual): 명백한 약속 파기, 거짓말, 선 넘는 행동 등 
   객관적 판단이 가능한 갈등
   
2. 차이형 (difference): 서로의 성격·가치관·취향·욕구의 차이로 인한 갈등. 
   어느 쪽이 틀린 것이 아닌 갈등
   
3. 혼합형 (mixed): 사실 문제와 차이 문제가 섞여 있는 갈등

출력: {"type": "factual|difference|mixed", "reasoning": "..."}
```

---

## Step 2: 요소별 스코어링

A와 B 각각에 대해 5개 요소를 0-10점으로 스코어링. **점수가 높을수록 책임이 큼**.

```typescript
interface ScoringFactors {
  boundaryViolation: number;      // 경계 침범 (약속 파기, 거짓말 등)
  fourHorsemenUsage: number;       // Four Horsemen 언어 빈도
  repairAttemptLack: number;       // Repair Attempt 시도 부족
  perspectiveTakingLack: number;   // 조망수용 노력 부족
  escalationContribution: number;  // 갈등 격화 기여도
}

function calculateScore(factors: ScoringFactors): number {
  // 가중 평균
  const weights = {
    boundaryViolation: 0.30,      // 가장 큰 가중치 (객관적)
    fourHorsemenUsage: 0.25,
    repairAttemptLack: 0.15,
    perspectiveTakingLack: 0.15,
    escalationContribution: 0.15,
  };
  
  const totalScore = 
    factors.boundaryViolation * weights.boundaryViolation +
    factors.fourHorsemenUsage * weights.fourHorsemenUsage +
    factors.repairAttemptLack * weights.repairAttemptLack +
    factors.perspectiveTakingLack * weights.perspectiveTakingLack +
    factors.escalationContribution * weights.escalationContribution;
  
  return totalScore; // 0-10
}
```

### 요소별 스코어링 기준

#### boundaryViolation (경계 침범)

| 점수 | 기준 |
|---|---|
| 0-2 | 명시적/암묵적 경계 침범 없음 |
| 3-5 | 작은 약속 불이행, 사소한 선 넘기 |
| 6-8 | 명확한 약속 파기, 반복적 문제 |
| 9-10 | 심각한 신뢰 파괴 (중대한 거짓말 등) |

#### fourHorsemenUsage (Four Horsemen 사용)

| 점수 | 기준 |
|---|---|
| 0-2 | 없음 또는 미미 |
| 3-5 | 1-2가지 패턴 탐지 |
| 6-8 | 3가지 이상 패턴 반복 |
| 9-10 | 경멸(Contempt) 명확히 탐지 (가장 위험) |

#### repairAttemptLack (Repair 시도 부족)

| 점수 | 기준 |
|---|---|
| 0-2 | 여러 번 화해 시도, 상대 제스처 수용 |
| 3-5 | 한두 번 시도 |
| 6-8 | 시도 없음 |
| 9-10 | 상대의 Repair Attempt 명시적 거부 |

#### perspectiveTakingLack (조망수용 부족)

| 점수 | 기준 |
|---|---|
| 0-2 | Turn 5-6에서 상대 입장 잘 표현 |
| 3-5 | 부분적으로 이해 시도 |
| 6-8 | 자기 입장만 주장 |
| 9-10 | Turn 5-6 스킵 + 상대 입장 완전 무시 |

#### escalationContribution (격화 기여)

| 점수 | 기준 |
|---|---|
| 0-2 | 차분하게 소통 |
| 3-5 | 일부 감정적 표현 |
| 6-8 | 자주 격앙된 표현 |
| 9-10 | 격화의 주된 원인 |

---

## Step 3: 초기 비율 산출

```typescript
function calculateInitialRatio(
  scoreA: number,  // 0-10
  scoreB: number   // 0-10
): { a: number, b: number } {
  const total = scoreA + scoreB;
  
  if (total === 0) {
    return { a: 50, b: 50 }; // 둘 다 책임 없음 → 반반
  }
  
  const ratioA = (scoreA / total) * 100;
  const ratioB = (scoreB / total) * 100;
  
  return { a: ratioA, b: ratioB };
}
```

---

## Step 4: 유형별 클리핑

**가장 중요한 로직**. LLM 출력만 믿지 말고 코드 레벨에서 강제.

```typescript
function clipRatio(
  ratio: { a: number, b: number },
  conflictType: ConflictType
): { a: number, b: number } {
  let { a, b } = ratio;
  
  if (conflictType === 'difference') {
    // 차이형: 최대 70:30
    if (a > 70) { a = 70; b = 30; }
    if (b > 70) { a = 30; b = 70; }
  } 
  else if (conflictType === 'mixed') {
    // 혼합형: 최대 85:15 (사실+차이 섞임)
    if (a > 85) { a = 85; b = 15; }
    if (b > 85) { a = 15; b = 85; }
  }
  else if (conflictType === 'factual') {
    // 사실형: 100:0까지 가능
    // 클리핑 없음
  }
  
  return { a, b };
}
```

---

## Step 5: 5 단위 반올림

73:27 같은 정밀한 숫자는 오히려 불신감 유발. 5 단위로 통일.

```typescript
function roundToFive(ratio: { a: number, b: number }): { a: number, b: number } {
  const roundedA = Math.round(ratio.a / 5) * 5;
  const roundedB = 100 - roundedA;
  return { a: roundedA, b: roundedB };
}
```

---

## Step 6: 긍정 라벨 부여

숫자만 보여주지 않고 **긍정 라벨**을 병행. 어느 쪽도 "나쁜 사람"이 되지 않도록.

```typescript
function assignLabels(ratio: { a: number, b: number }): { a: string, b: string } {
  if (ratio.a > ratio.b) {
    return {
      a: '먼저 다가가면 좋은 쪽',
      b: '마음 열고 기다려주면 좋은 쪽',
    };
  } else if (ratio.b > ratio.a) {
    return {
      a: '마음 열고 기다려주면 좋은 쪽',
      b: '먼저 다가가면 좋은 쪽',
    };
  } else {
    return {
      a: '함께 다가가기 좋은 쪽',
      b: '함께 다가가기 좋은 쪽',
    };
  }
}
```

---

## 전체 함수

```typescript
export function calculateContributionRatio(
  scoresA: ScoringFactors,
  scoresB: ScoringFactors,
  conflictType: ConflictType,
): ContributionResult {
  // Step 2: 각자 점수
  const scoreA = calculateScore(scoresA);
  const scoreB = calculateScore(scoresB);
  
  // Step 3: 초기 비율
  let ratio = calculateInitialRatio(scoreA, scoreB);
  
  // Step 4: 유형별 클리핑
  ratio = clipRatio(ratio, conflictType);
  
  // Step 5: 반올림
  ratio = roundToFive(ratio);
  
  // Step 6: 라벨
  const labels = assignLabels(ratio);
  
  return {
    a: ratio.a,
    b: ratio.b,
    label: labels,
    conflictType,
  };
}
```

---

## 엣지 케이스

### 완전 대칭
- 양쪽 점수 동일 → 50:50
- 라벨: 양쪽 모두 "함께 다가가기 좋은 쪽"

### Solo 모드
- **화해 기여도 계산 안 함**
- 대신 "A의 커뮤니케이션 패턴 피드백"만 제공

### 키워드 가드 발동
- 세션 즉시 중단
- 리포트 생성 안 함

### 차이형 99:1 시도 차단
- 코드 레벨에서 70:30으로 강제 클리핑
- LLM이 우회 시도해도 후처리에서 차단

---

## 샘플 케이스

### 사실형 예시
```
갈등: A가 B와의 중요한 약속을 3번 연속 당일 취소
분류: factual

A 점수:
- boundaryViolation: 8 (반복적 약속 파기)
- fourHorsemenUsage: 5 (방어적 태도)
- repairAttemptLack: 6 (제대로 사과 안 함)
- perspectiveTakingLack: 7
- escalationContribution: 5
→ 가중 평균: 6.45

B 점수:
- boundaryViolation: 1
- fourHorsemenUsage: 3 (조금 비판적 표현)
- repairAttemptLack: 3
- perspectiveTakingLack: 2
- escalationContribution: 3
→ 가중 평균: 2.25

초기 비율: A 74.1 : B 25.9
factual이므로 클리핑 없음
반올림: A 75 : B 25
라벨: A "먼저 다가가면 좋은 쪽" / B "마음 열고 기다려주면 좋은 쪽"
```

### 차이형 예시
```
갈등: 연락 빈도 — A는 하루 종일 연락 원함, B는 할 일 할 때는 답 못함
분류: difference

A 점수: 4.5
B 점수: 3.5

초기 비율: A 56.25 : B 43.75
difference 클리핑 (70 이하) → 그대로
반올림: A 55 : B 45
라벨: A "먼저 다가가면 좋은 쪽" / B "마음 열고 기다려주면 좋은 쪽"
```

### 혼합형 예시
```
갈등: A가 돈 빌리고 안 갚음 + 서로 소비 가치관 차이
분류: mixed

A 점수: 6.8
B 점수: 2.2

초기 비율: A 75.6 : B 24.4
mixed 클리핑 (85 이하) → 그대로
반올림: A 75 : B 25
```

---

## Mock 구현 주의사항

Mock API에서는 **미리 준비된 3가지 시나리오** 중 하나를 랜덤 반환:
1. 사실형 샘플 (A 75 : B 25)
2. 차이형 샘플 (A 55 : B 45)
3. 혼합형 샘플 (A 65 : B 35)

실제 LLM 연동 전까지는 위 계산 로직을 Mock 데이터에 맞춰 하드코딩 가능.
단, 클리핑 로직은 Mock에서도 동작하도록 구현해서 나중에 LLM 응답도 이 게이트를 통과하게.

---

## UI 노출 정책 (2026-04-26 명문화)

### 노출 대상

- **화해 기여도 비율**: 양쪽 합 100% 정규화, 정수
- **4 Horsemen 점수**: 양쪽 각각, 4개 항목, 정수 (0~10)

### 노출 금지

- 관계 위험도, 이혼 가능성 등 종합 점수
- 시간 경과에 따른 점수 변화 그래프 (사용자 불안 트리거)
- 다른 부부와의 비교 점수 (랭킹)

### 표현 강제 규칙

| 항목 | 규칙 |
|---|---|
| 수치 형식 | 소수점 없는 정수만 |
| 레이블 | "화해 기여도", "비난/경멸/방어/담쌓기" 한국어 표기 |
| 색상 | 따뜻한 단일 계열 그라디언트. 빨강/위험색 금지 |
| 동반 문구 | 매 노출 시 "잘잘못이 아니라 노력의 양"임을 명시 |
| 부정 표현 | "위험", "위기", "심각" 등 금지 (FORBIDDEN_WORDS.md 준수) |

### 향후 변경 조건

다음 조건 중 하나 충족 시 정량 점수 노출 정책 재검토:

1. MAU 1,000 이상 도달 시
2. 사용자 부정 피드백이 전체 피드백의 10% 이상 누적 시
3. 점수로 인한 사용자 분쟁 사례 5건 이상 보고 시

재검토 시 옵션:
- 옵션 A: 현 정책 유지
- 옵션 B: 점수 노출 옵트인(기본 비노출) 전환
- 옵션 C: 내부 로깅·누적 트래킹 전용으로 전환
