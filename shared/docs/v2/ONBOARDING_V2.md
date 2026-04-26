# 온보딩 v2 — 10문항 + MBTI 슬라이더 + 60문항 정밀 테스트

**버전**: v2.0
**대상**: Claude Code
**연관 작업**: `REFINEMENT_WORK_ORDER.md` Phase 3

---

## 🎯 목표

**달콩님 요구사항**:
- 10문항 경향성 테스트는 **필수**
- MBTI는 **수동 입력** 가능 (단순 입력 X, **각 축마다 비율 슬라이더**)
- 또는 **60문항 정밀 테스트**로 정확한 비율 산출
- MBTI 데이터를 LLM 컨텍스트에 반영하여 **결과 카드 개인화**

---

## 📊 3단계 온보딩 구조

```
[STEP 1] 10문항 경향성 테스트 (필수, 90초)
  → 6가지 커뮤니케이션 스타일 (파도형/산형/불꽃형/이파리형/달빛형/별빛형)
  
       │
       ▼
[STEP 2] MBTI 입력 (선택, 안내 화면)
  ┌─────────────────────────────────────────┐
  │ MBTI를 어떻게 입력할까요?               │
  │                                         │
  │ ① 알고 있어요 → 슬라이더로 빠르게       │
  │ ② 정확히 알고 싶어요 → 60문항 정밀 테스트│
  │ ③ 나중에 입력할게요 → 결과 페이지로     │
  └─────────────────────────────────────────┘
       │
       ├── ① → STEP 3a (슬라이더)
       └── ② → STEP 3b (60문항)
       
[STEP 3a] MBTI 슬라이더 (1분)
  4축 각각 0-100 비율 슬라이더
  
[STEP 3b] 60문항 정밀 테스트 (10-15분)
  자동 비율 산출 → 슬라이더에 결과 반영
  → 사용자가 슬라이더 미세조정 가능
```

---

## 1️⃣ STEP 1: 10문항 경향성 테스트 (기존 유지)

기존 `ONBOARDING_MAPPING.md` 그대로 유지. 변경 없음.

- 5점 리커트 척도
- 6가지 스타일 매핑 (파도형/산형/불꽃형/이파리형/달빛형/별빛형)
- 평균 90초 소요
- **회원가입 직후 필수 통과**

### 결과 화면 후 안내

10문항 결과 카드 표시 후 다음 화면:

```
┌─────────────────────────────────────────────┐
│ 🎉 [닉네임]님은 [🌊 파도형]이시군요!         │
│                                             │
│ 더 정확한 안내를 받고 싶다면, MBTI도         │
│ 함께 알려주세요.                             │
│                                             │
│ MBTI는 결과 카드를 더 개인화하는 데          │
│ 사용돼요.                                    │
│                                             │
│ ┌──────────────────────────────────┐        │
│ │ 🎯 MBTI 슬라이더로 빠르게 입력    │        │
│ │ (이미 알고 있어요)                │        │
│ └──────────────────────────────────┘        │
│                                             │
│ ┌──────────────────────────────────┐        │
│ │ 📝 60문항 정밀 테스트             │        │
│ │ (정확하게 알고 싶어요, 10-15분)   │        │
│ └──────────────────────────────────┘        │
│                                             │
│ ┌──────────────────────────────────┐        │
│ │ ⏭️ 나중에 입력할게요              │        │
│ └──────────────────────────────────┘        │
└─────────────────────────────────────────────┘
```

---

## 2️⃣ STEP 3a: MBTI 슬라이더 (`/onboarding/mbti`)

### UI 구조

```
┌─────────────────────────────────────────────┐
│ 🎯 MBTI 슬라이더                            │
│                                             │
│ 각 축의 비율을 자유롭게 조절해주세요.        │
│                                             │
│ ─────────────────────────────────────       │
│                                             │
│ 1. 에너지 방향                              │
│ E (외향) ←──────●──────→ I (내향)           │
│           65       35                       │
│                                             │
│ ─────────────────────────────────────       │
│                                             │
│ 2. 정보 수집                                │
│ S (감각) ←────────●────→ N (직관)           │
│           45         55                     │
│                                             │
│ ─────────────────────────────────────       │
│                                             │
│ 3. 의사 결정                                │
│ T (사고) ←──●──────────→ F (감정)           │
│         25         75                       │
│                                             │
│ ─────────────────────────────────────       │
│                                             │
│ 4. 생활 양식                                │
│ J (판단) ←──────────●──→ P (인식)           │
│             80       20                     │
│                                             │
│ ─────────────────────────────────────       │
│                                             │
│ 결과: ENFJ (외향 65% / 직관 55% / 감정 75% / │
│       판단 80%)                             │
│                                             │
│ ┌──────────────────────────────────┐        │
│ │ ✅ 저장하기                       │        │
│ └──────────────────────────────────┘        │
│                                             │
│ ┌──────────────────────────────────┐        │
│ │ 📝 60문항 테스트로 정확하게 알고  │        │
│ │   싶어요                          │        │
│ └──────────────────────────────────┘        │
└─────────────────────────────────────────────┘
```

