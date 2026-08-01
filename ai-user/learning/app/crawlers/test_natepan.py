"""natepan 파서 단위 테스트 — view/like/comment 지표 + COMMENT 행."""
from bs4 import BeautifulSoup

from app.crawlers.natepan import (
    _extract_view_count,
    _extract_like_count,
    _extract_comment_count,
    _extract_comment_rows,
    _parse_detail_bundle,
)


# 실측 DOM에 맞춘 최소 fixture (2026-08 natepan talk detail)
SAMPLE_DETAIL_HTML = """
<html><body>
<div class="info">
  <a class="writer">테스터</a>
  <span class="date">2026.07.31 18:55</span>
  <span class="count"><span class="tit">조회</span>44,903</span>
</div>
<h2 class="tit">시댁 갈등 사연 제목입니다</h2>
<div id="contentArea">
  """ + ("본문 내용이 충분히 길어야 통과합니다. " * 10) + """
</div>
<div class="btnbox up">
  <button type="button" value="R"><span>추천</span></button>
  <span class="count"><em>추천수</em><span>93</span></span>
</div>
<div class="btnbox down">
  <button type="button" value="A"><span>반대</span></button>
  <span class="count"><em>반대수</em><span>20</span></span>
</div>
<div class="cmt_tit"><span class="num"><strong>83</strong>개의 댓글</span></div>
<div class="cmt_list">
  <dl class="cmt_item">
    <dt><a class="nameui">닉A</a><i>2026.07.31 19:03</i></dt>
    <dd class="usertxt">이건 정말 선 넘는 질문이에요 맞아요</dd>
  </dl>
  <dl class="cmt_item">
    <dt><a class="nameui">닉B</a><i>2026.07.31 20:00</i></dt>
    <dd class="usertxt">누나가 남동생 재산 갈취했다고 생각하나보네</dd>
  </dl>
  <dl class="cmt_item">
    <dt><a class="nameui">닉C</a><i>2026.07.31 21:10</i></dt>
    <dd class="usertxt">짧은댓</dd>
  </dl>
  <dl class="cmt_item">
    <dt><a class="nameui">닉D</a><i>2026.07.31 22:00</i></dt>
    <dd class="usertxt">결혼 전부터 재산 이야기 꺼내는 건 위험신호입니다 정말요</dd>
  </dl>
</div>
</body></html>
"""


class TestEngagementParsers:
    def test_view_count_tit_span(self):
        soup = BeautifulSoup(SAMPLE_DETAIL_HTML, "html.parser")
        assert _extract_view_count(soup) == 44903

    def test_like_count_recomm_su_nested(self):
        """'추천수93' 형태 — 구 정규식 '추천\\s*(\\d+)'는 실패하던 케이스."""
        soup = BeautifulSoup(SAMPLE_DETAIL_HTML, "html.parser")
        assert _extract_like_count(soup) == 93

    def test_like_count_ignores_oppose(self):
        soup = BeautifulSoup(SAMPLE_DETAIL_HTML, "html.parser")
        # 반대수 20을 추천수로 오인하지 않음
        assert _extract_like_count(soup) != 20

    def test_comment_count_from_header(self):
        soup = BeautifulSoup(SAMPLE_DETAIL_HTML, "html.parser")
        assert _extract_comment_count(soup) == 83


class TestCommentRows:
    def test_extract_comment_rows_schema_and_limit(self):
        soup = BeautifulSoup(SAMPLE_DETAIL_HTML, "html.parser")
        url = "https://pann.nate.com/talk/375547488"
        rows = _extract_comment_rows(soup, url, category="OTHER", limit=3)
        # 길이 미달("짧은댓")은 스킵 → 유효 3건 중 limit=3
        assert len(rows) == 3
        for i, row in enumerate(rows, start=1):
            assert row["content_type"] == "COMMENT"
            assert row["source"] == "natepan"
            assert row["category"] == "OTHER"
            assert row["source_url"] == f"{url}#cmt{i}"
            assert "content" in row and len(row["content"]) >= 10
            assert "author_id" in row
            assert "posted_at" in row

    def test_parse_detail_bundle_post_plus_comments(self):
        url = "https://pann.nate.com/talk/375547488"
        rows = _parse_detail_bundle(SAMPLE_DETAIL_HTML, url, max_comments=2)
        assert rows[0]["content_type"] == "POST"
        assert rows[0]["view_count"] == 44903
        assert rows[0]["like_count"] == 93
        assert rows[0]["comment_count"] == 83
        comments = [r for r in rows if r["content_type"] == "COMMENT"]
        assert len(comments) == 2
        assert comments[0]["author_id"] == "닉A"
        assert comments[0]["posted_at"] == "2026-07-31 19:03:00"

    def test_parse_detail_bundle_no_comments_when_budget_zero(self):
        rows = _parse_detail_bundle(
            SAMPLE_DETAIL_HTML,
            "https://pann.nate.com/talk/1",
            max_comments=0,
        )
        assert len(rows) == 1
        assert rows[0]["content_type"] == "POST"


if __name__ == "__main__":
    import pytest
    pytest.main([__file__, "-v"])
