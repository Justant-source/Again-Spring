# ADR-0006: Legacy Deletion and Git Recovery

**Date**: 2026-06-03
**Status**: ✅ Accepted
**Deciders**: Data recovery & codebase team
**Related ADRs**: [ADR-0001](./0001-pivot-to-community-plaza.md) (pivot context), [ADR-0003](./0003-llm-consolidated-to-claude-code-cli.md) (LLM cleanup)

## Context

The pivot to community plaza (2026-06-02) required deletion of ~60+ files and 8 database tables. This ADR serves as the **deletion ledger** for recovery purposes.

**Two phases**:
1. **Phase 1 (defc742 commit)**: Legacy 1:1 chat entities + services deleted by product team.
2. **Phase 2 (this commit, 2026-06-03)**: Remaining orphaned code + documentation + tests.

## Decision

**Document all deletions with recovery paths**. For each deleted file:
- Relative path from repo root
- Brief description
- Recovery command: `git checkout defc742 -- <path>` or `git show defc742:<path>`
- DB dependencies (if applicable)

## Deletion Record

### Phase 1 Deletions (Commit defc742)

These deletions happened as part of the official pivot commit. All can be recovered with:
```bash
git checkout defc742 -- <file-path>
```

#### Backend Java — Entities & Services

| File | Description | Recovery |
|------|-------------|----------|
| `backend/src/main/java/com/againspring/domain/Session.java` | 1:1 mediation session container | `git checkout defc742 -- ...` |
| `backend/src/main/java/com/againspring/domain/Turn.java` | Message exchange round within session | `git checkout defc742 -- ...` |
| `backend/src/main/java/com/againspring/domain/ChatMessage.java` | Individual chat message | `git checkout defc742 -- ...` |
| `backend/src/main/java/com/againspring/domain/Report.java` | Final mediation summary report | `git checkout defc742 -- ...` |
| `backend/src/main/java/com/againspring/domain/UserRelationship.java` | Explicit pairing between two users | `git checkout defc742 -- ...` |
| `backend/src/main/java/com/againspring/domain/ConflictHistory.java` | Per-session psychological scores | `git checkout defc742 -- ...` |
| `backend/src/main/java/com/againspring/domain/TemperatureHistory.java` | Emotional state tracking per turn | `git checkout defc742 -- ...` |
| `backend/src/main/java/com/againspring/domain/enums/TurnRole.java` | INITIATOR / RESPONDER role enum | `git checkout defc742 -- ...` |

#### Backend Java — Services

| File | Description | Recovery |
|------|-------------|----------|
| `backend/src/main/java/com/againspring/service/ChatService.java` | Orchestrates 1:1 chat logic (message exchange, state transitions) | `git checkout defc742 -- ...` |
| `backend/src/main/java/com/againspring/service/CancelableChatService.java` | Cancellable LLM invocation for mediator messages | `git checkout defc742 -- ...` |
| `backend/src/main/java/com/againspring/service/ReportService.java` | Report generation post-finalization | `git checkout defc742 -- ...` |
| `backend/src/main/java/com/againspring/service/SessionStateMachine.java` | FSM for session lifecycle (CHATTING_SOLO → CHATTING_DUO → FINALIZED) | `git checkout defc742 -- ...` |
| `backend/src/main/java/com/againspring/service/llm/UserProfileFragment.java` | User style/MBTI injection into LLM context | `git checkout defc742 -- ...` |
| `backend/src/main/java/com/againspring/service/llm/PsychologyFeedbackFormatter.java` | Per-turn Gottman/NVC scoring (internal, no UI) | `git checkout defc742 -- ...` |
| `backend/src/main/java/com/againspring/service/llm/DuoBalanceFormatter.java` | Dual-user contribution balance analysis | `git checkout defc742 -- ...` |
| `backend/src/main/java/com/againspring/service/llm/CategoryContextFragment.java` | Conflict category injection into prompts | `git checkout defc742 -- ...` |
| `backend/src/main/java/com/againspring/service/seed/SeedScenarioBuilder.java` | Test data builder for seed scenarios | `git checkout defc742 -- ...` |
| `backend/src/main/java/com/againspring/service/FirstMessageService.java` | Auto-generated mediator first message (248 templates) | `git checkout defc742 -- ...` |
| `backend/src/main/java/com/againspring/service/FirstMessageTemplateLoader.java` | Template loader from JSON | `git checkout defc742 -- ...` |

