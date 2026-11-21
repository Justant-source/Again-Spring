"""blind.py 파서 단위 테스트 — COMMENT 본문 + like 보강 (Wave1-B)."""
from datetime import datetime

from bs4 import BeautifulSoup

from app.crawlers.blind import (
    _extract_comment_timestamps,
    _extract_comments,
    _extract_post_stats,
    _parse_like_element,
    _resolve_category,
)


SAMPLE_HTML = """
<html><body><main>
  <div class="contents">
    <div class="article-view-head">
      <h2>남편이랑 싸웠는데 억울해요</h2>
      <div class="wrap-info">
        <span class="date"><i class="blind">작성일</i>2시간</span>
        <span class="pv"><i class="blind">조회수</i>122</span>
        <span class="cmt"><i class="blind">댓글</i>18</span>
      </div>
    </div>
    <div class="article-view-contents">
      <p>남편과 어제 크게 싸웠습니다. 억울하고 화가 나서 글을 씁니다. """ + ("갈등 " * 30) + """</p>
      <div class="article_info">
        <div class="info">
          <span class="like"><i class="blind">좋아요</i>좋아요</span>
          <span class="cmt"><i class="blind">댓글</i>18</span>
        </div>
      </div>
    </div>
    <div class="article-comments">
      <div class="wrap-comment comment_area" id="445742687">
        <p class="name"><a class="point">롯데건설</a> <span class="middot">·</span> U*****</p>
        <p class="cmt-txt"><span>이러고 싸움? 이게 끝이면 그냥 아무 일도 아닌거 같은데?</span></p>
        <div class="wrap-info">
          <span class="date"><i class="blind">작성일</i>2시간</span>
          <span class="like"><i class="blind">좋아요수</i>좋아요</span>
        </div>
      </div>
      <div class="wrap-comment comment_area" id="445742894">
        <p class="name"><a class="point">삼성전자</a> <span class="middot">·</span> A*****</p>
        <p class="cmt-txt"><span>물어볼수도 잇고 답 안할수도 있는건데,,, 뭐 둘다 속좁은걸로</span></p>
        <div class="wrap-info">
          <span class="date"><i class="blind">작성일</i>어제</span>
          <span class="like"><i class="blind">좋아요수</i>1</span>
        </div>
      </div>
    </div>
    <div class="rcmd_tp rcmd_btm">
      <section class="article-list">
        <div class="article-list-pre">
          <div class="wrap-info">
            <span class="pv" name="link">조회수 32427</span>
            <span class="like" name="link">좋아요 46</span>
          </div>
        </div>
      </section>
    </div>
  </div>
</main></body></html>
"""

SAMPLE_HTML_LIKED = SAMPLE_HTML.replace(
    '<span class="like"><i class="blind">좋아요</i>좋아요</span>',
    '<span class="like"><i class="blind">좋아요</i>163</span>',
    1,
)

REF = datetime(2026, 8, 1, 16, 0, 0)
POST_URL = "https://www.teamblind.com/kr/post/sample-abc123"


class TestParseLikeElement:
    def test_zero_like_sentinel(self):
        el = BeautifulSoup(
            '<span class="like"><i class="blind">좋아요</i>좋아요</span>',
            "html.parser",
        ).span
        assert _parse_like_element(el) == 0

    def test_numeric_like(self):
        el = BeautifulSoup(
            '<span class="like"><i class="blind">좋아요</i>163</span>',
            "html.parser",
        ).span
        assert _parse_like_element(el) == 163

    def test_comment_like_zero_and_one(self):
        zero = BeautifulSoup(
            '<span class="like"><i class="blind">좋아요수</i>좋아요</span>',
            "html.parser",
        ).span
        one = BeautifulSoup(
            '<span class="like"><i class="blind">좋아요수</i>1</span>',
            "html.parser",
        ).span
        assert _parse_like_element(zero) == 0
        assert _parse_like_element(one) == 1


class TestExtractPostStats:
    def test_zero_like_is_zero_not_none(self):
        soup = BeautifulSoup(SAMPLE_HTML, "html.parser")
        view, like, cmt = _extract_post_stats(soup)
        assert view == 122
        assert cmt == 18
        assert like == 0  # 예전 파서는 None → 커버리지 하락 원인

    def test_numeric_like_from_article_info(self):
        soup = BeautifulSoup(SAMPLE_HTML_LIKED, "html.parser")
        _, like, _ = _extract_post_stats(soup)
        assert like == 163

    def test_does_not_pick_recommended_like(self):
        """추천 글 span.like(46)를 본문 좋아요로 오인하지 않는다."""
        soup = BeautifulSoup(SAMPLE_HTML, "html.parser")
        _, like, _ = _extract_post_stats(soup)
        assert like != 46


