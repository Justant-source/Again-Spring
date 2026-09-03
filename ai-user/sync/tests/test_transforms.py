from datetime import datetime, timezone

import sync


NOW = datetime(2026, 9, 3, tzinfo=timezone.utc)
CTX = sync.SyncContext(synthetic_ids=frozenset({"bot1"}))


def _post(author, **extra):
    row = dict(
        id="p" * 32, author_id=author, title="진짜 제목", body_raw="원문 원문", body_published="공개본",
        partner_body_raw="상대 원문", partner_body_published="상대 공개본", invite_token="tok",
        source_original_body="크롤 원문", promo_title="훅",
    )
    row.update(extra)
    return row


def test_synthetic_post_keeps_body_but_drops_invite_token():
    out = sync._mask_real_post(_post("bot1"), NOW, CTX)
    assert out["body_published"] == "공개본"
    assert out["body_raw"] == "원문 원문"
    assert out["invite_token"] is None


def test_real_user_post_is_masked():
    out = sync._mask_real_post(_post("human9"), NOW, CTX)
    assert out["title"].startswith("[비식별]")
    assert "원문" not in out["body_published"]
    assert out["body_raw"] is None
    assert out["partner_body_raw"] is None
    assert out["source_original_body"] is None
    assert out["promo_title"] is None
    assert out["invite_token"] is None
    assert out["partner_body_published"] and "상대" not in out["partner_body_published"]


def test_real_user_comment_is_masked_and_synthetic_kept():
    real = sync._mask_real_comment({"id": 7, "author_id": "human9", "body": "비밀"}, NOW, CTX)
    bot = sync._mask_real_comment({"id": 8, "author_id": "bot1", "body": "봇 댓글"}, NOW, CTX)
    assert "비밀" not in real["body"] and real["body"].startswith("[비식별 댓글")
    assert bot["body"] == "봇 댓글"


def test_mask_real_user_ignores_ctx():
    out = sync._mask_real_user({"id": "abcdefgh12345678", "synthetic": 0, "email": "a@b"}, NOW, CTX)
    assert out["email"].endswith("@dev.invalid")