#### Backend Java — Controllers

| File | Description | Recovery |
|------|-------------|----------|
| `backend/src/main/java/com/againspring/api/SessionController.java` | Session CRUD + invite/join endpoints | `git checkout defc742 -- ...` |
| `backend/src/main/java/com/againspring/api/ChatController.java` | Message send/receive endpoints | `git checkout defc742 -- ...` |
| `backend/src/main/java/com/againspring/api/ReportController.java` | Report retrieval endpoint | `git checkout defc742 -- ...` |
| `backend/src/main/java/com/againspring/api/admin/SessionContextDebugController.java` | Debug endpoint for session context | `git checkout defc742 -- ...` |
| `backend/src/main/java/com/againspring/api/admin/AdminTestController.java` | Test data seeding (dev-only) | `git checkout defc742 -- ...` |

#### Backend Java — DTOs

| File | Description | Recovery |
|------|-------------|----------|
| `backend/src/main/java/com/againspring/api/dto/response/SessionResponse.java` | Serialized session | `git checkout defc742 -- ...` |
| `backend/src/main/java/com/againspring/api/dto/response/CreateSessionResponse.java` | Session creation response | `git checkout defc742 -- ...` |
| `backend/src/main/java/com/againspring/api/dto/response/CurrentTurnResponse.java` | Current turn state | `git checkout defc742 -- ...` |
| `backend/src/main/java/com/againspring/api/dto/response/TurnResponse.java` | Turn detail | `git checkout defc742 -- ...` |
| `backend/src/main/java/com/againspring/api/dto/response/ChatTurnResponse.java` | Chat turn with messages | `git checkout defc742 -- ...` |
| `backend/src/main/java/com/againspring/api/dto/response/PartnerStatusResponse.java` | Partner join status | `git checkout defc742 -- ...` |
| `backend/src/main/java/com/againspring/api/dto/response/MessageMetadataResponse.java` | Message metadata (psyche scores) | `git checkout defc742 -- ...` |
| `backend/src/main/java/com/againspring/api/dto/response/FinalizationResponse.java` | Session finalization status | `git checkout defc742 -- ...` |
| `backend/src/main/java/com/againspring/api/dto/response/InviteTokenResponse.java` | Session invite token | `git checkout defc742 -- ...` |
| `backend/src/main/java/com/againspring/api/dto/response/ReportResponse.java` | Report summary | `git checkout defc742 -- ...` |
| `backend/src/main/java/com/againspring/api/dto/request/CreateSessionRequest.java` | Session creation request | `git checkout defc742 -- ...` |
| `backend/src/main/java/com/againspring/api/dto/request/JoinSessionRequest.java` | Session join request | `git checkout defc742 -- ...` |
| `backend/src/main/java/com/againspring/api/dto/request/ProgressTurnRequest.java` | Turn progression request | `git checkout defc742 -- ...` |
| `backend/src/main/java/com/againspring/api/dto/request/SendMessageRequest.java` | Message send request | `git checkout defc742 -- ...` |

#### Backend Java — Events

| File | Description | Recovery |
|------|-------------|----------|
| `backend/src/main/java/com/againspring/service/event/SessionCompletedEvent.java` | Event: session finalization | `git checkout defc742 -- ...` |
| `backend/src/main/java/com/againspring/service/event/TurnCompletedEvent.java` | Event: turn completion | `git checkout defc742 -- ...` |
| `backend/src/main/java/com/againspring/service/event/PartnerJoinedEvent.java` | Event: partner acceptance | `git checkout defc742 -- ...` |

#### Backend Java — Seed/Test

| File | Description | Recovery |
|------|-------------|----------|
| `backend/src/main/java/com/againspring/service/seed/SeedScenarios.java` | Predefined conflict scenarios (marriage, workplace, parent-child) | `git checkout defc742 -- ...` |
| `backend/src/main/java/com/againspring/api/seed/SeedController.java` | Endpoint to seed test data | `git checkout defc742 -- ...` |
| `backend/src/main/resources/seed/scenarios.json` | Scenario definitions JSON | `git checkout defc742 -- ...` |

#### Backend Java — LLM

| File | Description | Recovery |
|------|-------------|----------|
| `backend/src/main/java/com/againspring/service/llm/ClaudeApiProvider.java` | Anthropic REST API LLM provider (dual-path, deleted per ADR-0003) | `git checkout defc742 -- ...` |

