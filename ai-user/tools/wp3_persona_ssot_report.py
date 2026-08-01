#!/usr/bin/env python3
"""WP3 SSOT report: prod DB personas ↔ local YAML profiles.

Read-only. Compares active persona IDs in MariaDB (or YAML-only mode) against
`ai-user/docs/personas/profiles/*/profile.yml` (fallback: voice.yml `persona_id`).

Usage (from repo root):

  # YAML corpus only (no DB)
  python3 ai-user/tools/wp3_persona_ssot_report.py --from-yaml-only

  # prod DB via docker (default) + YAML
  python3 ai-user/tools/wp3_persona_ssot_report.py

  # explicit JDBC-ish URL or host overrides
  python3 ai-user/tools/wp3_persona_ssot_report.py \\
      --jdbc 'mysql://root:pass@127.0.0.1:3307/againspring_prod'

  WP3_DB_HOST / WP3_DB_USER / WP3_DB_PASSWORD / WP3_DB_NAME
  (or WP1B_DB_*) also work — same pattern as wp1b_purify_voices.py.

Exit 0 always on successful report generation (mismatches are data, not errors).
"""

from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
from collections import Counter
from pathlib import Path
from typing import Any
from urllib.parse import unquote, urlparse

sys.path.insert(0, str(Path(__file__).resolve().parent))
from baseline_lib import DEFAULT_PROFILES_DIR, read_yaml  # noqa: E402

try:
    import pymysql
except ImportError:  # pragma: no cover
    pymysql = None  # type: ignore[assignment]


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter
    )
    p.add_argument(
        "--profiles-dir",
        type=Path,
        default=DEFAULT_PROFILES_DIR,
        help=f"profiles root (default: {DEFAULT_PROFILES_DIR})",
    )
    p.add_argument(
        "--from-yaml-only",
        action="store_true",
        help="skip DB; report YAML inventory + voice_type distribution only",
    )
    p.add_argument(
        "--jdbc",
        default=None,
        help="optional mysql://user:pass@host:port/db (overrides env/docker)",
    )
    p.add_argument("--out", type=Path, default=None, help="optional JSON report path")
    return p.parse_args()


def load_yaml_profiles(profiles_dir: Path) -> dict[str, dict[str, Any]]:
    """Return {persona_id: {slug, voice_type, source_file}}."""
    out: dict[str, dict[str, Any]] = {}
    if not profiles_dir.is_dir():
        return out
    for slug_dir in sorted(p for p in profiles_dir.iterdir() if p.is_dir()):
        profile_path = slug_dir / "profile.yml"
        voice_path = slug_dir / "voice.yml"
        pid = ""
        voice_type = ""
        source = ""
        if profile_path.is_file():
            data = read_yaml(profile_path)
            pid = str(data.get("id") or "").strip()
            activity = data.get("activity") if isinstance(data.get("activity"), dict) else {}
            voice_type = str(activity.get("voice") or "").strip().upper()
            source = str(profile_path)
        if (not pid or not voice_type) and voice_path.is_file():
            voice = read_yaml(voice_path)
            if not pid:
                pid = str(voice.get("persona_id") or "").strip()
            if not voice_type:
                voice_type = str(voice.get("voice_type") or "").strip().upper()
            if not source:
                source = str(voice_path)
        if not pid:
            continue
        out[pid] = {
            "slug": slug_dir.name,
            "voice_type": voice_type or "UNKNOWN",
            "source_file": source,
        }
    return out


def parse_jdbc(url: str) -> dict[str, Any]:
    """Parse mysql://user:pass@host:port/db into connect kwargs."""
    raw = url.strip()
    if raw.startswith("jdbc:"):
        raw = raw[len("jdbc:") :]
    if "://" not in raw:
        raw = "mysql://" + raw
    parsed = urlparse(raw)
    if parsed.scheme not in {"mysql", "mariadb"}:
        raise SystemExit(f"unsupported jdbc scheme: {parsed.scheme!r} (want mysql/mariadb)")
    db = (parsed.path or "/").lstrip("/").split("?")[0]
    if not db:
        raise SystemExit("--jdbc URL must include database path")
    return {
        "host": parsed.hostname or "127.0.0.1",
        "port": parsed.port or 3306,
        "user": unquote(parsed.username or "root"),
        "password": unquote(parsed.password or ""),
        "database": db,
    }


def connect_prod(jdbc: str | None) -> Any:
    if pymysql is None:
        raise SystemExit("pymysql required: pip install pymysql")
    if jdbc:
        kw = parse_jdbc(jdbc)
        return pymysql.connect(charset="utf8mb4", autocommit=True, **kw)

    host = os.getenv("WP3_DB_HOST") or os.getenv("WP1B_DB_HOST")
    if not host:
        host = subprocess.check_output(
            [
                "docker",
                "inspect",
                "againspring-mariadb-prod",
                "--format",
                "{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}",
            ],
            text=True,
        ).strip().split()[0]
    password = os.getenv("WP3_DB_PASSWORD") or os.getenv("WP1B_DB_PASSWORD")
    if not password:
        password = subprocess.check_output(
            ["docker", "exec", "againspring-mariadb-prod", "printenv", "MARIADB_ROOT_PASSWORD"],
            text=True,
        ).strip()
    user = os.getenv("WP3_DB_USER") or os.getenv("WP1B_DB_USER", "root")
    database = os.getenv("WP3_DB_NAME") or os.getenv("WP1B_DB_NAME", "againspring_prod")
    port = int(os.getenv("WP3_DB_PORT") or os.getenv("WP1B_DB_PORT") or "3306")
    return pymysql.connect(
        host=host,
        port=port,
        user=user,
        password=password,
        database=database,
        charset="utf8mb4",
        autocommit=True,
    )


