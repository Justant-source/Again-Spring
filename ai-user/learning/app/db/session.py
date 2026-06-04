import os
import pymysql
import pymysql.cursors
from contextlib import contextmanager

DB_CONFIG = {
    "host": os.getenv("DB_HOST", "againspring-mariadb-dev"),
    "port": int(os.getenv("DB_PORT", "3306")),
    "database": os.getenv("DB_NAME", "againspring_dev"),
    "user": os.getenv("DB_USER", "againspring"),
    "password": os.getenv("DB_PASSWORD", ""),
    "charset": "utf8mb4",
    "cursorclass": pymysql.cursors.DictCursor,
    "autocommit": False,
}

@contextmanager
def get_db():
    conn = pymysql.connect(**DB_CONFIG)
    try:
        yield conn
        conn.commit()
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()