### 슬라이더 컴포넌트

```typescript
// components/onboarding/MbtiAxisSlider.tsx
interface MbtiAxisSliderProps {
  axisLabel: string;      // "에너지 방향"
  leftLabel: string;      // "E (외향)"
  rightLabel: string;     // "I (내향)"
  value: number;          // 0-100, 0=완전 left, 100=완전 right
  onChange: (value: number) => void;
}

export function MbtiAxisSlider({
  axisLabel,
  leftLabel,
  rightLabel,
  value,
  onChange
}: MbtiAxisSliderProps) {
  return (
    <div className="space-y-3">
      <h3 className="text-sm font-medium text-gray-700">{axisLabel}</h3>
      <div className="flex items-center gap-4">
        <span className="text-sm font-medium">{leftLabel}</span>
        <input
          type="range"
          min={0}
          max={100}
          step={5}
          value={value}
          onChange={(e) => onChange(Number(e.target.value))}
          className="flex-1 h-2 bg-gradient-to-r from-spring-lavender to-spring-pink rounded-lg appearance-none cursor-pointer"
        />
        <span className="text-sm font-medium">{rightLabel}</span>
      </div>
      <div className="flex justify-center text-xs text-gray-500">
        {100 - value}% : {value}%
      </div>
    </div>
  );
}
```

### 결과 계산 로직

```typescript
// lib/utils/mbtiCalculator.ts

export interface MbtiProfile {
  e_i: number;  // 0=E(0%), 100=I(100%)
  s_n: number;  // 0=S, 100=N
  t_f: number;  // 0=T, 100=F
  j_p: number;  // 0=J, 100=P
}

export function calculateMbtiType(profile: MbtiProfile): string {
  // 50% 기준으로 4글자 결정
  const e_i_letter = profile.e_i < 50 ? 'E' : 'I';
  const s_n_letter = profile.s_n < 50 ? 'S' : 'N';
  const t_f_letter = profile.t_f < 50 ? 'T' : 'F';
  const j_p_letter = profile.j_p < 50 ? 'J' : 'P';
  
  return `${e_i_letter}${s_n_letter}${t_f_letter}${j_p_letter}`;
}

export function describeMbtiProfile(profile: MbtiProfile): string {
  const type = calculateMbtiType(profile);
  
  // 강도 분류
  const intensities = {
    e_i: getIntensity(profile.e_i),
    s_n: getIntensity(profile.s_n),
    t_f: getIntensity(profile.t_f),
    j_p: getIntensity(profile.j_p),
  };
  
  return `${type} (${formatAxisDescription('외향', '내향', profile.e_i)} / ${
    formatAxisDescription('감각', '직관', profile.s_n)
  } / ${formatAxisDescription('사고', '감정', profile.t_f)} / ${
    formatAxisDescription('판단', '인식', profile.j_p)
  })`;
}

function getIntensity(value: number): 'strong' | 'moderate' | 'mild' {
  const distance = Math.abs(value - 50);
  if (distance >= 35) return 'strong';
  if (distance >= 15) return 'moderate';
  return 'mild';
}

function formatAxisDescription(left: string, right: string, value: number): string {
  const leftPct = 100 - value;
  const rightPct = value;
  return value < 50 ? `${left} ${leftPct}%` : `${right} ${rightPct}%`;
}
```

---

## 3️⃣ STEP 3b: 60문항 정밀 테스트 (`/onboarding/mbti/full`)

### 구조

- 각 축당 15문항씩 총 60문항
- 5점 리커트 척도 (전혀 그렇지 않다 ~ 매우 그렇다)
- 평균 10-15분 소요
- 진행률 바 표시 (1/60, 2/60, ...)

### 문항 예시 (각 축 15문항씩)

#### E↔I (외향성-내향성) 15문항

