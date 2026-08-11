# 금지어 정책

## 철학적 기반

> **"공감이지 판결이 아니다"**

다시봄의 공감 비율은 법정 판결과 다르다. 커뮤니티·AI 출력은 유죄·무죄를 선고하지 않고 **공감·관점**을 표현한다.

- **허용**: "공감 비율", "작성자/상대방", "관점" — 제품 용어
- **금지**: 실제 법적 결론("유죄/무죄/가해자/피해자/이겼다/졌다") + 관계 결정 지시 + 임상 진단명
- **핵심 구별**: 집계되는 것은 "작성자 공감 비율 X%"이지, "작성자가 잘못했다"가 아님

이 원칙은 UI 카피, 코드 변수명, LLM 프롬프트, AI 생성 텍스트 모두에 적용됩니다.  
단, **사용자가 직접 입력한 텍스트**에는 미적용 (사용자 자율성 존중).

---

## Source of truth

- FE: `frontend/lib/constants/forbiddenWords.ts`, `frontend/scripts/check-forbidden-words.js`
- BE: `backend/src/main/resources/safety/forbidden-words.yml`, `backend/.../safety/KeywordGuard.java`

코드와 다르면 코드가 옳습니다 — 이 문서를 갱신하세요.

## 검증

```bash
cd frontend && npm run lint:words   # 하드코딩된 금지어 스캔, 위반 시 exit 1
```

BE는 `KeywordGuard`가 LLM 응답 후처리에서 재검사. 사용자 입력에는 적용 안 함(광장형 정책).

---

## Level 1 — 법률 용어 (변호사법 저촉 우려)

| 금지어 | 대체어 |
|---|---|
| 과실비율 | 공감 비율 |
| 판결 | 공감 / 관점 / 분석 |
| 판사 | (사용 금지) |
| 유죄 | (사용 금지) |
| 무죄 | (사용 금지) |
| 가해자 | (사용 금지, 낙인) |
| 피해자 | (사용 금지, 낙인) |
| 고소 | (사용 금지) |
| 소송 | (사용 금지) |
| 증거 | 입력 / 말씀 |
| 심판 | (사용 금지) |

---

## Level 2 — 임상·낙인 진단명

| 금지어 | 대체 |
|---|---|
| 나르시시스트 / 소시오패스 | (사용 금지) → 구체적 행동 기술 |
| 가스라이팅 | (사용 금지) |
| PTSD / 조울 / 우울증 확정 진단 | 깊은 상처 / 힘든 마음 (진단 단정 금지) |

---

## Level 3 — 승패·파국 조장

| 금지어 | 이유 |
|---|---|
| 이겼다 / 졌다 / 승자 / 패자 | 승패 프레임 |
| 헤어지세요 / 이혼 추천 | 관계 결정은 사용자의 몫 |
| 절교 / 손절 / 인연 끊기 | 관계 파국 조장 |
| "이런 사람 만나지 마세요" | 낙인·판단 |

---

## AI 출력 카피 가이드

### ✅ 좋은 표현 (공감 비율·관점 기반)

- "작성자 입장에서는 이 부분이 납득하기 어려웠을 것 같습니다"
- "상대방 입장에서는 이런 마음이 들었을 수 있어요"
- "두 분 모두 나름의 이유가 있으셨어요"
- "공감 비율: 작성자 62% · 상대방 38%"

### ❌ 나쁜 표현 (판결·진단·파국 기반)

- "작성자가 잘못하셨네요" (판결)
- "상대방이 문제예요" (판결)
- "이건 가스라이팅이에요" (진단)
- "당신이 피해자입니다" (낙인)
- "이 관계는 끝내는 게 좋겠어요" (파국 조장)
- "과실비율 7:3" (법적 결론)

---

## 검증 스크립트 (`frontend/scripts/check-forbidden-words.js`)

```javascript
const FORBIDDEN = [
  '과실비율','판결','판사','유죄','무죄',
  '가해자','피해자','승자','패자',
  '나르시시스트','소시오패스','가스라이팅',
  '손절','절교','절연',
];
const ALLOWED_CONTEXTS = ['공감이지 판결이 아니다', '판결·승패']; // 사전 승인된 안내 문맥
```

`app/`, `components/`, `lib/`, `mocks/` 스캔. 위반 시 exit 1로 빌드 차단.

```json
"scripts": { "lint:words": "node scripts/check-forbidden-words.js" }
```

---

## BE 측 일치 (`KeywordGuard`)

`backend/src/main/java/com/againspring/safety/KeywordGuard.java`가 동일 단어 목록을 `safety/forbidden-words.yml`에서 로드하여:

1. LLM 응답에 포함 시 → 응답 차단 + `FallbackResponses` 반환
2. **사용자 입력에는 적용 안 함** (광장형 정책: 사용자 입력 필터 미적용)

FE의 `forbiddenWords.ts`와 BE의 `forbidden-words.yml`이 어긋나면 BE가 마지막 게이트 — 변경 시 양쪽 동시 갱신 필요.

---

## AI 유저 안전 가드 (`ContentSafetyGuard`)

봇 생성 콘텐츠 전용 — `ai-user/orchestrator/.../safety/ContentSafetyGuard.java`.  
**사용자 입력에는 미적용** (봇 콘텐츠만).

- **언어 가드** (2026-06-18): 한글 비율 < 10% → 무효 — 영어 거절·오류 감지 근본 방어
- **위기 키워드**: 자살, 자해, 극단적 선택 등 → 게시 차단
- **PII 패턴**: 전화번호, 주민번호, 이메일 등 → 게시 차단
- **LLM 오류 시그니처**: `LlmErrorSignature.java` 참조 (상세: `.claude/rules/llm-safety.md`)
- **내부 운영 메타 누출 차단** (2026-06-23): `적용 처리 메모`, `[작성 노트]`, `| 항목 | 처리 내용 |`, `- 트리거:` 등 내부 첨삭/규칙 요약이 붙으면 차단
- **거절/오류 이력 반영 금지**: DB `persona_history_entries`, legacy `history/*.md`, `voice_profile` 강화에도 동일 규칙 적용
  - 예: `I can't write this`, `I can't do this`, `I appreciate the detailed request`, `이 요청은 도와드릴 수 없습니다`, `실제 운영 중인`, `가짜 페르소나`

`KeywordGuard`(BE 실유저)와 `ContentSafetyGuard`(AI 유저 봇)는 별개 게이트 — 양쪽 모두 활성.
