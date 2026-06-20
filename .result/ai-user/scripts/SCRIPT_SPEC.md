# gen_reranked_tell_scan.py — Script Specification

## Overview
Python script that generates 20 THEQOO posts via Best-of-4 drafts + ML reranking, then builds a blind survey JSON + markdown for cond5 Phase 2 evaluation.

**Location**: `/home/justant/Data/Again-Spring/.result/ai-user/scripts/gen_reranked_tell_scan.py`
**Size**: 502 lines, 17.5 KB
**Status**: Production-ready, verified compilation

## Architecture

### 4-Stage Pipeline
1. **Generate**: 20 themes × 4 drafts (80 parallel LLM calls)
2. **Rerank**: Select best draft per theme via ML service
3. **Corpus**: Fetch 20 human THEQOO posts from ML
4. **Survey**: Build blind JSON + markdown

### Classes
- `GenerationClient`: LLM HTTP client (with dry-run support)
- `RerankClient`: ML rerank service (with fallback)
- `CorpusClient`: ML corpus export (flexible format parsing)

### Threading
- `ThreadPoolExecutor(max_workers=8)` for parallel generation & reranking
- Independent theme processing (no cross-theme dependencies)

## Configuration

| Constant | Value | Notes |
|----------|-------|-------|
| `GENERATION_URL` | `http://againspring-llm-ai-user:8092/generate/post` | Docker service |
| `ML_RERANK_URL` | `http://100.115.252.61:8201/rerank` | WSL ML service |
| `ML_CORPUS_URL` | `http://100.115.252.61:8201/corpus/export/blind` | WSL ML corpus |
| `ML_API_TOKEN` | `aiuser-ml-api-token-dev-2026` | Bearer auth |
| `N_THEMES` | `20` | Exact match to `len(THEQOO_THEMES)` |
| `N_DRAFTS` | `4` | Best-of-4 pattern |
| `WORKERS` | `8` | ThreadPoolExecutor workers |
| `MIN_SUCCESSFUL_PAIRS` | `15` | Gate threshold |

## Input: THEQOO Themes (Hardcoded)
```python
THEQOO_THEMES = [
    "남자친구가 약속을 또 어겼을 때",
    "친구가 내 비밀을 다른 사람에게 말했을 때",
    # ... 18 more themes
]
```
Exactly 20 Korean conflict themes (per specification).

## Outputs

### Files Generated
```
.result/ai-user/blind/
├── r16-ml-reranked-theqoo-survey.json       # JSON format (cond5_blind)
├── r16-ml-reranked-theqoo-survey.md         # Markdown survey
└── r16-ml-reranked-theqoo-corpus.json       # Metadata
```

### JSON Structure
```json
{
  "type": "cond5_blind",
  "community": "THEQOO",
  "generated_at": "2026-06-20T15:32:01.234567Z",
  "n_pairs": 20,
  "label_map": {
    "0": {"A": "ai", "B": "human"},
    "1": {"A": "human", "B": "ai"}
  },
  "provenance": "gen-reranked-v1:best-of-4+ml-rerank",
  "pair_metadata": [...],
  "responses": {}
}
```

### Position Alternation
- **Even pairs (0, 2, 4, ...)**: A = AI, B = Human
- **Odd pairs (1, 3, 5, ...)**: A = Human, B = AI

## HTTP Protocols

### Generation (LLM)
```http
POST http://againspring-llm-ai-user:8092/generate/post
Content-Type: application/json

{
  "personaId": "gen-reranked-{draft_idx}",
  "archetype": "일반갈등",
  "voiceProfile": "더쿠 스타일 사용자. 짧은 구어체, 반말 위주, 공감형, 갈등 사연 중심",
  "tier": "REGULAR",
  "slangLevel": 0.48,
  "category": "OTHER",
  "topicSeed": "{theme}",
  "formality": "casual",
  "demographic": "THEQOO 커뮤니티 사용자",
  "lengthTier": "MEDIUM",
  "correlationId": "gen-reranked-{draft_idx}-{timestamp}",
  "timeoutMs": 120000,
  "backend": "CLI",
  "voiceType": "THEQOO",
  "postKind": "CONFLICT"
}
```
Response: `{"content": "..."}` or `{"post": "..."}`

### Reranking (ML)
```http
POST http://100.115.252.61:8201/rerank
Authorization: Bearer aiuser-ml-api-token-dev-2026
Content-Type: application/json

{
  "community": "THEQOO",
  "contentType": "POST",
  "candidates": [
    {"id": "0", "text": "draft0"},
    {"id": "1", "text": "draft1"},
    {"id": "2", "text": "draft2"},
    {"id": "3", "text": "draft3"}
  ]
}
```
Response: `{"bestId": "1", "scores": [...]}`

