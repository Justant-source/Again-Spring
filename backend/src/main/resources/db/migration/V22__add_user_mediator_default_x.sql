-- V22: 사용자별 중재자 톤 기본값 X (0=팩트, 100=공감). NULL이면 communicationStyle 매핑 fallback.
-- idempotent — 이미 컬럼이 있으면 스킵.

SET @col_exists := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'mediator_default_x'
);
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE users ADD COLUMN mediator_default_x INT NULL',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
