# 심리학 모델: Gottman / NVC / EFT

**목적**: AI 생성 콘텐츠(주로 AI-user)와 안전 카피가 참조하는 심리학 프레임워크 요약.

**범위**: AI 출력만 (사용자 입력에 미적용). 과거 매핑 결정은 [`docs/shared/90-adr/0002`](../90-adr/0002-psychology-model-repurposed-for-jurors.md)에만 남긴다.

---

## 1. Gottman 4 Horsemen (갈등 패턴)

| 기마병 | 정의 | AI 출력 가이드 |
|---|---|---|
| **Criticism** | 인격 비난 | 행동 관찰로 재구성, 판결 금지 |
| **Contempt** | 경멸 | 가치 재설정·공감 프레임 |
| **Defensiveness** | 방어·역공 | 상대 필요를 묻기 |
| **Stonewalling** | 회피·침묵 | 재연결의 안전한 조건 제시 |

점수·진단 UI 노출 금지. "당신이 틀렸다" 금지.

---

## 2. NVC (비폭력대화)

관찰 → 느낌 → 욕구 → 요청. AI 출력은 관찰·욕구 언어를 우선하고 처방·명령 금지.

---

## 3. Attachment / EFT

애착 상처 가설은 **단정 진단이 아니라** 가능한 관점으로만. 임상 라벨 확정 금지 (`forbidden-words.md`).

---

## 현재 런타임에서의 위치

| 경로 | 역할 |
|---|---|
| AI-user 프롬프트·voice | 주력 — 커뮤니티 글/댓글 톤 |
| `docs/shared/prompts/community/post_tonalization.md` | 파트너 초대 답변 톤 정규화 (`TonalizationService` / `AnswerProcessingService`) |
| `PromptSanitizer` / `KeywordGuard` / `ContentSafetyGuard` | 판결·진단·오류 문자열 차단 |

---

## 관련 문서

- `docs/shared/70-policy/forbidden-words.md`
- `.claude/rules/llm-safety.md`
- ADR-0002 (역사적 결정만)

**상태**: 현재 제품 = 광장 사연 + 커뮤니티 공감 투표 + AI-user
