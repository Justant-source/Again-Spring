-- V17: 사용자 동의 시각 컬럼 추가 (기존 회원은 NULL → 다음 로그인 시 재동의 모달)
ALTER TABLE users
    ADD COLUMN terms_agreed_at      DATETIME(6) NULL COMMENT '이용약관 동의 시각',
    ADD COLUMN privacy_agreed_at    DATETIME(6) NULL COMMENT '개인정보 처리방침 동의 시각',
    ADD COLUMN disclaimer_agreed_at DATETIME(6) NULL COMMENT '면책 고지 동의 시각',
    ADD COLUMN marketing_agreed_at  DATETIME(6) NULL COMMENT '마케팅 수신 동의 시각 (선택)';
