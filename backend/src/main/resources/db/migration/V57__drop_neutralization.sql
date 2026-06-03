-- V57: 중립화(neutralization) 제거
-- 글은 사용자 원문 그대로 즉시 VOTING으로 등록되므로
-- 기존 NEUTRALIZING 상태 행을 VOTING으로 백필하고
-- null인 body_published / title 을 원문으로 채운다.

UPDATE posts SET status = 'VOTING'        WHERE status = 'NEUTRALIZING';
UPDATE posts SET body_published = body_raw WHERE body_published IS NULL OR body_published = '';
UPDATE posts SET title = user_title        WHERE (title IS NULL OR title = '') AND user_title IS NOT NULL;
