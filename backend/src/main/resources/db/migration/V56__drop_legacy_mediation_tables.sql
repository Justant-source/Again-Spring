-- V56: 구 1:1 중재 채팅 모델 테이블 삭제 (2026-06-02 광장형 피벗 후 orphan)
-- 광장형(C3: Post/PostComment/Vote/Juror) 완전 전환. Session/Turn/Message 모델 제거.

SET FOREIGN_KEY_CHECKS = 0;

-- messages 테이블 삭제 (V7에서 생성, V1.5 카톡식 채팅)
-- 외래키: fk_messages_session → sessions(id)
DROP TABLE IF EXISTS messages;

-- turns 테이블 삭제 (V1에서 생성, V5에서 이미 삭제했을 수 있음)
-- 외래키: fk_turns_session → sessions(id)
DROP TABLE IF EXISTS turns;

-- reports 테이블 삭제 (V1에서 생성, 1:1 중재 분석 리포트)
DROP TABLE IF EXISTS reports;

-- conflict_history 테이블 삭제 (V1에서 생성, V5에서 제거했을 수 있음)
DROP TABLE IF EXISTS conflict_history;

-- temperature_history 테이블 삭제 (V1에서 생성, V5에서 제거했을 수 있음)
DROP TABLE IF EXISTS temperature_history;

-- user_relationships 테이블 삭제 (V1에서 생성, Neo4j 대체용)
-- 참고: 광장형에서는 사용자 관계 추적 불필요 (댓글/투표는 익명 또는 유저 ID 기반)
DROP TABLE IF EXISTS user_relationships;

-- sessions 테이블 삭제 (V1에서 생성, 1:1 중재 세션 메타)
-- 광장형 전환 후 Session 모델 완전 폐기
DROP TABLE IF EXISTS sessions;

SET FOREIGN_KEY_CHECKS = 1;
