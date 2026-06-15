import logging
from app.db.session import get_db

logger = logging.getLogger(__name__)

EXAMPLE_BANK_DDL = """
CREATE TABLE IF NOT EXISTS example_bank (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    content LONGTEXT NOT NULL,
    content_type VARCHAR(16) NOT NULL,
    category VARCHAR(32),
    topic VARCHAR(16) DEFAULT NULL,
    source VARCHAR(32) NOT NULL,
    quality_score DECIMAL(4,2),
    register VARCHAR(16) DEFAULT NULL COMMENT 'casual|polite|mixed',
    title VARCHAR(512) DEFAULT NULL COMMENT '원본 커뮤니티 글 제목 (신규 크롤부터)',
    source_url VARCHAR(1024) DEFAULT NULL COMMENT '원본 커뮤니티 글 URL',
    embedding VECTOR(1024) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT NOW(3),
    KEY idx_type_cat (content_type, category),
    KEY idx_topic_type (topic, content_type),
    KEY idx_source (source),
    KEY idx_register (register)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
"""

# 기존 테이블에 topic 컬럼이 없으면 추가 (idempotent)
EXAMPLE_BANK_ADD_TOPIC_SQL = """
ALTER TABLE example_bank ADD COLUMN IF NOT EXISTS topic VARCHAR(16) DEFAULT NULL
"""

# 기존 테이블에 register 컬럼이 없으면 추가 (idempotent)
EXAMPLE_BANK_ADD_REGISTER_SQL = """
ALTER TABLE example_bank ADD COLUMN IF NOT EXISTS register VARCHAR(16) DEFAULT NULL COMMENT 'casual|polite|mixed'
"""

# 기존 테이블에 title 컬럼이 없으면 추가 (idempotent) — 원본 비교 기능용
EXAMPLE_BANK_ADD_TITLE_SQL = """
ALTER TABLE example_bank ADD COLUMN IF NOT EXISTS title VARCHAR(512) DEFAULT NULL COMMENT '원본 커뮤니티 글 제목 (신규 크롤부터)'
"""

# 기존 테이블에 source_url 컬럼이 없으면 추가 (idempotent) — 원본 비교 기능용
EXAMPLE_BANK_ADD_SOURCE_URL_SQL = """
ALTER TABLE example_bank ADD COLUMN IF NOT EXISTS source_url VARCHAR(1024) DEFAULT NULL COMMENT '원본 커뮤니티 글 URL'
"""

CRAWL_LOG_DDL = """
CREATE TABLE IF NOT EXISTS crawl_log (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    source VARCHAR(32),
    items_collected INT DEFAULT 0,
    items_saved INT DEFAULT 0,
    status VARCHAR(16),
    error_msg TEXT,
    created_at DATETIME(3) NOT NULL DEFAULT NOW(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
"""

DAILY_TOPIC_DDL = """
CREATE TABLE IF NOT EXISTS daily_topic (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    day DATE NOT NULL COMMENT 'KST 날짜',
    category VARCHAR(32) NOT NULL COMMENT 'COUPLE|MARRIED|FRIEND|FAMILY|WORK|OTHER',
    seed_text LONGTEXT NOT NULL COMMENT '추상화 갈등 시드 (1~2문장, 원문/PII 없음)',
    source_signal VARCHAR(255) COMMENT '반영된 hot_topic 라벨',
    used_count INT NOT NULL DEFAULT 0 COMMENT '오케스트레이터 사용 횟수',
    quality_score DECIMAL(4,2) DEFAULT 0.80,
    embedding VECTOR(1024) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT NOW(3),
    KEY idx_day_cat (day, category),
    KEY idx_used (used_count)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
"""

DAILY_TOPIC_VECTOR_INDEX_CHECK_SQL = """
SELECT COUNT(*) AS cnt FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'daily_topic'
  AND index_name = 'idx_daily_topic_emb'
"""

DAILY_TOPIC_VECTOR_INDEX_DDL = "ALTER TABLE daily_topic ADD VECTOR INDEX idx_daily_topic_emb (embedding)"

VECTOR_INDEX_CHECK_SQL = """
SELECT COUNT(*) AS cnt FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'example_bank'
  AND index_name = 'idx_emb'
"""

VECTOR_INDEX_DDL = "ALTER TABLE example_bank ADD VECTOR INDEX idx_emb (embedding)"


def create_tables():
    with get_db() as conn:
        with conn.cursor() as cur:
            cur.execute(EXAMPLE_BANK_DDL)
            cur.execute(CRAWL_LOG_DDL)
            cur.execute(DAILY_TOPIC_DDL)
            # topic 컬럼 추가 (기존 테이블에도 idempotent — ADD COLUMN IF NOT EXISTS, MariaDB 10.0.2+)
            try:
                cur.execute(EXAMPLE_BANK_ADD_TOPIC_SQL)
                logger.info("example_bank.topic column ensured")
            except Exception as e:
                logger.warning(f"topic column alter skipped (may already exist): {e}")
            # register 컬럼 추가
            try:
                cur.execute(EXAMPLE_BANK_ADD_REGISTER_SQL)
                logger.info("example_bank.register column ensured")
            except Exception as e:
                logger.warning(f"register column alter skipped (may already exist): {e}")
            # title 컬럼 추가 (원본 비교 기능)
            try:
                cur.execute(EXAMPLE_BANK_ADD_TITLE_SQL)
                logger.info("example_bank.title column ensured")
            except Exception as e:
                logger.warning(f"title column alter skipped (may already exist): {e}")
            # source_url 컬럼 추가 (원본 비교 기능)
            try:
                cur.execute(EXAMPLE_BANK_ADD_SOURCE_URL_SQL)
                logger.info("example_bank.source_url column ensured")
            except Exception as e:
                logger.warning(f"source_url column alter skipped (may already exist): {e}")
            # VECTOR INDEX for example_bank
            cur.execute(VECTOR_INDEX_CHECK_SQL)
            row = cur.fetchone()
            if row and row["cnt"] == 0:
                try:
                    cur.execute(VECTOR_INDEX_DDL)
                    logger.info("VECTOR INDEX idx_emb created on example_bank")
                except Exception as e:
                    logger.warning(f"VECTOR INDEX creation skipped: {e}")
            # VECTOR INDEX for daily_topic
            cur.execute(DAILY_TOPIC_VECTOR_INDEX_CHECK_SQL)
            row2 = cur.fetchone()
            if row2 and row2["cnt"] == 0:
                try:
                    cur.execute(DAILY_TOPIC_VECTOR_INDEX_DDL)
                    logger.info("VECTOR INDEX idx_daily_topic_emb created on daily_topic")
                except Exception as e:
                    logger.warning(f"VECTOR INDEX for daily_topic skipped: {e}")
        conn.commit()
    logger.info("example_bank, crawl_log, daily_topic tables ready")
