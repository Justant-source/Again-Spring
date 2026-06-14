# ADR-0004: Onboarding + MBTI Hidden, Not Removed

**Date**: 2026-06-02
**Status**: ✅ Accepted
**Deciders**: Backend data retention team
**Related ADRs**: [ADR-0001](./0001-pivot-to-community-plaza.md) (pivot context)

## Context

The original 1:1 chat model included a **10-question onboarding flow**:

1. User answers questions about conflict style, attachment, values, etc.
2. Answers map to 6 mediation **styles** (Peacemaker, Validator, Guardian, Dreamer, Pioneer, Sage).
3. System stores style + MBTI inference in User entity.
4. Per-session, system injects user's style into LLM context (`UserProfileFragment`).
5. Per-turn analysis included style-based psychology feedback.

**Code implementation** (now deleted):
- `UserController.POST /me/onboarding` — 10-Q form submission
- `UserController.PATCH /me/mediator-style` — style override
- `UserController.POST /me/tutorial/complete` — 30-second tutorial modal
- `StyleCalculator` class — Q&A → style mapping
- `User.mediator_style`, `User.mbti_type` DB columns
- FE: `OnboardingModal.tsx`, 3-step modal with dot indicator
- Prompts: User style injected in `UserProfileFragment`

**With community plaza**:

Onboarding is **no longer required**:
- No sessions to customize by style
- Jurors are generic (not personalized per user profile)
- Anonymous posts don't benefit from user style data

**Decision question**: Delete onboarding data entirely, or hide and preserve?

## Decision

**Hide onboarding UX; preserve code + DB columns**. Rationale:

1. **Low-risk preservation**: Code + DB columns are inert (no active calls).
2. **Future reuse**: Juror v2 personalization (ADR-0002 roadmap) will re-enable per-user customization.
3. **User privacy**: Existing users' style/MBTI already stored; deleting columns is immoral data destruction.
4. **Minimal cleanup cost**: Hide FE screens (1 line per route) vs. delete (3 files + 2 columns + 8 queries).

## Rationale

| Aspect | Delete | Hide | Winner |
|--------|--------|------|--------|
| **Complexity** | High (schema + code + tests) | Low (1 line per route) | Hide |
| **Future reuse** | Lost forever | Ready-to-reactivate | Hide |
| **Code debt** | Removed | Dormant | Tie |
| **Data respect** | Destructive | Preserves history | Hide |
| **Timeline** | 2+ hours | <5 min | Hide |
| **Safety** | Risk of bugs | Zero risk | Hide |

**Strategic choice**: Preserve for future personalization; hide to unclutter current product.

## Positive Consequences

- ✅ **Fast implementation**: Hide routes + disable FE UI (no BE changes needed).
- ✅ **Reversible**: Re-enable by adding 1 line back to route guard.
- ✅ **Future ready**: Juror v2 can immediately use MBTI data without schema migration.
- ✅ **Data continuity**: Users' historical styles available if accessed via admin API (future).
- ✅ **Ethical**: Respects data users previously provided.

## Negative Consequences

- ❌ **Dead code**: `StyleCalculator`, `UserProfileFragment` remain in codebase (not called).
- ❌ **Schema cruft**: `User.mediator_style`, `User.mbti_type` unused columns.
- ❌ **Testing confusion**: Tests for hidden code must be disabled or removed.
- ❌ **Documentation burden**: Future devs see code and wonder "why is this here?" (mitigated by this ADR).

## Implementation Notes

### Frontend Changes

**Hide routes** (add route guard to `/app/(auth)/signup/page.tsx`, `/app/(dashboard)/profile/page.tsx`):
```typescript
// Before: User sees onboarding on signup
// After: Onboarding step skipped, direct to dashboard
if (shouldShowOnboarding) {
  // ...deprecated, skip to dashboard
  redirect('/dashboard');
}
```

**Hide components**:
- `OnboardingModal.tsx` — no longer mounted
- `StyleCalculator` calls removed from signup flow

**Disabled tests**:
- `tests/e2e-realbe/invariants/onboarding-*.spec.ts` (if any exist, mark as disabled)

### Backend Changes

**No changes** to code or schema:
- `UserController.POST /me/onboarding` still exists (no calls, unused)
- `User.mediator_style`, `User.mbti_type` columns remain (unused, not dropped)
- `StyleCalculator` class not deleted (dormant, available for reactivation)

**Mark as deprecated** (optional, in code comment):
```java
/**
 * DEPRECATED (ADR-0004): Onboarding hidden pending Juror v2 personalization.
 * Route not exposed in FE; see ADR-0004 for recovery path.
 * @deprecated
 */
@PostMapping("/onboarding")
public ResponseEntity<UserResponse> onboarding(...) { ... }
```

### Database

**No migrations** to run. Schema already exists:
- `users.mediator_style` VARCHAR(50) NULL
- `users.mbti_type` VARCHAR(10) NULL

Existing user data preserved.

### Testing

**Disable (don't delete)** tests:
- `UserControllerTest.testOnboarding()` — mark `@Disabled("ADR-0004: onboarding hidden")`
- `StyleCalculatorTest` — mark entire class `@Disabled("ADR-0004: onboarding hidden")`

**Rationale**: Tests document hidden code; disabling (not deleting) lets future developer understand intent.

### Reactivation (Juror v2)

When personalizing jurors per user style:
1. Uncomment `UserController.POST /me/onboarding`
2. Re-mount `OnboardingModal.tsx` in signup flow
3. Inject `User.mediator_style` into `JuryService` system prompt
4. Re-enable tests
5. Write new ADR-000X explaining reactivation (supersedes ADR-0004)

## Related Assets

- **Deprecated controller**: `backend/src/main/java/com/againspring/api/UserController.java` (POST /me/onboarding)
- **Deprecated service**: `backend/src/main/java/com/againspring/service/StyleCalculator.java`
- **Deprecated component**: `frontend/components/auth/OnboardingModal.tsx`
- **User entity**: `backend/src/main/java/com/againspring/domain/User.java` (mediator_style, mbti_type columns)
- **Disabled tests**: `backend/src/test/.../UserControllerTest.java::testOnboarding`

---

**Next ADR**: ADR-0005 (Marketing Automation Retained Unchanged)
