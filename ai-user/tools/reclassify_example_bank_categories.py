#!/usr/bin/env python3
"""Reclassify example_bank.category for crawl POSTs (high-confidence local classifier).

Unlike reclassify_post_categories.py (posts table), this updates example_bank only.
No LLM. Weak/tie rows are left unchanged (including OTHER).

Usage:
  python3 ai-user/tools/reclassify_example_bank_categories.py --env prod
  python3 ai-user/tools/reclassify_example_bank_categories.py --env prod --apply
  python3 ai-user/tools/reclassify_example_bank_categories.py --self-check
"""
from __future__ import annotations

import argparse
import subprocess
import sys
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
LEARNING_SERVICES = ROOT / "ai-user" / "learning" / "app" / "services"
sys.path.insert(0, str(LEARNING_SERVICES))

PLAZAS = ("COUPLE", "MARRIED", "FRIEND", "FAMILY", "WORK")
CHANNEL_HINT_BONUS = 2
MIN_WINNER_SCORE = 6

STORED_TO_HINT = {
    "romance": "COUPLE",
    "marriage": "MARRIED",
    "workplace": "WORK",
    "COUPLE": "COUPLE",
    "MARRIED": "MARRIED",
    "FRIEND": "FRIEND",
    "FAMILY": "FAMILY",
    "WORK": "WORK",
}

CONTAINERS = {
    "prod": "againspring-mariadb-prod",
    "dev": "againspring-mariadb-dev",
}
ENV_FILES = {
    "prod": ROOT / "env" / ".env.prod",
    "dev": ROOT / "env" / ".env.dev",
}


def load_env(path: Path) -> dict[str, str]:
    out: dict[str, str] = {}
    for line in path.read_text().splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        k, v = line.split("=", 1)
        out[k] = v.strip().strip('"').strip("'")
    return out


def mariadb(container: str, env: dict[str, str], sql: str) -> list[list[str]]:
    r = subprocess.run(
        [
            "docker",
            "exec",
            "-i",
            container,
            "mariadb",
            "-u",
            env["MARIADB_USER"],
            f"-p{env['MARIADB_PASSWORD']}",
            env["MARIADB_DATABASE"],
            "-N",
            "-B",
            "-e",
            sql,
        ],
        capture_output=True,
        text=True,
    )
    if r.returncode != 0:
        raise RuntimeError(r.stderr[:800])
    if not r.stdout.strip():
        return []
    return [line.split("\t") for line in r.stdout.strip().splitlines()]


def channel_hint_from_stored(category: str | None) -> str | None:
    if not category:
        return None
    return STORED_TO_HINT.get(category.strip())


def confident_enough(scores: dict[str, int]) -> bool:
    """True iff the winner is a clear high-confidence plaza.

    Winner score >= 6 and >= 2× runner-up. A lone positive plaza is confident
    at >= 6. Ties (equal top scores) are not confident.
    """
    vals = sorted((scores or {}).values(), reverse=True)
    if not vals:
        return False
    winner = vals[0]
    runner = vals[1] if len(vals) > 1 else 0
    if winner < MIN_WINNER_SCORE:
        return False
    if runner <= 0:
        return True
    if winner == runner:
        return False
    return winner >= 2 * runner


def _local_score_all(content: str, title: str, channel_hint: str | None) -> dict[str, int]:
    from plaza_classifier import _score_plaza  # type: ignore

    scores = {p: int(_score_plaza(content or "", title or "", p)) for p in PLAZAS}
    if channel_hint in scores:
        scores[channel_hint] = scores[channel_hint] + CHANNEL_HINT_BONUS
    return scores


def score_plazas(content: str, title: str, channel_hint: str | None = None) -> dict[str, int]:
    try:
        from plaza_classifier import score_all_plazas  # type: ignore
    except ImportError:
        return _local_score_all(content, title, channel_hint)
    try:
        scores = dict(score_all_plazas(content, title, channel_hint=channel_hint))
    except TypeError:
        scores = dict(score_all_plazas(content, title))
        if channel_hint in scores:
            scores[channel_hint] = int(scores[channel_hint]) + CHANNEL_HINT_BONUS
    return {p: int(scores.get(p, 0)) for p in PLAZAS}


