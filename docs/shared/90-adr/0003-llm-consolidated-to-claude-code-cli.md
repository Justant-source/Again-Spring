# ADR-0003: LLM Consolidated to Claude Code CLI

**Date**: 2026-06-02
**Status**: ⛔ Superseded by [ADR-0007](./0007-llm-provider-abstraction-and-stateless-worker.md) (2026-09) — CLI 단일화·API 키 제거·`/root/.claude` 마운트·`api/llm/RemoteLlmProvider` 경로 서술은 현행과 다르다. 역사 기록으로만 남긴다.
**Deciders**: Backend infrastructure team
**Related ADRs**: [ADR-0001](./0001-pivot-to-community-plaza.md) (pivot context)

## Context

**Pre-pivot LLM architecture** (V13 final):

Two parallel paths:
- **Chat path** (1:1 mediation): `ClaudeApiProvider` → Anthropic REST API (ANTHROPIC_API_KEY, session-aware)
  - Per-turn LLM inference (fast, context-aware via cached system prompt)
  - Model: `claude-haiku-4-5-20251001`
  - Used by: `ChatService.generateMediatorMessage()`

- **Report path** (summary generation): `RemoteLlmProvider` → Claude Code CLI (host `~/.claude`, no API key)
  - Batch inference after 5+ messages (slower, more expensive)
  - Model: `claude-sonnet-5`
  - Used by: `ReportService.generateReport()`

**Hybrid complexity**:
- Dual code paths (`LlmProviderConfig` routing logic)
- Two LLM models in active use
- Anthropic API key required in prod (security/cost concern)
- Claude Code CLI path underutilized (only report generation)

**With community plaza**:
- No per-turn inference needed (jurors are post-level, one-shot)
- No session context required (posts are stateless)
- Report generation remains (optional future feature)

**Decision question**: Single consolidation path or maintain both?

## Decision

**Consolidate all LLM calls to Claude Code CLI via `RemoteLlmProvider`**.

1. **Remove dual paths**: Delete `ClaudeApiProvider` class and all references.
2. **Single route**: All LLM tasks (juror generation, future report) → `RemoteLlmProvider`.
3. **No API key**: Rely on host `~/.claude` mount (no ANTHROPIC_API_KEY in env).
4. **Sidecar worker**: Dedicated `againspring-llm-{dev,prod}` container (Spring Boot LLM worker) running Claude Code CLI.

## Rationale

| Factor | ClaudeApi (REST) | Claude Code CLI | Winner |
|--------|------------------|-----------------|--------|
| **Setup complexity** | API key, env var | Host ~/.claude mount, container link | CLI (1-time) |
| **Per-request latency** | <2s (REST call) | ~30s (CLI invocation) | API ✓ |
| **Batch throughput** | High (concurrent requests) | Moderate (process pool 100) | API ✓ |
| **Cost** | Direct billing to ANTHROPIC_API_KEY | No charge (CLI via 1 account) | CLI ✓ |
| **Security** | API key in env (risk) | Key in host ~/.claude (safer) | CLI ✓ |
| **Feature access** | Latest models only | Claude Code CLI default (up-to-date) | Tie |
| **Code simplification** | 2 providers | 1 provider | CLI ✓ |
| **Unification** | N/A | Backend LLM worker isolation | CLI ✓ |

**Strategic choice**: Prioritize code simplification + cost (no API key) + backend isolation. Accept ~30s latency tradeoff for juror generation (acceptable for async plaza model).

## Positive Consequences

- ✅ **One code path**: Single `RemoteLlmProvider` + `RemoteLlmWorker` service.
- ✅ **No ANTHROPIC_API_KEY**: Removed from all `.env.*` files (security win).
- ✅ **Cost savings**: CLI-based (~$0 direct billing) vs. API (per-token billing).
- ✅ **Easier deployment**: No credential management per container.
- ✅ **Backend isolation**: LLM work silo'd in dedicated worker container (easier to scale).
- ✅ **Testing**: Simpler mocking (single provider to mock in tests).

## Negative Consequences

