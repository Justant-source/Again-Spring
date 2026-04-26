-- V6: Solo 모드를 디폴트로 (V1.5 솔로-퍼스트 전환)
-- 기존 row의 NULL은 false로 채워서 일관성 유지 (백워드 호환)
UPDATE sessions SET solo_mode = false WHERE solo_mode IS NULL;
ALTER TABLE sessions MODIFY COLUMN solo_mode BOOLEAN NOT NULL DEFAULT TRUE;