def fetch_db_personas(conn: Any) -> list[dict[str, Any]]:
    sql = """
        SELECT id, active,
               COALESCE(
                 JSON_UNQUOTE(JSON_EXTRACT(voice_profile, '$.voice_type')),
                 ''
               ) AS voice_type
        FROM personas
    """
    with conn.cursor() as cur:
        cur.execute(sql)
        rows = cur.fetchall()
    out: list[dict[str, Any]] = []
    for row in rows:
        if isinstance(row, dict):
            pid, active, vt = row["id"], row["active"], row["voice_type"]
        else:
            pid, active, vt = row[0], row[1], row[2]
        out.append(
            {
                "id": str(pid).strip(),
                "active": bool(active),
                "voice_type": str(vt or "").strip().upper() or "UNKNOWN",
            }
        )
    return out


def build_report(
    *,
    profiles: dict[str, dict[str, Any]],
    db_rows: list[dict[str, Any]] | None,
    from_yaml_only: bool,
    profiles_dir: Path,
) -> dict[str, Any]:
    profile_dirs = len(profiles)
    yaml_ids = set(profiles)
    yaml_voice = Counter(v["voice_type"] for v in profiles.values())

    if from_yaml_only or db_rows is None:
        return {
            "mode": "yaml_only",
            "profiles_dir": str(profiles_dir),
            "db_active_count": None,
            "db_total_count": None,
            "profile_dir_count": profile_dirs,
            "missing_in_docs": [],
            "missing_in_db": [],
            "missing_in_docs_count": None,
            "missing_in_db_count": None,
            "voice_type_distribution": {
                "yaml": dict(sorted(yaml_voice.items())),
                "db_active": None,
            },
            "note": "DB skipped (--from-yaml-only or unreachable)",
        }

    active = [r for r in db_rows if r["active"]]
    db_active_ids = {r["id"] for r in active if r["id"]}
    db_voice = Counter(r["voice_type"] for r in active)
    missing_in_docs = sorted(db_active_ids - yaml_ids)
    missing_in_db = sorted(yaml_ids - db_active_ids)

    return {
        "mode": "db+yaml",
        "profiles_dir": str(profiles_dir),
        "db_active_count": len(db_active_ids),
        "db_total_count": len(db_rows),
        "profile_dir_count": profile_dirs,
        "missing_in_docs": missing_in_docs,
        "missing_in_db": missing_in_db,
        "missing_in_docs_count": len(missing_in_docs),
        "missing_in_db_count": len(missing_in_db),
        "voice_type_distribution": {
            "yaml": dict(sorted(yaml_voice.items())),
            "db_active": dict(sorted(db_voice.items())),
        },
    }


def print_report(report: dict[str, Any]) -> None:
    print(f"mode              : {report['mode']}")
    print(f"profiles_dir      : {report['profiles_dir']}")
    print(f"DB active         : {report['db_active_count']}")
    print(f"DB total          : {report['db_total_count']}")
    print(f"profile dirs      : {report['profile_dir_count']}")
    if report["mode"] == "db+yaml":
        print(f"missing-in-docs   : {report['missing_in_docs_count']}")
        for pid in report["missing_in_docs"][:30]:
            print(f"  - {pid}")
        if report["missing_in_docs_count"] > 30:
            print(f"  ... +{report['missing_in_docs_count'] - 30} more")
        print(f"missing-in-DB     : {report['missing_in_db_count']}")
        for pid in report["missing_in_db"][:30]:
            print(f"  - {pid}")
        if report["missing_in_db_count"] > 30:
            print(f"  ... +{report['missing_in_db_count'] - 30} more")
    vt = report["voice_type_distribution"]
    print(f"voice_type (YAML) : {vt['yaml']}")
    print(f"voice_type (DB)   : {vt['db_active']}")
    if report.get("note"):
        print(f"note              : {report['note']}")


def main() -> int:
    args = parse_args()
    profiles = load_yaml_profiles(args.profiles_dir)
    if not profiles and not args.profiles_dir.is_dir():
        print(f"ERROR: profiles dir missing: {args.profiles_dir}", file=sys.stderr)
        return 2

    db_rows: list[dict[str, Any]] | None = None
    if not args.from_yaml_only:
        try:
            conn = connect_prod(args.jdbc)
            try:
                db_rows = fetch_db_personas(conn)
            finally:
                conn.close()
        except Exception as e:
            print(f"WARN: DB unreachable ({e}); falling back to --from-yaml-only", file=sys.stderr)
            args.from_yaml_only = True

    report = build_report(
        profiles=profiles,
        db_rows=db_rows,
        from_yaml_only=args.from_yaml_only or db_rows is None,
        profiles_dir=args.profiles_dir,
    )
    print_report(report)

    if args.out:
        args.out.parent.mkdir(parents=True, exist_ok=True)
        args.out.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(f"wrote {args.out}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
