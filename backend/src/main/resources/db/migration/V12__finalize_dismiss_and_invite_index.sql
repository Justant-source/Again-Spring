-- V12: finalizeSuggestion 메시지 dismiss 영속화 + 초대 토큰 인덱스
ALTER TABLE messages ADD COLUMN dismissed_at TIMESTAMP NULL DEFAULT NULL;

CREATE INDEX idx_messages_session_sender_dismissed
    ON messages(session_id, sender, dismissed_at);
