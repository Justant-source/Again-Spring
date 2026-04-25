-- 소셜 로그인용 컬럼 추가
ALTER TABLE users
    ADD COLUMN provider VARCHAR(50) NULL COMMENT 'google|kakao|naver (null=이메일 가입)',
    ADD COLUMN provider_id VARCHAR(255) NULL COMMENT 'OAuth provider의 유저 ID',
    MODIFY COLUMN email VARCHAR(255) NULL,
    MODIFY COLUMN password_hash VARCHAR(255) NULL;

ALTER TABLE users
    ADD UNIQUE KEY uk_users_provider (provider, provider_id);

-- 게스트 지속성 테이블 (초대 URL별 Guest ID 유지)
CREATE TABLE IF NOT EXISTS guest_sessions (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    invite_token VARCHAR(64) NOT NULL,
    guest_id    VARCHAR(32) NOT NULL COMMENT 'Guest-XXXXXX 형식',
    guest_nickname VARCHAR(100),
    created_at  TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    expires_at  TIMESTAMP(3) NOT NULL,
    INDEX idx_guest_sessions_invite_token (invite_token),
    INDEX idx_guest_sessions_guest_id (guest_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
