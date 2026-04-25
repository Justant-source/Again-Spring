-- 비밀번호 재설정 토큰
CREATE TABLE IF NOT EXISTS password_reset_tokens (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  email VARCHAR(255) NOT NULL,
  token VARCHAR(64) NOT NULL UNIQUE,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  expires_at TIMESTAMP(3) NOT NULL,
  used BOOLEAN NOT NULL DEFAULT FALSE,
  INDEX idx_prt_token (token),
  INDEX idx_prt_email (email),
  INDEX idx_prt_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- JWT 블랙리스트
CREATE TABLE IF NOT EXISTS revoked_tokens (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  jti VARCHAR(64) NOT NULL UNIQUE,
  user_id VARCHAR(64),
  revoked_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  expires_at TIMESTAMP(3) NOT NULL,
  INDEX idx_rt_jti (jti),
  INDEX idx_rt_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
