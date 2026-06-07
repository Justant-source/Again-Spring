-- 글 분석 캐시 — 글 1건당 Haiku 1회 분석 결과를 영구 저장.
-- 게시 후 본문은 불변이므로 캐시 무효화 불필요 (편집 글 edge case는 후속 과제).
-- 50개 페르소나의 모든 좋아요·투표 결정이 이 캐시를 로컬에서 재사용 → 행동당 LLM 토큰 0.
-- posts 테이블 FK 미사용: 파생 캐시이므로 orphan 무해, 서비스 경계 결합 회피.

CREATE TABLE IF NOT EXISTS post_analysis (
    post_id          VARCHAR(32)   NOT NULL,
    author_sympathy  DECIMAL(3,2)  NOT NULL DEFAULT 0.50,  -- 0=작성자 잘못, 0.5=반반, 1=작성자 피해자
    ambiguity        DECIMAL(3,2)  NOT NULL DEFAULT 0.50,  -- 양쪽 주장 팽팽한 정도
    severity         DECIMAL(3,2)  NOT NULL DEFAULT 0.50,  -- 감정적 강도
    topics           JSON          NOT NULL,               -- List<String> 핵심 주제
    emotions         JSON          NOT NULL,               -- List<String> 드러난 감정
    archetype_frame  VARCHAR(64)   NULL,                   -- 인식된 archetype id (best-effort)
    political_hint   VARCHAR(16)   NOT NULL DEFAULT 'neutral',  -- progressive|conservative|neutral
    analyzed_at      DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    correlation_id   VARCHAR(64)   NULL,
    PRIMARY KEY (post_id),
    KEY idx_analyzed_at (analyzed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
