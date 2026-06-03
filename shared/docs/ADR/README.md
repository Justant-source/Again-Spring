# Architecture Decision Records (ADR)

This directory contains Architecture Decision Records for the Again Spring project, tracking major design decisions, their context, and rationale since the pivot to community plaza (2026-06-02).

## What is an ADR?

An ADR is a short document describing a significant architectural choice, its context, and the consequences. See [ADR 0000](./0000-record-architecture-decisions.md) for our framework.

## Active ADRs

| ID | Title | Status | Impact |
|---|---|---|---|
| [0000](./0000-record-architecture-decisions.md) | Record Architecture Decisions | ✅ Accepted | Process framework |
| [0001](./0001-pivot-to-community-plaza.md) | Pivot to Community Plaza | ✅ Accepted | 🟥 High — entire product |
| [0002](./0002-psychology-model-repurposed-for-jurors.md) | Psychology Model Repurposed for Jurors | ✅ Accepted | 🟨 Medium — LLM prompts |
| [0003](./0003-llm-consolidated-to-claude-code-cli.md) | LLM Consolidated to Claude Code CLI | ✅ Accepted | 🟨 Medium — backend infrastructure |
| [0004](./0004-onboarding-mbti-hidden-not-removed.md) | Onboarding + MBTI Hidden, Not Removed | ✅ Accepted | 🟩 Low — code/DB, no UX |
| [0005](./0005-marketing-automation-retained-unchanged.md) | Marketing Automation Retained Unchanged | ✅ Accepted | 🟩 Low — isolated subsystem |
| [0006](./0006-legacy-deletion-and-git-recovery.md) | Legacy Deletion and Git Recovery | ✅ Accepted | 🔴 Critical — deletion record |

## Writing a New ADR

1. **Copy template**: Use [ADR-0000](#adr-0000-template) as a template.
2. **Assign ID**: Next available (sequential 4-digit).
3. **File naming**: `NNNN-kebab-case-title.md` (e.g., `0007-feature-x-decision.md`).
4. **Fill sections**: Status → Accepted/Proposed/Deprecated, all 6 sections.
5. **Link in this README**: Add row to Active ADRs table, update status.

### ADR Template

```markdown
# ADR-NNNN: Title of Decision

**Date**: YYYY-MM-DD
**Status**: Proposed / Accepted / Deprecated
**Deciders**: [Who decided]
**Related ADRs**: [Link to related]

## Context

[What is the issue we're facing that is motivating this decision?]

## Decision

[What is the change that we're proposing and/or doing?]

## Rationale

[Why is this decision the right one given the context?]

## Positive Consequences

[Benefits and opportunities that result from this decision.]

## Negative Consequences

[Drawbacks, risks, or trade-offs.]

## Implementation Notes

[Code locations, migration paths, testing strategy.]

## Related Assets

[Files, schemas, test cases affected.]
```

---

**Last Updated**: 2026-06-03
