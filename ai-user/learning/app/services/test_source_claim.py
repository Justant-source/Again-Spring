"""Unit tests for popularity-based source claim (SQL-light / pure logic)."""
from datetime import datetime, timedelta
from unittest.mock import MagicMock, patch

import pytest

from app.services.source_claim import (
    ALLOWED_SOURCES,
    bank_categories_for_plaza,
    commit_source,
    filter_claim_candidates,
    is_allowed_source,
    is_reservation_blocking,
    parse_reserve_until,
    release_source,
    row_to_claimed_item,
    window_attempts,
)


def test_allowed_sources_only_blind_natepan():
    assert is_allowed_source("blind") is True
    assert is_allowed_source("NATEPAN") is True
    assert is_allowed_source("blind ") is True
    assert is_allowed_source("reddit") is False
    assert is_allowed_source("SELF_GENERATED") is False
    assert ALLOWED_SOURCES == frozenset({"blind", "natepan"})


def test_window_attempts_expands_once():
    assert window_attempts(14, 30) == [14, 30]
    assert window_attempts(14, 14) == [14]
    assert window_attempts(30, 14) == [30]  # expand not larger → no second try
    assert window_attempts(7, 30) == [7, 30]


def test_reservation_blocking_committed_and_active_soft():
    now = datetime(2026, 8, 5, 12, 0, 0)
    assert is_reservation_blocking("COMMITTED", now - timedelta(days=1), now) is True
    assert is_reservation_blocking("SOFT", now + timedelta(hours=1), now) is True
    assert is_reservation_blocking("SOFT", now - timedelta(seconds=1), now) is False
    assert is_reservation_blocking(None, None, now) is False


def _sample_rows(now: datetime):
    return [
        {
            "id": 1,
            "content": "low",
            "content_type": "POST",
            "source": "blind",
            "source_url": "https://blind/1",
            "popularity_pct": 0.2,
            "created_at": now - timedelta(days=2),
            "title": "t1",
            "category": "marriage",
        },
        {
            "id": 2,
            "content": "high",
            "content_type": "POST",
            "source": "blind",
            "source_url": "https://blind/2",
            "popularity_pct": 0.95,
            "created_at": now - timedelta(days=1),
            "title": "t2",
            "category": "marriage",
        },
        {
            "id": 3,
            "content": "other source",
            "content_type": "POST",
            "source": "natepan",
            "source_url": "https://nate/3",
            "popularity_pct": 0.99,
            "created_at": now - timedelta(days=1),
            "title": "t3",
            "category": "FRIEND",
        },
        {
            "id": 4,
            "content": "comment",
            "content_type": "COMMENT",
            "source": "blind",
            "source_url": "https://blind/4",
            "popularity_pct": 0.99,
            "created_at": now - timedelta(days=1),
            "title": None,
            "category": "marriage",
        },
        {
            "id": 5,
            "content": "no url",
            "content_type": "POST",
            "source": "blind",
            "source_url": None,
            "popularity_pct": 0.99,
            "created_at": now - timedelta(days=1),
            "title": None,
            "category": "marriage",
        },
        {
            "id": 6,
            "content": "no pct",
            "content_type": "POST",
            "source": "blind",
            "source_url": "https://blind/6",
            "popularity_pct": None,
            "created_at": now - timedelta(days=1),
            "title": None,
            "category": "marriage",
        },
        {
            "id": 7,
            "content": "old",
            "content_type": "POST",
            "source": "blind",
            "source_url": "https://blind/7",
            "popularity_pct": 0.99,
            "created_at": now - timedelta(days=20),
            "title": "old",
            "category": "marriage",
        },
        {
            "id": 8,
            "content": "romance high",
            "content_type": "POST",
            "source": "blind",
            "source_url": "https://blind/8",
            "popularity_pct": 0.97,
            "created_at": now - timedelta(days=1),
            "title": "연애",
            "category": "romance",
        },
        {
            "id": 9,
            "content": "friend nate",
            "content_type": "POST",
            "source": "natepan",
            "source_url": "https://nate/9",
            "popularity_pct": 0.9,
            "created_at": now - timedelta(days=1),
            "title": "친구",
            "category": "FRIEND",
        },
    ]


def test_bank_categories_for_plaza_maps_blind_and_natepan():
    assert bank_categories_for_plaza("COUPLE") == ("COUPLE", "romance")
    assert bank_categories_for_plaza("married") == ("MARRIED", "marriage")
    assert bank_categories_for_plaza("FRIEND") == ("FRIEND",)
    assert bank_categories_for_plaza(None) is None
    assert bank_categories_for_plaza("  ") is None
    assert bank_categories_for_plaza("UNKNOWN") is None


