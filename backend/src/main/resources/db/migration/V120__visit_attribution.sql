-- 유입 계측 확장 (2026-08-29)
--
-- 배경: 런칭 후 30일간 신규 가입 1명. 원인 조사 결과 "클릭 → 방문 → 가입"을 잇는
-- 계측이 없었다. visit_events는 UTM/외부 referrer가 있을 때만 기록해 전체 방문량을
-- 알 수 없었고, users.acquisition_source/campaign 컬럼은 존재하지만 이를 채우는
-- 코드가 한 줄도 없어 전 행이 NULL이었다.
--
-- 이 마이그레이션은 방문 쪽 필드를 넓힌다. 가입 귀속은 AcquisitionAttribution이
-- 기존 users 컬럼을 채우므로 스키마 변경이 필요 없다.

ALTER TABLE visit_events
    -- 30일 쿠키 기반 고유 방문자 식별자. session_key(세션 단위)보다 상위 개념.
    ADD COLUMN visitor_key VARCHAR(64) NULL AFTER session_key,
    -- 봇 판정 근거를 남긴다. 판정 로직이 바뀌어도 과거 행을 재분류할 수 있어야 한다.
    ADD COLUMN user_agent VARCHAR(300) NULL AFTER visitor_key,
    -- 집계는 항상 is_bot=0으로 필터한다. 봇 행도 버리지 않고 남겨 오탐을 추적한다.
    ADD COLUMN is_bot TINYINT(1) NOT NULL DEFAULT 0 AFTER user_agent,
    ADD COLUMN country VARCHAR(8) NULL AFTER is_bot,
    ADD COLUMN device_type VARCHAR(16) NULL AFTER country,
    -- 방문자가 로그인/게스트 상태면 user_id를 남겨 방문 → 투표 → 가입을 잇는다.
    ADD COLUMN user_id VARCHAR(32) NULL AFTER device_type;

CREATE INDEX idx_visit_events_visitor_key ON visit_events (visitor_key);
CREATE INDEX idx_visit_events_bot_time ON visit_events (is_bot, occurred_at);
CREATE INDEX idx_visit_events_source_time ON visit_events (utm_source, occurred_at);

-- 가입 귀속 조회용 (채널별 가입 수 집계)
CREATE INDEX idx_users_acquisition ON users (acquisition_source, created_at);
