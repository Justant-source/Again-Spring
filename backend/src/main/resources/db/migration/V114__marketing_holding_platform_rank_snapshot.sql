-- Persist the actual independent platform rank used when an automatic T+24h holding is selected.
-- Doc-Sync: docs/shared/marketing/platforms.md · docs/shared/api/database-schema.md
ALTER TABLE marketing_holding
  ADD COLUMN platform_rank_snapshot JSON NULL AFTER rank_snapshot;
