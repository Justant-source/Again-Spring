# 금지어 및 대체어 사전

서비스 UI 전체에서 다음 단어들은 **절대 사용 금지**.
Claude Code는 커밋 전 전체 파일에서 검색해 확인.

---

## Level 1: 법적 리스크 단어 (UI 전면 금지)

| 금지어 | 대체어 | 이유 |
|---|---|---|
| 과실비율 | 화해 기여도 | 법률 용어, 변호사법 저촉 우려 |
| 판결 | 결과 / 분석 | 사법 용어 |
| 판사 | 중재자 | 사법 용어 |
| 유죄 | — (사용 금지) | 사법 용어 |
| 무죄 | — (사용 금지) | 사법 용어 |
| 가해자 | — (사용 금지) | 낙인 |
| 피해자 | — (사용 금지) | 낙인 |
| 고소 | — (사용 금지) | 법적 행위 |
| 소송 | — (사용 금지) | 법적 행위 |
| 증거 | 입력 / 말씀 | 사법 용어 |
| 심판 | 중재 | 사법 용어 |

---

## Level 2: 임상·병리 단어 (UI 전면 금지)

| 금지어 | 이유 |
|---|---|
| 나르시시스트 | 진단명 |
| 소시오패스 | 진단명 |
| 가스라이팅 | 악용 가능 용어 |
| 회피성 성격 | 임상 용어 |
| 경계성 성격 | 임상 용어 |
| 공의존 | 임상 용어 |
| 트라우마 | 임상 용어 (신중) |
| PTSD | 임상 용어 |
| ADHD / 자폐 | 임상 용어 |
| 우울증 / 조울증 | 진단명 |
| 회피형 / 불안형 (애착) | 애착이론은 이번 버전에서 제거 |

**대체 원칙**: 인격이나 진단이 아닌 **구체적 행동**으로 기술.
예: "회피형이시네요" → "대화 중 거리를 두고 싶어하시는 편이군요"

---

## Level 3: 판결·승패 단어 (UI 전면 금지)

| 금지어 | 대체어 |
|---|---|
| 이겼다 / 졌다 | — (사용 금지) |
| 승자 / 패자 | — (사용 금지) |
| 맞다 / 틀렸다 | 다르다 / 각자의 관점 |
| A가 잘못했다 | A님의 이 부분이 아쉬웠어요 |
| B가 나쁘다 | B님의 이 행동이 영향을 줬어요 |
| 정답 / 오답 | — (사용 금지) |

---

## Level 4: 관계 파국 조장 단어 (UI 전면 금지)

| 금지어 | 이유 |
|---|---|
| 헤어지세요 | 관계 결정은 서비스 범위 외 |
| 이혼 추천 | 법적 조언 |
| 절교 | 관계 파국 조장 |
| 손절 | 관계 파국 조장 |
| 인연 끊기 | 관계 파국 조장 |
| "이런 사람 만나지 마세요" | 낙인·판단 |

---

## 위험 키워드 (세션 즉시 중단 트리거)

Level 1 위험 키워드 감지 시 → **세션 강제 종료 + Crisis Resource 모달**:

```typescript
export const CRISIS_KEYWORDS = {
  domestic_violence: [
    '때리', '때렸', '맞았', '맞고', '폭행', '폭력', 
    '학대', '때릴', '때려', '구타', '상해', 
  ],
  sexual_violence: [
    '강간', '성폭행', '성폭력', '강제로', 
  ],
  self_harm: [
    '죽고 싶', '죽고싶', '자살', '자해', '뛰어내리', 
    '목 매', '목매', '약 먹고 죽',
  ],
  child_abuse: [
    '아이를 때', '애를 때', '아동학대',
  ],
};

// Level 2: 경고 표시 + 계속 진행
export const WARNING_KEYWORDS = {
  legal: ['이혼', '절연', '고소', '신고', '소송', '변호사'],
  extreme_emotion: ['미치겠', '참을 수 없', '죽여버리'],
};
```

### 키워드 감지 로직

