# Evaluator Registry — AI-User v2.1 Blind Evaluation

> **Version**: 2.1-Phase-1  
> **Updated**: 2026-06-23  
> **Purpose**: Track evaluator assignments to prevent memory contamination (same evaluator seeing same kit twice).

---

## Overview

This registry maintains a **single source of truth** for which evaluators have participated in which kits and rounds. Before assigning a new evaluator to a kit, check this table to ensure no repeats.

**Key principle**: 
- Naive evaluators must have "fresh eyes" each time
- If an evaluator has already rated a kit or version, **do not re-assign them**
- Owner is calibration-only; their entries are marked separately and excluded from gate decisions

---

## Current Status

**Phase 5 (Baseline)**: ✓ COMPLETED (2026-06-21)
- Kit v2.1-phase5-01 evaluation by 3 naive evaluators (임슬기·박진수·김철수)
- **Result**: AI 식별률 80% (kill criterion ≤60% → FAIL)
- **보충(E-S1 부정선거, 2026-06-22)**: 60% — Phase 8 키트 오전달로 Phase 5 평가됨(참고치, 게이트 미반영). **평가자 편차 60~80% 확인** → ≥3인 평균 필요성 강화. 상세: `phase5/v2.1-phase5-01-result-supplement.md`
- **Next**: Phase 6 결정론 다양화 (T1~T4 제거) → Phase 7 재평가 → Phase 8 최종

**Phase 8 (Final)**: ✅ **SHIPPED** (2026-06-22) — 출하 시점 naive 4인 평균 **20%**, 추가 평가자 E-008·E-009·E-010 포함 **7인 평균 37.1% ≤ 60% → PASS** 유지. 결과: `phase8/v2.1-phase8-01-results.md` · 분석: `phase8/v2.1-phase8-01-analysis.md`
- 🚨 **키트 오전달 주의**: E-S1에게 Phase 5 HTML이 전달됨 → Phase 8 평가자에게는 반드시 `eval/v2.1/phase8/v2.1-phase8-01-evaluator.html`(Phase 8)을 전달할 것. Phase 5 파일과 혼동 금지.
- 배치: A-A-H-A-H-H-A-H-H-A (AI 문제1·2·4·7·10 / Human 문제3·5·6·8·9)
- AI 계정 5종: WORK(ai-user-043/Vibe2026)·FRIEND(ai-user-057/I1l1IiliI)·MARRIED(ai-user-035/RiderX9)·COUPLE(ai-user-060/통장이텅장)·FAMILY(ai-user-032/햇살받는햄스터)
- Human 계정: FRIEND(천주교의민단)·MARRIED(좋은글)·COUPLE(ㅇㅇㅇ)·FAMILY(냉동딸기)·WORK(쓰니)
- Phase 8 평가자는 Phase 5와 **다른 인원** 구성 (E-001~E-003 재사용 금지, 2026-07-05 이후 가능)
- 모집 가이드: `eval/v2.1/phase8/evaluator-guide.md`

---

## Registry Table

