#!/usr/bin/env python3
"""Reclassify posts.category from content (and optional example_bank source).

Fixes historical mislabels where popularity claim ignored plaza category —
e.g. marriage reconstruct labeled FRIEND/FAMILY.

Usage:
  # dry-run against prod (default)
  python3 ai-user/tools/reclassify_post_categories.py --env prod

  # apply updates
  python3 ai-user/tools/reclassify_post_categories.py --env prod --apply
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

from plaza_classifier import _score_plaza, classify_plaza  # type: ignore

PLAZAS = ("COUPLE", "MARRIED", "FRIEND", "FAMILY", "WORK")

BANK_TO_PLAZA = {
    "romance": "COUPLE",
    "marriage": "MARRIED",
    "workplace": "WORK",
    "COUPLE": "COUPLE",
    "MARRIED": "MARRIED",
    "FRIEND": "FRIEND",
    "FAMILY": "FAMILY",
    "WORK": "WORK",
    "OTHER": "OTHER",
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


def predicted_for_post(
    *,
    title: str,
    body: str,
    bank_category: str | None,
    include_classifier: bool,
    min_score: int,
) -> tuple[str, str] | None:
    """Return (predicted_plaza, reason) or None if no confident change."""
    if bank_category and bank_category in BANK_TO_PLAZA and bank_category not in ("OTHER", "talk"):
        return BANK_TO_PLAZA[bank_category], f"bank:{bank_category}"
    if not include_classifier:
        return None
    pred = classify_plaza(body or "", title or "")
    scores = {p: _score_plaza(body or "", title or "", p) for p in PLAZAS}
    top = max(scores.values()) if scores else 0
    if top < min_score:
        return None
    return pred, f"classifier:score={top}"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--env", choices=("prod", "dev"), default="prod")
    parser.add_argument("--apply", action="store_true")
    parser.add_argument(
        "--include-classifier",
        action="store_true",
        help="Also use plaza_classifier when bank category is missing/noisy (more recall, more risk)",
    )
    parser.add_argument("--min-score", type=int, default=4)
    args = parser.parse_args()

    env = load_env(ENV_FILES[args.env])
    container = CONTAINERS[args.env]

    rows = mariadb(
        container,
        env,
        """
        SELECT p.id,
               p.category,
               COALESCE(p.user_title, p.title, ''),
               COALESCE(p.body_published, p.body_raw, ''),
               COALESCE(eb.category, '')
        FROM posts p
        LEFT JOIN example_bank eb ON eb.id = p.source_example_id
        WHERE p.deleted_at IS NULL
        """,
    )

    changes: list[tuple[str, str, str, str, str]] = []
    for post_id, cur, title, body, bank_cat in rows:
        bank = bank_cat if bank_cat and bank_cat != "NULL" else None
        result = predicted_for_post(
            title=title,
            body=body,
            bank_category=bank,
            include_classifier=args.include_classifier,
            min_score=args.min_score,
        )
        if result is None:
            continue
        pred, reason = result
        if pred == cur:
            continue
        changes.append((post_id, cur, pred, reason, title[:60]))

    print(f"env={args.env} posts={len(rows)} to_fix={len(changes)} apply={args.apply}")
    by = Counter(f"{a}->{b}" for _, a, b, _, _ in changes)
    for k, v in by.most_common():
        print(f"  {k}: {v}")
    print("\nsample:")
    for row in changes[:15]:
        print(f"  {row[0]} {row[1]}->{row[2]} ({row[3]}) {row[4]!r}")

    if not args.apply:
        print("\n(dry-run only; pass --apply to UPDATE)")
        return 0

    updated = 0
    for post_id, _cur, pred, _reason, _title in changes:
        mariadb(
            container,
            env,
            f"UPDATE posts SET category='{pred}' WHERE id='{post_id}' AND deleted_at IS NULL",
        )
        updated += 1
    print(f"\nupdated={updated}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
