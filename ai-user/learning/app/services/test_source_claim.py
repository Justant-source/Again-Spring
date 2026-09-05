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
            "content": "남편이 자꾸 저를 무시하고 일만 바쁘다고 핑계를 댑니다. 저는 육아까지 혼자 하면서 너무 지쳐있는데 남편은 집에 와서도 휴대폰만 봅니다. 이 상황이 계속되면 이혼을 진지하게 생각하게 될 것 같습니다. 어떻게 해야 할까요. 남편과 대화를 시도했지만 좋아질 기미가 보이지 않습니다. 저희 결혼생활이 이대로 끝날까봐 정말 두렵습니다.",
            "content_type": "POST",
            "source": "blind",
            "source_url": "https://blind/1",
            "popularity_pct": 0.2,
            "created_at": now - timedelta(days=2),
            "title": "남편과의 불화",
            "category": "marriage",
            "quality_score": 0.7,
        },
        {
            "id": 2,
            "content": "아내가 저를 무시하는 것 같습니다. 저는 열심히 일해서 가정을 책임지려고 하는데 아내는 제 노력을 인정하지 않습니다. 아이들도 엄마 말만 듣고 저는 무시합니다. 이런 상황이 계속되니까 정말 답답하고 서운합니다. 제가 뭘 잘못한 건지 모르겠습니다. 제 의견도 소중한데 왜 듣지 않을까요. 결혼생활이 너무 외로워집니다.",
            "content_type": "POST",
            "source": "blind",
            "source_url": "https://blind/2",
            "popularity_pct": 0.95,
            "created_at": now - timedelta(days=1),
            "title": "아내와의 갈등",
            "category": "marriage",
            "quality_score": 0.8,
        },
        {
            "id": 3,
            "content": "제 친구가 저를 자꾸 무시합니다. 우리는 오래전부터 알고 지낸 친구인데 최근에 친구가 좋은 일이 생겼다고 해서 축하해주니까 자기는 성공했다고 자랑만 합니다. 그리고 저를 자꾸 깔보는 것 같습니다. 이런 친구와의 관계를 어떻게 정리해야 할지 모르겠습니다. 정말 마음이 아픕니다.",
            "content_type": "POST",
            "source": "natepan",
            "source_url": "https://nate/3",
            "popularity_pct": 0.99,
            "created_at": now - timedelta(days=1),
            "title": "친구와의 갈등",
            "category": "FRIEND",
            "quality_score": 0.7,
        },
        {
            "id": 4,
            "content": "너무 좋아",
            "content_type": "COMMENT",
            "source": "blind",
            "source_url": "https://blind/4",
            "popularity_pct": 0.99,
            "created_at": now - timedelta(days=1),
            "title": None,
            "category": "marriage",
            "quality_score": 0.5,
        },
        {
            "id": 5,
            "content": "no url content placeholder",
            "content_type": "POST",
            "source": "blind",
            "source_url": None,
            "popularity_pct": 0.99,
            "created_at": now - timedelta(days=1),
            "title": None,
            "category": "marriage",
            "quality_score": 0.7,
        },
        {
            "id": 6,
            "content": "no pct placeholder content",
            "content_type": "POST",
            "source": "blind",
            "source_url": "https://blind/6",
            "popularity_pct": None,
            "created_at": now - timedelta(days=1),
            "title": None,
            "category": "marriage",
            "quality_score": 0.7,
        },
        {
            "id": 7,
            "content": "아버지가 자꾸 저한테 간섭합니다. 이미 성인이 된 저에게도 제 삶의 모든 것에 대해서 의견을 주려고 합니다. 저는 독립적으로 살고 싶은데 아버지는 제 선택을 무조건 반대합니다. 이런 상황이 너무 답답하고 서운합니다. 어떻게 아버지와의 관계를 개선할 수 있을까요. 저는 제 인생을 제 방식대로 살고 싶습니다.",
            "content_type": "POST",
            "source": "blind",
            "source_url": "https://blind/7",
            "popularity_pct": 0.99,
            "created_at": now - timedelta(days=20),
            "title": "부모님과의 갈등",
            "category": "marriage",
            "quality_score": 0.75,
        },
        {
            "id": 8,
            "content": "남자친구와의 연애가 힘들어집니다. 우리는 3년을 오래 사귀었는데 최근에 남자친구가 자꾸 저를 무시하는 것 같습니다. 제 의견을 들어주지 않고 항상 자신의 생각만 맞다고 합니다. 연애를 계속해야 할지 헤어져야 할지 정말 고민이 됩니다. 이런 상황이 계속되면 제 마음도 식을 것 같습니다. 저는 남자친구를 정말 사랑했는데 요즘은 서운함을 많이 느낍니다. 어떻게 해야 할까요. 남자친구와 대화를 시도했지만 좋아질 기미가 보이지 않습니다. 저 혼자 노력해도 부족한 것 같고 남자친구는 변할 생각이 없어 보입니다. 정말 힘들고 외롭습니다.",
            "content_type": "POST",
            "source": "blind",
            "source_url": "https://blind/8",
            "popularity_pct": 0.97,
            "created_at": now - timedelta(days=1),
            "title": "남자친구와의 갈등",
            "category": "romance",
            "quality_score": 0.8,
        },
        {
            "id": 9,
            "content": "제 친구가 저한테 자꾸 거짓말을 합니다. 우리는 오래전부터 알고 지낸 친구인데 최근에 친구의 거짓말 때문에 제가 많은 피해를 입었습니다. 정말 마음이 상했고 앞으로 이 친구와의 관계를 어떻게 해야 할지 모르겠습니다. 친구를 믿을 수 없게 되었습니다.",
            "content_type": "POST",
            "source": "natepan",
            "source_url": "https://nate/9",
            "popularity_pct": 0.9,
            "created_at": now - timedelta(days=1),
            "title": "친구와의 신뢰 문제",
            "category": "FRIEND",
            "quality_score": 0.75,
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


def test_filter_excludes_reported_chatter_without_experience():
    """잡담 배제 재설계(2026-08-22, commit 41857752): 구 Phase 2 게이트(길이·품질)는
    실측(확실한 사연 200건 vs OTHER 300건)에서 판별력이 없어 폐기됐다. 현재는
    "전언 형식이면서 1인칭 경험 서술이 전혀 없는 글"만 배제한다
    (looks_reported AND NOT has_experience). 근거는 source_claim.py 주석 참조.
    """
    now = datetime(2026, 8, 22, 12, 0, 0)
    window_start = now - timedelta(days=14)

    rows = [
        {  # id=1: reported ("다고 밝혔다"), 경험 서술 전혀 없음 → BLOCKED
            "id": 1,
            "content": "그녀는 곧 결혼한다고 밝혔다",
            "content_type": "POST",
            "source": "blind",
            "source_url": "https://blind/1",
            "popularity_pct": 0.95,
            "created_at": now - timedelta(days=1),
            "category": "OTHER",
        },
        {  # id=2: reported("다고 밝혔") + 경험 서술("제가 ") 동시 존재 → 통과
            "id": 2,
            "content": "제가 남편이랑 이혼한다고 밝혔다",
            "content_type": "POST",
            "source": "blind",
            "source_url": "https://blind/2",
            "popularity_pct": 0.90,
            "created_at": now - timedelta(days=1),
            "category": "OTHER",
        },
        {  # id=3: 전언 형식 자체가 아님 → looks_reported=False → 통과
            "id": 3,
            "content": "오늘 정말 힘든 하루였다",
            "content_type": "POST",
            "source": "blind",
            "source_url": "https://blind/3",
            "popularity_pct": 0.88,
            "created_at": now - timedelta(days=1),
            "category": "OTHER",
        },
        {  # id=4: reported("인터뷰") + 경험("말했는데") → 통과
            "id": 4,
            "content": "동료가 인터뷰에서 그렇게 말했는데 진짜 웃겼다",
            "content_type": "POST",
            "source": "blind",
            "source_url": "https://blind/4",
            "popularity_pct": 0.87,
            "created_at": now - timedelta(days=1),
            "category": "OTHER",
        },
        {  # id=5: reported("누리꾼"), 경험 서술 없음 → BLOCKED
            "id": 5,
            "content": "누리꾼들 반응이 뜨거웠다",
            "content_type": "POST",
            "source": "blind",
            "source_url": "https://blind/5",
            "popularity_pct": 0.86,
            "created_at": now - timedelta(days=1),
            "category": "OTHER",
        },
    ]

    result = filter_claim_candidates(
        rows,
        source="blind",
        used_example_ids=set(),
        reservations={},
        window_start=window_start,
        now=now,
    )

    ids = [r["id"] for r in result]
    assert set(ids) == {2, 3, 4}, f"Expected {{2, 3, 4}}, got {set(ids)}"
    # Verify they're ranked by popularity DESC
    assert ids == [2, 3, 4]  # 0.90 > 0.88 > 0.87


def test_filter_chatter_exclusion_edge_cases():
    """경계 케이스: 트리거 종류(보도/기록으로)와 마커 등장 순서가 무관함을 검증."""
    now = datetime(2026, 8, 22, 12, 0, 0)
    window_start = now - timedelta(days=14)

    rows = [
        {  # id=1: reported("보도"), 경험 서술 없음 → BLOCKED
            "id": 1,
            "content": "관련 내용이 언론에 보도됐다",
            "content_type": "POST",
            "source": "natepan",
            "source_url": "https://nate/1",
            "popularity_pct": 0.99,
            "created_at": now - timedelta(days=1),
            "category": "OTHER",
        },
        {  # id=2: 경험 마커("내가 ")만 있고 reported 트리거 자체가 없음 → 통과
            "id": 2,
            "content": "내가 어제 겪은 일인데 진짜 황당했다",
            "content_type": "POST",
            "source": "natepan",
            "source_url": "https://nate/2",
            "popularity_pct": 0.98,
            "created_at": now - timedelta(days=1),
            "category": "OTHER",
        },
        {  # id=3: 경험 마커("내가 ")가 reported 트리거("인터뷰")보다 먼저 등장 →
            # 순서와 무관하게 has_experience=True로 통과해야 함
            "id": 3,
            "content": "내가 실은 인터뷰에서 그렇게 말했다",
            "content_type": "POST",
            "source": "natepan",
            "source_url": "https://nate/3",
            "popularity_pct": 0.97,
            "created_at": now - timedelta(days=1),
            "category": "OTHER",
        },
        {  # id=4: reported("기록으로"), 경험 서술 없음 → BLOCKED
            "id": 4,
            "content": "당시 상황이 기록으로 남아있다",
            "content_type": "POST",
            "source": "natepan",
            "source_url": "https://nate/4",
            "popularity_pct": 0.96,
            "created_at": now - timedelta(days=1),
            "category": "OTHER",
        },
    ]

    result = filter_claim_candidates(
        rows,
        source="natepan",
        used_example_ids=set(),
        reservations={},
        window_start=window_start,
        now=now,
    )

    ids = [r["id"] for r in result]
    assert set(ids) == {2, 3}, f"Expected {{2, 3}}, got {set(ids)}"