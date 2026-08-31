# ADR-0000: Record Architecture Decisions

**Date**: 2026-06-03
**Status**: ✅ Accepted
**Deciders**: Claude Code Agent (Stream E)
**Related ADRs**: None (foundational)

## Context

The Again Spring project underwent a major pivot on 2026-06-02, shifting from a 1:1 mediation chat model (Session → Turn → ChatMessage → Report) to a community plaza model (Post → Juror → Vote/Comment). This pivot introduces significant architectural changes affecting:

- Data models (8 entities deleted, 5 created)
- LLM integration (dual-path removed, CLI-only)
- Frontend routes and components (30+ deleted/refactored)
- Database schema (Flyway V56 DROP, V57+ additions)
- Psychology model application (per-turn inference → juror persona)

To preserve decision rationale and support future maintenance, recovery, and feature development, we adopt **Architecture Decision Records** (ADRs) as our primary mechanism for documenting "why" behind major changes.

## Decision

1. **Adopt ADRs** in `/docs/shared/ADR/` as the source of truth for architectural decisions post-pivot.
2. **Document all 7 major decisions** from the pivot in dedicated ADRs (0001–0006).
3. **Use the template** defined in [ADR README](./README.md) for future decisions.
4. **Link ADRs** from relevant code comments, backend docs, and CLAUDE.md.

## Rationale

- **Traceability**: Future developers can understand "why we deleted Session" without digging through git history.
- **Recovery**: Each ADR includes git checkout commands and file paths for partial recovery if needed.
- **Trade-off documentation**: Negative consequences and risks are explicit, enabling informed future pivots.
- **Onboarding**: New contributors can quickly learn the architectural landscape via the ADR index.

## Positive Consequences

- Clear audit trail of major decisions.
- Reduced cognitive load for new team members.
- Easy reference during code reviews ("per ADR-0001, sessions are deleted").
- Historical record for research/post-mortems.

## Negative Consequences

- Initial overhead: writing 7 ADRs for the pivot.
- ADRs can become stale if not maintained alongside code changes.
- Requires discipline to write new ADRs for future major changes.

## Implementation Notes

1. All ADRs are written at the time of pivot (2026-06-03).
2. Status progresses: Proposed → Accepted → (Deprecated if superseded).
3. File naming: `NNNN-kebab-case-title.md` (zero-padded 4-digit ID).
4. All ADRs link back to this one for context.

## Related Assets

- ADR directory: `/docs/shared/ADR/`
- Pivot commit: defc742b1e15c0550dd7bd7a80c744f35b120ebf (last of old model)
- CLAUDE.md: Links to specific ADRs where relevant
- backend/docs: Sections on legacy deletion and recovery procedures

---

**Next ADR**: ADR-0001 (Pivot to Community Plaza)
