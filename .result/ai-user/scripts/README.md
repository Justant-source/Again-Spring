# Generation & Reranking Scripts

## gen_reranked_tell_scan.py

Generate 20 THEQOO posts via Best-of-4 drafts + ML reranking, then build blind survey for cond5 Phase 2.

### Usage

```bash
cd /home/justant/Data/Again-Spring/.result/ai-user

# Normal mode (requires running services)
python3 scripts/gen_reranked_tell_scan.py

# Dry-run mode (no LLM/ML calls, placeholder texts)
python3 scripts/gen_reranked_tell_scan.py --dry-run
```

### What it does

1. **Generates 20 × 4 drafts** in parallel (workers=8)
   - LLM endpoint: `http://againspring-llm-ai-user:8092/generate/post`
   - Theme: THEQOO_THEMES (exactly 20 hardcoded)
   - Pipeline: Best-of-4 for each theme

2. **Rerankss each theme's drafts** via ML service
   - Rerank endpoint: `http://100.115.252.61:8201/rerank`
   - Selects best draft per theme
   - Fallback: first draft if rerank fails

3. **Fetches 20 human THEQOO posts** from ML corpus
   - Corpus endpoint: `http://100.115.252.61:8201/corpus/export/blind`
   - Creates blind pairs (AI vs human)

4. **Builds survey JSON + markdown**
   - Output: `.result/ai-user/blind/r16-ml-reranked-theqoo-survey.{json,md}`
   - Format: matches r15 blind survey spec
   - Alternates A/B positions for each pair

### Output files

After successful run:
- `blind/r16-ml-reranked-theqoo-survey.json` — survey data + metadata
- `blind/r16-ml-reranked-theqoo-survey.md` — markdown for ensemble_blind_judge.py
- `blind/r16-ml-reranked-theqoo-corpus.json` — pair metadata

### Next steps (post-script)

```bash
# Run ensemble blind judge (3 seeds)
python3 ensemble_blind_judge.py \
  --survey blind/r16-ml-reranked-theqoo-survey.md \
  --answers blind/r16-ml-reranked-theqoo-survey.json \
  --output blind/r16-ml-reranked-theqoo-judge.json \
  --seed 42

# Run cond5 auto-gate on judge output
python3 cond5_auto_gate.py \
  --judge-output blind/r16-ml-reranked-theqoo-judge.json
```

### Configuration

Edit constants at top of script:
- `GENERATION_URL` — LLM generation endpoint
- `ML_RERANK_URL` — ML rerank service
- `ML_CORPUS_URL` — ML corpus export
- `N_THEMES`, `N_DRAFTS`, `WORKERS` — generation params

### Requirements

- Python 3.8+
- `requests` library
- Access to:
  - `againspring-llm-ai-user:8092` (LLM docker container)
  - `100.115.252.61:8201` (WSL ML service)
- Minimum 15 successful pairs to pass gate

### Error handling

- **Generation fails for a theme**: logs warning, skips theme
- **Rerank fails**: uses first draft as fallback
- **Human post fetch fails**: uses placeholder posts (dry-run)
- **Too few pairs** (< 15): exits with error

### Logging

All events logged to stdout with ISO timestamps:
```
2026-06-20T15:32:01,234 [INFO] Step 1: Generating drafts for all themes...
2026-06-20T15:32:15,456 [WARNING] Theme 5: all drafts failed
```
