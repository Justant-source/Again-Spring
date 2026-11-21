"""source_quality 단위 테스트 — 실측 오염 사례(prod 30일 example_bank)를 픽스처로."""
from datetime import datetime
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
    sql % ("blind", 14)
    assert re.search(r"NOT LIKE '%%\(광고\)%%'", sql)


def _fake_db(cursor):
    gd = MagicMock()
    gd.return_value.__enter__.return_value.cursor.return_value.__enter__.return_value = cursor
    gd.return_value.__exit__.return_value = False
    return gd


def test_claim_skips_unusable_and_takes_next_usable():
    rows = [
        {"id": 1, "content": PREVIEW, "source": "blind", "title": "a", "source_url": "u1", "popularity_pct": 0.9, "category": "MARRIED"},
        {"id": 2, "content": CONTAMINATED, "source": "blind", "title": "b", "source_url": "u2", "popularity_pct": 0.8, "category": "MARRIED"},
        {"id": 3, "content": GOOD_BLIND, "source": "blind", "title": "c", "source_url": "u3", "popularity_pct": 0.7, "category": "MARRIED"},
    ]
    seen_excludes = []

    def fake_select(cur, src, days, exclude, cats=None):
        seen_excludes.append(list(exclude))
        for r in rows:
            if r["id"] not in exclude:
                return r
        return None

    with patch.object(source_claim, "get_db", _fake_db(MagicMock())), \
         patch.object(source_claim, "_select_candidate", side_effect=fake_select), \
         patch.object(source_claim, "_soft_reserve", return_value=True) as reserve:
        item = source_claim.claim_popular_source(
            source="blind", reservation_key="k", reserve_until=datetime(2030, 1, 1), category="MARRIED",
        )
    assert item["id"] == 3
    reserve.assert_called_once()  # 오염 후보는 예약조차 하지 않는다
    assert seen_excludes[-1] == [1, 2]


def test_claim_quality_skips_do_not_consume_race_budget():
    n = source_claim._CLAIM_ATTEMPTS_PER_WINDOW + 5  # 경합 예산보다 많은 불량 후보
    bad = [{"id": i, "content": PREVIEW, "source": "blind", "title": "x", "source_url": f"u{i}", "popularity_pct": 1.0, "category": "MARRIED"} for i in range(1, n + 1)]
    good = {"id": 999, "content": GOOD_BLIND, "source": "blind", "title": "g", "source_url": "ug", "popularity_pct": 0.1, "category": "MARRIED"}
    rows = bad + [good]

    def fake_select(cur, src, days, exclude, cats=None):
        return next((r for r in rows if r["id"] not in exclude), None)

    with patch.object(source_claim, "get_db", _fake_db(MagicMock())), \
         patch.object(source_claim, "_select_candidate", side_effect=fake_select), \
         patch.object(source_claim, "_soft_reserve", return_value=True):
        item = source_claim.claim_popular_source(
            source="blind", reservation_key="k", reserve_until=datetime(2030, 1, 1),
        )
    assert item["id"] == 999


def test_claim_returns_none_when_only_unusable_and_budget_bounded():
    calls = {"n": 0}

    def fake_select(cur, src, days, exclude, cats=None):
        calls["n"] += 1
        return {"id": calls["n"] + 1000, "content": PREVIEW, "source": "blind", "title": "x", "source_url": "u", "popularity_pct": 1.0, "category": "MARRIED"}

    with patch.object(source_claim, "get_db", _fake_db(MagicMock())), \
         patch.object(source_claim, "_select_candidate", side_effect=fake_select), \
         patch.object(source_claim, "_soft_reserve", return_value=True) as reserve:
        item = source_claim.claim_popular_source(
            source="blind", reservation_key="k", reserve_until=datetime(2030, 1, 1),
        )
    assert item is None
    reserve.assert_not_called()
    # window 2회(14일, 30일) × 품질 스킵 예산
    assert calls["n"] == 2 * source_claim._CLAIM_QUALITY_SKIPS_PER_WINDOW
