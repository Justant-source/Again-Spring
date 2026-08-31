# ADR-0001: Pivot to Community Plaza

**Date**: 2026-06-02
**Status**: ✅ Accepted
**Deciders**: Product team
**Related ADRs**: [ADR-0002](./0002-psychology-model-repurposed-for-jurors.md) (psychology model), [ADR-0006](./0006-legacy-deletion-and-git-recovery.md) (deletion record)

## Context

The original Again Spring product (Sessions V1–V13) operated as a 1:1 mediation chat platform:

1. **User A** initiates a session with **User B** (partner/family member).
2. User B must accept the invite within 30 days (high friction, low adoption).
3. Both users exchange messages in a turn-based chat.
4. AI mediator analyzes psychology (Gottman 4-Horsemen, NVC) and provides feedback per turn.
5. After 5+ messages, users receive a report showing "contribution ratio" and advice.

**Core Problems**:
- **High entry barrier**: User B must be known, reachable, and willing to join. Many disputes don't allow for prior coordination.
- **Low conversion**: Asymmetric participation (only User A initiates) → User B lacks agency → low activation.
- **Requires both**: Neither user can benefit from insights until both contribute. Single-sided perspective is incomplete.
- **Synchronous bias**: Mediation chat feels like forced interaction; async participation is not supported.

**Market signal**: V13 beta showed strong product-market fit in Korean community (marketing automation via social posts), but user retention dropped after first session due to inability to invite partners.

## Decision

**Pivot from 1:1 mediation chat to asynchronous community plaza model**:

1. **Post-first**: User posts their perspective on a conflict (anonymous, category, optional context).
2. **AI Jurors**: System generates N=9 AI jurors (psychology counselor personas) who each provide perspective on the post.
3. **Community voting**: Other users vote on which juror perspective is most helpful (empathy voting, not judgment).
4. **Comments & Discussion**: Community can comment and debate perspectives (moderated, no personal attacks).
5. **Dual posting** (optional): If User B also posts their side, AI jurors can analyze both perspectives together and provide a "bridge" perspective.
6. **No invite required**: User can benefit from posting alone; no dependency on partner acceptance.

## Rationale

| Factor | 1:1 Chat | Community Plaza | Winner |
|--------|----------|-----------------|--------|
| **Barrier to entry** | Requires partner invite + 30d acceptance | Post-once, get insights immediately | Plaza |
| **Participation asymmetry** | Both required | Both optional, one sufficient | Plaza |
| **Content network effect** | Locked in 1:1 (no discovery) | Public posts → feed discovery → re-engagement | Plaza |
| **Async support** | No (turn-based chat) | Yes (post → jurors → vote → comment over days) | Plaza |
| **User agency** | Partner must accept | Post anonymously, no friction | Plaza |
| **Psychology depth** | Per-turn detailed analysis | AI persona synthesis (breadth > depth) | Chat ✓ |
| **Dual perspective** | High quality (both users) | Requires both posts + manual correlation | Chat ✓ |