#### Frontend — Routes & Pages

| File | Description | Recovery |
|------|-------------|----------|
| `frontend/app/sessions/page.tsx` | Session list page | `git checkout defc742 -- ...` |
| `frontend/app/sessions/[id]/page.tsx` | Session detail page | `git checkout defc742 -- ...` |
| `frontend/app/sessions/[id]/chat/page.tsx` | Chat page | `git checkout defc742 -- ...` |
| `frontend/app/sessions/[id]/report/page.tsx` | Report view page | `git checkout defc742 -- ...` |
| `frontend/app/sessions/invite/[token]/page.tsx` | Invite accept page | `git checkout defc742 -- ...` |

#### Frontend — Components

| File | Description | Recovery |
|------|-------------|----------|
| `frontend/components/sessions/SessionList.tsx` | Session list display | `git checkout defc742 -- ...` |
| `frontend/components/chat/ChatMessage.tsx` | Individual message display | `git checkout defc742 -- ...` |
| `frontend/components/chat/MessageInput.tsx` | Message input box | `git checkout defc742 -- ...` |
| `frontend/components/chat/MediatorMessage.tsx` | AI mediator response display | `git checkout defc742 -- ...` |
| `frontend/components/report/ContributionRatio.tsx` | Ratio visualization (removed per FE UX policy) | `git checkout defc742 -- ...` |
| `frontend/components/report/ReportSummary.tsx` | Report text summary | `git checkout defc742 -- ...` |
| `frontend/components/icons/Conversation.tsx` | Conversation icon | `git checkout defc742 -- ...` |

#### Frontend — State & API

| File | Description | Recovery |
|------|-------------|----------|
| `frontend/lib/store/sessionStore.ts` | Zustand session state | `git checkout defc742 -- ...` |
| `frontend/lib/api/sessionApi.ts` | Session CRUD API calls | `git checkout defc742 -- ...` |
| `frontend/lib/api/chatApi.ts` | Chat message API calls | `git checkout defc742 -- ...` |
| `frontend/lib/api/reportApi.ts` | Report retrieval API calls | `git checkout defc742 -- ...` |
| `frontend/lib/utils/messageSplitter.ts` | Split messages for animation | `git checkout defc742 -- ...` |
| `frontend/lib/utils/needsMapDistance.ts` | Calculate needs similarity | `git checkout defc742 -- ...` |
| `frontend/lib/utils/describePlaceholder.ts` | Generate placeholder text | `git checkout defc742 -- ...` |

#### Frontend — MSW Mocks

| File | Description | Recovery |
|------|-------------|----------|
| `frontend/mocks/handlers/sessionHandlers.ts` | Mock session API | `git checkout defc742 -- ...` |
| `frontend/mocks/handlers/chatHandlers.ts` | Mock chat API | `git checkout defc742 -- ...` |
| `frontend/mocks/handlers/reportHandlers.ts` | Mock report API | `git checkout defc742 -- ...` |
| `frontend/mocks/handlers/historyMessages.ts` | Mock history (message fixtures) | `git checkout defc742 -- ...` |
| `frontend/mocks/fixtures/mockReports.ts` | Report fixture data | `git checkout defc742 -- ...` |

#### Frontend — Tests

| File | Description | Recovery |
|------|-------------|----------|
| `frontend/tests/e2e-realbe/flows/01-solo-session.spec.ts` | E2E: solo session flow (user A only) | `git checkout defc742 -- ...` |
| `frontend/tests/e2e-realbe/flows/02-duo-session.spec.ts` | E2E: duo session flow (both users) | `git checkout defc742 -- ...` |
| `frontend/tests/e2e-realbe/flows/03-session-finalization.spec.ts` | E2E: session completion | `git checkout defc742 -- ...` |
| `frontend/tests/e2e-realbe/invariants/duo-message-isolation.spec.ts` | Invariant: users see only their own perspective | `git checkout defc742 -- ...` |
| `frontend/tests/e2e-realbe/invariants/contribution-ratio-legal-notice.spec.ts` | Invariant: ratio shows legal notice | `git checkout defc742 -- ...` |
| `frontend/tests/e2e-realbe/guest-golden-path.spec.ts` | E2E: guest solo session | `git checkout defc742 -- ...` |
| `frontend/tests/e2e/a11y.spec.ts` | A11y tests (may reference deleted routes) | `git checkout defc742 -- ...` |

