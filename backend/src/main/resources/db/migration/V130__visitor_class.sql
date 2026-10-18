-- 방문자 분류 도입 (2026-10-18)
--
-- 배경: visit_events의 사람/봇 이분법이 오염됐다. 30일 "사람" 1,117건 중 84%가 우리 ASM 캡처였고,
-- 약 150건은 구글 링크 안전검사(Google-Safety)가 URL을 연 직후 해외 IP의 브라우저로 다시
-- 들어온 방문(링크검사 프록시)이었다. is_bot은 유지하되 visitor_class/class_reason을 근거로 파생한다.
-- 분류 규칙의 SSOT는 VisitorClassifier.java이고, 아래 백필은 같은 규칙의 SQL 사본이다.
--
-- 멱등: 컬럼/인덱스는 IF NOT EXISTS, 백필은 visitor_class IS NULL 행만 건드린다.
-- 이 파일의 백필 구간은 VisitorClassBackfillIT가 그대로 읽어 실행한다(마커 행 이하).

ALTER TABLE visit_events
    ADD COLUMN IF NOT EXISTS visitor_class VARCHAR(32) NULL AFTER is_bot,
    ADD COLUMN IF NOT EXISTS class_reason VARCHAR(80) NULL AFTER visitor_class;

CREATE INDEX IF NOT EXISTS idx_visit_events_class_time ON visit_events (visitor_class, occurred_at);

-- ==== BACKFILL ====

-- 1) UA 기반 봇 분류 (SELF > SCANNER > CRAWLER > SOCIAL_PREVIEW > 범용 crawler > AUTOMATION)
UPDATE visit_events
SET visitor_class = CASE
        WHEN LOWER(user_agent) LIKE '%againspringcapture%' OR LOWER(user_agent) = 'mozilla/5.0 (x11; linux x86_64) applewebkit/537.36 (khtml, like gecko) chrome/125.0.0.0 safari/537.36' THEN 'BOT_SELF'
        WHEN LOWER(user_agent) REGEXP 'google-safety|paloaltonetworks|palo alto networks|censys|masscan|zgrab|expanse' THEN 'BOT_SCANNER'
        WHEN LOWER(user_agent) REGEXP 'googlebot|bingbot|yeti|duckduckbot|baiduspider|yandex|ahrefsbot|semrushbot|mj12bot|dotbot|petalbot|trendictionbot|applebot|linkfluence' THEN 'BOT_CRAWLER'
        WHEN LOWER(user_agent) REGEXP 'twitterbot|facebookexternalhit|linkedinbot|telegrambot|discordbot|slackbot|whatsapp|kakaotalk-scrap|embedly|quora link preview|pinterestbot|redditbot' THEN 'BOT_SOCIAL_PREVIEW'
        WHEN LOWER(user_agent) REGEXP 'bot|spider|crawler|crawl|slurp' THEN 'BOT_CRAWLER'
        WHEN LOWER(user_agent) REGEXP 'curl/|wget|python-requests|python-httpx|go-http-client|java/|okhttp|axios|node-fetch|libwww-perl|httpclient|headlesschrome|phantomjs|puppeteer|playwright|lighthouse' THEN 'BOT_AUTOMATION'
        WHEN LOWER(user_agent) NOT LIKE 'mozilla/%' THEN 'BOT_AUTOMATION'
        ELSE NULL END,
    class_reason  = CASE
        WHEN LOWER(user_agent) LIKE '%againspringcapture%' OR LOWER(user_agent) = 'mozilla/5.0 (x11; linux x86_64) applewebkit/537.36 (khtml, like gecko) chrome/125.0.0.0 safari/537.36' THEN 'bf:self'
        WHEN LOWER(user_agent) REGEXP 'google-safety|paloaltonetworks|palo alto networks|censys|masscan|zgrab|expanse' THEN 'bf:scanner-ua'
        WHEN LOWER(user_agent) REGEXP 'googlebot|bingbot|yeti|duckduckbot|baiduspider|yandex|ahrefsbot|semrushbot|mj12bot|dotbot|petalbot|trendictionbot|applebot|linkfluence' THEN 'bf:crawler-ua'
        WHEN LOWER(user_agent) REGEXP 'twitterbot|facebookexternalhit|linkedinbot|telegrambot|discordbot|slackbot|whatsapp|kakaotalk-scrap|embedly|quora link preview|pinterestbot|redditbot' THEN 'bf:social-preview-ua'
        WHEN LOWER(user_agent) REGEXP 'bot|spider|crawler|crawl|slurp' THEN 'bf:crawler-ua'
        WHEN LOWER(user_agent) REGEXP 'curl/|wget|python-requests|python-httpx|go-http-client|java/|okhttp|axios|node-fetch|libwww-perl|httpclient|headlesschrome|phantomjs|puppeteer|playwright|lighthouse' THEN 'bf:automation-ua'
        WHEN LOWER(user_agent) NOT LIKE 'mozilla/%' THEN 'bf:automation-ua'
        ELSE NULL END,
    is_bot = 1
