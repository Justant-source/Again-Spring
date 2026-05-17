-- sessions.relationship_type → relation_type
-- dev에서 ddl-auto=update로 컬럼명이 변경됐지만 prod 마이그레이션에 누락됨
ALTER TABLE sessions
    CHANGE COLUMN relationship_type relation_type VARCHAR(32) COMMENT 'RelationType enum';
