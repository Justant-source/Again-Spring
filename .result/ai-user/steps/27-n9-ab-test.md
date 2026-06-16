## Round 1 A-B test results (pre-ingest model)

### NATEPAN (Job: 01KV7FX7W8KB24DZD2B0BK99WG — OLD, mauve=None)
- Was None because: only 5 themes in script (< 10 contexts minimum for _try_mauve)
- Fix applied: added 5 more NATEPAN themes to run_ab_test.py → 10 themes total

### NATEPAN (NEW — with 10 themes)
- Job submitted after fix
- mauve_rerank: 0.9669
- mauve_random: 0.9669
- delta: +0.0000
- n_contexts: 10
- degraded: False
- Model: AUC=0.9988, n_train=670 (n_human=445, n_ai=225), version=01KV7GCS9TQB76FBNCF9MNT6PK

### THEQOO (Round 1 — pre-ingest retrain)
- Job: 01KV7GPJK6BETH26QD0C1GWF48
- mauve_rerank: 0.9111
- mauve_random: 0.9111
- delta: +0.0000
- n_contexts: 10
- degraded: False
- Previous (Step 15, contaminated data): mauve_rerank=0.629, mauve_random=0.985, delta=-0.356
- MODEL AUC at test time: 0.980482 (trained on n_human=256 clean data)

### CLIEN (Round 1 — pre-ingest retrain)
- mauve_rerank: 0.9900
- mauve_random: 0.9999
- delta: -0.0099
- n_contexts: 10
- degraded: False
- MODEL AUC at test time: 0.989035

## Round 2 results (post-ingest retrain)
Mark as: ⏳ 실행 중 (결과 대기)

## What changed from Step 15

| 커뮤니티 | Step15 Δ | N9 Round1 Δ | 변화 | 원인 |
|---|---|---|---|---|
| THEQOO | -0.356 | 0.000 | ✅ 역전 해소 | N1 디오염 + T8 적용 |
| CLIEN | 0.000 | -0.0099 | ≈동등 (노이즈 범위) | 무변화 |
| NATEPAN | (미측정) | 0.000 | 첫 측정 | N8 신규 |

## Analysis

- THEQOO: N1 decontamination succeeded. Discriminator was inverted before (trained on contaminated corpus mixing link-posts with AI posts). Now delta=0 (neutral — reranker doesn't help OR hurt). cond4 still fails (requires delta>0) but no longer actively harmful.
- CLIEN: Consistent with Step 15. delta≈0. The reranker provides no signal for CLIEN — likely because CLIEN AI output is already very close to human distribution (MAUVE=0.990), so there's no room for improvement.
- NATEPAN: First measurement. delta=0. Discriminator cannot rank 4 AI drafts by human-likeness — likely because all 4 drafts score similarly (discriminator trained on AI vs human, not on ranking AI drafts by quality).

## T8 MAUVE verification

THEQOO:
- Before T8 (Step 13 baseline, orc bot): MAUVE=0.345
- After T8: MAUVE=0.9111 (rerank) / 0.9111 (random) — A-B test AI candidate MAUVE (not orc bot MAUVE)

Note: The A-B test MAUVE uses claude-haiku generated drafts (simpler, more direct) not the full orchestrator output. For orc bot MAUVE improvement (T8 effect), baseline rerun needed.

## cond4 현황

| 커뮤니티 | Δ | cond4 |
|---|---|---|
| NATEPAN | 0.000 | ❌ |
| THEQOO | 0.000 | ❌ (개선: -0.356→0) |
| CLIEN | -0.0099 | ❌ |

## Root cause of delta≈0 across all communities

Discriminator learns binary AI vs human. Given 4 AI drafts per context, the discriminator assigns similar P(human) scores to all 4. argmax selection = essentially random. delta = 0.

To get delta>0, the discriminator would need to be calibrated enough to rank AI drafts by naturalness within the AI-output distribution. This requires either:
1. Fine-grained training with human quality ratings (not available)
2. Much more human data so the discriminator learns subtle naturalness features
3. Alternative reranking approach (e.g., perplexity-based)

## 추가 조치

### corpus/stats 및 readiness 최신 수치
After corpus ingest agent (new data ingested):
- CLIEN: n_human=974 (was 294), retrained AUC=0.995522
- THEQOO: n_human=387 (was 256), retrained AUC=0.999432

Round 2 A-B tests (with new models) are running. Add results when available.
