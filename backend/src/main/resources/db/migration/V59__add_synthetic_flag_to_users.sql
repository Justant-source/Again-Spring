-- V59: synthetic 플래그 — AI 유저(봇) 계정 식별용
-- synthetic=1: 봇 계정. 응답/화면/메타데이터 노출 금지(내부 전용).
-- 0=실제 사용자 (기본값)
ALTER TABLE users ADD COLUMN synthetic BIT(1) NOT NULL DEFAULT 0 COMMENT 'AI 유저 봇 계정 식별자 (내부 전용, 절대 노출 금지)';
CREATE INDEX idx_users_synthetic ON users (synthetic);
