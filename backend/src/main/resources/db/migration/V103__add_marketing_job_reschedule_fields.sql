-- Add reschedule tracking fields to marketing_job table
ALTER TABLE marketing_job
ADD COLUMN scheduled_publish_at DATETIME(6) NULL COMMENT '예약된 발행 시각',
ADD COLUMN rescheduled_count INT DEFAULT 0 COMMENT '이월된 횟수',
ADD COLUMN rescheduled_reason VARCHAR(255) NULL COMMENT '이월 사유',
ADD COLUMN original_scheduled_at DATETIME(6) NULL COMMENT '원래 예약 시각 (이월 추적용)',
ADD COLUMN last_rescheduled_at DATETIME(6) NULL COMMENT '마지막 이월 시각';
