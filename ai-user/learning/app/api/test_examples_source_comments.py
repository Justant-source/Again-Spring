"""/examples/source-comments · /examples/reject-source 엔드포인트 테스트."""
from unittest.mock import MagicMock, patch

from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)


def test_source_comments_ok_and_limit_passthrough():
    with patch("app.services.source_comments.get_source_comments",
               return_value={"comments": ["a댓", "b댓"], "matched": True}) as m:
        r = client.post("/examples/source-comments", json={"example_id": 7, "limit": 5})
    assert r.status_code == 200
    assert r.json() == {"comments": ["a댓", "b댓"], "matched": True}
    m.assert_called_once_with(7, 5)


def test_source_comments_camel_and_default_limit():
    with patch("app.services.source_comments.get_source_comments",
               return_value={"comments": [], "matched": False}) as m:
        r = client.post("/examples/source-comments", json={"exampleId": 7})
    assert r.status_code == 200
    m.assert_called_once_with(7, 12)


def test_source_comments_error_is_empty_not_500():
    with patch("app.services.source_comments.get_source_comments", side_effect=RuntimeError("db down")):
        r = client.post("/examples/source-comments", json={"example_id": 1})
    assert r.status_code == 200
    assert r.json() == {"comments": [], "matched": False}


def test_reject_source_ok():
    with patch("app.services.source_claim.reject_source",
               return_value={"status": "rejected", "ids": [1]}) as m:
        r = client.post("/examples/reject-source",
                        json={"example_id": 1, "reservation_key": "k1", "reason": "갈등 없음"})
    assert r.status_code == 200 and r.json() == {"ok": True}
    m.assert_called_once_with(example_id=1, reservation_key="k1", reason="갈등 없음")


def test_reject_source_db_error_500():
    with patch("app.services.source_claim.reject_source", side_effect=RuntimeError("x")):
        r = client.post("/examples/reject-source", json={"example_id": 1, "reservation_key": "k"})
    assert r.status_code == 500