| Evaluator ID | Name (Sealed) | Role | Round | Kit ID | Category | Date | Status | Notes |
|--------------|---------------|------|-------|--------|----------|------|--------|-------|
| E-001 | 임슬기 | Naive | 5 | v2.1-phase5-01 | BASELINE | 2026-06-21 | ✓ Completed | AI 식별률 80% (4/5), 신뢰도 100% (과도한 신뢰) |
| E-002 | 박진수 | Naive | 5 | v2.1-phase5-01 | BASELINE | 2026-06-21 | ✓ Completed | AI 식별률 80% (4/5), 오판 3건 (Human→Bot) |
| E-003 | 김철수 | Naive | 5 | v2.1-phase5-01 | BASELINE | 2026-06-21 | ✓ Completed | AI 식별률 80% (4/5), 신중도 적절 (오판 0건) |
| E-S1 | 부정선거 | Naive | 5 (보충) | v2.1-phase5-01 | BASELINE | 2026-06-22 | ✓ Completed (참고) | AI 식별률 **60%** (3/5: 문제2·3·8 식별, 5·10 놓침), 오탐 2건(문제1·9 Human→Bot). ⚠️ **Phase 8 키트 오전달로 Phase 5 평가** — 게이트 미반영, 참고치 |
| Owner | @justant | Calibration | 5 | v2.1-phase5-01 | BASELINE | 2026-06-21 | ✓ Completed | **EXCLUDED from gate** |
| E-004 | 최일찬 | Naive | 8 | v2.1-phase8-01 | COUPLE/MARRIED/FRIEND/FAMILY/WORK | 2026-06-22 | ✓ Completed | AI 식별률 **40%** (2/5: Q2·Q10), Human 오탐 1건(Q3) |
| E-005 | 김태준/태추 | Naive | 8 | v2.1-phase8-01 | COUPLE/MARRIED/FRIEND/FAMILY/WORK | 2026-06-22 | ✓ Completed | AI 식별률 **0%** (0/5), Human 오탐 4건. 전 문항 확신 50%(순수 추측) |
| E-006 | 김윤태 | Naive | 8 | v2.1-phase8-01 | COUPLE/MARRIED/FRIEND/FAMILY/WORK | 2026-06-22 | ✓ Completed | AI 식별률 **0%** (0/5), Human 오탐 3건. 확신 ~100%인데 0%(과신 오답, E-001 패턴) |
| E-007 | 윤도현 | Naive | 8 | v2.1-phase8-01 | COUPLE/MARRIED/FRIEND/FAMILY/WORK | 2026-06-22 | ✓ Completed | AI 식별률 **40%** (2/5: Q1·Q2), Human 오탐 3건. **신규 추가**(슬롯 외) |
| E-008 | 쎄오일시 | Naive | 8 | v2.1-phase8-01 | COUPLE/MARRIED/FRIEND/FAMILY/WORK | 2026-06-22 | ✓ Completed | AI 식별률 **60%** (3/5: Q1·Q2·Q4), Human 오탐 4건(Q3·Q5·Q6·Q8). 투입 시점 5인 평균 28%, 최신 7인 37.1% — PASS 유지 |
| E-009 | 이한별 | Naive | 8 | v2.1-phase8-01 | COUPLE/MARRIED/FRIEND/FAMILY/WORK | 2026-06-22 | ✓ Completed | AI 식별률 **80%** (4/5: Q1·Q2·Q4·Q7), Human 오탐 1건(Q3). 투입 시점 6인 평균 36.7%, 최신 7인 37.1% — PASS 유지 |
| E-010 | 곽평안 | Naive | 8 | v2.1-phase8-01 | COUPLE/MARRIED/FRIEND/FAMILY/WORK | 2026-06-22 | ✓ Completed | AI 식별률 **40%** (2/5: Q7·Q10), Human 오탐 2건(Q8·Q9). 7인 평균 37.1% — PASS 유지 |
| Owner | @justant | Calibration | 8 | v2.1-phase8-01 | COUPLE/MARRIED/FRIEND/FAMILY/WORK | TBD | 선택(미실시 가능) | **EXCLUDED from gate** — gate 판정 비필수 |

---

## Example Entries (Template)

> **아카이브 메모**: 아래 표는 레지스트리 형식 설명용 예시다. 실제 Phase 5/8 기록은 위 Registry Table과 Gate Decision Log를 권위본으로 읽는다.

Below are **placeholder examples** showing how rows will be recorded once Phase 5 begins:

