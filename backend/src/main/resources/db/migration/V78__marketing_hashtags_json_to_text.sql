-- V78: marketing_contents.hashtags 컬럼 JSON → TEXT 변경
-- 근본 원인: V30이 hashtags를 JSON으로 정의 → MariaDB가 CHECK(JSON_VALID(hashtags)) 자동 생성.
-- 그러나 GenerationOutput은 해시태그 배열을 공백 구분 평문("#다시봄 #갈등해결 …")으로 저장 →
-- 유효한 JSON이 아니므로 인스타그램/네이버 콘텐츠 저장 시 제약 위반 →
-- DataIntegrityViolationException → 콘텐츠 REJECTED.
-- (X는 hashtags 미생성 → NULL → JSON_VALID(NULL) 통과해서 영향 없었음)
-- 엔티티는 이미 @Column(columnDefinition = "TEXT") String 이므로 TEXT가 올바른 타입.
-- JSON → TEXT MODIFY 시 JSON_VALID CHECK 제약도 함께 제거됨.

ALTER TABLE marketing_contents
    MODIFY COLUMN hashtags TEXT COMMENT 'Platform hashtags (space-separated plain text)';
