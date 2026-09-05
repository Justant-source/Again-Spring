"""purge_offtarget_posts.py 단위 테스트 — DB/claude CLI 없이 순수 함수만 검증."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import purge_offtarget_posts as mod  # noqa: E402


# --- parse_classification_response -----------------------------------------


def test_parse_classification_response_plain_json():
    raw = json.dumps(
        [
            {"id": "post_a", "verdict": "OFF_TARGET", "reason": "정년·손주"},
            {"id": "post_b", "verdict": "ON_TARGET", "reason": "30대 직장 갈등"},
        ]
    )
    rows = mod.parse_classification_response(raw)
    assert rows == [
        {"id": "post_a", "verdict": "OFF_TARGET", "reason": "정년·손주"},
        {"id": "post_b", "verdict": "ON_TARGET", "reason": "30대 직장 갈등"},
    ]


def test_parse_classification_response_strips_markdown_fence():
    raw = '```json\n[{"id":"post_c","verdict":"off_target","reason":"환갑"}]\n```'
    rows = mod.parse_classification_response(raw)
    assert rows == [{"id": "post_c", "verdict": "OFF_TARGET", "reason": "환갑"}]


def test_parse_classification_response_rejects_unknown_verdict():
    raw = '[{"id":"post_d","verdict":"MAYBE","reason":"?"}]'
    try:
        mod.parse_classification_response(raw)
        assert False, "should have raised"
    except mod.ClassifierError:
        pass


def test_extract_json_array_finds_array_in_prose():
    text = "판정 결과는 다음과 같다:\n[{\"id\":\"x\",\"verdict\":\"ON_TARGET\",\"reason\":\"ok\"}]\n감사합니다."
    array_text = mod.extract_json_array(text)
    assert json.loads(array_text) == [{"id": "x", "verdict": "ON_TARGET", "reason": "ok"}]


# --- build_apply_sql ---------------------------------------------------------


def test_build_apply_sql_generates_transaction():
    sql = mod.build_apply_sql(["post_a", "post_b"])
    assert "START TRANSACTION;" in sql
    assert "COMMIT;" in sql
    assert "UPDATE posts SET deleted_at = NOW(3) WHERE id IN ('post_a', 'post_b')" in sql
    assert "UPDATE post_comments SET deleted_at = NOW(3) WHERE post_id IN ('post_a', 'post_b')" in sql


def test_build_apply_sql_empty_list_is_noop():
    sql = mod.build_apply_sql([])
    assert "UPDATE" not in sql


def test_build_apply_sql_rejects_unsafe_id():
    try:
        mod.build_apply_sql(["post_a'; DROP TABLE posts; --"])
        assert False, "should have raised"
    except ValueError:
        pass


# --- prod 가드 (cmd_apply) ---------------------------------------------------


def _args(**overrides) -> argparse.Namespace:
    base = dict(
        env_file="env/.env.dev",
        env_name=None,
        db_container=None,
        db_user=None,
        db_password=None,
        db_name=None,
        apply=None,
        i_mean_it=False,
        dry_run=True,
    )
    base.update(overrides)
    return argparse.Namespace(**base)


def test_cmd_apply_refuses_prod_without_i_mean_it(tmp_path, monkeypatch):
    jsonl = tmp_path / "out.jsonl"
    jsonl.write_text('{"id":"post_a","verdict":"OFF_TARGET","reason":"r"}\n', encoding="utf-8")
    args = _args(env_file="env/.env.prod", apply=str(jsonl))

    called = {"ran": False}

    def fake_run_mariadb_sql(target, sql):  # pragma: no cover - must not be called
        called["ran"] = True
        return ""

    monkeypatch.setattr(mod, "run_mariadb_sql", fake_run_mariadb_sql)
    monkeypatch.setattr(mod, "load_dotenv", lambda path: {"MARIADB_USER": "u", "MARIADB_PASSWORD": "p", "MARIADB_DATABASE": "d"})

    rc = mod.cmd_apply(args)
    assert rc == 1
    assert called["ran"] is False


def test_cmd_apply_allows_prod_with_i_mean_it_dry_run(tmp_path, monkeypatch):
    jsonl = tmp_path / "out.jsonl"
    jsonl.write_text('{"id":"post_a","verdict":"OFF_TARGET","reason":"r"}\n', encoding="utf-8")
    args = _args(env_file="env/.env.prod", apply=str(jsonl), i_mean_it=True, dry_run=True)

    called = {"ran": False}
    monkeypatch.setattr(mod, "run_mariadb_sql", lambda target, sql: called.__setitem__("ran", True))
    monkeypatch.setattr(mod, "load_dotenv", lambda path: {"MARIADB_USER": "u", "MARIADB_PASSWORD": "p", "MARIADB_DATABASE": "d"})

    rc = mod.cmd_apply(args)
    assert rc == 0
    assert called["ran"] is False  # dry-run이므로 실제 DB 호출 없음


def test_cmd_apply_dev_dry_run_skips_execution(tmp_path, monkeypatch):
    jsonl = tmp_path / "out.jsonl"
    jsonl.write_text(
        '{"id":"post_a","verdict":"OFF_TARGET","reason":"r"}\n{"id":"post_b","verdict":"ON_TARGET","reason":"r2"}\n',
        encoding="utf-8",
    )
    args = _args(env_file="env/.env.dev", apply=str(jsonl), dry_run=True)

    called = {"ran": False}
    monkeypatch.setattr(mod, "run_mariadb_sql", lambda target, sql: called.__setitem__("ran", True))
    monkeypatch.setattr(mod, "load_dotenv", lambda path: {"MARIADB_USER": "u", "MARIADB_PASSWORD": "p", "MARIADB_DATABASE": "d"})

    rc = mod.cmd_apply(args)
    assert rc == 0
    assert called["ran"] is False


# --- infer_env_name / build_db_target ---------------------------------------


def test_infer_env_name_dev_and_prod():
    assert mod.infer_env_name("env/.env.dev") == "dev"
    assert mod.infer_env_name("env/.env.prod") == "prod"


def test_infer_env_name_unknown_raises():
    try:
        mod.infer_env_name("env/.env.mystery")
        assert False, "should have raised"
    except ValueError:
        pass


def test_build_db_target_uses_container_map():
    args = _args(env_file="env/.env.dev")
    target = mod.build_db_target(args, {"MARIADB_USER": "u", "MARIADB_PASSWORD": "p", "MARIADB_DATABASE": "d"})
    assert target.env_name == "dev"
    assert target.container == "againspring-mariadb-dev"
    assert target.user == "u"
    assert target.database == "d"


# --- classify_all: LLM 실패 시 ERROR 로 표기(무음 실패 금지) ------------------


def test_classify_all_marks_failed_batch_as_error(monkeypatch):
    posts = [{"id": "post_a", "title": "t", "body": "b", "author": "n"}]

    def fake_call(batch, **kwargs):
        raise mod.ClassifierError("boom")

    monkeypatch.setattr(mod, "call_claude_classifier", fake_call)
    rows = mod.classify_all(posts)
    assert len(rows) == 1
    assert rows[0]["verdict"] == "ERROR"
    assert "boom" in rows[0]["reason"]


def test_classify_all_happy_path(monkeypatch):
    posts = [{"id": "post_a", "title": "t", "body": "b", "author": "n"}]

    def fake_call(batch, **kwargs):
        return [{"id": "post_a", "verdict": "OFF_TARGET", "reason": "환갑"}]

    monkeypatch.setattr(mod, "call_claude_classifier", fake_call)
    rows = mod.classify_all(posts)
    assert rows == [
        {"id": "post_a", "title": "t", "author_persona": "n", "verdict": "OFF_TARGET", "reason": "환갑"}
    ]
