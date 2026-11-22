"""source_quality 단위 테스트 — 실측 오염 사례(prod 30일 example_bank)를 픽스처로."""
from datetime import datetime, timedelta
from unittest.mock import MagicMock, patch

from app.services import source_claim
from app.services.source_quality import (
    MIN_USABLE_CHARS,
    assess_source,
    is_truncated_preview,
)

# 블라인드 og:description 미리보기: 250자 + "..." = 253, 줄바꿈 없음
PREVIEW = ("결혼한지 1년 안됐고 강아지를 키우는 중인데 아직 배변훈련이 완벽하지 않음 " * 8)[:250] + "..."

# 사이드바 오염 본문(실제 사례 재구성): 본문 몇 줄 + 광고 + 다른 글 제목 + 같은 제목 반복
CONTAMINATED = "\n".join([
    "친정, 시댁 가까운게 복인듯...",
    "애들 1박으로 재워주실때가 많아서",
    "매일 오전 7시, 단 하루 특가",
    "가연에서 솔로 탈출 가능할까?",
    "(광고) 이영순 삶은 스지 수육 400g",
    "담배가 이별사유가 된다 안된다?",
    "소개팅녀에게 2년만에 연락했음",
    "새벽반 연애 슨배임들을 뵈옵니다",
    "담배가 이별사유가 된다 안된다?",
    "소개팅녀에게 2년만에 연락했음",
])

GOOD_BLIND = (
    "남편이 연애땐 안그랬는데 결혼 후 게임에 빠져서 주말에 하루는 꼭이고\n"
    "어쩔땐 토, 일 점심 먹고 나가서 저녁 먹을때쯤 들어와\n"
    "내가 게임하러 가지 말라고 하면 안가긴 하는데,\n"
    "집에서도 각자 폰보고 티비보고 하니까 그냥 게임 하고 오라고 보내주는 편인데\n"
    "다들 이렇게 사는건가 라는 생각이 문득 드네.. 지금두 남편 게임하러 나감"
)


def test_blind_preview_is_truncated():
    assert len(PREVIEW) == 253
    assert is_truncated_preview(PREVIEW, "blind")
    ok, why = assess_source("t", PREVIEW, "blind")
    assert not ok and "truncated_preview" in why


def test_natepan_ellipsis_ending_is_not_truncation():
    body = ("남자친구랑 데이트비용 문제로 계속 다투게 돼요. " * 8).strip()[:250] + "..."
    ok, why = assess_source("t", body, "natepan")
    assert ok, why


def test_blind_ellipsis_with_linebreaks_or_other_length_is_kept():
    # 줄바꿈이 있으면 미리보기가 아니다 (<br> 본문 추출본)
    multi = GOOD_BLIND + "\n그래서 고민이야..."
    assert assess_source("t", multi, "blind")[0]
    # 길이 대역 밖(사람이 쓴 긴 말줄임 글)
    long_ = ("그날 이후로 계속 생각이 많아졌다. " * 30) + "..."
    assert not is_truncated_preview(long_, "blind")
    assert assess_source("t", long_, "blind")[0]


def test_contaminated_sidebar_rejected_with_reasons():
    ok, why = assess_source("t", CONTAMINATED, "blind")
    assert not ok
    assert "ad_marker" in why and "sidebar_boilerplate" in why and "duplicate_lines" in why


def test_ad_marker_alone_rejects():
    text = GOOD_BLIND + "\n(광고) 오휘 미라클 모이스처 스킨 150ml"
    ok, why = assess_source("t", text, "blind")
    assert not ok and "ad_marker" in why


def test_company_label_line_flagged_only_in_multiline():
    text = GOOD_BLIND + "\n유나이티드파슬서비스코리아 ·"
    ok, why = assess_source("t", text, "blind")
    assert not ok and "sidebar_boilerplate" in why


def test_too_short_rejected_and_threshold_boundary():
    ok, why = assess_source("t", "한 줄 푸념이야 " * 5, "natepan")
    assert not ok and why == ["too_short"]
    edge = "가" * MIN_USABLE_CHARS
    assert assess_source("t", edge, "natepan")[0]
    assert not assess_source("t", edge[:-1], "natepan")[0]


def test_empty():
    assert assess_source(None, "  ", "blind") == (False, ["empty"])


