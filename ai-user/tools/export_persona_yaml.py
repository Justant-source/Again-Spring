#!/usr/bin/env python3
"""ai-user/tools/export_persona_yaml.py — DB(읽기 전용) → 150명 profile.yml/voice.yml 재작성.

(.request/persona-diversity-v4/01-wp1-persona-data.md §7-b)

이 도구는 여기서는 만들기만 한다 — 실제 150명 전체 실행은 Phase 3 이후 Fable이 한다.
기본 동작은 dry-run(파일 미기록, 계획만 출력). 실제로 쓰려면 --apply.

Usage (dev DB, 호스트 포트 매핑 기준):
  python3 ai-user/tools/export_persona_yaml.py --env-file env/.env.dev --db-port 3309
  python3 ai-user/tools/export_persona_yaml.py --env-file env/.env.dev --db-port 3309 \\
      --ids <id1>,<id2> --apply
"""
from __future__ import annotations

import argparse
import json as _json
import sys
from pathlib import Path
from typing import Any

import yaml

REPO_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_PROFILES_DIR = REPO_ROOT / "ai-user" / "docs" / "personas" / "profiles"

# 계약 1 (00-shared.md): 23~29→20s_late · 30~36→30s_early · 37~39→30s_late · 40~49→40s
_AGE_BAND_CEILINGS = [(29, "20s_late"), (36, "30s_early"), (39, "30s_late")]


def age_band(age_years: int) -> str:
    for ceiling, label in _AGE_BAND_CEILINGS:
        if age_years <= ceiling:
            return label
    return "40s"


def parse_env_file(path: Path) -> dict[str, str]:
    """최소 .env 파서 — KEY=VALUE 줄, '#' 주석, 선택적 따옴표. 값은 반환만 하고 출력하지 않는다."""
    out: dict[str, str] = {}
    if not path.is_file():
        return out
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, _, value = line.partition("=")
        out[key.strip()] = value.strip().strip('"').strip("'")
    return out


def db_config_from_env(env: dict[str, str], *, host: str, port: int) -> dict[str, Any]:
    return {
        "host": host,
        "port": port,
        "user": env.get("MARIADB_USER", "againspring"),
        "password": env.get("MARIADB_PASSWORD", ""),
        "database": env.get("MARIADB_DATABASE", "againspring_dev"),
        "charset": "utf8mb4",
    }


# ── 순수 함수 (DB·파일시스템 없이 단위 테스트 가능) ─────────────────────────

def build_identity_block(row: dict[str, Any]) -> dict[str, Any]:
    """계약 1 신규 컬럼 → profile.yml identity: 블록."""
    identity: dict[str, Any] = {
        "age_years": row["age_years"],
        "gender": row["gender"],
        "marital": row["marital"],
        "job_type": row["job_type"],
        "tier": row["tier"],
    }
    if row.get("married_years") is not None:
        identity["married_years"] = row["married_years"]
    identity["has_kids"] = bool(row.get("has_kids"))
    if row.get("job_title"):
        identity["job_title"] = row["job_title"]
    if row.get("style_axes"):
        identity["style_axes"] = row["style_axes"]
    return identity


def merge_profile_yaml(existing: dict[str, Any] | None, row: dict[str, Any]) -> dict[str, Any]:
    """기존 profile.yml(dict, 없으면 None) + DB 신규 축 → 병합된 dict.

    id/email/nickname/demographics 등 기존 키는 보존하고 identity: 블록만 갱신/추가한다.
    기존 파일이 없으면(101~150 신규) 최소 골격을 만든다.
    """
    profile: dict[str, Any] = dict(existing) if existing else {}
    profile["id"] = row["id"]
    if not profile.get("email"):
        profile["email"] = row.get("email") or f"ai-user-{row['id'][:8]}@againspring.internal"
    if not profile.get("nickname"):
        profile["nickname"] = row.get("nickname") or row["id"][:8]
    demographics = dict(profile.get("demographics") or {})
    demographics.setdefault("age_band", age_band(row["age_years"]))
    demographics.setdefault("gender", row["gender"])
    if row.get("region"):
        demographics.setdefault("region", row["region"])
    profile["demographics"] = demographics
    profile["identity"] = build_identity_block(row)
    return profile


