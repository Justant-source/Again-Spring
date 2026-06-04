-- 실제 회원(is_guest=0, deleted_at IS NULL)에 대한 닉네임 중복 방지
-- 게스트는 임시 닉네임이 중복될 수 있어 부분 인덱스 적용 불가
-- → 대신 unique 인덱스는 앱 레벨(AuthService)에서 검증하고
--   여기서는 AI 유저 닉네임 선점을 위한 synthetic 마킹만 확인
--
-- AI 유저 100명 닉네임 등록 완료 확인 (synthetic=1)
-- 이 마이그레이션은 V62로 기록만 남기고 데이터 변경은 없음
SELECT COUNT(*) AS ai_users_with_synthetic FROM users WHERE synthetic=1;
