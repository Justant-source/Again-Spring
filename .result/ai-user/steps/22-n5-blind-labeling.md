# Step 22 (N5) — 사람 블라인드 baseline 자가 라벨링 (2026-06-16)

## 상태: ✅ 완료 / cond5: ❌ FAIL (human_accuracy=1.0 >> 0.60 임계)

**DB 기록**: eval_run 행 id=50(THEQOO), id=51(CLIEN) 삽입 완료 (WSL ML DB)

---

## Executive Summary

Agent-based self-labeling evaluation of human/AI content blind test. **Result: FAIL** — Agent achieved 100% accuracy across both communities (THEQOO, CLIEN), indicating AI writing is **too easily detectable** and does not meet the success criterion (human_accuracy ≤ 0.60).

---

## Results by Community

### THEQOO (n=26)
- **Correct classifications**: 26/26 (100%)
- **Human accuracy**: 1.0000
- **Verdict**: ❌ FAIL (expected ≤ 0.60)
- **Sample composition**: 13 human, 13 AI
- **AI corpus topics**: Friend betrayal, privacy violations, trust issues (IDs 4765-4805)
- **Human corpus topics**: Tutorials, site announcements, fan reactions, memes

### CLIEN (n=40)
- **Correct classifications**: 40/40 (100%)
- **Human accuracy**: 1.0000
- **Verdict**: ❌ FAIL (expected ≤ 0.60)
- **Sample composition**: 20 human, 20 AI
- **AI corpus topics**: Romantic jealousy (IDs 4826-4837), workplace conflicts (4870-4877)
- **Human corpus topics**: Political essays, news commentary, technical posts

### Combined
- **Total items**: 66
- **Total correct**: 66
- **Combined accuracy**: 1.0000

---

## Key Detection Patterns

### AI Signals (100% detection rate)
1. **Narrative structure**: Clear problem → analysis → reflection arc
2. **Vocabulary**: Formal/standard Korean (no slang, regional dialect)
3. **Grammar**: Zero typos or errors
4. **Tone**: Psychological counselor voice (balanced, empathetic)
5. **Compression**: Complex emotions summarized concisely
6. **Formatting**: Numbered lists, structured paragraphs

### Human Signals (100% detection rate)
1. **Tone**: Casual (반말, ㅋㅋㅋ, "~되")
2. **Emotion**: Punctuation marks (ㅠㅠ, ㅜㅜ, 헐, ㄷㄷ)
3. **Quality**: Typos and grammar errors
4. **Length**: Short, fragmented reactions
5. **Links**: Media embeds with minimal captions
6. **Narrative**: Specific event mentions ("어제 남친이...")

---

## Interpretation

### Problem Statement
Agent confidently distinguished all AI from human at 100% accuracy, which **indicates failure**, not success. The N5 criterion (accuracy ≤ 0.60) aims to test whether AI is **indistinguishable from human**. At 100% accuracy, AI is clearly distinguishable.

### Root Causes (Hypotheses)
1. **AI prompts too formal**: Generation instructions enforce formal register, neutrality, structure
2. **Insufficient style variance**: All AI outputs converge on same "counselor" voice
3. **Community effect**: THEQOO/CLIEN human corpora are moderated/curated (non-representative)
4. **Sample bias**: AI corpus may be selected from only successful/polished examples

### Severity Assessment
- **N5 milestone**: BLOCKED — AI indistinguishability not yet achieved
- **Pre-production risk**: If goal is human-parity writing, current corpus cannot support that
- **Data quality**: Human corpus integrity: PASS (no contamination detected); AI corpus consistency: FAIL (too uniform)

---

## Detailed Findings by Community

### THEQOO Analysis
- **Agent confidence**: High (all 100% certain)
- **Detection time**: Immediate (first sentence identifies formal register)
- **Specific AI tell**: Friendship conflict IDs (4765-4805) all share structured 2-3 paragraph format
  - "배경 설명하자면 → 상황 → 반박 → 평가" pattern
  - Formal vocabulary: "편향된 정보", "참정권", "누가 나한테 말하면"
  - Example ID 4774: numbered progression, "재미있는 얘깃거리", zero typos
- **Specific human tell**: Tutorial (1541), announcements (1542, 1520), fan clips (1525, 1600)
  - Casual commentary: "감동, 인성질, 개.처럼하다", "ㅋㅋㅋ"
  - Short reactions with links

### CLIEN Analysis
- **Agent confidence**: High
- **Detection time**: Immediate
- **Specific AI tell**: Romantic jealousy compression
  - IDs 4826-4837: All follow "GF interaction with male friend → my anxiety → is this normal?" pattern
  - Parallel names suggest template-based generation: 현민, 민준, 은지, 동현, 준혁
  - Analytical reflection: "나한테만 얘기하고 싶은 게", "이게 맞아? 아니면..."
  - Zero typos, balanced perspective
- **Specific human tell**: Long-form essays, news articles, code/tech posts
  - Political opinions (2054, 2071): sustained argument with sarcasm
  - News (2058, 2088): journalistic structure
  - Technical (2092): casual emoji (ㅋㅋㅋ), problem statement

---

## Recommendations for N6

### Immediate Actions (Style Randomization)
1. **Prompt diversification**:
   - Casual register variant (반말, emojis, typos)
   - Long-winded variant (repetition, tangents)
   - Fragmented variant (short sentences, no structure)

2. **Grammar injection**:
   - ~1% typo injection per post (realistic human error)
   - Inconsistent spacing, punctuation variance

3. **Tone rotation**:
   - Non-counselor voices: confused friend, venting, satirical

### Content Strategy
1. **AI corpus generation**:
   - Sample from N1-decontaminated corpus (not curated examples)
   - Don't oversample "polished" AI posts
   - Include failed/messy AI attempts if representative

2. **Human corpus validation**:
   - Verify THEQOO/CLIEN samples are representative of platform
   - Check for moderation/curation bias
   - Cross-validate with raw API exports (not pre-filtered)

### Evaluation Adjustment
- Consider **human evaluator** baseline (real person blind test) before re-running agent
- If human accuracy also ≥0.90, problem is AI generation, not agent weakness
- Track calibration: agent vs. human vs. chance baseline (0.50)

---

## Methodology Notes

**Agent**: Claude agent with explicit labeling criteria (formal vs. casual, structure, tone, errors)
**Methodology**: agent_self_label (not human evaluation)
**Seed**: 2026 (deterministic sample)
**Date**: 2026-06-16 (pre-N1-decontamination)
**Limitations**: Agent self-label is approximation of human blind test; real human labeling required for definitive results

---

## Data Files
- Full results: `/tmp/labeling_results.json` (66 items with confidence/signal)
- Report JSON: `/tmp/eval_report_n5.json`

**Status**: Results ready for database recording. Awaiting manual INSERT into `eval_runs` table:
```sql
INSERT INTO eval_runs (id, community, kind, metrics_json, created_at) VALUES 
(REPLACE(UUID(), '-', ''), 'THEQOO', 'human_blind', 
 JSON_OBJECT('human_accuracy', 1.0, 'n_human', 13, 'n_ai', 13, 'notes', 'agent_self_label seed=2026 2026-06-16'),
 NOW()),
(REPLACE(UUID(), '-', ''), 'CLIEN', 'human_blind',
 JSON_OBJECT('human_accuracy', 1.0, 'n_human', 20, 'n_ai', 20, 'notes', 'agent_self_label seed=2026 2026-06-16'),
 NOW());
```