def render_voice_yaml(row: dict[str, Any]) -> dict[str, Any]:
    """voice_profile(JSON, DB) → voice.yml 전체 내용(dict). 있는 그대로 재직렬화."""
    voice_profile = row.get("voice_profile") or {}
    voice: dict[str, Any] = dict(voice_profile)
    voice["persona_id"] = row["id"]
    voice["nickname"] = row.get("nickname") or row["id"][:8]
    voice["voice_type"] = voice_profile.get("voice_type", row.get("voice_type", ""))
    return voice


def resolve_profile_dir_name(existing_dirs_by_id: dict[str, str], persona_id: str,
                              next_seq: list[int]) -> str:
    """기존 profiles/*/profile.yml에서 id로 찾은 디렉터리명을 재사용하고, 없으면 다음 순번을
    ai-user-XXX로 새로 배정한다. next_seq=[다음 후보 번호] (호출 간 공유되는 mutable 카운터)."""
    if persona_id in existing_dirs_by_id:
        return existing_dirs_by_id[persona_id]
    used_names = set(existing_dirs_by_id.values())
    name = f"ai-user-{next_seq[0]:03d}"
    while name in used_names:
        next_seq[0] += 1
        name = f"ai-user-{next_seq[0]:03d}"
    next_seq[0] += 1
    return name


def render_specsheet_row(row: dict[str, Any], dir_name: str) -> str:
    nickname = row.get("nickname") or row["id"][:8]
    return (f"| {dir_name} | {row['id'][:8]}… | {nickname} | {row['age_years']} | {row['gender']} | "
            f"{row['marital']} | {row['job_type']} | {row['tier']} |")


def render_specsheet(rows_with_dirs: list[tuple[dict[str, Any], str]]) -> str:
    header = (
        "# 150인 페르소나 스펙시트 — persona-diversity-v4 (WP1)\n"
        "# 자동 생성: ai-user/tools/export_persona_yaml.py. 손으로 고치지 말 것.\n"
        "# 내부 참조용. 외부 공개 금지.\n\n"
        "| 디렉터리 | id | 닉네임 | 나이 | 성별 | 결혼 | 직업군 | tier |\n"
        "|---|---|---|---|---|---|---|---|\n"
    )
    body = "\n".join(render_specsheet_row(row, dir_name) for row, dir_name in rows_with_dirs)
    return header + body + "\n"


# ── DB·파일시스템 I/O (단위 테스트에서 호출하지 않음) ───────────────────────

def fetch_active_personas(conn, ids: list[str] | None) -> list[dict[str, Any]]:
    with conn.cursor() as cur:
        base_sql = (
            "SELECT p.id, u.nickname, p.age_years, p.gender, p.marital, p.married_years, "
            "p.has_kids, p.job_type, p.job_title, p.tier, p.style_axes, p.voice_profile "
            "FROM personas p JOIN users u ON u.id = p.id WHERE p.active = 1"
        )
        if ids:
            placeholders = ",".join(["%s"] * len(ids))
            cur.execute(base_sql + f" AND p.id IN ({placeholders}) ORDER BY p.id", ids)
        else:
            cur.execute(base_sql + " ORDER BY p.id")
        rows = list(cur.fetchall())
    out = []
    for r in rows:
        row = dict(r)
        for key in ("style_axes", "voice_profile"):
            val = row.get(key)
            if isinstance(val, str) and val:
                row[key] = _json.loads(val)
        out.append(row)
    return out