```typescript
export function checkKeywords(text: string): {
  level: 1 | 2 | null;
  category: string | null;
} {
  for (const [category, keywords] of Object.entries(CRISIS_KEYWORDS)) {
    for (const keyword of keywords) {
      if (text.includes(keyword)) {
        return { level: 1, category };
      }
    }
  }
  
  for (const [category, keywords] of Object.entries(WARNING_KEYWORDS)) {
    for (const keyword of keywords) {
      if (text.includes(keyword)) {
        return { level: 2, category };
      }
    }
  }
  
  return { level: null, category: null };
}
```

---

## Level 1 감지 시 대응 (Crisis Resource)

세션 즉시 중단 후 모달:

```
🚨 중요한 안내

말씀해주신 상황은 저희 서비스의 범위를 넘어서는 
매우 중요한 문제예요. 지금 바로 전문 기관의 도움을 받아주세요.

━━━━━━━━━━━━━━━━━━━━━━
가정폭력·성폭력 (여성긴급전화)
📞 1366  (24시간, 무료)

정신건강 위기상담
📞 1577-0199  (24시간, 무료)

아동학대 신고
📞 112 또는 1391

청소년 상담
📞 1388  (24시간)

자살예방상담
📞 1393  (24시간, 무료)
━━━━━━━━━━━━━━━━━━━━━━

[전화 걸기] [문자 상담] [나중에]

※ 이 창은 나중에 다시 볼 수 있도록 저장됩니다.
```

---

## Level 2 감지 시 대응 (경고 표시)

세션은 계속 진행하되 안내 배너:

```
⚠️ 안내

"이혼"과 같은 법적 결정은 저희 서비스가 도와드릴 수 없어요.
이 서비스는 관계 회복을 위한 대화 정리를 돕는 것이 목표입니다.
법적 조언이 필요하시면 대한법률구조공단(132)을 이용해주세요.
```

---

## UI 카피 가이드

### 좋은 카피 예시

- ✅ "두 분 모두 나름의 이유가 있으셨어요"
- ✅ "이 부분에서 마음이 어려우셨을 것 같아요"
- ✅ "함께 다가가면 관계가 회복될 수 있어요"
- ✅ "서로의 방식이 달랐을 뿐이에요"
- ✅ "조금 더 이해해볼 수 있을까요?"

### 나쁜 카피 예시

- ❌ "A님이 잘못하셨네요"
- ❌ "B님이 문제예요"
- ❌ "이건 가스라이팅이에요"
- ❌ "당신이 피해자입니다"
- ❌ "이 관계는 끝내는 게 좋겠어요"

---

## Claude Code 검증 스크립트

커밋 전 자동 검사용 Node 스크립트 (개발 중 추가 권장):

```javascript
// scripts/check-forbidden-words.js
const fs = require('fs');
const path = require('path');

const FORBIDDEN = [
  '과실비율', '판결', '판사', '유죄', '무죄', 
  '가해자', '피해자', '승자', '패자',
  '나르시시스트', '소시오패스', '가스라이팅',
];

function scanDir(dir) {
  const files = fs.readdirSync(dir);
  for (const file of files) {
    const fullPath = path.join(dir, file);
    if (fs.statSync(fullPath).isDirectory() && !file.startsWith('.') && file !== 'node_modules') {
      scanDir(fullPath);
    } else if (/\.(tsx?|jsx?|md)$/.test(file)) {
      const content = fs.readFileSync(fullPath, 'utf-8');
      for (const word of FORBIDDEN) {
        if (content.includes(word)) {
          // 내부 주석이나 설명 문서는 예외 (FORBIDDEN_WORDS.md 등)
          if (file.includes('FORBIDDEN') || file.includes('SYSTEM_PROMPT')) continue;
          console.error(`⚠️ ${fullPath}: "${word}" 발견`);
        }
      }
    }
  }
}

scanDir('./app');
scanDir('./components');
scanDir('./lib');
```

`package.json`에 추가:
```json
"scripts": {
  "lint:words": "node scripts/check-forbidden-words.js"
}
```