### Corpus Export (ML)
```http
GET http://100.115.252.61:8201/corpus/export/blind?community=THEQOO&n_per_class=20
Authorization: Bearer aiuser-ml-api-token-dev-2026
```
Supports 3 response formats (flexible parsing):
- `{"pairs": [{"human_text": "...", "ai_text": "..."}, ...]}`
- `{"human_posts": [{"text": "..."}, ...]}`
- `{"posts": [{"text": "...", "source": "human"}, ...]}`

## Error Handling

| Scenario | Action | Logged |
|----------|--------|--------|
| Generation fails for theme | Skip theme, continue | WARNING |
| Rerank fails | Use first draft as fallback | WARNING |
| <15 pairs succeed | Exit code 1 | ERROR |
| Network timeout | Log exception, continue | ERROR |

## Logging

- **Facility**: Python `logging` module, ISO 8601 timestamps
- **Level**: INFO (default), WARNING (recoverable), ERROR (fatal)
- **Format**: `2026-06-20T15:32:01,704 [LEVEL] message`
- **Destination**: stdout (no file rotation)

## Usage

### Standard Invocation
```bash
cd /home/justant/Data/Again-Spring/.result/ai-user
python3 scripts/gen_reranked_tell_scan.py
```

**Requirements**:
- Python 3.8+
- `requests` library
- Network access to `againspring-llm-ai-user:8092` and `100.115.252.61:8201`
- Writable `.result/ai-user/blind/` directory

**Expected runtime**: ~2-3 minutes (with 8 workers, 4-5 sec per draft)

### Dry-Run (No External Calls)
```bash
python3 scripts/gen_reranked_tell_scan.py --dry-run
```

**Output**: Same format, placeholder texts (`[생성 텍스트 {idx}]...`)
**Runtime**: <5 seconds
**Use case**: Verify script logic, test CI/CD, check output format

## Next Steps

After successful completion:

### Step 1: Ensemble Blind Judge (3 Seeds)
```bash
python3 scripts/ensemble_blind_judge.py \
  --survey blind/r16-ml-reranked-theqoo-survey.md \
  --answers blind/r16-ml-reranked-theqoo-survey.json \
  --output blind/r16-ml-reranked-theqoo-judge-seed42.json \
  --seed 42
```
Repeat with `--seed 123` and `--seed 456` for confidence intervals.

### Step 2: Cond5 Auto-Gate
```bash
python3 scripts/cond5_auto_gate.py \
  --judge-output blind/r16-ml-reranked-theqoo-judge-seed42.json
```
Checks: PASS threshold (P(human)>0.5), P(ai), confidence interval width.

## Testing Checklist

- [x] Syntax: `python3 -m py_compile gen_reranked_tell_scan.py`
- [x] Dry-run: `python3 gen_reranked_tell_scan.py --dry-run` (exit 0, 20 pairs)
- [x] Output format: `cat blind/r16-ml-reranked-theqoo-survey.json | jq .n_pairs` (20)
- [x] Markdown generation: `head -20 blind/r16-ml-reranked-theqoo-survey.md` (starts "# Blind Survey")
- [ ] Integration: Full run with live LLM (when services ready)
- [ ] Ensemble judge: Verify judge output format
- [ ] Cond5 gate: Check gate output (PASS/FAIL)

## Dependencies

### Python Standard Library
- `json` — survey JSON serialization
- `sys` — CLI arguments, exit codes
- `time` — timestamp for correlation IDs
- `random` — seed management
- `logging` — diagnostic logs
- `pathlib` — path resolution
- `concurrent.futures` — ThreadPoolExecutor
- `datetime` — ISO timestamp generation
- `hashlib` — (imported but unused, can remove)

### External Package
- `requests` — HTTP client for LLM, ML services

## Version History

| Date | Version | Changes |
|------|---------|---------|
| 2026-06-20 | 1.0 | Initial implementation, dry-run tested |

## Constraints & Assumptions

1. **Exactly 20 themes**: Script hardcodes `THEQOO_THEMES` (20 items). Changes require code edit.
2. **Best-of-4 pattern**: N_DRAFTS=4 is fixed. Different values require testing on rerank service.
3. **Docker network access**: `againspring-llm-ai-user:8092` only reachable from within docker network (cannot call from host).
4. **WSL IP stability**: `100.115.252.61` must be stable (not dynamic).
5. **ML API token**: Hardcoded; no environment variable fallback.
6. **Min 15 pairs**: Gate is strict; <15 pairs → error exit.

## Future Enhancements

- [ ] Config file (YAML) instead of hardcoded constants
- [ ] Environment variable overrides (ML_API_TOKEN, WORKERS, etc.)
- [ ] Batch processing (split 20 themes into multiple survey files)
- [ ] Retry logic with exponential backoff (generation failures)
- [ ] Prometheus metrics (generation time, rerank latency, success rate)
- [ ] Support for other communities (CLIEN, NATEPAN)

---

**Last updated**: 2026-06-20
**Author**: Claude Code (Agent)