```
1. 새로운 사람을 만나면 에너지가 충전된다.
2. 혼자 있는 시간이 필요하다.  (역방향)
3. 모임에 가면 시간 가는 줄 모르고 즐긴다.
4. 깊이 생각할 때는 혼자 있는 게 좋다.  (역방향)
5. 처음 보는 사람과도 쉽게 대화를 시작한다.
6. 큰 모임보다는 1-2명과 깊은 대화를 좋아한다.  (역방향)
7. 휴일에는 외출하는 것이 좋다.
8. 휴일에는 집에서 쉬는 것이 좋다.  (역방향)
9. 생각을 말로 정리하는 편이다.
10. 생각을 정리한 후에 말하는 편이다.  (역방향)
... (15문항까지)
```

#### S↔N (감각-직관) 15문항

```
1. 구체적인 사실과 데이터를 중요시한다.
2. 미래의 가능성에 더 흥미를 느낀다.  (역방향)
3. 눈에 보이는 것을 그대로 받아들인다.
4. 행간을 읽으려고 한다.  (역방향)
5. 실용적인 것이 좋다.
6. 새롭고 독창적인 것이 좋다.  (역방향)
7. 한 번에 한 가지에 집중한다.
8. 여러 가지를 동시에 생각한다.  (역방향)
9. 디테일에 강하다.
10. 큰 그림을 보는 편이다.  (역방향)
... (15문항까지)
```

#### T↔F (사고-감정) 15문항

```
1. 결정할 때 논리와 객관성을 중시한다.
2. 결정할 때 사람의 감정을 먼저 생각한다.  (역방향)
3. 갈등 상황에서 옳고 그름을 따진다.
4. 갈등 상황에서 화합을 우선시한다.  (역방향)
5. 비판도 발전을 위한 거라면 환영한다.
6. 비판은 듣기 힘들다.  (역방향)
7. 감정에 휘둘리지 않는 편이다.
8. 다른 사람의 감정을 잘 알아챈다.  (역방향)
... (15문항까지)
```

#### J↔P (판단-인식) 15문항

```
1. 계획대로 진행되는 것이 좋다.
2. 상황에 따라 유연하게 대응하는 것이 좋다.  (역방향)
3. 마감 전에 미리 끝내야 안심된다.
4. 마감 직전에 집중력이 올라간다.  (역방향)
5. 결정을 빠르게 내리는 편이다.
6. 가능성을 열어두는 것이 좋다.  (역방향)
7. 정리정돈이 잘 되어있어야 한다.
8. 자유로운 환경이 좋다.  (역방향)
... (15문항까지)
```

### 60문항 결과 → 슬라이더 자동 반영

```typescript
// lib/utils/mbtiFullTestCalculator.ts

interface MbtiFullTestAnswers {
  e_i: number[];  // 15개 답변 (1-5)
  s_n: number[];
  t_f: number[];
  j_p: number[];
}

export function calculateMbtiFromFullTest(
  answers: MbtiFullTestAnswers,
  reverseScored: { e_i: boolean[], s_n: boolean[], t_f: boolean[], j_p: boolean[] }
): MbtiProfile {
  
  function calculateAxis(scores: number[], reverseFlags: boolean[]): number {
    let sum = 0;
    scores.forEach((score, i) => {
      // 역방향 문항은 6 - score로 변환
      const adjusted = reverseFlags[i] ? 6 - score : score;
      sum += adjusted;
    });
    
    // 15문항 * 5점 만점 = 75점, 15문항 * 1점 최소 = 15점
    // 정규화: (sum - 15) / 60 * 100 → 0~100 범위
    const normalized = ((sum - 15) / 60) * 100;
    return Math.round(normalized);
  }
  
  return {
    e_i: calculateAxis(answers.e_i, reverseScored.e_i),
    s_n: calculateAxis(answers.s_n, reverseScored.s_n),
    t_f: calculateAxis(answers.t_f, reverseScored.t_f),
    j_p: calculateAxis(answers.j_p, reverseScored.j_p),
  };
}
```

### 60문항 완료 후 슬라이더 화면 자동 이동

```typescript
// app/(onboarding)/mbti/full/page.tsx

// 60문항 완료 시:
const profile = calculateMbtiFromFullTest(answers, REVERSE_FLAGS);
await api.users.updateMbti(profile);

// 슬라이더 페이지로 리다이렉트하되 결과 미리 채움
router.push(`/onboarding/mbti?prefilled=true`);

// 슬라이더 페이지는 기존 사용자 MBTI 프로필 로드해서 표시
// 사용자가 미세조정 가능
```

