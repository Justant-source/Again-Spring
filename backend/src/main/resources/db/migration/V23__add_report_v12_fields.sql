-- V12: Solo 리포트 컨텍스트 반영 필드 추가
ALTER TABLE reports
    ADD COLUMN core_summary      LONGTEXT,
    ADD COLUMN four_stage_flow   JSON,
    ADD COLUMN metaphor_id       VARCHAR(100),
    ADD COLUMN metaphor_display_name VARCHAR(100),
    ADD COLUMN metaphor_reason   LONGTEXT,
    ADD COLUMN nvc_observation   LONGTEXT,
    ADD COLUMN nvc_feeling       LONGTEXT,
    ADD COLUMN nvc_need          LONGTEXT,
    ADD COLUMN nvc_request       LONGTEXT,
    ADD COLUMN recommended_actions JSON,
    ADD COLUMN external_resource_guidance JSON,
    ADD COLUMN status            VARCHAR(20) DEFAULT 'OK';
