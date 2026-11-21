"""source_comments — 걸러내기·매칭·보충·무작위 표본 단위 테스트 (DB는 가짜 커서)."""
from contextlib import contextmanager
from unittest.mock import MagicMock, patch

from app.services import source_comments as sc

POST_URL = "https://pann.nate.com/talk/375724415"


def test_clean_comment_filters():
    assert sc.clean_comment("ㅇ") is None  # 2자 미만
    assert sc.clean_comment("가" * 201) is None
    assert sc.clean_comment("가" * 200) is not None
    assert sc.clean_comment("작성자가 삭제한 댓글입니다.") is None
    assert sc.clean_comment("신고에 의해 숨김 처리 되었습니다.") is None
    assert sc.clean_comment("연락주세요 010-1234-5678") is None
    assert sc.clean_comment("메일 a.b@gmail.com 로") is None
    assert sc.clean_comment("https://example.com 참고") is None
    assert sc.clean_comment("  ㅋㅋ 진짜 웃기네  ") == "ㅋㅋ 진짜 웃기네"
    assert sc.clean_comment("삭제하지 말아줘 흔들릴때마다 보고싶어") is not None  # '삭제' 단어만으론 안 거름


def test_clean_comment_signature():
    sigs = MagicMock()
    sigs.contains_signature.side_effect = lambda t: "credit balance" in t
    sigs.has_prompt_leak.return_value = False
    assert sc.clean_comment("Credit balance is too low", sigs) is None
    assert sc.clean_comment("정상 댓글이에요", sigs) == "정상 댓글이에요"


def test_clamp_limit():
    assert sc.clamp_limit(None) == 12
    assert sc.clamp_limit(99) == 20
    assert sc.clamp_limit(0) == 1


class FakeCur:
    """SQL 키워드로 응답을 가르는 가짜 커서."""

    def __init__(self, post, matched_rows, fill_rows, dup_contents=()):
        self.post, self.matched_rows, self.fill_rows, self.dup = post, matched_rows, fill_rows, set(dup_contents)
        self._r = []
        self.fill_sqls = []

    def execute(self, sql, params=None):
        if "WHERE id = %s" in sql:
            self._r = [self.post] if self.post else []
        elif "source_url >= %s" in sql:
            self._r = self.matched_rows
        elif "GROUP BY content" in sql:
            self._r = [{"content": t, "c": 5} for t in params[:-1] if t in self.dup]
        else:
            self.fill_sqls.append((sql, params))
            self._r = [{"content": t} for t in self.fill_rows]

    def fetchone(self):
        return self._r[0] if self._r else None

    def fetchall(self):
        return list(self._r)


def _run(cur, example_id=1, limit=12):
    @contextmanager
    def fake_db():
        conn = MagicMock()
        conn.cursor.return_value.__enter__.return_value = cur
        yield conn

    with patch.object(sc, "get_db", fake_db), patch.object(sc, "_signatures", return_value=None):
        return sc.get_source_comments(example_id, limit)


POST = {"id": 1, "source": "natepan", "source_url": POST_URL, "category": "MARRIED", "content_type": "POST"}


def _m(i, text, url=None):
    return {"source_url": url or f"{POST_URL}#cmt{i}", "content": text}


def test_missing_or_non_post_returns_empty():
    assert _run(FakeCur(None, [], [])) == {"comments": [], "matched": False}
    assert _run(FakeCur({**POST, "content_type": "COMMENT"}, [], [])) == {"comments": [], "matched": False}


def test_matched_enough_no_fill():
    rows = [_m(i, f"댓글 번호 {i}") for i in range(8)]
    cur = FakeCur(POST, rows, ["보충1", "보충2"])
    out = _run(cur, limit=5)
    assert out["matched"] is True and len(out["comments"]) == 5
    assert set(out["comments"]) <= {f"댓글 번호 {i}" for i in range(8)}
    assert cur.fill_sqls == []


def test_defrag_mismatch_dedupe_and_filter_dropped():
    rows = [
        _m(1, "정상 댓글"),
        _m(2, "정상 댓글"),  # 중복
        _m(3, "다른 글 댓글", url="https://pann.nate.com/talk/3757244150#cmt1"),  # 접두사만 같은 다른 글
        _m(4, "작성자가 삭제한 댓글입니다."),
    ]
    cur = FakeCur(POST, rows, [])
    out = _run(cur)
    assert out == {"comments": ["정상 댓글"], "matched": True}


def test_few_matched_filled_from_same_source_and_category_first():
    rows = [_m(1, "원문 댓글 하나"), _m(2, "원문 댓글 둘")]
    fill = [f"보충 댓글 {i}" for i in range(30)]
    cur = FakeCur(POST, rows, fill)
    out = _run(cur, limit=12)
    assert out["matched"] is True
    assert len(out["comments"]) == 12
    assert "원문 댓글 하나" in out["comments"] and "원문 댓글 둘" in out["comments"]
    sql, params = cur.fill_sqls[0]
    assert "category = %s" in sql and params[:2] == ["natepan", "MARRIED"]


def test_no_match_gives_filler_with_matched_false():
    cur = FakeCur(POST, [], [f"보충 댓글 {i}" for i in range(20)])
    out = _run(cur, limit=6)
    assert out["matched"] is False and len(out["comments"]) == 6


def test_global_duplicate_ads_dropped():
    ad = "에너자이저 알카라인 AA 건전지"
    rows = [_m(i, f"진짜 댓글 {i}") for i in range(6)] + [_m(9, ad)]
    out = _run(FakeCur(POST, rows, [], dup_contents={ad}))
    assert ad not in out["comments"] and len(out["comments"]) == 6


def test_sample_keeps_short_comments():
    """상위 좋아요 컷이 아니라 무작위 — 짧은 댓글이 표본에 섞인다."""
    rows = [_m(i, "ㅋㅋ웃기다" if i % 2 else "긴 댓글 " * 10) for i in range(40)]
    rows = [{**r, "content": r["content"] + str(i)} for i, r in enumerate(rows)]
    out = _run(FakeCur(POST, rows, []), limit=20)
    assert 0 < sum(len(c) <= 30 for c in out["comments"]) < 20
