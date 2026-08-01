"""Unit tests for crawl popularity gate."""
from app.services.popularity_gate import (
    filter_comments_for_parents,
    has_any_metric,
    parent_post_url,
    passes_absolute_floor,
    select_popular_posts,
)


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