WHERE visitor_class IS NULL
  AND user_agent IS NOT NULL
  AND TRIM(user_agent) <> ''
  AND (CASE
        WHEN LOWER(user_agent) LIKE '%againspringcapture%' OR LOWER(user_agent) = 'mozilla/5.0 (x11; linux x86_64) applewebkit/537.36 (khtml, like gecko) chrome/125.0.0.0 safari/537.36' THEN 'BOT_SELF'
        WHEN LOWER(user_agent) REGEXP 'google-safety|paloaltonetworks|palo alto networks|censys|masscan|zgrab|expanse' THEN 'BOT_SCANNER'
        WHEN LOWER(user_agent) REGEXP 'googlebot|bingbot|yeti|duckduckbot|baiduspider|yandex|ahrefsbot|semrushbot|mj12bot|dotbot|petalbot|trendictionbot|applebot|linkfluence' THEN 'BOT_CRAWLER'
        WHEN LOWER(user_agent) REGEXP 'twitterbot|facebookexternalhit|linkedinbot|telegrambot|discordbot|slackbot|whatsapp|kakaotalk-scrap|embedly|quora link preview|pinterestbot|redditbot' THEN 'BOT_SOCIAL_PREVIEW'
        WHEN LOWER(user_agent) REGEXP 'bot|spider|crawler|crawl|slurp' THEN 'BOT_CRAWLER'
        WHEN LOWER(user_agent) REGEXP 'curl/|wget|python-requests|python-httpx|go-http-client|java/|okhttp|axios|node-fetch|libwww-perl|httpclient|headlesschrome|phantomjs|puppeteer|playwright|lighthouse' THEN 'BOT_AUTOMATION'
        WHEN LOWER(user_agent) NOT LIKE 'mozilla/%' THEN 'BOT_AUTOMATION'
        ELSE NULL END) IS NOT NULL;

-- 2) UA 없는 과거 행: 사람 취급이던 것은 UNKNOWN_LEGACY(is_bot=0 유지), 이미 봇이던 것은 AUTOMATION
UPDATE visit_events
SET visitor_class = IF(is_bot = 0, 'UNKNOWN_LEGACY', 'BOT_AUTOMATION'),
    class_reason  = IF(is_bot = 0, 'bf:ua-null-legacy', 'bf:ua-missing')
WHERE visitor_class IS NULL
  AND (user_agent IS NULL OR TRIM(user_agent) = '');

-- 3) 나머지는 브라우저 UA = 사람. 국가로 나눈다.
UPDATE visit_events
SET visitor_class = CASE
        WHEN country IS NULL OR country = '' THEN 'HUMAN_UNKNOWN_GEO'
        WHEN UPPER(country) = 'KR' THEN 'HUMAN_KR'
        ELSE 'HUMAN_OVERSEAS' END,
    class_reason  = CASE
        WHEN country IS NULL OR country = '' THEN 'bf:geo-none'
        WHEN UPPER(country) = 'KR' THEN 'bf:geo-KR'
        ELSE 'bf:geo-overseas' END,
    is_bot = 0
WHERE visitor_class IS NULL;

-- 4) 링크검사 프록시 의심 (보수적): 이번 백필로 HUMAN_OVERSEAS가 된 행 중 referrer가 없고,
--    같은 utm_campaign(없으면 같은 path의 utm 없는 행)의 BOT_SCANNER 행이 직전 10초 안에 있던 것.
UPDATE visit_events v
SET v.visitor_class = 'SUSPECT_SCANNER_PROXY',
    v.class_reason  = 'bf:scanner<=10s,no-referrer,overseas',
    v.is_bot = 1
WHERE v.visitor_class = 'HUMAN_OVERSEAS'
  AND v.class_reason = 'bf:geo-overseas'
  AND (v.referrer IS NULL OR v.referrer = '')
  AND EXISTS (
      SELECT 1 FROM (SELECT id, utm_campaign, path, occurred_at FROM visit_events WHERE visitor_class = 'BOT_SCANNER') s
      WHERE s.occurred_at <= v.occurred_at
        AND s.occurred_at >= v.occurred_at - INTERVAL 10 SECOND
        AND ((v.utm_campaign IS NOT NULL AND s.utm_campaign = v.utm_campaign)
             OR (v.utm_campaign IS NULL AND s.utm_campaign IS NULL AND s.path = v.path))
  );
