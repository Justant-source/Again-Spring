#!/usr/bin/env python3
"""
Run SQL against the dev MariaDB either via exposed host port or other direct access.

This is used as a fallback in restricted environments where docker socket access
is blocked but the dev DB is published on localhost:3309.
"""

from __future__ import annotations

import argparse
import os
import sys
from pathlib import Path

import pymysql


DEFAULT_ENV_FILE = Path(__file__).resolve().parents[3] / "env" / ".env.prod"


def read_env_file(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    if not path.exists():
        return values
    for line in path.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip().strip("\"'")
    return values


def resolve_config(env_file: Path) -> dict[str, str | int]:
    file_values = read_env_file(env_file)

    def pick(key: str, default: str) -> str:
        return os.environ.get(key) or file_values.get(key) or default

    return {
        "host": os.environ.get("DB_HOST", "127.0.0.1"),
        "port": int(os.environ.get("DB_PORT", "3309")),
        "user": pick("MARIADB_USER", "againspring"),
        "password": pick("MARIADB_PASSWORD", ""),
        "database": pick("MARIADB_DATABASE", "againspring"),
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--env-file", default=str(DEFAULT_ENV_FILE))
    parser.add_argument("--query")
    parser.add_argument("--raw", action="store_true")
    args = parser.parse_args()

    config = resolve_config(Path(args.env_file))
    if not config["password"]:
        print("ERROR: MARIADB_PASSWORD를 찾을 수 없습니다.", file=sys.stderr)
        return 1

    sql = args.query if args.query is not None else sys.stdin.read()
    if not sql.strip():
        print("ERROR: SQL이 비어 있습니다.", file=sys.stderr)
        return 1

    conn = pymysql.connect(
        host=str(config["host"]),
        port=int(config["port"]),
        user=str(config["user"]),
        password=str(config["password"]),
        database=str(config["database"]),
        charset="utf8mb4",
        autocommit=True,
    )
    try:
        with conn.cursor() as cur:
            for statement in [part.strip() for part in sql.split(";") if part.strip()]:
                cur.execute(statement)

            if cur.description:
                rows = cur.fetchall()
                if args.raw:
                    for row in rows:
                        print("\t".join("" if value is None else str(value) for value in row))
                else:
                    print(rows)
    finally:
        conn.close()

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
