-- V102: Marketing waiting-board holding (seed/draft before T+24h commit)
-- Doc-Sync: docs/shared/api/database-schema.md · docs/shared/api/rest-spec.md

CREATE TABLE marketing_holding (
  post_id         VARCHAR(32)  NOT NULL,
  status          VARCHAR(20)  NOT NULL DEFAULT 'IN_POOL'
                    COMMENT 'IN_POOL|PINNED|OUT_OF_CUT|COMMITTED|DROPPED',
  pin_format      VARCHAR(10)  NULL
                    COMMENT 'VIDEO|TEXT when PINNED; unused until S3',
  draft_json      JSON         NULL
                    COMMENT 'Marketing draft (BriefDto-shaped)',
  score_snapshot  DOUBLE       NULL,
  rank_snapshot   INT          NULL,
  locked_at       TIMESTAMP(3) NULL
                    COMMENT 'Set on COMMITTED job create; draft read-only after',
  created_at      TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at      TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                                 ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (post_id),
  CONSTRAINT fk_mh_post FOREIGN KEY (post_id) REFERENCES posts(id),
  CONSTRAINT chk_mh_status CHECK (status IN (
    'IN_POOL', 'PINNED', 'OUT_OF_CUT', 'COMMITTED', 'DROPPED'
  )),
  CONSTRAINT chk_mh_pin_format CHECK (
    pin_format IS NULL OR pin_format IN ('VIDEO', 'TEXT')
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Waiting-board marketing draft/seed per post (pre T+24h)';

CREATE INDEX idx_mh_status ON marketing_holding(status);
CREATE INDEX idx_mh_rank ON marketing_holding(rank_snapshot);