def test_filter_scopes_by_plaza_category_regression():
    """FRIEND claim must not pick blind marriage stories (user-reported mislabel)."""
    now = datetime(2026, 8, 5, 12, 0, 0)
    window_start = now - timedelta(days=14)
    marriage_only = filter_claim_candidates(
        _sample_rows(now),
        source="blind",
        used_example_ids=set(),
        reservations={},
        window_start=window_start,
        now=now,
        bank_categories=bank_categories_for_plaza("FRIEND"),
    )
    assert marriage_only == []

    couple = filter_claim_candidates(
        _sample_rows(now),
        source="blind",
        used_example_ids=set(),
        reservations={},
        window_start=window_start,
        now=now,
        bank_categories=bank_categories_for_plaza("COUPLE"),
    )
    assert [r["id"] for r in couple] == [8]

    married = filter_claim_candidates(
        _sample_rows(now),
        source="blind",
        used_example_ids=set(),
        reservations={},
        window_start=window_start,
        now=now,
        bank_categories=bank_categories_for_plaza("MARRIED"),
    )
    assert [r["id"] for r in married] == [2, 1]

    friend = filter_claim_candidates(
        _sample_rows(now),
        source="natepan",
        used_example_ids=set(),
        reservations={},
        window_start=window_start,
        now=now,
        bank_categories=bank_categories_for_plaza("FRIEND"),
    )
    assert [r["id"] for r in friend] == [3, 9]


def test_row_to_claimed_item_includes_category():
    item = row_to_claimed_item(
        {
            "id": 1,
            "content": "c",
            "source": "blind",
            "title": "t",
            "source_url": "https://x",
            "popularity_pct": 0.5,
            "category": "marriage",
        }
    )
    assert item["category"] == "marriage"
    assert item["id"] == 1


def test_filter_source_and_rank_by_popularity():
    now = datetime(2026, 8, 5, 12, 0, 0)
    window_start = now - timedelta(days=14)
    got = filter_claim_candidates(
        _sample_rows(now),
        source="blind",
        used_example_ids=set(),
        reservations={},
        window_start=window_start,
        now=now,
    )
    ids = [r["id"] for r in got]
    assert ids == [8, 2, 1]  # romance 0.97, marriage 0.95, marriage 0.2
    assert 3 not in ids
    assert 4 not in ids
    assert 5 not in ids
    assert 6 not in ids
    assert 7 not in ids  # outside 14d window


def test_filter_window_expand_includes_older():
    now = datetime(2026, 8, 5, 12, 0, 0)
    rows = _sample_rows(now)
    narrow = filter_claim_candidates(
        rows,
        source="blind",
        used_example_ids=set(),
        reservations={},
        window_start=now - timedelta(days=14),
        now=now,
    )
    wide = filter_claim_candidates(
        rows,
        source="blind",
        used_example_ids=set(),
        reservations={},
        window_start=now - timedelta(days=30),
        now=now,
    )
    assert 7 not in [r["id"] for r in narrow]
    assert 7 in [r["id"] for r in wide]
    # expand still ranks by popularity DESC
    assert [r["id"] for r in wide][0] in (8, 2, 7)


def test_filter_excludes_used_and_active_reservations():
    now = datetime(2026, 8, 5, 12, 0, 0)
    window_start = now - timedelta(days=14)
    got = filter_claim_candidates(
        _sample_rows(now),
        source="blind",
        used_example_ids={2, 8},
        reservations={
            1: {"status": "SOFT", "reserve_until": now + timedelta(hours=2)},
        },
        window_start=window_start,
        now=now,
    )
    assert got == []

    # expired soft does not block
    got2 = filter_claim_candidates(
        _sample_rows(now),
        source="blind",
        used_example_ids={2, 8},
        reservations={
            1: {"status": "SOFT", "reserve_until": now - timedelta(minutes=1)},
        },
        window_start=window_start,
        now=now,
    )
    assert [r["id"] for r in got2] == [1]

    # COMMITTED forever
    got3 = filter_claim_candidates(
        _sample_rows(now),
        source="blind",
        used_example_ids={8},
        reservations={2: {"status": "COMMITTED", "reserve_until": now - timedelta(days=9)}},
        window_start=window_start,
        now=now,
    )
    assert [r["id"] for r in got3] == [1]


def test_filter_excludes_sibling_rows_sharing_source_url():
    """Duplicate crawl rows (same URL, different ids) must collapse to one claim."""
    now = datetime(2026, 8, 9, 18, 37, 0)
    window_start = now - timedelta(days=14)
    url = "https://www.teamblind.com/kr/post/결혼-후회-5g7f5qlk"
    rows = [
        {
            "id": 20417,
            "content": "body",
            "content_type": "POST",
            "source": "blind",
            "source_url": url,
            "popularity_pct": 0.972,
            "created_at": now - timedelta(days=7),
            "title": "결혼 후회",
        },
        {
            "id": 19916,
            "content": "body",
            "content_type": "POST",
            "source": "blind",
            "source_url": url,
            "popularity_pct": 0.972,
            "created_at": now - timedelta(days=7),
            "title": "결혼 후회",
        },
    ]

    # Soft-reserve on one sibling blocks the other
    got = filter_claim_candidates(
        rows,
        source="blind",
        used_example_ids=set(),
        reservations={
            20417: {"status": "SOFT", "reserve_until": now + timedelta(hours=20)},
        },
        window_start=window_start,
        now=now,
    )
    assert got == []

    # Published via example_id still blocks the unused sibling URL
    got2 = filter_claim_candidates(
        rows,
        source="blind",
        used_example_ids={20417},
        reservations={
            20417: {"status": "COMMITTED", "reserve_until": now},
        },
        window_start=window_start,
        now=now,
    )
    assert got2 == []