---

## 🗄️ DB 스키마 변경

### `users` 테이블 추가 컬럼

```sql
ALTER TABLE users
ADD COLUMN mbti_profile JSON NULL,
ADD COLUMN mbti_input_method ENUM('manual_slider', 'full_test', 'none') DEFAULT 'none',
ADD COLUMN mbti_completed_at TIMESTAMP NULL;
```

### 데이터 형식

```json
{
  "e_i": 35,
  "s_n": 70,
  "t_f": 25,
  "j_p": 60,
  "calculatedType": "ESTJ",
  "intensityProfile": {
    "e_i": "moderate",
    "s_n": "strong",
    "t_f": "strong",
    "j_p": "moderate"
  }
}
```

---

## 🌐 API 엔드포인트

### `PATCH /api/users/me/mbti`

**Request**
```json
{
  "profile": {
    "e_i": 35,
    "s_n": 70,
    "t_f": 25,
    "j_p": 60
  },
  "inputMethod": "manual_slider"  // or "full_test"
}
```

**Response 200**
```json
{
  "mbtiProfile": {
    "e_i": 35,
    "s_n": 70,
    "t_f": 25,
    "j_p": 60,
    "calculatedType": "ESTJ",
    "intensityProfile": { ... }
  },
  "completedAt": "2026-04-24T10:30:00Z"
}
```

### `POST /api/users/me/mbti/full-test`

60문항 답변 제출.

**Request**
```json
{
  "answers": {
    "e_i": [3, 5, 2, 4, 1, 5, 3, 4, 2, 5, 3, 4, 1, 5, 2],
    "s_n": [4, 2, 5, 1, 3, ...],
    "t_f": [...],
    "j_p": [...]
  }
}
```

**Response 200**
```json
{
  "mbtiProfile": { ... },
  "redirectTo": "/onboarding/mbti?prefilled=true"
}
```

---

## 🎨 LLM 컨텍스트 주입

### 시스템 프롬프트 추가 섹션

```markdown
# 사용자 성격 정보

## 사용자 A
- 커뮤니케이션 스타일: 🌊 파도형 (감정 표현이 풍부, 즉각적)
- MBTI: ENFJ (외향 65% / 직관 55% / 감정 75% / 판단 80%)
  → 외향성: 사람과의 교류에서 에너지 얻음, 침묵하는 시간이 길면 답답함 느낄 수 있음
  → 직관: 큰 그림과 가능성을 보지만, 구체적 계획 부족할 수 있음
  → 감정: 결정 시 사람의 감정을 우선시, 비판에 민감
  → 판단: 계획·구조·결론을 선호, 갈등을 빨리 해결하고 싶어함

## 사용자 B
- 커뮤니케이션 스타일: 🏔️ 산형 (차분하고 거리를 두고 생각)
- MBTI: 입력 안 함 (또는 ISTP 등)

## 활용 지침
- 결과 카드 작성 시 두 분의 성격 차이를 자연스럽게 반영
- 임상 용어(나르시시스트, 회피형 등)는 절대 사용 X
- "성격이 다르다"가 아니라 "에너지를 얻는 방식이 다르다" 식으로 부드럽게
```

### 메타포 카드 LLM 프롬프트에 MBTI 활용

```markdown
# MBTI 기반 카드 개인화

## 카드 1 "두 분의 욕구"
- A의 외향성/내향성 비율을 반영해 "함께 있는 시간 vs 혼자 시간" 욕구를 자연스럽게 묘사
- T-F 비율을 반영해 "논리적 해결 vs 감정 인정" 욕구 차이 표현

## 카드 2 "함께 자라는 길"
- J-P 비율 반영
- A가 J 강하고 B가 P 강하다면 "결론을 빨리 내고 싶은 분과 가능성을 열어두고 싶은 분의 만남"
- S-N 비율 반영
- A가 S 강하고 B가 N 강하다면 "구체적 사실을 중시하는 분과 큰 그림을 보는 분"

## 카드 3 "다음 한 걸음"
- 사용자 MBTI에 맞는 행동 제안
- 외향형: "직접 만나서 얘기" 제안
- 내향형: "글로 마음 정리 후 전달" 제안
- 감정형: "감정 먼저 인정" 제안
- 사고형: "구체적 합의 사항" 제안
```

---

## 📋 컴포넌트 명세

### 신규 컴포넌트

