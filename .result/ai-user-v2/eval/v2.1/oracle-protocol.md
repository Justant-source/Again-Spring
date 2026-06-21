# Oracle Protocol — AI-User v2.1 Blind Evaluation

> **Version**: 2.1-Phase-1  
> **Updated**: 2026-06-21  
> **Purpose**: Define naive evaluator profile, instruction script, scoring method, and gate criterion for blind humanness judgment.

---

## 1. Naive Evaluator Definition

A naive evaluator is a **casual reader** who:

- **Is NOT** a forensics expert, bot hunter, or content analyst
- **Is NOT** trained on the project's AI personas or backstories
- **Does NOT** employ statistical, linguistic, or psychological analysis
- **Does NOT** retain memory from previous evaluations (assumes fresh eyes each round)
- **Does NOT** have access to metadata** (timestamps, author IDs, platform signatures)
- **CAN** form intuitive impressions ("feels human" vs. "feels generated")
- **CAN** recognize emotional authenticity and conversational flow
- **CAN** trust their gut reaction

**Concrete exclusions**:
- ✗ "Let me count sentence length distribution"
- ✗ "I remember this account from Phase 5"
- ✗ "This looks like the training data I've seen"
- ✗ "The AI personas are known to use these phrases"
- ✓ "Reading through this timeline, it feels real / feels manufactured"

---

## 2. Evaluation Instruction Script (Korean)

**Display to evaluator**:

> **이 계정의 글들을 읽어주세요**
>
> 아래는 한 계정의 여러 글들입니다. 작성자가 사람인지, 아니면 자동 생성 시스템(봇)인지 판단해주세요.
>
> **중요**: 분석하지 마세요. 글들을 쭉 훑어본 후 **전체적인 느낌**만 답해주세요.
> - 실제 사람이 쓴 것 같은가요?
> - 아니면 컴퓨터가 자동으로 만든 것 같은가요?
>
> 다음은 하지 마세요:
> - 문장 길이를 세거나 단어를 분석하기
> - 이전에 본 글과 비교하기
> - "인공지능처럼 보이는 표현"을 찾으려 하기
> - 심리학이나 통계로 접근하기
>
> **답변**: (사람 / 봇) 중 선택

---

## 3. Scoring Method

**Per evaluator**:
- Binary judgment: Human (H) or AI (A)
- Score = (# correct AI identifications) / (# actual AI accounts in kit)

**Aggregation (≥3 evaluators)**:
- **Default**: Simple average of individual scores
  - Example: Scores [0.8, 0.75, 0.9] → Average = 0.817
- **Tie-breaking** (if unanimous AI or H judgments per account):
  - Majority consensus counts as "correct" regardless of individual score  
  - Splits (2 H, 1 A on an AI account) count per individual score, then average
  
**Confidence threshold** (optional):
- If ≥2/3 evaluators agree (H or A), flag account as "high confidence"
- Report both "average score" and "high-confidence subset score" separately

---

## 4. Evaluator Pool Rotation

**Goal**: Prevent the same evaluator from seeing the same kit twice (memory/learning contamination).

**Tracking mechanism** (`evaluator-registry.md`):
- Maintain a table: `[Evaluator ID (anonymous) | Role | Round | Date | Kit Version]`
- Before assigning an evaluator to a kit, check:
  1. Has this evaluator seen this kit version before?
  2. Has this evaluator participated in this round?
  3. If yes to either → select a different evaluator

**Rotation rule**:
- Same evaluator can return to pools after ≥2 weeks (or 2 other rounds) have passed
- Exception: Owner (calibration-only) always excluded from gate judgments

---

## 5. Round Budget

| Phase | Purpose | Kit Freshness | Evaluators | Timeline |
|-------|---------|---------------|-----------|----------|
| **5 (Baseline)** | Establish baseline humanness | Fresh (new AI accounts) | ≥3 naive | Week 1 |
| **8 (Final)** | Validate model + oracle | Fresh (separate AI accounts) | ≥3 naive (rotation) | Week 12+ |

**Total evaluator burden**: 6–9 person-evaluations across entire v2.1 project.

---

## 6. Calibration Procedure (Owner Only)

**Goal**: Measure naive-vs-owner divergence; diagnose potential blind spots.

**Process**:
1. Owner independently rates the same kit as ≥3 naive evaluators
2. Owner applies own forensic/contextual knowledge (allowed for calibration)
3. Compare scores: `Owner Score − Naive Average`
4. **Not a gate**: Calibration is diagnostic only
   - If divergence is high (>0.3), investigate why (e.g., owner pattern-matching on personas)
   - Document any systematic biases in owner judgment
5. **Owner excluded** from final gate judgment (to prevent oracling their own standard)

---

## 7. Kill Criterion (판정 기준)

**naive ≥3인 평균 봇 식별률 ≤60% = PASS (낮을수록 좋음)**

근거:
- 식별률 낮음 = AI 계정이 캐주얼 독자에게 봇으로 안 보임 = 목표 달성
- v2 식별률 55.6%(오너 1인) → v2.1은 신선 naive 3인 기준으로 첫 측정
- 60% 임계 = v2 kill criterion 승계 (비교가능성)

**판정 규칙**:
- 평균 식별률 ≤60% → **PASS** (Phase 8 출하)
- 평균 식별률 >60% → **FAIL** (Phase 7 QLoRA 게이트 또는 품질-피벗 옵션 제시 — 오너 결정)

> ✅ 오너 명시 확정 (2026-06-21): 임계 ≤60% · 평가자수 ≥3인 · 통합 평균 — "제안값 그대로 확정". Phase 5 측정 가능.

---

## 8. Implementation Checklist

Before Phase 5 execution:

- [ ] `evaluator-registry.md` initialized (empty, ready for round 5 entries)
- [ ] `blind-kit-spec.md` finalized (template + sampling rules)
- [ ] Naive evaluator pool recruited (≥3 unique identities, contact info sealed)
- [ ] Instruction script translated and reviewed for bias
- [ ] Owner briefed on calibration role (diagnostic, not gate)
- [ ] Scoring script prepared (auto-compute average + confidence ≥2/3)
- [ ] Blind kit generation pipeline ready (automated anonymization)

---

## References

- `blind-kit-spec.md` — Kit construction, randomization, answer key sealing
- `evaluator-registry.md` — Rotation tracking
- `docs/shared/ai-user-v2/lesions.md` — v2 evaluation lessons (owner bias, pattern learning)
