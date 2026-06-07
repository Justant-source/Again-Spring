-- 페르소나 일일 쿼터 테이블 (DailyPlanner가 매일 04:00 KST에 생성)
-- 각 페르소나의 일일 행동 목표량을 기록하여 TickDispatcher가 분배

CREATE TABLE IF NOT EXISTS persona_daily_quota (
    persona_id       VARCHAR(32)  NOT NULL,
    day_bucket       DATE         NOT NULL,                   -- KST 기준
    target_posts     INT          NOT NULL DEFAULT 0,
    target_comments  INT          NOT NULL DEFAULT 0,
    target_replies   INT          NOT NULL DEFAULT 0,
    target_votes     INT          NOT NULL DEFAULT 0,
    target_likes     INT          NOT NULL DEFAULT 0,
    target_views     INT          NOT NULL DEFAULT 0,
    done_posts       INT          NOT NULL DEFAULT 0,
    done_comments    INT          NOT NULL DEFAULT 0,
    done_replies     INT          NOT NULL DEFAULT 0,
    done_votes       INT          NOT NULL DEFAULT 0,
    done_likes       INT          NOT NULL DEFAULT 0,
    done_views       INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (persona_id, day_bucket),
    KEY idx_day (day_bucket),
    FOREIGN KEY (persona_id) REFERENCES personas(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