def predicted_plaza(
    content: str,
    title: str,
    channel_hint: str | None = None,
) -> tuple[str, dict[str, int]] | None:
    """High-confidence plaza or None. Channel is a hint, never a veto."""
    try:
        from plaza_classifier import confident_plaza  # type: ignore
    except ImportError:
        confident_plaza = None  # type: ignore

    if confident_plaza is not None:
        try:
            result = confident_plaza(content, title, channel_hint=channel_hint)
        except TypeError:
            result = confident_plaza(content, title)
        if result is None:
            return None
        if isinstance(result, tuple):
            plaza, scores = result[0], dict(result[1]) if len(result) > 1 else {}
        else:
            plaza, scores = result, {}
        if plaza not in PLAZAS:
            return None
        if scores and not confident_enough(scores):
            return None
        return plaza, scores

    scores = score_plazas(content, title, channel_hint)
    if not confident_enough(scores):
        return None
    winner = max(PLAZAS, key=lambda p: scores[p])
    return winner, scores


def decide_bank_category(title: str, body: str, stored_category: str | None) -> str | None:
    hint = channel_hint_from_stored(stored_category)
    result = predicted_plaza(body or "", title or "", channel_hint=hint)
    if result is None:
        return None
    plaza, _scores = result
    return plaza


def run_self_check() -> int:
    assert confident_enough({"COUPLE": 6, "MARRIED": 0, "FRIEND": 0, "FAMILY": 0, "WORK": 0})
    assert confident_enough({"COUPLE": 8, "MARRIED": 4, "FRIEND": 0, "FAMILY": 0, "WORK": 0})
    assert not confident_enough({"COUPLE": 5, "MARRIED": 0, "FRIEND": 0, "FAMILY": 0, "WORK": 0})
    assert not confident_enough({"COUPLE": 6, "MARRIED": 4, "FRIEND": 0, "FAMILY": 0, "WORK": 0})
    assert not confident_enough({"COUPLE": 8, "MARRIED": 8, "FRIEND": 0, "FAMILY": 0, "WORK": 0})
    assert not confident_enough({})
    assert channel_hint_from_stored("romance") == "COUPLE"
    assert channel_hint_from_stored("marriage") == "MARRIED"
    assert channel_hint_from_stored("OTHER") is None
    print("self-check ok")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--env", choices=("prod", "dev"), default="prod")
    parser.add_argument("--apply", action="store_true")
    parser.add_argument(
        "--self-check",
        action="store_true",
        help="Run local confidence-rule checks (no DB)",
    )
    args = parser.parse_args()

    if args.self_check:
        return run_self_check()

    env = load_env(ENV_FILES[args.env])
    container = CONTAINERS[args.env]

    rows = mariadb(
        container,
        env,
        """
        SELECT id,
               COALESCE(category, ''),
               COALESCE(title, ''),
               COALESCE(content, '')
        FROM example_bank
        WHERE content_type = 'POST'
          AND LOWER(source) IN ('blind', 'natepan')
        """,
    )

    changes: list[tuple[str, str, str, str]] = []
    skipped = 0
    for bank_id, cur, title, body in rows:
        stored = cur if cur and cur != "NULL" else None
        pred = decide_bank_category(title, body, stored)
        if pred is None:
            skipped += 1
            continue
        if pred == cur:
            continue
        changes.append((bank_id, cur or "(empty)", pred, (title or "")[:60]))

    print(
        f"env={args.env} bank_posts={len(rows)} to_fix={len(changes)} "
        f"left_unmoved={skipped} apply={args.apply}"
    )
    by = Counter(f"{a}->{b}" for _, a, b, _ in changes)
    for k, v in by.most_common():
        print(f"  {k}: {v}")
    print("\nsample:")
    for row in changes[:15]:
        print(f"  {row[0]} {row[1]}->{row[2]} {row[3]!r}")

    if not args.apply:
        print("\n(dry-run only; pass --apply to UPDATE)")
        return 0

    updated = 0
    for bank_id, _cur, pred, _title in changes:
        mariadb(
            container,
            env,
            f"UPDATE example_bank SET category='{pred}' WHERE id={int(bank_id)}",
        )
        updated += 1
    print(f"\nupdated={updated}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
