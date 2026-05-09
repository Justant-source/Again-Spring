-- V18: suhday@naver.com 에 ADMIN 역할 부여 (멱등 — 이미 있으면 추가 안 함)
UPDATE users
SET roles = JSON_ARRAY_APPEND(COALESCE(roles, JSON_ARRAY()), '$', 'ADMIN')
WHERE email = 'suhday@naver.com'
  AND JSON_SEARCH(COALESCE(roles, JSON_ARRAY()), 'one', 'ADMIN') IS NULL;
