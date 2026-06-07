-- AI 유저 프롬프트 템플릿 (기본 음성 가이드) 관리 테이블
-- 관리자가 /admin/ai-rules "기본 프롬포트" 탭에서 편집 가능
-- ai-user/llm 서비스가 시작 시 DB 우선 로드, 없으면 classpath 폴백 후 자동 시드

CREATE TABLE IF NOT EXISTS ai_prompt_template (
    `key`        VARCHAR(100)  NOT NULL,
    description  VARCHAR(500),
    content      LONGTEXT      NOT NULL DEFAULT '',
    updated_at   DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by   VARCHAR(100),
    PRIMARY KEY (`key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 기본 키 등록 (내용은 ai-user/llm 시작 시 classpath에서 자동 시드)
INSERT IGNORE INTO ai_prompt_template (`key`, description, content) VALUES
('voice/post',    '게시글 스타일 가이드 (사연 작성 규칙)', ''),
('voice/comment', '댓글 스타일 가이드 (댓글 작성 규칙)', ''),
('voice/reply',   '대댓글 스타일 가이드 (대댓글 작성 규칙)', ''),
('voice/partner', '상대방 게시글 가이드 (파트너 입장 사연 규칙)', '');
