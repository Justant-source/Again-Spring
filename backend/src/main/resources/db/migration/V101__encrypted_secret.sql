-- V101: AS 앱 시크릿 AES-256-GCM vault (마케팅 제외 — 마케팅은 ASM credential)
-- Doc-Sync: docs/shared/api/database-schema.md · docs/env/environment-variables.md

CREATE TABLE encrypted_secret (
    secret_key  VARCHAR(128)  NOT NULL COMMENT 'Logical key e.g. jwt.secret, github.pat.Justant-source',
    enc_blob    TEXT          NOT NULL COMMENT 'Base64(iv || ciphertext || gcm_tag) via AesGcmCipher',
    updated_at  TIMESTAMP(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                               ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (secret_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Non-marketing app secrets; master key = AS_SECRET_MASTER_KEY env';
