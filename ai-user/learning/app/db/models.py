import logging
from app.db.session import get_db

logger = logging.getLogger(__name__)

EXAMPLE_BANK_DDL = """
CREATE TABLE IF NOT EXISTS example_bank (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    content LONGTEXT NOT NULL,
    content_type VARCHAR(16) NOT NULL,
    category VARCHAR(32),
    source VARCHAR(32) NOT NULL,
    quality_score DECIMAL(4,2),
    embedding VECTOR(1024) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT NOW(3),
    KEY idx_type_cat (content_type, category),
    KEY idx_source (source)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
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
            # Add VECTOR INDEX separately (cannot be in CREATE TABLE)
            cur.execute(VECTOR_INDEX_CHECK_SQL)
            row = cur.fetchone()
            if row and row["cnt"] == 0:
                try:
                    cur.execute(VECTOR_INDEX_DDL)
                    logger.info("VECTOR INDEX idx_emb created on example_bank")
                except Exception as e:
                    logger.warning(f"VECTOR INDEX creation skipped: {e}")
        conn.commit()
    logger.info("example_bank and crawl_log tables ready")