def test_clean_multiline_story_passes_and_few_repeated_short_lines_ok():
    assert assess_source("t", GOOD_BLIND, "blind") == (True, [])
    # 짧은(8자 미만) 추임새 줄 반복은 중복 판정 대상이 아니다
    text = GOOD_BLIND + "\nㅠㅠ\nㅠㅠ\n하..\n하.."
    assert assess_source("t", text, "blind")[0]


def test_single_dup_line_small_share_not_rejected():
    text = GOOD_BLIND + "\n정말 어떡하면 좋을지 모르겠어요\n정말 어떡하면 좋을지 모르겠어요"
    assert assess_source("t", text, "natepan")[0]


def test_sql_percent_signs_escaped_for_param_binding():
    """2026-08-22 사고: SQL 문자열에 이스케이프 안 된 % 가 있으면 claim 전체가 깨진다."""
    import re

    sql = source_claim._SELECT_CANDIDATE_SQL.format(category_clause="", exclude_clause="")
    stripped = sql.replace("%s", "").replace("%%", "")
    assert "%" not in stripped
    # 실제 pymysql 방식 포맷이 예외 없이 되는지
    sql % ("blind", 14, 40)
    assert re.search(r"NOT LIKE '%%\(광고\)%%'", sql)


def _fake_db(cursor):
    gd = MagicMock()
    gd.return_value.__enter__.return_value.cursor.return_value.__enter__.return_value = cursor
    gd.return_value.__exit__.return_value = False
    return gd


def _row(i, content, pct=1.0):
    return {"id": i, "content": content, "source": "blind", "title": "t", "source_url": f"u{i}",
            "popularity_pct": pct, "category": "MARRIED"}


def _claim(select_side_effect, reserve=True):
    cur = MagicMock()
    with patch.object(source_claim, "get_db", _fake_db(cur)), \
         patch.object(source_claim, "_select_candidates", side_effect=select_side_effect) as sel, \
         patch.object(source_claim, "_mark_unusable") as mark, \
         patch.object(source_claim, "_soft_reserve", return_value=reserve) as res:
        item = source_claim.claim_popular_source(
            source="blind", reservation_key="k", reserve_until=datetime(2030, 1, 1), category="MARRIED",
        )
    return item, sel, mark, res


def test_claim_batch_skips_unusable_and_takes_next_usable_in_one_query():
    rows = [_row(1, PREVIEW, 0.9), _row(2, CONTAMINATED, 0.8), _row(3, GOOD_BLIND, 0.7)]
    item, sel, mark, res = _claim(lambda *a, **k: rows)
    assert item["id"] == 3
    assert sel.call_count == 1  # 후보 쿼리는 한 번
    res.assert_called_once()  # 불량 후보는 예약조차 하지 않는다
    marked = mark.call_args.args[1]
    assert [e[0] for e in marked] == [1, 2]
    assert all(e[1].startswith("quality:") for e in marked)


def test_claim_all_unusable_returns_none_and_records_rows():
    rows = [_row(i, PREVIEW) for i in range(1, 6)]
    # 5건 < 배치 크기 → 윈도우당 쿼리 1회, 14일/30일 두 윈도우
    seen = []

    def sel(cur, src, days, exclude, cats=None, limit=None):
        seen.append(list(exclude))
        return [r for r in rows if r["id"] not in exclude]

    item, selm, mark, res = _claim(sel)
    assert item is None
    res.assert_not_called()
    assert selm.call_count == 2
    assert [e[0] for e in mark.call_args_list[0].args[1]] == [1, 2, 3, 4, 5]


def test_claim_fetches_second_batch_when_first_is_all_unusable():
    n = source_claim._CLAIM_BATCH_SIZE
    first = [_row(i, PREVIEW) for i in range(1, n + 1)]
    second = [_row(999, GOOD_BLIND, 0.1)]
    batches = iter([first, second])
    item, sel, mark, res = _claim(lambda *a, **k: next(batches))
    assert item["id"] == 999
    assert sel.call_count == 2


