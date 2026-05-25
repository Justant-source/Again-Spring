-- V36: marketing_system 가상 유저 시드 (마케팅 시뮬레이션 전용)
-- DO NOT DELETE — required for marketing simulations
-- 이 유저는 마케팅 시뮬레이션에서 가상 당사자 A의 세션 소유자로 사용됩니다.
-- testRun=true 세션을 통해 일반 사용자 통계와 격리됩니다.
INSERT IGNORE INTO users (id, nickname, is_guest, must_change_password, roles, onboarding_completed_at, created_at, updated_at)
VALUES ('marketing_system', '시뮬레이션-가상사용자', FALSE, FALSE, '[]', NOW(), NOW(), NOW());
