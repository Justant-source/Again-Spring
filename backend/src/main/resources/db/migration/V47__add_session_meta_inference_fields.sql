-- V47: 자유 서술 기반 자동 추론 필드 추가
-- sessions: 제목·키워드·한국특화태그 저장. users: 중재자 성향 Y축 기본값.
-- idempotent — 이미 컬럼이 있으면 스킵.

-- sessions.title
SET @col_exists := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sessions' AND COLUMN_NAME = 'title'
);
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE sessions ADD COLUMN title VARCHAR(255) NULL',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- sessions.keywords (JSON 배열, 추론 핵심 키워드 최대 2개)
SET @col_exists := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sessions' AND COLUMN_NAME = 'keywords'
);
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE sessions ADD COLUMN keywords JSON NULL',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- sessions.title_edited_by_user (사용자 수동 편집 시 자동 덮어쓰기 차단 플래그)
SET @col_exists := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sessions' AND COLUMN_NAME = 'title_edited_by_user'
);
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE sessions ADD COLUMN title_edited_by_user TINYINT(1) NOT NULL DEFAULT 0',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- sessions.korean_tag (한국 특화 4종 추론 태그: in_law / face / lingered / generation / NULL)
SET @col_exists := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sessions' AND COLUMN_NAME = 'korean_tag'
);
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE sessions ADD COLUMN korean_tag VARCHAR(32) NULL',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- users.mediator_default_y (Y축 기본값 0=경청, 100=능동. NULL이면 communicationStyle fallback)
SET @col_exists := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'mediator_default_y'
);
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE users ADD COLUMN mediator_default_y INT NULL',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