def test_claim_race_loss_moves_to_next_candidate_in_batch():
    rows = [_row(1, GOOD_BLIND, 0.9), _row(2, GOOD_BLIND, 0.8)]
    cur = MagicMock()
    with patch.object(source_claim, "get_db", _fake_db(cur)), \
         patch.object(source_claim, "_select_candidates", return_value=rows) as sel, \
         patch.object(source_claim, "_mark_unusable"), \
         patch.object(source_claim, "_soft_reserve", side_effect=[False, True]) as res:
        item = source_claim.claim_popular_source(
            source="blind", reservation_key="k", reserve_until=datetime(2030, 1, 1),
        )
    assert item["id"] == 2
    assert sel.call_count == 1


def test_mark_unusable_never_overwrites_existing_reservation():
    cur = MagicMock()
    source_claim._mark_unusable(cur, [(7, "quality:truncated_preview"), (3, "x" * 300)])
    (del_sql, del_params), (ins_sql, ins_params) = [c.args for c in cur.execute.call_args_list]
    # 만료된 SOFT 만 지운다 — COMMITTED/REJECTED/살아있는 SOFT 는 남는다
    assert "status = %s AND reserve_until <= NOW(3)" in " ".join(del_sql.split())
    assert del_params == [3, 7, "SOFT"]
    # 남은 행은 PK 충돌로 건드리지 않는다
    assert "INSERT IGNORE" in ins_sql
    assert "ON DUPLICATE" not in ins_sql
    assert ins_params[:4] == [3, "x" * 128, "UNUSABLE", "2099-12-31 00:00:00"]
    assert ins_params[4:8] == [7, "quality:truncated_preview", "UNUSABLE", "2099-12-31 00:00:00"]


def test_unusable_does_not_block_same_url_sibling():
    now = datetime(2026, 1, 1)
    rows = [
        {"id": 1, "content": GOOD_BLIND, "source": "blind", "content_type": "POST", "source_url": "same",
         "popularity_pct": 0.9, "category": "MARRIED", "created_at": now},
        {"id": 2, "content": GOOD_BLIND, "source": "blind", "content_type": "POST", "source_url": "same",
         "popularity_pct": 0.8, "category": "MARRIED", "created_at": now},
    ]
    res = {1: {"status": "UNUSABLE", "reserve_until": datetime(2099, 12, 31)}}
    out = source_claim.filter_claim_candidates(
        rows, source="blind", used_example_ids=set(), reservations=res,
        window_start=now - timedelta(days=1), now=now,
    )
    assert [r["id"] for r in out] == [2]  # 형제는 청구 가능, UNUSABLE 행 자신은 제외
    assert source_claim.is_reservation_blocking("UNUSABLE", datetime(2099, 12, 31), now) is False


def test_candidate_sql_excludes_unusable_row_level_only():
    sql = source_claim._SELECT_CANDIDATE_SQL
    assert "u.example_id = eb.id AND u.status = 'UNUSABLE'" in sql
    assert "r.status IN ('COMMITTED', 'REJECTED')" in sql  # 가족 차단 목록에 UNUSABLE 없음
    assert "FOR UPDATE" not in sql


def test_soft_reserve_tolerates_unusable_sibling_pk_collision():
    from pymysql.err import IntegrityError
    cur = MagicMock()
    cur.fetchone.side_effect = [
        {"source_url": "u"},  # _sibling_ids_for_url
        None, None,           # family not blocked
        {"status": "UNUSABLE"},  # collision lookup for sibling 20
    ]
    cur.fetchall.return_value = [{"id": 10}, {"id": 20}]

    def execute(sql, params=None):
        if "INSERT" in sql and params[0] == 20:
            raise IntegrityError(1062, "dup")

    cur.execute.side_effect = execute
    assert source_claim._soft_reserve(cur, 10, "k", datetime(2030, 1, 1)) is True


def test_soft_reserve_fails_on_real_race_for_sibling():
    from pymysql.err import IntegrityError
    cur = MagicMock()
    cur.fetchone.side_effect = [{"source_url": "u"}, None, None, {"status": "SOFT"}]
    cur.fetchall.return_value = [{"id": 10}, {"id": 20}]

    def execute(sql, params=None):
        if "INSERT" in sql and params[0] == 20:
            raise IntegrityError(1062, "dup")

    cur.execute.side_effect = execute
    assert source_claim._soft_reserve(cur, 10, "k", datetime(2030, 1, 1)) is False
