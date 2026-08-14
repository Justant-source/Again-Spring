#!/usr/bin/env python3
"""Normalize historic crawl-derived community references (dry-run by default).

It only touches example_bank rows from crawler sources and posts linked to those
rows, never normal user-authored posts.  The crawl corpus is production SoT, so
the default is a production *dry run*; updates require explicit ``--apply``.
"""
from __future__ import annotations

import argparse
import base64
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "ai-user" / "learning"))
from app.services.community_reference_sanitizer import sanitize_crawled_text  # noqa: E402

CONTAINERS = {"prod": "againspring-mariadb-prod", "dev": "againspring-mariadb-dev"}
ENV_FILES = {"prod": ROOT / "env" / ".env.prod", "dev": ROOT / "env" / ".env.dev"}
CRAWL_SOURCES = "'blind','natepan'"


def load_env(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for line in path.read_text().splitlines():
        line = line.strip()
        if line and not line.startswith("#") and "=" in line:
            key, value = line.split("=", 1)
            values[key] = value.strip().strip('"').strip("'")
    return values


def mariadb(env: dict[str, str], container: str, sql: str) -> list[list[str]]:
    result = subprocess.run([
        "docker", "exec", "-i", container, "mariadb", "-u", env["MARIADB_USER"],
        f"-p{env['MARIADB_PASSWORD']}", env["MARIADB_DATABASE"], "-N", "-B", "--raw", "-e", sql,
    ], capture_output=True, text=True, check=False)
    if result.returncode:
        raise RuntimeError(result.stderr[:800])
    return [line.split("\t") for line in result.stdout.splitlines() if line]


def decode(value: str) -> str:
    return base64.b64decode(value).decode("utf-8") if value and value != "NULL" else ""


def encoded(value: str) -> str:
    return base64.b64encode(value.encode("utf-8")).decode("ascii")


def collect_changes(env: dict[str, str], container: str, table: str, fields: tuple[str, ...], where: str) -> list[tuple[str, str, str, str]]:
    # MariaDB TO_BASE64 inserts visual line breaks every 76 chars; strip them
    # so one DB row remains one TSV line even for LONGTEXT bodies.
    columns = ", ".join(f"REPLACE(TO_BASE64(COALESCE({field}, '')), CHAR(10), '')" for field in fields)
    rows = mariadb(env, container, f"SELECT id, {columns} FROM {table} WHERE {where}")
    changes: list[tuple[str, str, str, str]] = []
    for row in rows:
        row_id, values = row[0], row[1:]
        for field, value in zip(fields, values):
            original = decode(value)
            sanitized = sanitize_crawled_text(original) or ""
            if sanitized != original:
                changes.append((row_id, field, original, sanitized))
    return changes


def create_backup_table(env: dict[str, str], container: str) -> None:
    mariadb(env, container, """
        CREATE TABLE IF NOT EXISTS crawl_community_reference_sanitization_backup (
            run_id VARCHAR(40) NOT NULL,
            table_name VARCHAR(32) NOT NULL,
            row_id VARCHAR(64) NOT NULL,
            field_name VARCHAR(64) NOT NULL,
            original_value LONGTEXT NOT NULL,
            created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
            PRIMARY KEY (run_id, table_name, row_id, field_name)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
    """)


def apply_changes(env: dict[str, str], container: str, table: str, changes: list[tuple[str, str, str, str]], run_id: str) -> None:
    allowed = {"content", "title", "user_title", "body_raw", "body_published", "partner_body_raw", "partner_body_published", "promo_title", "source_original_title", "source_original_body"}
    for row_id, field, original, value in changes:
        if field not in allowed:
            raise ValueError(f"unexpected field: {field}")
        mariadb(env, container, f"""
            INSERT INTO crawl_community_reference_sanitization_backup
                (run_id, table_name, row_id, field_name, original_value)
            VALUES ('{run_id}', '{table}', '{row_id}', '{field}', CONVERT(FROM_BASE64('{encoded(original)}') USING utf8mb4));
            UPDATE {table}
            SET {field}=CONVERT(FROM_BASE64('{encoded(value)}') USING utf8mb4)
            WHERE id='{row_id}';
        """)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--env", choices=("dev", "prod"), default="prod")
    parser.add_argument("--apply", action="store_true")
    args = parser.parse_args()
    env, container = load_env(ENV_FILES[args.env]), CONTAINERS[args.env]

    bank = collect_changes(env, container, "example_bank", ("content", "title"), f"LOWER(source) IN ({CRAWL_SOURCES})")
    # Provenance links make this scope safe: do not match arbitrary user text.
    posts = collect_changes(
        env, container, "posts",
        ("title", "user_title", "promo_title", "body_raw", "body_published", "partner_body_raw", "partner_body_published", "source_original_title", "source_original_body"),
        f"source_example_id IN (SELECT id FROM example_bank WHERE LOWER(source) IN ({CRAWL_SOURCES}))",
    )
    print(f"env={args.env} example_bank_changes={len(bank)} post_changes={len(posts)} apply={args.apply}")
    for row_id, field, _original, value in (bank + posts)[:20]:
        print(f"  {row_id} {field}: {value[:100]!r}")
    if not args.apply:
        print("dry-run only; verify dev output, then pass --apply (prod requires explicit approval).")
        return 0
    run_id = datetime.now(timezone.utc).strftime("community-ref-%Y%m%dT%H%M%SZ")
    create_backup_table(env, container)
    apply_changes(env, container, "example_bank", bank, run_id)
    apply_changes(env, container, "posts", posts, run_id)
    print(f"updated={len(bank) + len(posts)} backup_run_id={run_id}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
