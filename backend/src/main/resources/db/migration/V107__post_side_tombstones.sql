-- Side-body tombstones for partner-invite ownership (author/partner independent delete).
-- Note: contract doc mentioned V100; that version is already used → this is V107.

ALTER TABLE posts
  ADD COLUMN author_body_deleted_at TIMESTAMP(6) NULL,
  ADD COLUMN partner_body_deleted_at TIMESTAMP(6) NULL;