| Evaluator ID | Name (Sealed) | Role | Round | Kit ID | Category | Date | Status | Notes |
|--------------|---------------|------|-------|--------|----------|------|--------|-------|
| E-001 | [SEALED] | Naive | 5 | v2.1-phase5-01 | COUPLE | 2026-06-22 | ✓ Completed | Avg score: 0.8 |
| E-002 | [SEALED] | Naive | 5 | v2.1-phase5-01 | COUPLE | 2026-06-23 | ✓ Completed | Avg score: 0.75 |
| E-003 | [SEALED] | Naive | 5 | v2.1-phase5-01 | COUPLE | 2026-06-24 | ✓ Completed | Avg score: 0.9 |
| Owner | @justant | Calibration | 5 | v2.1-phase5-01 | COUPLE | 2026-06-24 | ✓ Completed | **EXCLUDED from gate** |
| E-004 | [SEALED] | Naive | 5 | v2.1-phase5-02 | MARRIED | 2026-06-26 | ✓ Completed | Avg score: 0.65 |
| — | — | — | 8 | — | — | — | **TBD (Phase 8, new evaluators)** | |

---

## Constraints & Rules

### Rule 1: No Repeat Evaluators Within a Kit
- If evaluator E-001 rates kit v2.1-phase5-01, they **cannot rate it again** in Phase 8
- Different kits → different evaluators (cross-phase)

### Rule 2: Category Diversity
- Same evaluator can rate different categories (e.g., E-001 does COUPLE + MARRIED in different rounds)
- But not the same kit twice

### Rule 3: Owner Calibration Only
- Owner (`@justant`) evaluates the same kits as naive evaluators, **separately and after**
- Owner's score is recorded but **NOT included in gate judgment**
- Rows with `Role = "Calibration"` are for diagnostic only (divergence measurement)

### Rule 4: Minimum 2-Week Cool-Down (Optional)
- If pool is limited and evaluator must return:
  - Require ≥2 weeks between Phase 5 and next assignment
  - Or minimum 2 other rounds of other evaluators between re-use
- Document reason in **Notes** column if exception is needed

### Rule 5: Contact Info Sealed
- Evaluator identity is anonymized (E-001, E-002, etc.)
- Actual contact info stored separately in sealed `.secrets/evaluator-contacts.csv` (git-ignored)
- Only project lead (@justant) has access

---

## Evaluator Pool Management

### Initial Pool (Phase 5, ≥3 evaluators)

| ID | Status | Notes |
|----|--------|-------|
| E-001 | To recruit | — |
| E-002 | To recruit | — |
| E-003 | To recruit | — |

**Recruitment timeline**: June 15–21, 2026
**Criteria**: 
- Native or near-native Korean speaker
- Casual reader, not trained on AI personas
- Available ≥2 hours (Phase 5 kit takes ~30 min to evaluate)

### Pool Rotation (Phase 8)

| ID | Status | Notes |
|----|--------|-------|
| E-001 | Available? | If cool-down satisfied, can re-use |
| E-002 | Available? | — |
| E-003 | Available? | — |
| E-004 | To recruit (new) | Fresh evaluator for Phase 8 |
| E-005 | To recruit (new) | — |
| E-006 | To recruit (new) | — |

**Phase 8 goal**: ≥3 naive evaluators, preferably 1–2 fresh faces to avoid prior-round contamination

---

## Scoring Aggregation (By Kit)

Once evaluators submit, aggregate using oracle-protocol.md § 3:

**Example (Hypothetical Phase 5 Result)**:

Kit: `v2.1-phase5-01` (COUPLE, 10 accounts, 5 AI + 5 human)

| Evaluator | AI Correct / AI Total | Score | Confidence (≥2/3) |
|-----------|----------------------|-------|-------------------|
| E-001 | 4/5 | 0.80 | 3/5 high-conf (1x split) |
| E-002 | 3/5 | 0.60 | 4/5 high-conf |
| E-003 | 4/5 | 0.80 | 4/5 high-conf |
| **Average** | — | **0.73** | — |
| **Gate criterion** (≥0.60) | — | **PASS ✓** | — |
| **Owner Calibration** | 5/5 | 0.95 | (diagnostic, not gate) |

---

## Divergence Analysis (Owner Calibration)

After owner rates same kit:

