# A-B Test Driver for Again-Spring AI User Style Generation

## Overview

`run_ab_test.py` is a Python script that evaluates the quality of AI-generated community-style responses by comparing them against human-written samples using the ML service's evaluation pipeline.

**Workflow:**
1. Verify corpus has data for the target community
2. Generate synthetic human POST samples (or fetch from real corpus when endpoint available)
3. Generate N=4 AI draft responses per context using claude CLI (with community-specific persona)
4. Ingest both human and AI samples into ML service corpus
5. Queue evaluation job via `/eval/baseline` endpoint
6. Poll job status and display results (MAUVE score, style metrics)

## Installation

Requires:
- Python 3.9+
- `claude` CLI: `/home/justant/.nvm/versions/node/v24.14.1/bin/claude` (LLM bridge)
- Network access to ML service: `http://100.115.252.61:8201` (WSL)
- ML API token: `aiuser-ml-api-token-dev-2026` (in environment or hardcoded)

## Usage

```bash
python3 run_ab_test.py [OPTIONS]

Options:
  --community THEQOO|CLIEN|DCINSIDE|NATEPAN
    Community to evaluate (default: THEQOO)

  --limit N
    Number of human samples to generate/use (default: per-community; e.g., 15 for THEQOO, 10 for CLIEN)

  --drafts N
    Number of AI draft responses per context (default: 4)

  --dry-run
    Print prompts without making API calls (useful for testing)

  --blind
    [Future] Save blind export to JSONL files locally (not yet implemented)

  -h, --help
    Show this help message
```

## Examples

### Test with default THEQOO community (15 samples, 4 drafts)
```bash
python3 run_ab_test.py
```

### Dry-run: see prompts without API calls
```bash
python3 run_ab_test.py --community CLIEN --limit 5 --drafts 2 --dry-run
```

### Full test: DCINSIDE with 10 samples
```bash
python3 run_ab_test.py --community DCINSIDE --limit 10
```

## Community Styles

The script includes hardcoded persona traits for each community:

| Community  | Style Trait | Default Limit |
|-----------|------|---------|
| **THEQOO** | Female-focused, short, many exclamations (ㅋㅋ, 헉), occasional emojis | 15 |
| **CLIEN** | IT professionals, logical, formal speech, medium length | 10 |
| **DCINSIDE** | Male-focused, direct, slang, abbreviations (ㄹㅇ, ㅇㅈ) | 15 |
| **NATEPAN** | Housewives/working women, emotional, empathetic | 15 |

## API Endpoints Used

### ML Service (http://100.115.252.61:8201)

1. **GET /corpus/stats** — Check available corpus per community
   - Returns: `{"COMMUNITY": {"ai": N, "human": M}, ...}`

2. **POST /corpus/ingest** — Ingest labeled training items
   - Request: `{"items": [{"community": "THEQOO", "contentType": "POST|COMMENT", "text": "...", "label": "ai|human", "source": "..."}]}`
   - Response: `{"inserted": N, "skipped": M}`

3. **POST /eval/baseline** — Queue evaluation job
   - Request: `{"communities": ["THEQOO"], "contentType": "POST", "idempotencyKey": "..."}` 
   - Response: `{"job_id": "...", "status": "QUEUED|RUNNING|DONE|FAILED"}`

4. **GET /eval/{job_id}** — Poll job status
   - Response: `{"job_id": "...", "status": "...", "result": {...}, "error": null}`
   - Result includes: MAUVE score, style metrics (comma_rate, spacing_error, burstiness, etc.)

## Claude CLI Integration

The script uses subprocess to call claude CLI for draft generation:

```bash
claude -p "<prompt>" --model claude-haiku-4-5-20251001
```

Each draft:
- Takes ~10 seconds (Haiku model)
- Timeout: 30 seconds
- Output: single comment/reply text in Korean

Prompt template:
```
당신은 {community} 커뮤니티 스타일의 한국 인터넷 유저입니다.
아래 갈등 게시글을 읽고, 그 커뮤니티에서 볼 법한 짧은 댓글 하나를 써주세요.
- 댓글 길이: 1~3문장
- 언어: 한국어 구어체
- 커뮤니티 특성: {trait}
- 출력: 댓글 본문만 (다른 설명 없이)

[원문]
{context_text}
```

## Output

