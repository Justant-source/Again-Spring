"""Unit tests for crawl ingest concurrency lock helpers."""
from unittest.mock import MagicMock, patch

from app.api.crawl import _CRAWL_INGEST_LOCK_TIMEOUT_SEC, _crawl_ingest_lock_name


def test_crawl_ingest_lock_name_normalized():
    assert _crawl_ingest_lock_name("BLIND") == "ai_learning_crawl_ingest:blind"
    assert _crawl_ingest_lock_name("natepan") == "ai_learning_crawl_ingest:natepan"
    assert len(_crawl_ingest_lock_name("blind")) <= 64  # MariaDB GET_LOCK name limit
    assert _CRAWL_INGEST_LOCK_TIMEOUT_SEC == 120


def test_ingest_skips_url_present_after_lock_reload():
    """Under lock, a URL inserted by an overlapping crawl must be skipped."""
    from app.api import crawl as crawl_mod

    rows = [
        (
            "body",
            "POST",
            "WORK",
            "blind",
            0.9,
            "casual",
            "title",
            "https://blind/dup",
            None,
            None,
            None,
            None,
            None,
            None,
            0.9,
            "[0.1]",
        )
    ]

    mock_cur = MagicMock()
    # GET_LOCK → acquired=1, then SELECT source_url returns the competing insert
    mock_cur.fetchone.side_effect = [{"acquired": 1}]
    mock_cur.fetchall.return_value = [{"source_url": "https://blind/dup"}]

    mock_conn = MagicMock()
    mock_conn.cursor.return_value.__enter__.return_value = mock_cur
    mock_conn.cursor.return_value.__exit__.return_value = False

    saved = 0
    skipped_dupes = 0
    _URL_IDX = 7
    lock_name = _crawl_ingest_lock_name("blind")

    with patch.object(crawl_mod, "get_db") as gd:
        gd.return_value.__enter__.return_value = mock_conn
        gd.return_value.__exit__.return_value = False
        with crawl_mod.get_db() as conn:
            with conn.cursor() as cur:
                cur.execute(
                    "SELECT GET_LOCK(%s, %s) AS acquired",
                    (lock_name, _CRAWL_INGEST_LOCK_TIMEOUT_SEC),
                )
                assert (cur.fetchone() or {}).get("acquired") == 1
                try:
                    cur.execute(
                        "SELECT source_url FROM example_bank "
                        "WHERE LOWER(source)=%s AND source_url IS NOT NULL",
                        ("blind",),
                    )
                    locked_urls = {row["source_url"] for row in cur.fetchall()}
                    for params in rows:
                        source_url = params[_URL_IDX]
                        if source_url is not None and source_url in locked_urls:
                            skipped_dupes += 1
                            continue
                        saved += 1
                finally:
                    cur.execute("SELECT RELEASE_LOCK(%s)", (lock_name,))

    assert saved == 0
    assert skipped_dupes == 1
    release_calls = [
        c for c in mock_cur.execute.call_args_list if c.args and "RELEASE_LOCK" in str(c.args[0])
    ]
    assert len(release_calls) == 1
