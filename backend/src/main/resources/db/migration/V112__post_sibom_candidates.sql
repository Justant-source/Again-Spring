-- 시봄이 keyword shortlist for video insert planning (≤12).
-- Metaphor remains on posts for DB compat; video path uses sibom_plan instead.

ALTER TABLE posts
  ADD COLUMN sibom_candidates JSON NULL
    COMMENT 'Sibomi catalog id shortlist ≤12 (keyword score after story create)'
    AFTER metaphor_id;
