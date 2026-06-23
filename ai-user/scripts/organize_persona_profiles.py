#!/usr/bin/env python3
"""Generate human-readable persona summaries and migrate legacy file history to DB."""

from __future__ import annotations

import argparse
import hashlib
import json
import shutil
import subprocess
from dataclasses import dataclass
from decimal import Decimal
from datetime import datetime, timezone
from pathlib import Path
from typing import Any
from zoneinfo import ZoneInfo

import yaml

try:
    import pymysql
except Exception:  # pragma: no cover - optional dependency for DB import
    pymysql = None


@dataclass
class DbConfig:
    host: str
    port: int
    user: str
    password: str
    database: str


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--profiles-dir",
        default="ai-user/docs/personas/profiles",
        help="persona profiles root",
    )
    parser.add_argument("--env-file", default="env/.env.dev", help="dotenv file for DB defaults")
    parser.add_argument("--db-host", default=None)
    parser.add_argument("--db-port", type=int, default=None)
    parser.add_argument("--db-user", default=None)
    parser.add_argument("--db-password", default=None)
    parser.add_argument("--db-name", default=None)
    parser.add_argument("--skip-db-import", action="store_true")
    parser.add_argument("--skip-summaries", action="store_true")
    parser.add_argument("--skip-cleanup", action="store_true")
    return parser.parse_args()


def load_dotenv(path: str) -> dict[str, str]:
    values: dict[str, str] = {}
    env_path = Path(path)
    if not env_path.exists():
        return values
    for line in env_path.read_text().splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#") or "=" not in stripped:
            continue
        key, value = stripped.split("=", 1)
        values[key.strip()] = value.strip()
    return values


def build_db_config(args: argparse.Namespace, env_values: dict[str, str]) -> DbConfig:
    return DbConfig(
        host=args.db_host or "127.0.0.1",
        port=args.db_port or 3309,
        user=args.db_user or env_values.get("MARIADB_USER", "againspring"),
        password=args.db_password or env_values.get("MARIADB_PASSWORD", ""),
        database=args.db_name or env_values.get("MARIADB_DATABASE", "againspring_dev"),
    )


def read_yaml(path: Path) -> dict[str, Any]:
    with path.open("r", encoding="utf-8") as fh:
        loaded = yaml.safe_load(fh) or {}
    if not isinstance(loaded, dict):
        return {}
    return loaded


