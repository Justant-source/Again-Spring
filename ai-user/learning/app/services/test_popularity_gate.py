"""Unit tests for crawl popularity gate."""
from app.services.popularity_gate import (
    filter_comments_for_parents,
    has_any_metric,
    parent_post_url,
    passes_absolute_floor,
    plaza_group_key,
    select_popular_posts,
)


def _post(i: int, views: int, *, category: str = "OTHER") -> dict:
    return {
        "content": f"사연 본문 {i} " + ("갈등 " * 5),
        "content_type": "POST",
        "source_url": f"https://natepan.example/{i}",
        "category": category,
        "view_count": views,
        "like_count": views // 10,
        "comment_count": views // 20,
    }


def test_parent_post_url_strips_fragment():
    assert parent_post_url("https://x.example/post/1#cmt3") == "https://x.example/post/1"
    assert parent_post_url("https://x.example/post/1#comment-abc") == "https://x.example/post/1"
    assert parent_post_url(None) is None


def test_has_any_metric():
    assert has_any_metric({"view_count": 10}) is True
    assert has_any_metric({"like_count": 0}) is True
    assert has_any_metric({}) is False
    assert has_any_metric({"view_count": None, "like_count": None}) is False


def test_absolute_floor_natepan():
    assert passes_absolute_floor({"view_count": 100}, "natepan") is True
    assert passes_absolute_floor({"view_count": 10, "like_count": 1}, "natepan") is False
    assert passes_absolute_floor({"like_count": 3}, "natepan") is True


def test_select_popular_posts_keeps_top_half():
    posts = []
    for i, views in enumerate([10, 20, 50, 100, 200, 500]):
        posts.append({
            "content": f"사연 본문 {i} " + ("갈등 " * 5),
            "content_type": "POST",
            "source_url": f"https://natepan.example/{i}",
            "view_count": views,
            "like_count": views // 10,
            "comment_count": views // 20,
        })
    accepted, url_pct = select_popular_posts(posts, source="natepan", min_pct=0.5)
    assert len(accepted) >= 2
    assert all(url_pct[u] >= 0.5 for u in url_pct)
    # highest engagement URLs should be present
    assert "https://natepan.example/5" in url_pct


def test_filter_comments_only_popular_parents():
    comments = [
        {"content": "댓글1", "content_type": "COMMENT", "source_url": "https://p/1#cmt1"},
        {"content": "댓글2", "content_type": "COMMENT", "source_url": "https://p/2#cmt1"},
    ]
    kept = filter_comments_for_parents(comments, {"https://p/1"})
    assert len(kept) == 1
    assert kept[0]["source_url"] == "https://p/1#cmt1"


def test_plaza_group_key_normalizes_boards_and_blanks():
    assert plaza_group_key({"category": "romance"}) == "COUPLE"
    assert plaza_group_key({"category": "marriage"}) == "MARRIED"
    assert plaza_group_key({"category": "workplace"}) == "WORK"
    assert plaza_group_key({"category": "FAMILY"}) == "FAMILY"
    assert plaza_group_key({"category": "family"}) == "FAMILY"
    assert plaza_group_key({"category": ""}) == "OTHER"
    assert plaza_group_key({}) == "OTHER"
    assert plaza_group_key({"category": "talk"}) == "OTHER"


def test_select_popular_posts_ranks_within_plaza_not_whole_batch():
    """Huge WORK views must not push top-half FAMILY below the cut."""
    posts = [
        _post(0, 10_000, category="WORK"),
        _post(1, 20_000, category="WORK"),
        _post(2, 30_000, category="WORK"),
        _post(3, 40_000, category="WORK"),
        _post(10, 80, category="FAMILY"),
        _post(11, 120, category="FAMILY"),
        _post(12, 200, category="FAMILY"),
        _post(13, 400, category="FAMILY"),
    ]
    accepted, url_pct = select_popular_posts(posts, source="natepan", min_pct=0.5)
    family_kept = {u for u in url_pct if u.rsplit("/", 1)[-1] in {"10", "11", "12", "13"}}
    # 4 distinct FAMILY scores → mean percentiles 0.125, 0.375, 0.625, 0.875
    assert family_kept == {
        "https://natepan.example/12",
        "https://natepan.example/13",
    }
    assert url_pct["https://natepan.example/12"] >= 0.5
    assert url_pct["https://natepan.example/13"] >= 0.5
    assert "https://natepan.example/10" not in url_pct
    assert "https://natepan.example/11" not in url_pct
    # WORK cohort also keeps its own top half
    assert "https://natepan.example/3" in url_pct
    assert len(accepted) == 4


def test_select_popular_posts_absolute_floor_still_drops_natepan_view_10():
    posts = [
        _post(0, 10, category="FAMILY"),  # below natepan view/like/comment floors
        _post(1, 80, category="FAMILY"),
        _post(2, 200, category="FAMILY"),
        _post(3, 400, category="FAMILY"),
        _post(4, 800, category="FAMILY"),
    ]
    accepted, url_pct = select_popular_posts(posts, source="natepan", min_pct=0.5)
    assert "https://natepan.example/0" not in url_pct
    assert all(p.get("view_count", 0) >= 50 for p in accepted)


def test_select_popular_posts_single_plaza_member_kept_at_median():
    posts = [
        _post(0, 80, category="FAMILY"),
        _post(1, 10_000, category="WORK"),
        _post(2, 20_000, category="WORK"),
        _post(3, 30_000, category="WORK"),
        _post(4, 40_000, category="WORK"),
    ]
    accepted, url_pct = select_popular_posts(posts, source="natepan", min_pct=0.5)
    assert url_pct["https://natepan.example/0"] == 0.5
    assert any(p["source_url"] == "https://natepan.example/0" for p in accepted)
