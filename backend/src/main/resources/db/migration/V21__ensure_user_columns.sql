-- V21: dev/prod 컬럼 일관성 보강 — 과거 일부 환경에서 누락된 두 컬럼을 idempotent하게 추가
-- (이미 존재하면 스킵, 없으면 ALTER)

-- is_guest
SET @col_exists := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'is_guest'
);
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE users ADD COLUMN is_guest BIT(1) NOT NULL DEFAULT 0',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- onboarding_completed_at
SET @col_exists := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'onboarding_completed_at'
);
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE users ADD COLUMN onboarding_completed_at DATETIME(6) NULL',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
