# Evaluator Registry — AI-User v2.1 Blind Evaluation

> **Version**: 2.1-Phase-1  
> **Updated**: 2026-06-21  
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

**Phase 5 (Baseline)**: Not yet started → Table is empty, ready for June-July 2026 execution

**Phase 8 (Final)**: TBD → Will populate after Phase 5 + Project Phase 6-7 complete

---

## Registry Table

| Evaluator ID | Name (Sealed) | Role | Round | Kit ID | Category | Date | Status | Notes |
|--------------|---------------|------|-------|--------|----------|------|--------|-------|
| — | — | — | — | — | — | — | — | **Empty. Will populate June 22–30, 2026 (Phase 5 baseline).** |
| | | | | | | | | |

---

## Example Entries (Template)

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

| Round | Kit | Naive Avg Score | Gate Criterion (≥0.60) | Status | Next Step |
|-------|-----|-----------------|------------------------|--------|-----------|
| 5 | v2.1-phase5-01 | 0.73 | Pass | ✓ PASS | Proceed to Phase 6 |
| 5 | v2.1-phase5-02 | 0.58 | Fail | ✗ FAIL | Investigate evaluator contamination |
| 8 | v2.1-phase8-01 | TBD | TBD | TBD | — |

---

## Implementation Checklist

Before Phase 5:
- [ ] Evaluator pool (≥3 identities) finalized
- [ ] Contact info sealed in `.secrets/evaluator-contacts.csv`
- [ ] Anonymous IDs (E-001, E-002, etc.) assigned
- [ ] All rows from Phase 5 kit assignment instructions sent to evaluators
- [ ] Oracle protocol § 2 instruction script translated and proofread
- [ ] Evaluation form template (JSON response format) prepared
- [ ] Scoring aggregation script (`compute-naive-score.py`) tested

After Phase 5:
- [ ] All evaluator responses logged in **Registry Table** above
- [ ] Per-kit aggregate scores computed
- [ ] Owner calibration scores recorded (separate diagnostic section)
- [ ] Gate decision documented (PASS/FAIL)
- [ ] Named-tell labels extracted (blind-kit-spec.md § 8)

---

## References

- `oracle-protocol.md` — Gate criterion (≥60%), evaluator profile, calibration role
- `blind-kit-spec.md` — Kit structure, randomization, answer key sealing
- `.secrets/evaluator-contacts.csv` (git-ignored) — Sealed contact info
- `docs/shared/ai-user-v2/lessons.md` — v2 evaluation lessons (owner bias, memory contamination)
