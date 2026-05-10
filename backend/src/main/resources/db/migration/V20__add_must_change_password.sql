-- V20: 임시 비밀번호 발급 후 강제 비밀번호 변경 플래그
-- requestTemporaryPassword 호출 시 TRUE 설정, 사용자가 비밀번호 변경 후 FALSE로 해제

ALTER TABLE users
    ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE;