def scan_existing_dirs(profiles_dir: Path) -> dict[str, str]:
    """profiles/*/profile.yml의 id → 디렉터리명 매핑."""
    out: dict[str, str] = {}
    if not profiles_dir.is_dir():
        return out
    for child in sorted(profiles_dir.iterdir()):
        profile_file = child / "profile.yml"
        if not child.is_dir() or not profile_file.is_file():
            continue
        try:
            data = yaml.safe_load(profile_file.read_text(encoding="utf-8")) or {}
        except Exception:
            continue
        pid = data.get("id")
        if pid:
            out[pid] = child.name
    return out


def load_existing_profile(profiles_dir: Path, dir_name: str) -> dict[str, Any] | None:
    path = profiles_dir / dir_name / "profile.yml"
    if not path.is_file():
        return None
    try:
        return yaml.safe_load(path.read_text(encoding="utf-8")) or {}
    except Exception:
        return None


def write_persona_files(profiles_dir: Path, dir_name: str, profile: dict[str, Any],
                         voice: dict[str, Any]) -> None:
    target = profiles_dir / dir_name
    target.mkdir(parents=True, exist_ok=True)
    (target / "profile.yml").write_text(
        yaml.safe_dump(profile, allow_unicode=True, sort_keys=False), encoding="utf-8")
    (target / "voice.yml").write_text(
        yaml.safe_dump(voice, allow_unicode=True, sort_keys=False), encoding="utf-8")


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--env-file", type=Path, default=REPO_ROOT / "env" / ".env.dev",
                   help="DB 자격 소스 (기본 env/.env.dev — MARIADB_USER/PASSWORD/DATABASE)")
    p.add_argument("--db-host", default="127.0.0.1", help="MariaDB 접속 호스트 (기본 127.0.0.1)")
    p.add_argument("--db-port", type=int, default=3309, help="MariaDB 접속 포트 (dev 호스트 매핑 기본 3309)")
    p.add_argument("--profiles-dir", type=Path, default=DEFAULT_PROFILES_DIR)
    p.add_argument("--ids", default=None, help="콤마구분 persona id 목록 (기본: 활성 전체)")
    p.add_argument("--apply", action="store_true", help="실제로 파일을 쓴다 (기본은 dry-run)")
    return p.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    env = parse_env_file(args.env_file)
    db_config = db_config_from_env(env, host=args.db_host, port=args.db_port)

    import pymysql
    import pymysql.cursors
    conn = pymysql.connect(cursorclass=pymysql.cursors.DictCursor, **db_config)
    try:
        ids = [s.strip() for s in args.ids.split(",") if s.strip()] if args.ids else None
        rows = fetch_active_personas(conn, ids)
    finally:
        conn.close()

    if not rows:
        print("no active personas found", file=sys.stderr)
        return 1

    existing_dirs_by_id = scan_existing_dirs(args.profiles_dir)
    next_seq = [1]
    rows_with_dirs: list[tuple[dict[str, Any], str]] = []
    for row in rows:
        dir_name = resolve_profile_dir_name(existing_dirs_by_id, row["id"], next_seq)
        existing_dirs_by_id[row["id"]] = dir_name
        rows_with_dirs.append((row, dir_name))

        existing_profile = load_existing_profile(args.profiles_dir, dir_name)
        profile = merge_profile_yaml(existing_profile, row)
        voice = render_voice_yaml(row)

        if args.apply:
            write_persona_files(args.profiles_dir, dir_name, profile, voice)
        else:
            print(f"[dry-run] would write {dir_name}/profile.yml + voice.yml (id={row['id'][:8]}...)")

    if args.apply:
        (args.profiles_dir.parent / "_specsheet.md").write_text(
            render_specsheet(rows_with_dirs), encoding="utf-8")
        print(f"wrote {len(rows_with_dirs)} persona dirs + _specsheet.md under {args.profiles_dir}")
    else:
        print(f"[dry-run] would regenerate _specsheet.md with {len(rows_with_dirs)} rows")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