### Successful run
```
2026-06-16 11:48:28 [INFO] Starting A-B test for THEQOO (limit=1, drafts=1)
2026-06-16 11:48:28 [INFO] Checking corpus stats...
2026-06-16 11:48:28 [INFO]   Community stats: {'ai': 424, 'human': 333}
2026-06-16 11:48:28 [INFO] Generating 1 synthetic human POST samples...
2026-06-16 11:48:28 [INFO] Generating 1 drafts per context (total: 1)...
2026-06-16 11:48:28 [INFO] Generating drafts for context 1/1...
2026-06-16 11:48:28 [INFO]   Generated draft 1/1
2026-06-16 11:48:28 [INFO] Ingesting 2 items into corpus...
2026-06-16 11:48:28 [INFO]   Inserted: 2, Skipped: 0
2026-06-16 11:48:28 [INFO] Queuing eval/baseline job...
2026-06-16 11:48:28 [INFO]   Job ID: 01KV7591WPNAZ7B1DYWX8TW09A, Status: QUEUED
2026-06-16 11:48:28 [INFO] Polling job status...
2026-06-16 11:48:30 [INFO]   Status: RUNNING, elapsed: 2s
2026-06-16 11:48:38 [INFO] Job completed: {job_response}
2026-06-16 11:48:38 [INFO] === RESULTS ===
{
  "by_content_type": {
    "POST": {
      "THEQOO": {
        "n_human": 334,
        "n_ai": 65,
        "mauve": 0.3454,
        "human_spacing_error_rate": 0.404,
        "ai_spacing_error_rate": 0.790,
        "human_burstiness": 1.032,
        "ai_burstiness": 0.770,
        ...
      }
    }
  }
}
```

Key metrics in result:
- **mauve**: Style similarity score (0-1, higher is more similar to human style)
- **n_human**: Human samples in corpus
- **n_ai**: AI samples in corpus
- **human_spacing_error_rate**: Punctuation spacing errors in human corpus
- **ai_spacing_error_rate**: Punctuation spacing errors in AI corpus
- **human_burstiness**: Variation in sentence length (human)
- **ai_burstiness**: Variation in sentence length (AI)

## Known Issues / TODOs

1. **No real human corpus export**: Currently uses synthetic samples (`[COMMUNITY갈등사연]`). Should replace with actual `/examples/export` endpoint once available.

2. **Blind export not implemented**: `--blind` flag is accepted but does nothing. Should implement JSONL export of blind evaluation samples.

3. **Single-threaded**: Generates drafts sequentially. Could parallelize claude CLI calls for speed (5x speedup for --limit 15 --drafts 4).

4. **No deduplication on ingest**: Script doesn't deduplicate within a batch before submitting. ML service handles this, but could optimize locally.

5. **Hardcoded token**: `ML_API_TOKEN` should read from environment variable or `.env` file.

## Configuration

All hardcoded values are at the top of the script:

```python
ML_SERVICE_URL = "http://100.115.252.61:8201"
ML_API_TOKEN = "aiuser-ml-api-token-dev-2026"
CLAUDE_CLI_PATH = "/home/justant/.nvm/versions/node/v24.14.1/bin/claude"
CLAUDE_MODEL = "claude-haiku-4-5-20251001"
CLAUDE_TIMEOUT_SECONDS = 30
```

## Testing

### Dry-run (no API calls)
```bash
python3 run_ab_test.py --community THEQOO --limit 2 --drafts 2 --dry-run
# Prints all prompts that would be sent to claude CLI
```

### Real run with 1 sample, 1 draft (fast validation)
```bash
python3 run_ab_test.py --community THEQOO --limit 1 --drafts 1
# Takes ~20 seconds (10s claude + 10s eval job)
```

### Full scale test (slow, ~15-20 min for 15 samples, 4 drafts)
```bash
python3 run_ab_test.py --community CLIEN --limit 15 --drafts 4
# Generates 60 AI responses, ingests 75 items, waits for eval
```

## Error Handling

- **Unknown community**: Exits with error message listing valid communities
- **Claude CLI timeout**: Logs warning, skips draft, continues
- **HTTP errors**: Logs 422/5xx and error details, exits
- **Job failure**: Logs error and stops polling

## Dependencies

- **urllib** (stdlib) — HTTP requests with bearer token
- **json** (stdlib) — JSON parsing
- **subprocess** (stdlib) — Claude CLI invocation
- **logging** (stdlib) — Structured logging
- **argparse** (stdlib) — CLI argument parsing
- **datetime** (stdlib) — Timestamp for idempotency key

No external pip packages required.

---

**Created:** 2026-06-16  
**Author:** Claude Code (Agent)
