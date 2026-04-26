# 금지어 정책

UI · 코드 · 프롬프트 · LLM 출력 모두에서 다음 단어 사용 금지. 4단계로 분류.

## Source of truth

- FE: `frontend/lib/constants/forbiddenWords.ts`, `frontend/scripts/check-forbidden-words.js`
- BE: `backend/src/main/resources/safety/forbidden-words.yml`, `backend/.../safety/KeywordGuard.java`

코드와 다르면 코드가 옳습니다 — 문서를 갱신하세요.

## 검증

```bash
cd frontend && npm run lint:words   # 하드코딩된 금지어 스캔, 위반 시 exit 1
```

BE는 `KeywordGuard`가 사용자 입력 단계에서 자동 검사 + LLM 응답 후처리에서도 재검사.

---

## Level 1 — 법률 용어 (변호사법 저촉 우려)

| 금지어 | 대체어 |
|---|---|
| 과실비율 | 화해 기여도 |
| 판결 | 결과 / 분석 |
| 판사 | 중재자 |
| 유죄 | (사용 금지) |
| 무죄 | (사용 금지) |
| 가해자 | (사용 금지, 낙인) |
| 피해자 | (사용 금지, 낙인) |
| 고소 | (사용 금지) |
| 소송 | (사용 금지) |
| 증거 | 입력 / 말씀 |
| 심판 | 중재 |

## Level 2 — 임상·병리 용어

진단명·임상 라벨은 의료법 저촉 + 악용 가능 + 낙인 위험.

| 금지어 | 비고 |
|---|---|
| 나르시시스트 | 진단명 |
| 소시오패스 | 진단명 |
| 가스라이팅 | 악용 가능 용어 |
| 회피성 / 경계성 성격 | 임상 용어 |
| 공의존 | 임상 용어 |
| 트라우마 / PTSD | 임상 용어 |
| ADHD / 자폐 | 임상 용어 |
| 우울증 / 조울증 | 진단명 |
| 회피형 / 불안형 (애착) | 애착이론 자체 제거 |

**대체 원칙**: 진단·인격이 아니라 **구체적 행동**으로 기술.

| 진단 | 행동 기술로 대체 |
|---|---|
| "회피형이시네요" | "대화 중 거리를 두고 싶어하시는 편이군요" |
| "가스라이팅이에요" | "상대의 표현이 자주 흔들리는 것처럼 들리실 수 있어요" |

## Level 3 — 판결·승패 용어

관계는 누가 이기고 지는 게임이 아님.

| 금지어 | 대체어 |
|---|---|
| 이겼다 / 졌다 | (사용 금지) |
| 승자 / 패자 | (사용 금지) |
| 맞다 / 틀렸다 | 다르다 / 각자의 관점 |
| A가 잘못했다 | A님의 이 부분이 아쉬웠어요 |
| 정답 / 오답 | (사용 금지) |

## Level 4 — 관계 파국 조장

관계 결정(헤어짐 등)은 서비스 범위 외.

| 금지어 | 이유 |
|---|---|
| 헤어지세요 / 이혼 추천 | 관계 결정은 사용자의 몫 |
| 절교 / 손절 / 인연 끊기 | 관계 파국 조장 |
| "이런 사람 만나지 마세요" | 낙인·판단 |

---

## UI 카피 가이드

### ✅ 좋은 표현

- "두 분 모두 나름의 이유가 있으셨어요"
- "이 부분에서 마음이 어려우셨을 것 같아요"
- "함께 다가가면 관계가 회복될 수 있어요"
- "서로의 방식이 달랐을 뿐이에요"
- "조금 더 이해해볼 수 있을까요?"

### ❌ 나쁜 표현

- "A님이 잘못하셨네요"
- "B님이 문제예요"
- "이건 가스라이팅이에요"
- "당신이 피해자입니다"
- "이 관계는 끝내는 게 좋겠어요"

## 검증 스크립트 동작 (`frontend/scripts/check-forbidden-words.js`)

```javascript
const FORBIDDEN = [
  '과실비율','판결','판사','유죄','무죄',
  '가해자','피해자','승자','패자',
  '나르시시스트','소시오패스','가스라이팅',
  '손절','절교','절연',
];
const ALLOWED_CONTEXTS = ['판결이 아니라', '판결·승패']; // 사전 승인된 안내 문맥
```

`app/`, `components/`, `lib/`, `mocks/` 스캔. 위반 시 exit 1로 빌드 차단.

`package.json` 등록:
```json
"scripts": { "lint:words": "node scripts/check-forbidden-words.js" }
```

CI/CD 미사용 환경에서는 PR 전 수동 실행.

## 위험 키워드 (세션 즉시 중단)

위험 키워드 감지는 별도 정책: [crisis-detection.md](./crisis-detection.md).

## BE 측 일치 (`KeywordGuard`)

`backend/src/main/java/com/againspring/safety/KeywordGuard.java`가 동일 단어 목록을 `safety/forbidden-words.yml`에서 로드하여:

1. 사용자 입력에 포함 시 → `BusinessException(FORBIDDEN_WORD_DETECTED)`
2. LLM 응답에 포함 시 → 응답 차단 + `FallbackResponses` 반환

FE의 `forbiddenWords.ts`와 BE의 `forbidden-words.yml`이 어긋나면 BE가 마지막 게이트 — 변경 시 양쪽 동시 갱신 필요.
