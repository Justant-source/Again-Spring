# ADR-0002: Psychology Model Repurposed for Jurors

**Date**: 2026-06-02
**Status**: ✅ Accepted
**Deciders**: Claude Code LLM Bridge team
**Related ADRs**: [ADR-0001](./0001-pivot-to-community-plaza.md) (pivot context)

## Context

The original 1:1 chat model employed three psychology frameworks:

1. **Gottman 4-Horsemen**: Criticism, Contempt, Defensiveness, Stonewalling. Per-turn analysis to identify destructive patterns.
2. **Nonviolent Communication (NVC)**: Observation → Feeling → Need → Request. Used to reframe aggressive messages.
3. **Emotionally Focused Therapy (EFT)**: Attachment cycles, emotional injuries, bids for connection. Long-term relationship arc.

**Application in 1:1 chat**:
- Each message from User A/B is analyzed for Gottman markers.
- AI mediator reframes using NVC (message neutralization).
- After 5+ turns, report identifies attachment injuries and proposes EFT-based bridge.

**Code implementation** (now deleted):
- `EnforcedRatio` class: computed per-turn Gottman scores (not exposed to UI, only internal).
- `PromptSanitizer`: post-processing of LLM output to remove "judgment" language.
- Prompts: `shared/docs/prompts/chat/{solo_chat,duo_chat}.md` (task-specific system prompts).

**With pivot to community plaza**, the question arises:

> Can we reuse Gottman/NVC/EFT frameworks in the juror model (N=9 AI jurors, one-shot generation per post)?

## Decision

**Yes, repurpose psychology frameworks as juror personas**.

Rather than per-turn inference, each juror represents a distinct perspective grounded in psychology:

1. **NVC-focused juror**: "What needs are unspoken here? How can we observe without judgment?"
2. **Gottman-aware juror**: "I notice patterns of [Horseman]. Here's what I'd focus on instead."
3. **Attachment juror**: "This sounds like [attachment wound]. Often [reframe] helps."
4. **Practical/boundary juror**: "Your needs are valid. A boundary might look like [example]."
5. ... (5 more personas, see `jury_persona.md`)

**Payload**: Juror includes:
- `persona_name` (e.g., "Attachment Therapist")
- `perspective` (500-word reasoning, grounded in psychology)
- `key_insight` (1-2 sentences for voting prominence)

## Rationale

- **Breadth over depth**: 9 jurors ≈ 9 perspectives. User chooses insights that resonate.
- **Framework reuse**: Gottman/NVC/EFT don't disappear; they're embedded in juror system prompt + persona descriptions.
- **Explainability**: Juror persona name signals "why" this perspective. Transparency > black-box AI.
- **Flexibility**: Can add/remove jurors or adjust personas without DB migration (juror count is not hard-coded).
- **Safety**: Anonymized post + curated juror perspectives = safer than unmoderated community alone.

## Positive Consequences

- ✅ **Knowledge preservation**: Gottman/NVC/EFT still active in the system, just applied differently.
- ✅ **Persona appeal**: Users understand "why" juror says something ("therapist" ≠ "relationship guru").
- ✅ **Customization**: Future: add jurors for neurodivergence, cultural context, specific conflict types.
- ✅ **Explainable AI**: Each juror perspective is grounded in a named psychology school.

## Negative Consequences

- ❌ **Less personalized**: 1:1 chat could adapt to specific User A/B dynamic. Jurors are generic.
- ❌ **Persona consistency**: Must ensure each juror stays "in character" across prompts. Risk of drift.
- ❌ **Training data gap**: Juror personas trained on general psychology; no per-user learning.
- ❌ **Depth loss**: Per-turn Gottman analysis allowed micro-feedback. Juror generates once (one-shot).

## Implementation Notes

### Prompt Structure

**System prompt** (`shared/docs/prompts/system.md`):
- Describes AI role: "You are part of a diverse jury of psychology perspectives."
- Constraints: No judgment, no "should" prescriptions, focus on reframing.

**Persona prompts** (`shared/docs/prompts/community/jury_persona.md`):
- 9 distinct personas, each with:
  - Name (e.g., "Attachment Specialist")
  - Psychology school (Gottman/NVC/EFT/etc.)
  - Perspective (300-word system instruction)
  - Output format (JSON: `{persona_name, key_insight, perspective}`)

**Sanitizer** (`PromptSanitizer.java`):
- Removed "verdict" language (Gottman scores, contribution ratio).
- Kept: observation-based reframing, empathy, perspective-shifting.

### Code Changes

**New**:
- `JuryService.generateJurors(Post post)` — orchestrate 9 LLM calls (or 1 batch), collect juror perspectives.
- `PostComposeService.compose(Post post)` — prepare post context for juror generation (category, content, anonymity).

**Preserved (not deleted)**:
- `PromptSanitizer` — still used in `JuryService` to clean juror outputs.
- `EnforcedRatio` class — not wired to UI, not deleted. Future use: per-juror scoring (optional).
- Prompts directory structure — `/shared/docs/prompts/community/`.

**Deleted** (at defc742):
- Per-turn `PsychologyFeedbackFormatter` — no longer applies Gottman scoring to each message.
- `ChatService` turn analysis — replaced by one-shot juror generation.
- User profile injection (`UserProfileFragment`) in chat context — preserved, awaiting Juror v2 personalization.

### Testing

- Unit: `JuryServiceTest` validates 9 juror generation, persona variety.
- Integration: `CommunityPostControllerTest` ensures jurors surface in POST /posts/{id}/jury response.
- Prompt validation: Ensure each juror perspective avoids forbidden words (via `PromptSanitizer`).

### Juror v2 Roadmap (Future)

- **Personalization**: Inject user MBTI + relationship context into juror system prompt (conditional per persona).
- **Dynamic count**: Adjust juror count (N=5 for mobile, N=9 for web) based on device + time budget.
- **Juror refinement**: User upvote/flag → fine-tune persona weights in next post's juror generation.

## Related Assets

- **Persona definitions**: `shared/docs/prompts/community/jury_persona.md`
- **Neutralization guide**: `shared/docs/prompts/community/neutralize.md` (NVC reframing rules)
- **System prompt**: `shared/docs/prompts/system.md` (applies to all AI output)
- **Psychology policy**: `shared/docs/policies/psychology-model.md` (defines Gottman/NVC/EFT frameworks)
- **Sanitizer**: `backend/src/main/java/com/againspring/service/llm/PromptSanitizer.java`
- **Service**: `backend/src/main/java/com/againspring/service/community/JuryService.java`

---

**Next ADR**: ADR-0003 (LLM Consolidated to Claude Code CLI)