def tracked_head_text(path: Path) -> str | None:
    try:
        subprocess.run(
            ["git", "ls-files", "--error-unmatch", str(path)],
            check=True,
            cwd=Path(__file__).resolve().parents[2],
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
        result = subprocess.run(
            ["git", "show", f"HEAD:{path.as_posix()}"],
            check=True,
            cwd=Path(__file__).resolve().parents[2],
            capture_output=True,
            text=True,
        )
        return result.stdout
    except Exception:
        return None


def read_text_with_fallback(path: Path) -> str | None:
    try:
        return path.read_text(encoding="utf-8")
    except OSError:
        return tracked_head_text(path)


def can_recover_file(path: Path) -> bool:
    return read_text_with_fallback(path) is not None


def top_entry(data: dict[str, Any], absolute: bool = False) -> str:
    if not data:
        return "-"
    items = []
    for key, value in data.items():
        try:
            numeric = float(value)
        except Exception:
            continue
        items.append((key, numeric))
    if not items:
        return "-"
    if absolute:
        items.sort(key=lambda item: abs(item[1]), reverse=True)
    else:
        items.sort(key=lambda item: item[1], reverse=True)
    key, value = items[0]
    return f"{key} {value:.2f}" if absolute else f"{key} {value:.1f}"


def summary_list(block: dict[str, Any], key: str) -> str:
    value = block.get(key)
    if isinstance(value, list) and value:
        return ", ".join(str(item) for item in value[:3])
    return "-"


def render_summary(profile_dir: Path, profile: dict[str, Any], voice: dict[str, Any]) -> str:
    nickname = str(profile.get("nickname", profile_dir.name))
    activity = profile.get("activity") or {}
    demographics = profile.get("demographics") or {}
    orientation = profile.get("orientation") or {}
    interests = profile.get("interests") or {}
    bias_profile = profile.get("bias_profile") or {}
    lexicon = voice.get("lexicon") or {}
    hot_buttons = voice.get("hot_buttons") or {}
    general_style = str(voice.get("general_style", "-")).strip() or "-"
    if "\n" in general_style:
        general_style = general_style.splitlines()[0].strip()

    return f"""# {nickname}

## Snapshot

- Nickname: `{nickname}`
- Persona key: `{profile_dir.name}`
- Archetype: `{", ".join(profile.get("archetype_preferences", [])[:3]) or "-"}`
- Voice: `{voice.get("voice_type", activity.get("voice", "-"))}`
- Tier: `{activity.get("tier", "-")}`
- Formality: `{voice.get("formality", "-")}`

## Demographics

- Age band: `{demographics.get("age_band", "-")}`
- Gender: `{demographics.get("gender", "-")}`
- Region: `{demographics.get("region", "-")}`
- Job: `{demographics.get("job", "-")}`
- Politics: `{orientation.get("political", "-")}`

## Behavior

- Daily target: `{activity.get("daily_target", "-")}`
- Slang level: `{activity.get("slang_level", "-")}`
- Top interest: `{top_entry(interests)}`
- Strongest bias: `{top_entry(bias_profile, absolute=True)}`

## Style

- General style: {general_style}
- Signature phrases: {summary_list(lexicon, "signature_phrases")}
- Hot buttons: {summary_list(hot_buttons, "triggers")}
"""


def generate_summaries(profiles_dir: Path) -> tuple[int, int]:
    generated = 0
    skipped = 0
    for profile_dir in sorted(p for p in profiles_dir.iterdir() if p.is_dir()):
        profile_file = profile_dir / "profile.yml"
        voice_file = profile_dir / "voice.yml"
        if not profile_file.exists() or not voice_file.exists():
            skipped += 1
            continue
        profile = read_yaml(profile_file)
        voice = read_yaml(voice_file)
        (profile_dir / "README.md").write_text(
            render_summary(profile_dir, profile, voice),
            encoding="utf-8",
        )
        generated += 1
    return generated, skipped


def json_object(value: Any) -> Any:
    if value is None:
        return None
    if isinstance(value, (dict, list)):
        return value
    if isinstance(value, str):
        value = value.strip()
        if not value:
            return None
        try:
            return json.loads(value)
        except json.JSONDecodeError:
            return None
    return None


def string_value(value: Any, default: str = "-") -> str:
    if value is None:
        return default
    if isinstance(value, Decimal):
        value = float(value)
    text = str(value).strip()
    return text or default


def number_value(value: Any, default: float = 0.0) -> float:
    if value is None:
        return default
    if isinstance(value, (int, float, Decimal)):
        return float(value)
    try:
        return float(str(value).strip())
    except Exception:
        return default


def map_of(value: Any) -> dict[str, Any]:
    if isinstance(value, dict):
        return value
    return {}


def to_double_map(value: Any) -> dict[str, float]:
    data = json_object(value)
    if not isinstance(data, dict):
        return {}
    result: dict[str, float] = {}
    for key, raw in data.items():
        try:
            result[str(key)] = float(raw)
        except Exception:
            continue
    return result


def to_double_list(value: Any) -> list[float] | None:
    data = json_object(value)
    if not isinstance(data, list):
        return None
    result: list[float] = []
    for item in data:
        try:
            result.append(float(item))
        except Exception:
            continue
    return result or None


def default_circadian() -> list[float]:
    return [
        0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
        0.1, 0.2, 0.4, 0.5, 0.5, 0.5,
        0.4, 0.4, 0.4, 0.5, 0.5, 0.6,
        0.7, 0.8, 0.9, 0.8, 0.6, 0.2,
    ]


def extract_first_archetype(profile: dict[str, Any]) -> str:
    raw = profile.get("archetype_preferences", profile.get("archetypePreferences"))
    if isinstance(raw, list) and raw:
        return str(raw[0])
    return "general"


def build_voice_profile(profile: dict[str, Any], voice: dict[str, Any]) -> dict[str, Any]:
    activity = map_of(profile.get("activity"))
    voice_profile: dict[str, Any] = {}
    if voice:
        voice_profile["voice_type"] = string_value(voice.get("voice_type", activity.get("voice")), "GENERAL")
        voice_profile["general_style"] = string_value(voice.get("general_style"), "")
        post_block = map_of(voice.get("post"))
        voice_profile["post_style"] = string_value(post_block.get("style"), "커뮤니티 반말 서술형")
        if isinstance(post_block.get("example_post_openers"), list):
            voice_profile["example_post_openers"] = post_block["example_post_openers"]
        comment_block = map_of(voice.get("comment"))
        voice_profile["comment_style"] = string_value(comment_block.get("style"), "공감형 짧은 댓글")
        if isinstance(comment_block.get("example_comments"), list):
            voice_profile["example_comments"] = comment_block["example_comments"]
        reply_block = map_of(voice.get("reply"))
        if isinstance(reply_block.get("example_replies"), list):
            voice_profile["example_replies"] = reply_block["example_replies"]
        voice_profile["like_criteria"] = string_value(voice.get("like_criteria", voice.get("likeCriteria")), "관심 주제에 공감 시")
        voice_profile["vote_notes"] = string_value(voice.get("vote_notes", voice.get("voteNotes")), "편향 없음")
        voice_profile["formality"] = string_value(voice.get("formality"), "casual")
        if "like_score" in voice:
            voice_profile["like_score"] = voice["like_score"]
        if "vote_score" in voice:
            voice_profile["vote_score"] = voice["vote_score"]
        voice_profile["political_voice_notes"] = string_value(voice.get("political_voice_notes"), "")
        voice_profile["age_voice_notes"] = string_value(voice.get("age_voice_notes"), "")
        for key in ("reactions", "lexicon", "writing_quirks", "hot_buttons"):
            if isinstance(voice.get(key), dict):
                voice_profile[key] = voice[key]
        voice_profile["age"] = string_value(voice.get("age"), "")
        gender = string_value(voice.get("gender"), "")
        if not gender:
            gender = string_value(map_of(profile.get("demographics")).get("gender"), "")
        voice_profile["gender"] = gender
        voice_profile["political_orientation"] = string_value(voice.get("political_orientation"), "")
        voice_profile["political_strength"] = string_value(voice.get("political_strength"), "")
    else:
        voice_profile["voice_type"] = string_value(activity.get("voice"), "GENERAL")
        voice_profile["general_style"] = string_value(profile.get("voice_description"), "일반 커뮤니티 사용자, 반말 위주")
        voice_profile["post_style"] = string_value(profile.get("post_style"), "서술형")
        voice_profile["comment_style"] = string_value(profile.get("comment_style"), "공감형")
        voice_profile["like_criteria"] = string_value(profile.get("like_criteria"), "관심 주제")
        voice_profile["vote_notes"] = string_value(profile.get("vote_tendency"), "중립")
    return voice_profile


def resolve_profile_email(profile_dir: Path, profile: dict[str, Any] | None = None) -> str:
    return f"{profile_dir.name}@againspring.internal"


def resolve_persona_id(cursor: Any, profile_dir: Path) -> str | None:
    profile = read_yaml(profile_dir / "profile.yml") if (profile_dir / "profile.yml").exists() else {}
    email = resolve_profile_email(profile_dir, profile)
    cursor.execute(
        """
        SELECT p.id
        FROM personas p
        JOIN users u ON u.id = p.id
        WHERE u.email = %s
        LIMIT 1
        """,
        (email,),
    )
    row = cursor.fetchone()
    return row["id"] if row else None


def ensure_persona_rows(cursor: Any, profiles_dir: Path) -> int:
    inserted = 0
    for profile_dir in sorted(p for p in profiles_dir.iterdir() if p.is_dir()):
        profile_file = profile_dir / "profile.yml"
        voice_file = profile_dir / "voice.yml"
        if not profile_file.exists() or not voice_file.exists():
            continue
        profile = read_yaml(profile_file)
        voice = read_yaml(voice_file)
        email = resolve_profile_email(profile_dir, profile)
        cursor.execute(
            """
            SELECT p.id
            FROM personas p
            JOIN users u ON u.id = p.id
            WHERE u.email = %s
            LIMIT 1
            """,
            (email,),
        )
        if cursor.fetchone():
            continue

        persona_id = string_value(profile.get("id"), "")
        if not persona_id:
            continue

        activity = map_of(profile.get("activity"))
        cursor.execute("SELECT id FROM users WHERE id = %s LIMIT 1", (persona_id,))
        if not cursor.fetchone():
            continue

        cursor.execute(
            """
            INSERT INTO personas
                (id, archetype, tier, voice_profile, interests, bias_profile, circadian,
                 slang_level, daily_target, active, created_at)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, 1, UTC_TIMESTAMP(3))
            ON DUPLICATE KEY UPDATE id = id
            """,
            (
                persona_id,
                extract_first_archetype(profile),
                string_value(activity.get("tier"), "REGULAR"),
                json.dumps(build_voice_profile(profile, voice), ensure_ascii=False),
                json.dumps(to_double_map(profile.get("interests")), ensure_ascii=False),
                json.dumps(to_double_map(profile.get("bias_profile", profile.get("biasProfile"))), ensure_ascii=False),
                json.dumps(to_double_list(activity.get("circadian")) or default_circadian(), ensure_ascii=False),
                f"{number_value(activity.get('slang_level', activity.get('slangLevel')), 0.5):.2f}",
                int(number_value(activity.get("daily_target", activity.get("dailyTarget")), 6)),
            ),
        )
        inserted += cursor.rowcount
    return inserted


def render_db_summary(profile_dir: Path, row: dict[str, Any]) -> str:
    voice_profile = json_object(row.get("voice_profile"))
    if not isinstance(voice_profile, dict):
        voice_profile = {}
    interests = to_double_map(row.get("interests"))
    bias_profile = to_double_map(row.get("bias_profile"))
    general_style = string_value(voice_profile.get("general_style"), "-")
    if "\n" in general_style:
        general_style = general_style.splitlines()[0].strip()
    return f"""# {string_value(row.get("nickname"), profile_dir.name)}

## Snapshot

- Nickname: `{string_value(row.get("nickname"), profile_dir.name)}`
- Persona key: `{profile_dir.name}`
- Archetype: `{string_value(row.get("archetype"), "-")}`
- Voice: `{string_value(voice_profile.get("voice_type"), "-")}`
- Tier: `{string_value(row.get("tier"), "-")}`
- Formality: `{string_value(voice_profile.get("formality"), "-")}`

## Demographics

- Age band: `{string_value(voice_profile.get("age"), "-")}`
- Gender: `{string_value(voice_profile.get("gender"), "-")}`
- Region: `-`
- Job: `-`
- Politics: `{string_value(voice_profile.get("political_orientation"), "-")}`

## Behavior

- Daily target: `{string_value(row.get("daily_target"), "-")}`
- Slang level: `{number_value(row.get("slang_level"), 0.0):.2f}`
- Top interest: `{top_entry(interests)}`
- Strongest bias: `{top_entry(bias_profile, absolute=True)}`

## Style

- General style: {general_style}
- Signature phrases: {summary_list(map_of(voice_profile.get("lexicon")), "signature_phrases")}
- Hot buttons: {summary_list(map_of(voice_profile.get("hot_buttons")), "triggers")}
"""


def generate_db_backed_summaries(cursor: Any, profiles_dir: Path) -> int:
    generated = 0
    for profile_dir in sorted(p for p in profiles_dir.iterdir() if p.is_dir()):
        if (profile_dir / "profile.yml").exists() and (profile_dir / "voice.yml").exists():
            continue
        email = f"{profile_dir.name}@againspring.internal"
        cursor.execute(
            """
            SELECT u.nickname, p.archetype, p.tier, p.voice_profile, p.interests, p.bias_profile,
                   p.slang_level, p.daily_target
            FROM personas p
            JOIN users u ON u.id = p.id
            WHERE u.email = %s
            LIMIT 1
            """,
            (email,),
        )
        row = cursor.fetchone()
        if not row:
            continue
        (profile_dir / "README.md").write_text(render_db_summary(profile_dir, row), encoding="utf-8")
        generated += 1
    return generated


def extract_history_body(block: str, kind: str) -> str | None:
    stripped = block.strip()
    if not stripped:
        return None
    if kind == "posts":
        marker = None
        for line in block.splitlines():
            if line.startswith("### "):
                marker = line
                break
        if not marker:
            return None
        _, _, remainder = block.partition(marker)
        body = remainder.split("\n", 1)[1] if "\n" in remainder else ""
        body = body.strip()
        return body or None
    marker = "\n> "
    if marker in block:
        return block.split(marker, 1)[1].strip() or None
    stripped_leading = block.lstrip()
    if stripped_leading.startswith("> "):
        return stripped_leading[2:].strip() or None
    return None


def header_cells(block: str) -> list[str]:
    first_line = next((line for line in block.splitlines() if "|" in line), "")
    if "|" not in first_line:
        return []
    return [cell.strip() for cell in first_line.split("|") if cell.strip()]


def parse_legacy_ts(value: str) -> datetime:
    naive = datetime.strptime(value, "%Y-%m-%d %H:%M")
    return naive.replace(tzinfo=ZoneInfo("Asia/Seoul")).astimezone(timezone.utc)


def history_hash(kind: str, post_id: str, content: str) -> str:
    payload = f"{kind.upper()}\n{post_id}\n{content}".encode("utf-8")
    return hashlib.sha256(payload).hexdigest()


def import_history_file(cursor: Any, persona_id: str, file_path: Path, kind: str) -> int:
    if not file_path.exists():
        return 0
    raw = read_text_with_fallback(file_path)
    if raw is None:
        return 0
    inserted = 0
    for block in raw.split("\n---"):
        body = extract_history_body(block, kind)
        if not body:
            continue
        cells = header_cells(block)
        if not cells:
            continue
        created_at = parse_legacy_ts(cells[0]).strftime("%Y-%m-%d %H:%M:%S.%f")[:-3]
        category = cells[1] if kind == "posts" and len(cells) > 1 else ""
        post_id = cells[2] if len(cells) > 2 else ""
        cursor.execute(
            """
            INSERT INTO persona_history_entries
                (persona_id, entry_type, target_post_id, category, content_hash, content, created_at)
            VALUES (%s, %s, %s, %s, %s, %s, %s)
            ON DUPLICATE KEY UPDATE id = id
            """,
            (
                persona_id,
                "POST" if kind == "posts" else "COMMENT",
                post_id,
                category,
                history_hash(kind, post_id, body),
                body,
                created_at,
            ),
        )
        inserted += cursor.rowcount
    return inserted


def import_life_state(cursor: Any, persona_id: str, file_path: Path) -> int:
    if not file_path.exists():
        return 0
    raw = read_text_with_fallback(file_path)
    if raw is None:
        return 0
    payload = json.loads(raw)
    updated_at = payload.get("updatedAt")
    if updated_at:
        try:
            parsed = datetime.fromisoformat(updated_at.replace("Z", "+00:00"))
        except ValueError:
            parsed = datetime.now(tz=timezone.utc)
    else:
        parsed = datetime.now(tz=timezone.utc)
    cursor.execute(
        """
        INSERT INTO persona_life_state (persona_id, casual_streak, ongoing_situation, updated_at)
        VALUES (%s, %s, %s, %s)
        ON DUPLICATE KEY UPDATE
            casual_streak = VALUES(casual_streak),
            ongoing_situation = VALUES(ongoing_situation),
            updated_at = VALUES(updated_at)
        """,
        (
            persona_id,
            int(payload.get("casualStreak", 0)),
            str(payload.get("ongoingSituation", ""))[:80],
            parsed.astimezone(timezone.utc).strftime("%Y-%m-%d %H:%M:%S.%f")[:-3],
        ),
    )
    return cursor.rowcount


def import_legacy_history(profiles_dir: Path, config: DbConfig) -> tuple[int, int]:
    if pymysql is None:
        raise RuntimeError("PyMySQL is required for DB import")
    conn = pymysql.connect(
        host=config.host,
        port=config.port,
        user=config.user,
        password=config.password,
        database=config.database,
        charset="utf8mb4",
        cursorclass=pymysql.cursors.DictCursor,
        autocommit=False,
    )
    inserted_entries = 0
    imported_states = 0
    try:
        with conn.cursor() as cursor:
            ensure_schema(cursor)
            ensure_persona_rows(cursor, profiles_dir)
            for profile_dir in sorted(p for p in profiles_dir.iterdir() if p.is_dir()):
                persona_id = resolve_persona_id(cursor, profile_dir)
                if not persona_id:
                    continue
                inserted_entries += import_history_file(cursor, persona_id, profile_dir / "history/posts.md", "posts")
                inserted_entries += import_history_file(cursor, persona_id, profile_dir / "history/comments.md", "comments")
                imported_states += import_life_state(cursor, persona_id, profile_dir / "life_state.json")
        conn.commit()
        return inserted_entries, imported_states
    finally:
        conn.close()


def ensure_schema(cursor: Any) -> None:
    cursor.execute(
        """
        CREATE TABLE IF NOT EXISTS persona_history_entries (
            id BIGINT NOT NULL AUTO_INCREMENT,
            persona_id VARCHAR(32) NOT NULL,
            entry_type VARCHAR(16) NOT NULL,
            target_post_id VARCHAR(32) NOT NULL DEFAULT '',
            category VARCHAR(32) NOT NULL DEFAULT '',
            content_hash CHAR(64) NOT NULL,
            content LONGTEXT NOT NULL,
            created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
            PRIMARY KEY (id),
            CONSTRAINT fk_history_persona FOREIGN KEY (persona_id) REFERENCES personas(id) ON DELETE CASCADE,
            UNIQUE KEY uk_history_dedupe (persona_id, entry_type, created_at, target_post_id, content_hash),
            KEY idx_history_persona_type_time (persona_id, entry_type, created_at),
            KEY idx_history_persona_time (persona_id, created_at)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
        """
    )
    cursor.execute(
        """
        CREATE TABLE IF NOT EXISTS persona_life_state (
            persona_id VARCHAR(32) NOT NULL,
            casual_streak INT NOT NULL DEFAULT 0,
            ongoing_situation VARCHAR(255) NOT NULL DEFAULT '',
            updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
            PRIMARY KEY (persona_id),
            CONSTRAINT fk_life_state_persona FOREIGN KEY (persona_id) REFERENCES personas(id) ON DELETE CASCADE
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
        """
    )


def cleanup_profile_dirs(profiles_dir: Path) -> tuple[int, int, int]:
    removed_history_dirs = 0
    removed_life_state = 0
    removed_empty_dirs = 0
    for profile_dir in sorted(p for p in profiles_dir.iterdir() if p.is_dir()):
        history_dir = profile_dir / "history"
        if history_dir.exists():
            history_files = [path for path in history_dir.rglob("*") if path.is_file()]
            if all(can_recover_file(path) for path in history_files):
                try:
                    shutil.rmtree(history_dir)
                    removed_history_dirs += 1
                except OSError:
                    pass
        life_state = profile_dir / "life_state.json"
        if life_state.exists():
            if can_recover_file(life_state):
                try:
                    life_state.unlink()
                    removed_life_state += 1
                except OSError:
                    pass
        if not (profile_dir / "profile.yml").exists() and not (profile_dir / "voice.yml").exists():
            if not any(profile_dir.iterdir()):
                profile_dir.rmdir()
                removed_empty_dirs += 1
    return removed_history_dirs, removed_life_state, removed_empty_dirs


def main() -> int:
    args = parse_args()
    profiles_dir = Path(args.profiles_dir)
    env_values = load_dotenv(args.env_file)
    db_config = build_db_config(args, env_values)

    if not profiles_dir.exists():
        raise SystemExit(f"profiles dir not found: {profiles_dir}")

    if not args.skip_summaries:
        generated, skipped = generate_summaries(profiles_dir)
        print(f"summaries: generated={generated} skipped={skipped}")

    if not args.skip_db_import:
        inserted_entries, imported_states = import_legacy_history(profiles_dir, db_config)
        print(f"db_import: entries={inserted_entries} life_states={imported_states}")
        if not args.skip_summaries:
            conn = pymysql.connect(
                host=db_config.host,
                port=db_config.port,
                user=db_config.user,
                password=db_config.password,
                database=db_config.database,
                charset="utf8mb4",
                cursorclass=pymysql.cursors.DictCursor,
                autocommit=False,
            )
            try:
                with conn.cursor() as cursor:
                    generated = generate_db_backed_summaries(cursor, profiles_dir)
                print(f"db_summaries: generated={generated}")
            finally:
                conn.close()

    if not args.skip_cleanup:
        removed_history_dirs, removed_life_state, removed_empty_dirs = cleanup_profile_dirs(profiles_dir)
        print(
            "cleanup: history_dirs=%d life_state_files=%d empty_dirs=%d"
            % (removed_history_dirs, removed_life_state, removed_empty_dirs)
        )

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
