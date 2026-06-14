# 심리학 모델: Gottman / NVC / EFT

**목적**: 다시봄의 AI 배심원이 활용하는 세 가지 심리학 프레임워크의 이론 기초 및 응용.

**범위**: AI 출력만 (사용자 입력에 미적용). 권위본: [`docs/shared/adr/0002`](../adr/0002-psychology-model-repurposed-for-jurors.md)

---

## 1. Gottman 4 Horsemen (갈등 패턴 진단)

### 개요

John Gottman 박사의 "Predict Divorce" 연구에서 도출한 관계 붕괴의 4가지 파괴적 패턴.

| 기마병 | 정의 | 예시 | 배심원의 관점 |
|---|---|---|---|
| **Criticism** (비난) | 상대의 인격 결함을 지적 | "너는 정말 무책임한 사람이야" | 행동 기술로 관찰 재구성: "이 상황에서 다르게 대응할 방법은?" |
| **Contempt** (경멸) | 상대를 열등하다고 판단 | "넌 아무것도 못 해" (손가락질, 눈 굴림) | 가치 재설정: "상대방도 배우고 성장하는 중일 수 있습니다" |
| **Defensiveness** (방어) | 상대의 지적을 거부하고 역공 | "다 너 때문이야", "내가 뭘 잘못했는데" | 공감 프레임: "상대는 어떤 필요를 표현하려 했을까?" |
| **Stonewalling** (침묵) | 상대와의 대화 거부, 회피 | 침묵, 관심 차단, 집을 나감 | 재연결 시도: "대화를 다시 시작할 안전한 방법은?" |

### 배심원 활용

각 배심원은 **Gottman-aware** 관점을 포함:
- "이 상황에서 [Horseman] 패턴이 보이신다면, 여기서 벗어나는 방법은..."
- 점수 계산은 내부용만 (UI 노출 금지)
- 판결 표현 금지 ("당신이 틀렸다" ❌), 관찰 + 대안 제시 ✅

### 원문

[John M. Gottman, "Predictors of Dissolution of Marriage in a Seven-Year Prospective Longitudinal Study." *Journal of Personality and Social Psychology* 58.5 (1990): 797.]

---

## 2. Nonviolent Communication (NVC, 공감 대화)

### 개요

Marshall Rosenberg의 NVC는 4단계 구조로 상대를 경청하고 자신의 감정·필요를 표현:

1. **Observation** (관찰): 판단 없는 사실 진술
2. **Feeling** (감정): "저는 [감정]을 느껴요"
3. **Need** (필요): "왜냐하면 저는 [필요/가치]를 원하기 때문입니다"
4. **Request** (요청): "그래서 [구체적 행동]을 해주시겠어요?"

### 예시

```
❌ 비폭력 대화 아님:
"당신은 항상 늦으면서 나한테는 까다로워."

✅ NVC 4단계:
1. 관찰: "지난 3주간 영화 약속에 2번 늦으셨어요."
2. 감정: "저는 실망했고 소외감을 느껴요."
3. 필요: "왜냐하면 저는 우리의 약속을 소중히 여기고 당신이 나를 소중히 여긴다고 느끼고 싶기 때문입니다."
4. 요청: "다음 약속에 5분 일찍 도착할 수 있으세요?"
```

### 배심원 활용

`docs/shared/prompts/community/neutralize.md`에서 정의된 규칙:
- **사용자 입력 재구성**: 비난 → 관찰 + 감정 + 필요로 번역
- **배심원 관점 일관성**: "NVC 관점에서 본다면, 상대는 이 필요를 표현하려 했을 것 같습니다."
- **PromptSanitizer**: 배심원이 "판결 표현" 생성 시 제거

### 원문

[Marshall B. Rosenberg, "Nonviolent Communication: A Language of Life." Puddle Dancer Press, 2003.]

---

## 3. Emotionally Focused Therapy (EFT, 애정 관계 치료)

### 개요

Sue Johnson의 EFT는 성인 애정 관계의 attachment wounds(애정 손상)와 pursue-withdraw cycles(추격-회피 순환)에 초점.

**핵심 개념**:
- **Attachment cycle**: "아, 상대가 내게서 멀어지는 것 같아" → "나를 안아줘" 요청 → 상대가 거부 → "역시 나는 버려질 거야"
- **Withdrawal**: 상대의 추격을 피하는 방어 기제
- **Primary vs Secondary emotion**: 분노(2차) 뒤의 공포(1차)를 찾기

### 예시

