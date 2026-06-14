# ADR-0005: Marketing Automation — Moved to Again-Spring-Marketing

**Status**: SUPERSEDED (2026-06-09)
**Previous Status**: ✅ Accepted (2026-06-02)
**Superseded By**: WO-ASM-01 (Again-Spring-Marketing extraction work order)

## Supersession Notice

The marketing automation system described in ADR-0005 (V15, 2026-06-02) has been extracted from Again-Spring into a dedicated service project:

**Again-Spring-Marketing (ASM)** — Hosted on the WSL GPU server.

This repository now acts as a thin trigger/client only, calling ASM APIs for marketing operations.

## What Changed

- **Before (2026-06-02)**: Marketing services (`marketing-renderer`, `social-poster`) were sidecars in `docker-compose.dev.yml`.
- **After (2026-06-09)**: Marketing services removed. `marketing/` directory deleted. BE now calls ASM via HTTP (configurable `ASM_BASE_URL`).

## References

- **ASM Project**: `Again-Spring-Marketing/` (separate repository)
- **Integration Point**: `ASM_BASE_URL`, `ASM_API_TOKEN`, `ASM_ENABLED` env vars in Again-Spring backend
- **API Gateway**: nginx `/api/admin/marketing/` reverse-proxies to ASM when enabled

## Migration Path

For dev/testing:
1. Clone `Again-Spring-Marketing` to WSL GPU server
2. Configure Again-Spring `ASM_BASE_URL=http://100.115.252.61:8200`
3. Backend calls ASM endpoints as needed

For prod:
- Set `ASM_ENABLED=false` (default)
- Omit `ASM_BASE_URL`, `ASM_API_TOKEN` from `.env.prod`

See ASM project documentation for full integration guide.

---

**Previous ADR Content**: See `git show 99322622:shared/docs/ADR/0005-marketing-automation-retained-unchanged.md` (archived before extraction)
