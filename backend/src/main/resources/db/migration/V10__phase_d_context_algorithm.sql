-- Phase D - 컨텍스트 알고리즘 신규 컬럼
-- 권위본: shared/docs/policies/context-algorithm.md §4.1

ALTER TABLE sessions
    ADD COLUMN user_state_history JSON NULL COMMENT 'Phase D - UserState 전이 이력',
    ADD COLUMN issue_context JSON NULL COMMENT 'Phase D - 누적 이슈 컨텍스트',
    ADD COLUMN question_queue_a JSON NULL COMMENT 'Phase D - A에게 물을 질문 PQ',
    ADD COLUMN question_queue_b JSON NULL COMMENT 'Phase D - B에게 물을 질문 PQ';

-- 인덱스 불필요 — 모든 접근은 PK로 세션 조회 후 deserialize.
-- current_focus 컬럼은 PR-3에서 issue_context.headline과 동기화. 여기서는 변경 없음.