#### `components/onboarding/MbtiAxisSlider.tsx`
- 4축 각각 슬라이더
- 0-100 범위, 5단위 step
- 봄 색상 그라디언트 슬라이더
- 양쪽 라벨 + 비율 표시

#### `components/onboarding/MbtiResultPreview.tsx`
- 4글자 + 비율 + 강도 표시
- 실시간 업데이트 (슬라이더 조정 시)

#### `components/onboarding/MbtiQuestionCard.tsx`
- 60문항 테스트용
- 1문항씩 표시 + 5점 리커트
- 진행률 바
- 이전/다음 버튼

#### `components/onboarding/MbtiFullTestProgress.tsx`
- 4축별 진행률
- 1/60 같은 카운트
- 예상 남은 시간

### 페이지

- `app/(onboarding)/mbti/page.tsx` — 슬라이더 입력
- `app/(onboarding)/mbti/full/page.tsx` — 60문항 테스트
- `app/(onboarding)/mbti/full/result/page.tsx` — 결과 (자동으로 슬라이더 페이지로 리다이렉트)

---

## 🔗 온보딩 진행 흐름 통합

```
[회원가입] 
    ↓
[Step 1: 10문항 경향성 테스트] (필수, 90초)
    ↓
[Step 1 결과: 6스타일 카드]
    ↓
[Step 2: MBTI 안내]
    ├─→ "슬라이더로 빠르게" → [Step 3a: 슬라이더 1분]
    ├─→ "60문항 정밀 테스트" → [Step 3b: 60문항 10-15분]
    │       ↓
    │     [60문항 완료 → 슬라이더 자동 채움]
    │       ↓
    │     [Step 3a: 슬라이더 미세조정]
    └─→ "나중에 입력" → [건너뛰고 다음 단계]
    ↓
[Quick Describe로 이동]
```

---

## 🧪 검증 시나리오

### 시나리오 1: MBTI 알고 있는 사용자
```
1. 10문항 완료
2. "슬라이더로 빠르게" 클릭
3. E/I 슬라이더 = 65 (E 35%, I 65%)
4. S/N 슬라이더 = 30 (S 70%, N 30%)
5. T/F 슬라이더 = 75 (T 25%, F 75%)
6. J/P 슬라이더 = 20 (J 80%, P 20%)
7. 결과 미리보기: ISFJ (내향 65 / 감각 70 / 감정 75 / 판단 80)
8. 저장 → DB에 mbti_profile 저장
```

### 시나리오 2: MBTI 정확히 알고 싶은 사용자
```
1. 10문항 완료
2. "60문항 정밀 테스트" 클릭
3. 60문항 진행 (각 축 15문항)
4. 자동 비율 산출
5. 슬라이더 페이지로 리다이렉트 (결과 미리 채움)
6. 사용자 미세조정 가능
7. 저장
```

### 시나리오 3: MBTI 안 하는 사용자
```
1. 10문항 완료
2. "나중에 입력할게요" 클릭
3. Quick Describe로 바로 이동
4. mbti_profile = null 유지
5. LLM 프롬프트에 MBTI 컨텍스트 없이 진행
6. 프로필 페이지에서 언제든 추가 입력 가능
```

### 시나리오 4: LLM 결과 카드에 MBTI 반영
```
A: ENFJ (외향 65 / 직관 55 / 감정 75 / 판단 80)
B: ISTP (내향 70 / 감각 65 / 사고 60 / 인식 75)

기대 메타포 카드 1:
"한 분은 사람과의 만남에서 에너지를 얻고
 다른 한 분은 혼자만의 시간에서 회복하시는,
 서로 다른 리듬을 가진 두 분의 만남이에요."

(외향형 vs 내향형 차이를 자연스럽게 반영)
```

---

## ✅ Phase 3 완료 조건

- [ ] 10문항 경향성 테스트 정상 동작 (필수)
- [ ] MBTI 슬라이더 4축 정상 동작
- [ ] 60문항 정밀 테스트 정상 동작
- [ ] 60문항 결과가 슬라이더에 자동 반영
- [ ] 사용자가 슬라이더 미세조정 가능
- [ ] DB `users.mbti_profile` 정상 저장
- [ ] LLM 프롬프트에 MBTI 컨텍스트 주입 확인
- [ ] 메타포 카드 생성 시 MBTI 반영 확인
- [ ] MBTI 스킵 시 정상 동작
- [ ] 프로필 페이지에서 MBTI 재입력 가능

---

**끝.**
