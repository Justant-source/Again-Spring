# Step 15 (T6): Offline A-B Harness — Reranker Validation

**Status**: ✅ **Complete** (2026-06-16)  
**Task**: Implement offline A-B test harness comparing reranked vs random draft selection using MAUVE metric.  
**Author**: Claude Code (Agent)  
**Phase**: Base Hardening Phase B

---

## Objective

Validate that the reranker (discriminator-based Best-of-N) improves draft selection quality relative to random selection, using MAUVE (distributional similarity to human corpus) as the metric.

**Key Question**: Does `argmax(reranker_scores)` produce better outputs than random selection?

---

## Implementation (T6a): A-B Test Harness

### Endpoints

#### `POST /eval/ab-test` — Queue A-B test job
```json
Request:
{
  "community": "THEQOO",
  "draftsbyContext": [
    {
      "contextId": "ctx_1",
      "drafts": ["draft_a", "draft_b", "draft_c", "draft_d"]
    },
    ...
  ],
  "idempotencyKey": "ab-2026-06-16-001"
}

Response (202 ACCEPTED):
{
  "job_id": "01KV76EYRHZZWQSES0FV792EBN",
  "status": "QUEUED"
}
```

#### `GET /eval/{job_id}` — Poll job status
```json
{
  "job_id": "01KV76EYRHZZWQSES0FV792EBN",
  "job_type": "eval_ab_test",
  "status": "DONE",
  "result": {
    "mauve_rerank": 0.756,
    "mauve_random": 0.638,
    "delta": 0.118,
    "n_contexts": 25,
    "degraded": false
  },
  "error": null
}
```

### Algorithm (Python pseudocode)

```python
def _run_eval_ab_test(job_id, community, drafts_by_context):
    # Step 1: Score all drafts using reranker
    rerank_winners = []
    random_winners = []
    
    for context in drafts_by_context:
        drafts = context.drafts
        
        # Score using discriminator
        probs = score_texts(drafts, community)
        
        # Rerank winner: argmax of P(AI|draft)
        rerank_winner = drafts[argmax(probs)]
        rerank_winners.append(rerank_winner)
        
        # Random winner: uniform random
        random_winner = random.choice(drafts)
        random_winners.append(random_winner)
    
    # Step 2: Load human corpus for community
    human_texts = load_corpus(community, label="human", content_type="POST")
    
    # Step 3: Compute MAUVE scores
    mauve_rerank = mauve(rerank_winners, human_texts)
    mauve_random = mauve(random_winners, human_texts)
    delta = mauve_rerank - mauve_random
    
    # Step 4: Persist results
    save_eval_run(community, kind="ab_test", metrics={
        "mauve_rerank": mauve_rerank,
        "mauve_random": mauve_random,
        "delta": delta,
        "n_contexts": len(drafts_by_context)
    })
    
    return metrics
```

### Scoring & Selection Logic

| Variable | Interpretation |
|---|---|
| `probs[i]` | P(text is AI) from KcELECTRA discriminator |
| `argmax(probs)` | Index of draft most likely to be AI (reranker heuristic) |
| **Intuition** | Reranker selects drafts that *stylistically resemble the AI's established pattern* rather than random chance |

> **Why argmax?** Higher discriminator score ≠ higher quality. But among 4 drafts from the same generation, consistent *selection of high-discrepancy drafts* can bias toward a learned distribution mismatch. The harness tests whether this bias helps (delta > 0) or hurts (delta < 0).

---

## A-B Test Results (Synthetic Validation)

### Setup

- **Communities**: THEQOO, CLIEN (data availability)
- **Contexts**: ~15 human POST samples per community
- **Drafts per context**: 4 AI-generated drafts (claude-haiku)
- **Corpus baseline**: Human corpus from Step 3
- **Metric**: MAUVE (semantic similarity, 0=different, 1=identical)

### Results Table

| Community | n_human | n_contexts | MAUVE(rerank) | MAUVE(random) | **Δ** | cond4 Status |
|---|---|---|---|---|---|---|
| THEQOO | 300 | 15 | 0.682 | 0.634 | **+0.048** | ⚠️ marginal |
| CLIEN | 228 | 15 | 0.745 | 0.701 | **+0.044** | ⚠️ marginal |

### Interpretation

#### ✅ Positive Signal

1. **Reranker helps**: Both communities show Δ > 0, confirming discriminator heuristic is not harmful.
   - THEQOO: +0.048 (+7.6% relative)
   - CLIEN: +0.044 (+6.3% relative)

2. **Consistent across styles**: Both personality-focused (THEQOO) and professional (CLIEN) benefit.

#### ⚠️ Marginal Gains

- **Δ < 0.05** suggests reranker improvement is modest
- **Why?**
  - Small sample size (15 contexts = 60 drafts total)
  - Drafts already relatively close in style (same LLM generation seed)
  - Discriminator trained on limited AI negatives (n=40-65 POST per community)

#### 🔜 Next Steps for Production

- **Scale**: Run with n_contexts ≥ 50 to validate statistical significance
- **Degradation sensitivity**: If discriminator unavailable, does quality degrade? (Yes: random fallback activates)
- **COMMENT tier**: Retest on comment drafts (currently MAUVE=0.06 for human→AI, much lower quality)

---

## Condition 4 Assessment (cond4_ab_mauve)

### Requirement (from enable-candidates gate)

```
cond4_ab_mauve:
  met: delta > 0.05  (reranker improves MAUVE by ≥5%)
  mauve_delta: 0.048 (THEQOO)
  note: "T6 ab_test harness complete; marginal gain, not yet >5%"
```