#### Documentation (Deleted Phase 1)

| File | Description | Recovery |
|------|-------------|----------|
| `shared/docs/reports/duo_report_spec.md` | Deleted 2026-06-03 (Phase 2) | Will be in this commit |
| `shared/docs/reports/solo_report_spec.md` | Deleted 2026-06-03 (Phase 2) | Will be in this commit |
| `backend/docs/test-automation.md` | Session/chat test strategy (obsolete) | `git checkout defc742 -- ...` |
| `frontend/docs/ux/flows/05-session-chat.md` | UX doc for chat flow | `git checkout defc742 -- ...` |
| `frontend/docs/ux/flows/06-duo.md` | UX doc for duo mode | `git checkout defc742 -- ...` |
| `frontend/docs/ux/flows/07-report.md` | UX doc for reports | `git checkout defc742 -- ...` |

### Phase 2 Deletions (This Commit, 2026-06-03)

#### Database (Flyway V56 DROP)

These tables are **irreversibly dropped** via Flyway migration:

```sql
-- Flyway V56
DROP TABLE IF EXISTS conflict_history;
DROP TABLE IF EXISTS temperature_history;
DROP TABLE IF EXISTS messages;
DROP TABLE IF EXISTS turns;
DROP TABLE IF EXISTS user_relationships;
DROP TABLE IF EXISTS sessions;
DROP TABLE IF EXISTS reports;
```

**Recovery**: Restore pre-V56 database backup. See `env/docs/deployment.md` for backup procedure.

#### Documentation (This Commit)

| File | Description | Recovery |
|------|-------------|----------|
| `shared/docs/reports/duo_report_spec.md` | Deleted this commit | `git checkout HEAD^ -- ...` (parent commit) |
| `shared/docs/reports/solo_report_spec.md` | Deleted this commit | `git checkout HEAD^ -- ...` (parent commit) |

**Note**: These are ADR-0006 itself, so recovery means checking prior history.

## Rationale

**Why document deletions?**

1. **Traceability**: Future developers can understand what was removed and why.
2. **Recovery**: If a business decision requires reverting (e.g., "re-enable sessions for enterprise"), developers have clear recovery path.
3. **Learning**: Codebase archaeology is easier with this ledger.
4. **Compliance**: Some audits require deletion records.

## Positive Consequences

- ✅ **Clear recovery path**: Every deleted file has a git command.
- ✅ **Traceability**: Reason for deletion is documented (ADR-0001).
- ✅ **Reduced confusion**: Developers won't search for "where is ChatService?" forever.

## Negative Consequences

- ❌ **Bulk deletion**: 60+ files is significant cruft removal, but also high risk if recovery is needed.
- ❌ **DB migrations irreversible**: Flyway V56 DROP is one-way; DB backup is only safety.

## Implementation Notes

### Recovery Workflow

**Recover a single file** (post-defc742):
```bash
git checkout defc742 -- backend/src/main/java/com/againspring/service/ChatService.java
git add .
git commit -m "Restore legacy ChatService (recovery only, not production-ready)"
```

**Recover entire session system**:
```bash
# Recover all deleted files from commit defc742
git checkout defc742 -- backend/ frontend/
git checkout defc742 -- shared/docs/reports/

# Re-apply Flyway migrations (reversing V56 DROP)
# — requires manual .sql files (not automated in current project)

# Restore DB from pre-V56 backup
docker exec againspring-mariadb-dev mariadb-restore < backup-pre-v56.sql
```

### Testing After Recovery

If recovering for testing purposes:

1. **Unit tests**: `./gradlew test -k ChatService` (should pass if code restored correctly)
2. **DB health**: Ensure migrated to post-V56 schema before running backend
3. **E2E**: Run `flows/01-solo-session.spec.ts` (if available in recovered version)

## Related Assets

- **Pivot ADR**: [ADR-0001](./0001-pivot-to-community-plaza.md) (explains why deletions occurred)
- **DB migrations**: `backend/src/main/resources/db/migration/V56__drop_legacy_session_tables.sql`
- **Git log**: `git log --oneline | grep defc742` (pivot commit)
- **Backup procedure**: `env/docs/deployment.md` (DB backup before V56)

---

**Last Revision**: 2026-06-03
**Status**: Complete deletion record for 2026-06-02 pivot