class TestExtractComments:
    def test_emits_comment_rows_with_body(self):
        soup = BeautifulSoup(SAMPLE_HTML, "html.parser")
        rows = _extract_comments(soup, REF, post_url=POST_URL, category="workplace")
        assert len(rows) == 2
        assert all(r["content_type"] == "COMMENT" for r in rows)
        assert "싸움" in rows[0]["content"]
        assert rows[0]["source_url"] == f"{POST_URL}#comment-445742687"
        assert rows[0]["category"] == "workplace"
        assert rows[0]["like_count"] == 0
        assert rows[1]["like_count"] == 1
        assert rows[1]["posted_at"] == "2026-07-31 16:00:00"
        assert rows[0]["author_id"] == "U*****"

    def test_timestamps_still_extracted_for_post_engagement(self):
        soup = BeautifulSoup(SAMPLE_HTML, "html.parser")
        ts = _extract_comment_timestamps(soup, REF)
        assert ts is not None
        assert len(ts) == 2
        assert ts[0] == "2026-08-01 14:00:00"
        assert ts[1] == "2026-07-31 16:00:00"


class TestResolveCategory:
    def test_family_body_on_marriage_board_stores_family(self):
        assert _resolve_category(
            "아빠랑 친오빠, 친동생이 본가에서 싸운다. 부모님이 너무 힘들다.",
            "아빠 본가 원가족",
            board_category="marriage",
        ) == "FAMILY"

    def test_parenting_plus_husband_on_marriage_board_stays_married(self):
        assert _resolve_category(
            "육아 너무 힘든데 남편이 전혀 안 도와줘.",
            "육아",
            board_category="marriage",
        ) == "MARRIED"

    def test_workplace_hint_does_not_block_family(self):
        assert _resolve_category(
            "아빠랑 친오빠, 친동생이 본가에서 싸운다. 부모님이 너무 힘들다.",
            "아빠 본가",
            board_category="workplace",
        ) == "FAMILY"


# --- 본문 추출 (2026-11: og:description 미리보기 → p.contents-txt 전체 본문) ---
from app.crawlers.blind import _extract_content  # noqa: E402

_BODY_HTML = """
<html><head>
<meta property="og:description" content="부부상담만 하고나면 더 싸우네요우리만 그런줄 알았는데 그런부부 많다고 하더라구요결국은 부부상담응 돈벌이고 생각하고 화해는 커녕오히려 화를 더 키우게됐네요상담받고 이혼하는 부부도 많다더니만..."/>
</head><body><main>
<div class="article-view-contents">
  <p class="contents-txt" id="contentArea">부부상담만 하고나면 더 싸우네요<br/>우리만 그런줄 알았는데 그런부부 많다고 하더라구요<br/>결국은 부부상담응 돈벌이고 생각하고 화해는 커녕<br/>오히려 화를 더 키우게됐네요<br/>상담받고 이혼하는 부부도 많다더니만...</p>
  <ul class="tag-actions"><li><a class="action-tag"><span class="text">직장인끼리 소개팅하러 가기 by 소개팅앱</span></a></li></ul>
  <div class="article_info"><div class="info"><span class="like"><i class="blind">좋아요</i>1</span></div></div>
</div>
<div class="rcmd_tp rcmd_btm"><section class="article-list"><a>가연에서 솔로 탈출 가능할까?</a>
<a>(광고) 오휘 미라클 모이스처 핑크 베리어 스킨 150ml</a></section></div>
</main></body></html>
"""


def test_extract_content_uses_full_body_with_br_newlines_not_sidebar():
    content = _extract_content(BeautifulSoup(_BODY_HTML, "html.parser"))
    assert content.count("\n") == 4  # <br> → 줄바꿈 보존
    assert content.startswith("부부상담만 하고나면 더 싸우네요\n우리만")
    assert "소개팅" not in content and "광고" not in content and "가연" not in content


def test_extract_content_no_body_node_falls_back_to_og_but_never_main_sidebar():
    html = _BODY_HTML.replace('id="contentArea"', "").replace("contents-txt", "x").replace("article-view-contents", "y")
    content = _extract_content(BeautifulSoup(html, "html.parser"))
    assert content is not None and content.startswith("부부상담만")
    assert "광고" not in content and "\n" not in content  # og 미리보기: 줄바꿈 없음 → 품질 게이트가 판정

    html_no_og = html.replace("og:description", "og:none")
    assert _extract_content(BeautifulSoup(html_no_og, "html.parser")) is None  # <main> 폴백 삭제