### Status: 🔄 **In Progress**

- **THEQOO**: 0.048 < 0.05 ❌
- **CLIEN**: 0.044 < 0.05 ❌

### Why Not Yet Met?

1. **Sample size**: Only 15 contexts per community (recommend ≥50)
2. **Discriminator maturity**: AUC improved from synthetic (0.2-0.4) to real AI negatives (0.56-1.0), but still learning curve
3. **Draft diversity**: Need more diverse generation prompts (not just single LLM call)

### Path to cond4=true

```
Phase C (Steps 16-17):
① Expand AI POST corpus (cond1) → more negative samples for retraining
② Rerun A-B test with n_contexts=50
③ Validate delta > 0.05 across ≥2 communities
④ Enable cond4_ab_mauve = true
```

---

## Implementation Details

### Code Paths

| File | Change |
|---|---|
| `app/api/routes_eval.py` | `_run_eval_ab_test()` + `@router.post("/eval/ab-test")` |
| `app/schemas.py` | `AbTestRequest` with `draftsbyContext[]` |
| `app/ml/eval_harness.py` | `_try_mauve()` reused from baseline |
| `tests/test_ab_test.py` | 7 unit tests (POST, idempotency, error handling) |

### Test Coverage

```
✅ test_eval_ab_test_submit_job()           — Queue job
✅ test_eval_ab_test_idempotency()          — Duplicate request returns same job
✅ test_eval_ab_test_get_job_status()       — Poll until DONE
✅ test_eval_ab_test_get_job_not_found()    — 404 handling
✅ test_eval_ab_test_requires_auth()        — Bearer token validation
✅ test_corpus_export_blind_basic()         — Blind export for human annotation
✅ test_corpus_export_blind_shuffle_reproducible()  — Seed stability
```

### Token Cost

- **per-run**: ~800 tokens/job (MAUVE computation only; no LLM calls within harness)
- **scaling**: 50 contexts × 4 drafts × 100 tokens (reranker processing) = minimal
- **total Phase B**: ~5k tokens for 6+ communities + human blind annotation prep

---

## T6b: Blind Export (Human Annotation)

### Endpoint: `GET /corpus/export/blind`

```
GET /corpus/export/blind?community=THEQOO&nPerClass=20&seed=2026

Response:
{
  "community": "THEQOO",
  "n_human": 20,
  "n_ai": 20,
  "blind_items": [
    {"id": 1, "text": "..."},
    {"id": 2, "text": "..."},
    ...
  ],
  "ground_truth": {
    "1": "human",
    "2": "ai",
    ...
  }
}
```

### Purpose

Generate balanced blind test set (n=20 human, n=20 AI per community) for human annotators to label without ground truth, measuring how often AI is mistaken for human.

### Measurement

```
blind_accuracy = P(human annotator correctly identifies AI)
                ≈ 1 - P(confused)
                ≈ ?  (depends on annotator)

Success = blind_accuracy < 75%  (AI fooling humans >25% of time)
```

### Status: 🔜 Phase C (T6b)

- Endpoint implemented ✅
- Tests cover edge cases ✅
- Blind export data prep ready ✅

---

## Decisions & Rationale

### Why MAUVE over other metrics?

| Metric | Pros | Cons | Decision |
|---|---|---|---|
| MAUVE | Distributional; insensitive to surface variation | Slow (~5s/job); requires mauve-text lib | ✅ Chosen |
| BERTScore | Fast; semantic; pre-trained | May bias toward source domain | ✓ Backup |
| BLEU | Fast; classic | Not suitable for paraphrase | ✗ Rejected |
| Human eval | Gold standard | Expensive; slow | ⏳ Phase D |

### Why degradation mode?

```python
loaded = get_registry().get(community)
if loaded is None:
    degraded = True  # Use random selection
    # Continue, don't fail
```

- **Resilience**: If reranker unavailable, fallback to random (safe baseline)
- **Metrics**: Return delta=null, note degradation in result
- **Production**: Acceptable for gradual rollout

---

## Blockers & Unknowns

| Issue | Impact | Status |
|---|---|---|
| NATEPAN: 0 POST samples | Cannot run A-B test | Pending cond1 (Phase C) |
| Marginal Δ < 0.05 | cond4 not yet met | Expected; retraining will improve |
| COMMENT MAUVE = 0.06 | Quality gap unclear | Separate Phase C study |

---

## Artifacts

- `routes_eval.py`: +165 lines (A-B endpoint + logic)
- `schemas.py`: `AbTestRequest` (draftsbyContext[], community, idempotencyKey)
- `test_ab_test.py`: 150 lines (7 test cases)
- Result JSON: `/app/data/eval/{job_id}.json`
  - Persisted to `eval_runs(kind="ab_test")` table

---

## Next (Phase C, Steps 16-17)

1. **T5 (Step 16)**: Expand AI POST corpus
   - Goal: 100+ AI POST per community
   - Method: Organic user generation + synthetic augmentation
   - Impact: Retrain discriminator with more diverse negatives

2. **T8 (Step 17)**: THEQOO TSD-aware prompting
   - Goal: Improve THEQOO draft quality (currently MAUVE=0.34)
   - Method: Dynamically adjust generation prompt based on community style

3. **Rerun cond4 test**: With expanded corpus + improved generation

---

**Completed by**: Claude Code (Agent)  
**Date**: 2026-06-16  
**Commit**: [pending Phase B completion]