**Strategic choice**: Optimize for activation (lower barrier) over mediation quality (assume N jurors ≥ 1 user's insight).

## Positive Consequences

- ✅ **Activation**: New user can post immediately, see juror responses in minutes. No partner coordination.
- ✅ **Retention loop**: Feed of community posts → discovery → voting/commenting → re-engagement.
- ✅ **Scaling**: Jurors are AI (infinite capacity); 1 juror serves all users vs. 1:1 pairing model.
- ✅ **Psychology reuse**: Gottman/NVC/EFT models still applied, now in juror persona form (see ADR-0002).
- ✅ **SEO/Social**: Public posts + juror insights = discoverable, shareable content.
- ✅ **Safety**: Anonymous posting enables users in abusive/dangerous relationships to seek help without partner knowledge.

## Negative Consequences

- ❌ **Incomplete analysis**: Single perspective is one-sided; AI jurors trained on general psychology, not relationship-specific nuance.
- ❌ **Lower quality insights**: 9 jurors debating is breadth, not depth of 1:1 mediation.
- ❌ **Moderation burden**: Community comments require active moderation to prevent abuse.
- ❌ **False reconciliation**: Users might interpret juror perspectives as relationship advice, not just perspective-sharing.
- ❌ **Lost exclusivity**: 1:1 trust/privacy → public (mitigated by anonymity, but still a shift).
- ❌ **Depends on community**: Quality degrades if community is small or low-engagement.

## Implementation Notes

### Data Model Changes

**New entities** (Flyway V57+):
- `posts` — user perspective on conflict (category, content, anonymous flag)
- `post_comments` — community replies (moderated, no attachments)
- `votes` — upvote/reaction to juror perspective
- `vote_options` — predefined reactions (empathy, insight, new perspective)
- `jurors` — AI-generated perspectives per post (N=9, each with persona name + reasoning)

**Deleted entities** (Flyway V56 DROP, git recover at defc742):
- `sessions` — 1:1 mediation container
- `turns` — message exchange rounds
- `messages` — chat messages
- `reports` — final analysis + ratio
- `user_relationships` — explicit pairing
- `conflict_history` — per-session psychology scores
- `temperature_history` — emotional state tracking

### Code Changes

**Backend**:
- New: `CommunityPostController` (CRUD posts) + `JuryService` (generate jurors) + `VoteController` (upvote)
- Deleted: `SessionController`, `ChatService`, `CancelableChatService`, `ReportService` (all legacy at defc742)
- Refactored: `UserController` (remove GET /me/history, PATCH /me/mediator-style, POST /me/tutorial)
- Preserved: LLM bridge remains (now CLI-only per ADR-0003)

**Frontend**:
- New: `community/` routes (browse plaza, view post+jurors, vote, comment)
- Deleted: `sessions/`, `chat/`, `report/` routes + 30+ components
- Refactored: `layout.tsx`, `page.tsx`, `profile/` (remove session history)

**Prompts**:
- New: `/docs/shared/prompts/community/` (jury_persona.md, neutralize.md)
- Deleted: `/docs/shared/prompts/chat/` (solo_chat.md, duo_chat.md)

### Testing & Validation

- Unit tests: Post creation, juror generation, vote aggregation (80% coverage target).
- Integration: API endpoints (community posts flow) against real BE.
- Manual: Browse plaza, post, see jurors generate, vote, comment.
- E2E: `flows/04-community-plaza.spec.ts` (v1 covers guest post→juror flow).

### Rollout

- **Dev**: Deployed 2026-06-02 (commit defc742 onwards).
- **Prod**: Awaiting explicit "deploy to prod" directive + full e2e pass.

## Recovery (If Reverting)

1. **Restore old model**: `git checkout defc742 -- backend/ frontend/ docs/shared/` (partial, selective)
2. **Restore DB**: Pre-V56 backup restore (requires explicit save before V56 migration)
3. **Reverse Flyway**: Manual downgrade to V55 (not automated)

See ADR-0006 for complete deletion record with per-file recovery paths.

## Related Assets

- **Frontend routes**: `frontend/app/community/[id]/page.tsx`, `frontend/app/community/new/page.tsx`
- **Backend controller**: `backend/src/main/java/com/againspring/api/community/CommunityPostController.java`
- **LLM prompts**: `docs/shared/prompts/community/jury_persona.md`, `neutralize.md`
- **API spec**: `docs/shared/api/rest-spec.md` (Community section)
- **DB schema**: `docs/shared/api/database-schema.md` (posts, post_comments, votes, jurors)
- **E2E test**: `frontend/tests/e2e-realbe/flows/04-community-plaza.spec.ts`
- **Deletion record**: [ADR-0006](./0006-legacy-deletion-and-git-recovery.md)

---

**Next ADR**: ADR-0002 (Psychology Model Repurposed for Jurors)
