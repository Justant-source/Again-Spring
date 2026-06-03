# ADR-0005: Marketing Automation Retained Unchanged

**Date**: 2026-06-02
**Status**: ✅ Accepted
**Deciders**: Marketing & infrastructure team
**Related ADRs**: [ADR-0001](./0001-pivot-to-community-plaza.md) (pivot context)

## Context

**V15 Marketing Automation** (Flyway V28–V39, 2026-05-31) is a dev-only subsystem that:

1. **Simulates social posting**: `marketing-renderer` (Chrome headless) + `social-poster` (Playwright) automate post creation to:
   - Threads (Meta)
   - Facebook (Meta)
   - (Instagram scaffolded, not yet enabled)

2. **Content generation**: `ContentGenerator` interfaces + `ContentGeneratorRegistry` produce platform-specific post variations (copy, images, hashtags).

3. **Image rendering**: `/render-chat` endpoint (HTTP GET) returns HTML/CSS → PNG (chat UI mockup for social previews).

4. **Admin dashboard**: `marketing/admin` endpoints for managing content/campaigns (read-only in v1, write in v2).

**Artifact locations**:
- Code: `backend/src/main/java/com/againspring/service/marketing/`, `backend/src/main/java/com/againspring/api/admin/SocialPublishController.java`
- Services: `marketing-renderer/` (Express), `social-poster/` (Node CLI wrapper on Playwright)
- Database: `contents` table (Flyway V35+), `social_credentials`, `content_schedules`
- Docs: `shared/docs/v15/`, `marketing/docs/` (dev-only)

**With community plaza pivot**:

The old 1:1 chat code (Session/Turn/Message) used by V15 for `/render-chat` is now deleted. Question arises:

> Does V15 marketing automation depend on legacy chat code and break with the pivot?

**Investigation result**: **No dependency detected**. V15 is fully isolated:
- `/render-chat` endpoint is no-op (returns placeholder HTML, doesn't render real sessions)
- `ContentGenerator` is abstract; implementations exist for each platform (no chat coupling)
- No `SessionService` calls in `SocialPublishController`
- Database: `contents`, `social_credentials`, `content_schedules` tables exist independently

**Decision question**: Retain V15 unchanged, or prune/upgrade?

## Decision

**Retain V15 marketing automation unchanged**.

1. **No breaking changes**: Legacy code deletion doesn't affect V15 (verified).
2. **Keep isolation**: V15 is development-only; no prod customer impact.
3. **Future integration point**: V15.10+ can integrate real community posts into content generation (e.g., "trending posts" feature).
4. **Minimal effort**: Retaining is cheaper than removing + re-building later.

## Rationale

| Factor | Retain | Remove | Winner |
|--------|--------|--------|--------|
| **Breaking changes** | None | None (fully isolated) | Tie |
| **Cleanup cost** | Zero (already isolated) | Moderate (7 files, 2 tables) | Retain |
| **Future value** | High (content pipeline) | None | Retain |
| **Prod risk** | Zero (dev-only) | Zero | Tie |
| **Maintenance burden** | Minimal (no active use) | Minimal (already gone) | Tie |
| **Learning value** | Templates for next feature | Lost | Retain |

**Strategic choice**: Retain as template for future community + social integration.

## Positive Consequences

- ✅ **Zero work**: No changes needed; V15 works as-is.
- ✅ **Preserved template**: `ContentGenerator` interface is reusable for v2 (real posts → social).
- ✅ **Future integration**: Can generate social content from trending community posts (quick MVP).
- ✅ **Dev tooling**: Marketing team keeps dev testing environment operational.

## Negative Consequences

- ❌ **Code cruft**: 3 services + 7 services files remain unused in prod.
- ❌ **Documentation`: V15 docs are dev-only; confusing for main product docs.
- ❌ **Testing**: V15 tests not run in prod pipeline (E2E only covers community plaza).
- ❌ **Maintenance debt**: If V15 is revived, code may have rotted (dependencies, security patches).

## Implementation Notes

### V15 Isolation Verified

**Checked dependencies**:
- ❌ `SocialPublishController` → SessionService: NOT FOUND
- ❌ `ContentGenerator` → ChatService: NOT FOUND
- ❌ `MarketingImageController` → Turn/Message: NOT FOUND
- ✅ `ContentsTable` → independent schema (Flyway V35+)
- ✅ `/render-chat` endpoint → returns static HTML (no render logic)

**Services (unchanged)**:
- `marketing-renderer` (Express server, `/render-chat`, `/render-post`)
- `social-poster` (Node CLI for Threads/Facebook/Instagram)
- `social-credentials`, `content_schedules` tables (Flyway V36, V37)

### No Code Changes

- ✅ `backend/src/main/java/com/againspring/service/marketing/` — unchanged
- ✅ `backend/src/main/java/com/againspring/api/admin/SocialPublishController.java` — unchanged
- ✅ `marketing/` module — unchanged
- ✅ Database: Flyway V28–V39 migrations retained

### Documentation Updates

**Mark V15 as dev-only** in main docs:
- Add header to `shared/docs/README.md`: "**V15 Marketing Automation (dev-only)** — See `shared/docs/v15/` for details."
- Link: `[V15 Documentation](shared/docs/v15/README.md)`

**Separate docs path**:
- Main product docs: `shared/docs/api/`, `shared/docs/policies/`, `backend/docs/`, `frontend/docs/`
- V15 marketing docs: `shared/docs/v15/` (development-only, not indexed in main README)

### Rollout

- **Dev**: V15 fully operational. No changes needed.
- **Prod**: V15 containers (`marketing-renderer`, `social-poster`) not deployed (dev-only environment variables not set in prod).

## Future Integration (V15.10+)

Once V15 is considered for prod, integrate with community plaza:

```
Community Post (FE user input)
    ↓
PostComposeService (backend)
    ↓
ContentGenerator (extends for auto-social)
    ↓
SocialPublishController
    ↓
Threads/Facebook/Instagram (auto-posting)
```

See `marketing/docs/` for roadmap.

## Related Assets

- **Marketing services**: `backend/src/main/java/com/againspring/service/marketing/`
- **Social publishing**: `backend/src/main/java/com/againspring/api/admin/SocialPublishController.java`
- **V15 documentation**: `shared/docs/v15/README.md`, `marketing/docs/`
- **Content tables**: Flyway V35–V39 (`contents`, `social_credentials`, `content_schedules`)
- **Renderer service**: `marketing-renderer/` (Express server)
- **Poster service**: `social-poster/` (Playwright automation)
- **Docker compose**: `env/docker-compose.dev.yml` (only includes marketing services)

---

**Next ADR**: ADR-0006 (Legacy Deletion and Git Recovery)
