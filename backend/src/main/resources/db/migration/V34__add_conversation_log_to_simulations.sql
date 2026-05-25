-- V34: 시뮬레이션 대화 로그 컬럼 추가
-- SimulationOrchestrator가 생성한 A/B/Mediator 대화 내용을 저장
ALTER TABLE marketing_simulations
    ADD COLUMN conversation_log MEDIUMTEXT COMMENT 'Full conversation log (A: / B: / Mediator: turns)';