def test_row_to_claimed_item_camel_case():
    item = row_to_claimed_item({
        "id": 9,
        "content": "body",
        "source": "natepan",
        "title": "제목",
        "source_url": "https://nate/9",
        "popularity_pct": 0.812,
    })
    assert item == {
        "id": 9,
        "content": "body",
        "source": "natepan",
        "title": "제목",
        "sourceUrl": "https://nate/9",
        "score": pytest.approx(0.812),
        "category": None,
    }


def test_parse_reserve_until_z_suffix():
    dt = parse_reserve_until("2026-08-05T15:00:00Z")
    assert dt == datetime(2026, 8, 5, 15, 0, 0)
    assert dt.tzinfo is None


def test_commit_source_key_mismatch_and_success():
    mock_cur = MagicMock()
    mock_conn = MagicMock()
    mock_conn.cursor.return_value.__enter__.return_value = mock_cur
    mock_conn.cursor.return_value.__exit__.return_value = False

    mock_cur.fetchone.return_value = {
        "example_id": 10,
        "reservation_key": "abc",
        "status": "SOFT",
    }
    with patch("app.services.source_claim.get_db") as gd:
        gd.return_value.__enter__.return_value = mock_conn
        gd.return_value.__exit__.return_value = False
        assert commit_source(example_id=10, reservation_key="wrong") == {"status": "key_mismatch"}

    mock_cur.reset_mock()
    mock_cur.fetchone.return_value = {
        "example_id": 10,
        "reservation_key": "abc",
        "status": "SOFT",
    }
    with patch("app.services.source_claim.get_db") as gd:
        gd.return_value.__enter__.return_value = mock_conn
        gd.return_value.__exit__.return_value = False
        assert commit_source(example_id=10, reservation_key="abc") == {"status": "committed"}
        update_sqls = [str(c.args[0]) for c in mock_cur.execute.call_args_list if c.args]
        assert any("UPDATE" in s and "reservation_key" in s for s in update_sqls)


def test_release_source_soft_vs_noop():
    mock_cur = MagicMock()
    mock_conn = MagicMock()
    mock_conn.cursor.return_value.__enter__.return_value = mock_cur
    mock_conn.cursor.return_value.__exit__.return_value = False

    mock_cur.fetchone.return_value = {
        "example_id": 1,
        "reservation_key": "k",
        "status": "SOFT",
    }
    mock_cur.rowcount = 2  # family of duplicate crawl rows
    with patch("app.services.source_claim.get_db") as gd:
        gd.return_value.__enter__.return_value = mock_conn
        gd.return_value.__exit__.return_value = False
        assert release_source(example_id=1, reservation_key="k") == {"status": "released"}
        delete_sqls = [str(c.args[0]) for c in mock_cur.execute.call_args_list if c.args]
        assert any("DELETE" in s and "reservation_key" in s for s in delete_sqls)

    mock_cur.reset_mock()
    mock_cur.fetchone.return_value = {
        "example_id": 1,
        "reservation_key": "other",
        "status": "SOFT",
    }
    mock_cur.rowcount = 0
    with patch("app.services.source_claim.get_db") as gd:
        gd.return_value.__enter__.return_value = mock_conn
        gd.return_value.__exit__.return_value = False
        assert release_source(example_id=1, reservation_key="k") == {"status": "noop"}


def test_soft_reserve_locks_url_family():
    """Concurrent claimants on duplicate crawl rows share one reservation_key family."""
    from app.services.source_claim import _soft_reserve

    mock_cur = MagicMock()
    # source_url lookup, then sibling ids FOR UPDATE, then family_is_blocked checks
    mock_cur.fetchone.side_effect = [
        {"source_url": "https://blind/same"},  # _sibling_ids_for_url
        None,  # posts check
        None,  # reservation check
    ]
    mock_cur.fetchall.return_value = [{"id": 10}, {"id": 20}]

    until = datetime(2026, 8, 11, 12, 0, 0)
    assert _soft_reserve(mock_cur, 10, "res-key", until) is True

    sqls = [str(c.args[0]) for c in mock_cur.execute.call_args_list if c.args]
    assert any("FOR UPDATE" in s and "source_url" in s for s in sqls)
    inserts = [c for c in mock_cur.execute.call_args_list if c.args and "INSERT" in str(c.args[0])]
    assert len(inserts) == 2
    inserted_ids = {c.args[1][0] for c in inserts}
    assert inserted_ids == {10, 20}