| Kit | Naive Avg | Owner | Divergence | Interpretation |
|-----|-----------|-------|-----------|-----------------|
| v2.1-phase5-01 | 0.73 | 0.95 | +0.22 | Owner detected 1 AI account naive missed; slight tendency to pattern-match |
| v2.1-phase5-02 | 0.65 | 0.60 | −0.05 | Tight alignment; good baseline |

**Divergence threshold** (informational only):
- If |Divergence| > 0.3 → investigate owner methodology (e.g., relying on known personas)
- If |Divergence| < 0.1 → owner and naive evaluators well-calibrated
- **Never halt gate on divergence** (diagnostic purpose only)

---

## Gate Decision Log

After all evaluators in a round complete:

| Round | Kit | Naive Avg Score | Gate Criterion (≤60%) | Status | Next Step |
|-------|-----|-----------------|------------------------|--------|-----------|
| 5 | v2.1-phase5-01 | **80%** | 80% > 60% → FAIL | ✗ FAIL | Phase 6 결정론 다양화 (T1·T3·T4) |
| 8 | v2.1-phase8-01 | **37.1%** (7인, E-008·E-009·E-010 추가) | 37.1% ≤ 60% → PASS | ✅ SHIPPED (2026-06-22) | E-004~010 채점(40/0/0/40/60/80/40), 출하 시점 20% PASS 이후 사후 누적도 PASS 유지. 상세: `phase8/v2.1-phase8-01-analysis.md` |

---

## Implementation Checklist

> **아카이브 메모**: 이 체크리스트는 pre-Phase-5 운영 절차 보존용이다. 실제 완료 상태는 상단 Current Status, Registry Table, Gate Decision Log를 따른다.

Before Phase 5:
- [ ] Evaluator pool (≥3 identities) finalized
- [ ] Contact info sealed in `.secrets/evaluator-contacts.csv`
- [ ] Anonymous IDs (E-001, E-002, etc.) assigned
- [ ] All rows from Phase 5 kit assignment instructions sent to evaluators
- [ ] Oracle protocol § 2 instruction script translated and proofread
- [ ] Evaluation form template (JSON response format) prepared
- [ ] Scoring aggregation script (`compute-naive-score.py`) tested

After Phase 5: ✅ 완료 (2026-06-21)
- [x] All evaluator responses logged in **Registry Table** above
- [x] Per-kit aggregate scores computed (80%, 3인 평균)
- [x] Gate decision documented (FAIL → Phase 6)
- [x] Named-tell labels extracted: T1·T2·T3·T4 (steps/v2.1-05-baseline-result.md)

---

## Phase 8 Evaluator Assignment Constraints

**⚠️ Phase 5 완료 기록 (2026-06-21)**

| Evaluator | Phase 5 Kit | Status | Phase 8 Eligibility |
|-----------|------------|--------|-------------------|
| E-001 (임슬기) | v2.1-phase5-01 | Completed | **불가 (2026-07-05 이후 가능)** — 2주 쿨다운 |
| E-002 (박진수) | v2.1-phase5-01 | Completed | **불가 (2026-07-05 이후 가능)** — 2주 쿨다운 |
| E-003 (김철수) | v2.1-phase5-01 | Completed | **불가 (2026-07-05 이후 가능)** — 2주 쿨다운 |

**Phase 8 모집 전제**:
- **신규 평가자 우선** (E-004, E-005, E-006 등)
- 폴링 제약 시에만 E-001~E-003 재사용 가능 (2026-07-05 이후)
- E-001~E-003과 **다른 kit** 배정 필수 (v2.1-phase8-01/02/03 등)

---

## References

- `oracle-protocol.md` — Gate criterion (≥60%), evaluator profile, calibration role
- `blind-kit-spec.md` — Kit structure, randomization, answer key sealing
- `.secrets/evaluator-contacts.csv` (git-ignored) — Sealed contact info
- `docs/shared/ai-user-v2/lessons.md` — v2 evaluation lessons (owner bias, memory contamination)
- `.result/ai-user-v2/steps/v2.1-05-baseline-result.md` — Phase 5 baseline result + Named-Tell labels v0