- ❌ **Latency**: 30s+ for juror generation vs. <2s with REST API.
- ❌ **Throughput**: Process pool (100 concurrent) may bottleneck during viral posts.
- ❌ **CLI overhead**: Each request spawns a new Claude Code CLI process (no persistent session).
- ❌ **User experience**: Users wait ~1 minute for juror perspectives (acceptable for async, but noticeable).
- ❌ **Monitoring**: CLI failures harder to debug than structured REST errors.

## Implementation Notes

### Architecture

```
FrontEnd (Next.js)
    ↓ (API request)
Backend (Spring Boot) — `CommunityPostController`
    ↓ (async LLM call)
RemoteLlmProvider (HTTP client)
    ↓ (gRPC/REST to worker)
againspring-llm-{dev,prod} container
    ↓ (spawn CLI process)
Claude Code CLI (~/.claude authenticated)
    ↓
Anthropic API
```

### Configuration

**Backend** (`application-{dev,prod}.yml`):
```yaml
llm:
  jury-provider: remote
  jury-llm-model: claude-haiku-4-5-20251001
  compose-llm-model: claude-haiku-4-5-20251001
  remote-worker-url: http://againspring-llm-{dev,prod}:8090
```

**Deleted configs**:
- `llm.chat.provider: claude-api` (no longer exists)
- `ANTHROPIC_API_KEY` env var (removed from .env.dev/.env.prod)

**Mounted** (compose file):
```yaml
againspring-llm-{dev,prod}:
  volumes:
    - ${CLAUDE_HOST_CONFIG_DIR:-/home/justant/.claude}:/root/.claude
```

### Code Changes

**Deleted** (at defc742 or this commit):
- `ClaudeApiProvider.java` class (REST API provider)
- `LlmProviderConfig.java` routing logic (dual-path conditional)
- All `@Qualifier("chatLlmProvider")` references
- `ANTHROPIC_API_KEY` property binding

**Modified** (this commit):
- `PostComposeService.java`: Line 46 changed from `@Qualifier("chatLlmProvider")` to `@Qualifier("composeLlmProvider")` (bug fix, was broken).
- `application-prod.yml`: Removed `llm.chat.provider=claude-api` (now only remote).

**Preserved**:
- `RemoteLlmProvider.java` (expanded use, now handles all LLM)
- `RemoteLlmWorker` service (existing LLM sidecar)
- `PromptSanitizer.java` (post-processing, still active)

### Testing

- **Unit**: Mock `RemoteLlmProvider` → verify juror service logic.
- **Integration**: Spin up test `againspring-llm` container with mocked Claude CLI → verify workflow.
- **E2E**: Manual guest post → wait ~1-2m for jurors → verify jurors appear in API response.

### Rollout

- **Dev**: Already live (all new code uses RemoteLlmProvider).
- **Prod**: No change after this commit; was already CLI-only on prod (chat path was dev-only experiment).

## Rollback

If `RemoteLlmProvider` proves too slow:
1. Restore `ClaudeApiProvider`: `git checkout defc742 -- backend/src/main/java/com/againspring/service/llm/ClaudeApiProvider.java`
2. Restore `LlmProviderConfig`: `git checkout defc742 -- backend/src/main/java/com/againspring/config/LlmProviderConfig.java`
3. Restore config: `git checkout defc742 -- backend/src/main/resources/application-prod.yml`
4. Add back `ANTHROPIC_API_KEY` to `.env.prod`
5. Re-enable dual-path routing.

## Related Assets

- **Worker service**: `backend/src/main/java/com/againspring/api/llm/RemoteLlmProvider.java`
- **Worker container**: `env/docker-compose.{dev,prod}.yml` (`againspring-llm-{dev,prod}`)
- **Worker env**: `env/.env.{dev,prod}.example` (`LLM_WORKER_URL`, `CLAUDE_HOST_CONFIG_DIR`)
- **Config**: `backend/src/main/resources/application-{dev,prod}.yml` (llm.* settings)
- **Documentation**: `backend/docs/llm-bridge.md`, `docs/shared/prompts/README.md`

---

**Next ADR**: ADR-0004 (Onboarding + MBTI Hidden, Not Removed)
