-- WAIT_FOR_PARTNER private-until-partner 폐기 (Q14).
-- PRIVATE + WAIT_FOR_PARTNER 대기 글:
--   created_at > 30일 전 → soft-delete (posts.deleted_at; 기존 소프트삭제 패턴)
--   나머지 → PUBLIC + vote_close_at (publishNow와 동일: COALESCE(vote_duration_hours, 72)h)

UPDATE posts
SET deleted_at = UTC_TIMESTAMP(6)
WHERE visibility = 'PRIVATE'
  AND publish_mode = 'WAIT_FOR_PARTNER'
  AND deleted_at IS NULL
  AND created_at < (UTC_TIMESTAMP(6) - INTERVAL 30 DAY);

UPDATE posts
SET visibility = 'PUBLIC',
    vote_close_at = COALESCE(
        vote_close_at,
        DATE_ADD(UTC_TIMESTAMP(6), INTERVAL COALESCE(vote_duration_hours, 72) HOUR)
    )
WHERE visibility = 'PRIVATE'
  AND publish_mode = 'WAIT_FOR_PARTNER'
  AND deleted_at IS NULL;