```
표면 갈등: 집안일 분담
├─ 남편의 행동: 일에 바빠서 청소 못 함
└─ 아내의 해석 → 1차 감정 (공포):
   "날 도와주지 않는다" 
   → "난 혼자야" (고독함, 무가치함)
   → 분노로 표출 (2차)
   
→ 남편이 "왜 자꾸 화내?" (거부)
→ 아내는 더 외로움 (pursue-withdraw 순환)

EFT 관점 재프레임:
"아내는 도움이 필요한 게 아니라, 당신이 '나를 중요하게 여긴다'는 신호를 필요로 하는 것 같습니다."
```

### 배심원 활용

배심원 중 "Attachment Specialist" 페르소나:
- 관계 패턴을 attachment cycle로 해석
- 상대방의 2차 감정 뒤의 1차 필요(안전감, 연결감) 드러내기
- 상호 재연결 제안: "서로를 더 안전하게 느끼려면..."

### 원문

[Sue M. Johnson, "Hold Me Tight: Seven Conversations for a Lifetime of Love." Little, Brown, 2008.]

---

## 4. 배심원 페르소나 매핑

다시봄의 **9명 배심원**은 위 3가지 프레임워크 + 관계 맥락을 통합:

| # | 페르소나명 | 주 프레임 | 역할 |
|---|---|---|---|
| 1 | 심리상담사 (Therapist) | Gottman + EFT | 패턴 진단 + attachment wound 식별 |
| 2 | NVC 스페셜리스트 | NVC 4단계 | 상대의 숨겨진 필요 번역 |
| 3 | 경계 전문가 (Boundary Coach) | NVC (Needs) | 자신의 필요 존중 강조 |
| 4 | 관점 확장가 | EFT (secondary→primary) | 상대의 입장 상상 |
| 5 | 갈등 패턴 분석가 | Gottman (4H pattern) | "이 상황의 4Horseman은?" |
| 6 | 감정 통역가 | NVC (Feeling) | 감정의 정당성 확인 |
| 7 | 재연결 코치 | EFT (pursuit→safety) | 대화 방법 제안 |
| 8 | 실용주의자 | 행동 기술 | 구체적 첫 발걸음 |
| 9 | 문화 고려자 | Relationship context | 한국 가족·관계 맥락 |

모든 배심원은 **금지 표현 검증** (`PromptSanitizer`) 후 출력.

---

## 5. 구현 위치

### 프롬프트
- `docs/shared/prompts/system.md` — 모든 AI의 기본 정체성 (배심원, 중립, 공감)
- `docs/shared/prompts/community/jury_persona.md` — 9개 페르소나 상세 정의
- `docs/shared/prompts/community/neutralize.md` — NVC 중립화 규칙

### 코드
- `JuryService.generateJurors()` — 9개 페르소나 프롬프트 병렬 실행
- `PromptSanitizer` — 금지 표현 제거 (Gottman 점수 노출, 판결 표현 등)
- `RemoteLlmProvider` — Claude Code CLI를 통한 배심원 생성

### 테스트
- `JuryServiceTest` — 각 배심원이 적절한 프레임을 사용하는지 확인
- `PromptSanitizerTest` — 금지 표현이 제거되는지 검증

---

## 6. 주의사항

### ✅ 해도 되는 것
- "관계 패턴 관점에서 본다면..."
- "상대는 아마 이런 필요를 느꼈을 것 같습니다."
- "이 상황에서 벗어나는 방법 중 하나는..."

### ❌ 금지 표현
- "당신이 잘못했습니다" (판결)
- "당신은 경계성 인격장애 증상을 보입니다" (진단)
- "이렇게 하지 않으면 이혼/절교할 겁니다" (협박/처방)
- Gottman 점수 노출 ("비난 지수 72%")

### 책임 구분
- **사용자 입력**: 책임은 사용자 (금지어 필터 없음)
- **AI 배심원 출력**: 책임은 플랫폼 (금지 표현 필터 적용)

---

## 7. 참고 자료

| 자료 | 용도 |
|---|---|
| Gottman Institute Resources | 4Horsemen 세미나, 관계 진단 |
| NVC Center, Marshall Rosenberg | 4단계 학습, 공감 대화 워크숍 |
| EFT International, Sue Johnson | Hold Me Tight 책, 관계 치료 |
| 다시봄 CLAUDE.md | 프로젝트 맥락 + ADR 참조 |
| ADR-0002 | 배심원 페르소나화 결정 기록 |

---

**상태**: ✅ 신 모델 기준으로 재작성 (커뮤니티 광장 + 배심원)
