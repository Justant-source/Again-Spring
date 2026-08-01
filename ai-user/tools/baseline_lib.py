#!/usr/bin/env python3
"""Shared helpers for Wave1-E Phase 0-C baseline measurement tools.

Read-only. No DB writes. Used by:
  diff_persona_yaml_ids.py
  scan_voice_type_reassign.py
  scan_voice_contamination.py
"""

from __future__ import annotations

import csv
import json
import re
from pathlib import Path
from typing import Any, Iterable

try:
    import yaml
except ImportError:  # pragma: no cover
    yaml = None  # type: ignore[assignment]

REPO_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_PROFILES_DIR = REPO_ROOT / "ai-user" / "docs" / "personas" / "profiles"

ALLOWED_VOICE_TYPES = frozenset({"NATEPAN", "BLIND"})

# Out-of-domain keywords for conflict-community voices (§2.5). Keep simple.
OOD_KEYWORD_GROUPS: dict[str, tuple[str, ...]] = {
    "games": (
        "리그오브", "롤드컵", "니달리", "벨코즈", "큐스킬", "q스킬", "스킬",
        "인벤", "롤 ", " 롤", "게임", "공략", "캐릭", "티배깅", "티배그",
        "lol", "league of legends", "오버워치", "배그", "발로란트",
        # 2026-08-01 리뷰: 아래 항목이 없어 "핑공격도 아닌데 4만회를…" 한 문장이
        # 필터를 통과해 prod 페르소나 66명 example_comments에 복제됐다.
        "핑공격", "핑 ", "원딜", "서폿", "정글러", "탑솔", "미드라이너",
        "딜러", "탱커", "버프", "디버프", "쿨타임", "레이드", "던전",
        "만렙", "과금", "가챠", "PvP", "pvp", "e스포츠", "이스포츠",
    ),
    "sports": (
        "축구", "야구", "농구", "골프", "선수", "리그전", "승부차기",
        "mlb", "kbo", "월드컵", "올림픽", "응원가", "홈런", "득점",
    ),
    "idols": (
        "아이돌", "아이브", "케이팝", "k-pop", "kpop", "팬덤", "덕질",
        "콘서트", "컴백", "예능", "방송", "유튜브",
    ),
    "beauty": (
        "시술", "성형", "보톡스", "필러", "피부과", "쌍수",
    ),
    "other_ood": (
        "애니", "만화", "웹툰", "코인", "비트코인", "주식추천",
    ),
}

# Political axis leakage in general_style (§2.5 / §3.3).
POLITICAL_KEYWORDS: tuple[str, ...] = (
    "정치", "보수", "진보", "좌파", "우파", "정치성향", "정치적",
    "민주당", "국힘", "국민의힘", "윤석열", "문재인",
)


def read_yaml(path: Path) -> dict[str, Any]:
    if yaml is None:
        raise SystemExit("PyYAML required: pip install pyyaml")
    with path.open("r", encoding="utf-8") as fh:
        loaded = yaml.safe_load(fh) or {}
    return loaded if isinstance(loaded, dict) else {}


def load_yaml_profile_ids(profiles_dir: Path) -> dict[str, str]:
    """Return {persona_id: slug} from profiles/*/profile.yml."""
    mapping: dict[str, str] = {}
    if not profiles_dir.is_dir():
        return mapping
    for profile_path in sorted(profiles_dir.glob("*/profile.yml")):
        data = read_yaml(profile_path)
        pid = str(data.get("id") or "").strip()
        if not pid:
            continue
        mapping[pid] = profile_path.parent.name
    return mapping


def _as_text_list(value: Any) -> list[str]:
    if value is None:
        return []
    if isinstance(value, str):
        text = value.strip()
        return [text] if text else []
    if isinstance(value, list):
        out: list[str] = []
        for item in value:
            if isinstance(item, str) and item.strip():
                out.append(item.strip())
            elif isinstance(item, dict):
                body = item.get("body") or item.get("text") or item.get("content")
                if isinstance(body, str) and body.strip():
                    out.append(body.strip())
        return out
    return []


def collect_style_texts(voice: dict[str, Any]) -> dict[str, list[str]]:
    """Pull general_style + example_* from nested or flat voice_profile shapes."""
    buckets: dict[str, list[str]] = {
        "general_style": _as_text_list(voice.get("general_style")),
        "example_comments": [],
        "example_replies": [],
        "example_post_openers": [],
    }

    comment = voice.get("comment") if isinstance(voice.get("comment"), dict) else {}
    reply = voice.get("reply") if isinstance(voice.get("reply"), dict) else {}
    post = voice.get("post") if isinstance(voice.get("post"), dict) else {}

    buckets["example_comments"].extend(_as_text_list(voice.get("example_comments")))
    buckets["example_comments"].extend(_as_text_list(comment.get("example_comments")))
    buckets["example_replies"].extend(_as_text_list(voice.get("example_replies")))
    buckets["example_replies"].extend(_as_text_list(reply.get("example_replies")))
    buckets["example_post_openers"].extend(_as_text_list(voice.get("example_post_openers")))
    buckets["example_post_openers"].extend(_as_text_list(post.get("example_post_openers")))
    return buckets


def iter_yaml_voices(profiles_dir: Path) -> Iterable[dict[str, Any]]:
    """Yield normalized voice records from local YAML profiles."""
    if not profiles_dir.is_dir():
        return
    for voice_path in sorted(profiles_dir.glob("*/voice.yml")):
        voice = read_yaml(voice_path)
        profile_path = voice_path.parent / "profile.yml"
        profile = read_yaml(profile_path) if profile_path.exists() else {}
        pid = str(
            voice.get("persona_id") or profile.get("id") or ""
        ).strip()
        nickname = str(voice.get("nickname") or profile.get("nickname") or "").strip()
        voice_type = str(voice.get("voice_type") or "").strip().upper()
        if not voice_type:
            activity = profile.get("activity") if isinstance(profile.get("activity"), dict) else {}
            voice_type = str(activity.get("voice") or "").strip().upper()
        yield {
            "id": pid,
            "slug": voice_path.parent.name,
            "nickname": nickname,
            "voice_type": voice_type,
            "source": "yaml",
            "path": str(voice_path),
            "voice": voice,
            "texts": collect_style_texts(voice),
        }


def _normalize_json_record(raw: Any, index: int) -> dict[str, Any] | None:
    if isinstance(raw, str):
        return {
            "id": raw.strip(),
            "slug": "",
            "nickname": "",
            "voice_type": "",
            "source": "json",
            "path": f"json[{index}]",
            "voice": {},
            "texts": collect_style_texts({}),
        }
    if not isinstance(raw, dict):
        return None

    voice = raw.get("voice_profile")
    if isinstance(voice, str):
        try:
            voice = json.loads(voice)
        except json.JSONDecodeError:
            voice = {}
    if not isinstance(voice, dict):
        voice = {k: v for k, v in raw.items() if k not in {"id", "persona_id", "nickname", "slug"}}

    pid = str(raw.get("id") or raw.get("persona_id") or voice.get("persona_id") or "").strip()
    voice_type = str(
        raw.get("voice_type") or voice.get("voice_type") or ""
    ).strip().upper()
    nickname = str(raw.get("nickname") or voice.get("nickname") or "").strip()
    return {
        "id": pid,
        "slug": str(raw.get("slug") or "").strip(),
        "nickname": nickname,
        "voice_type": voice_type,
        "source": "json",
        "path": f"json[{index}]",
        "voice": voice if isinstance(voice, dict) else {},
        "texts": collect_style_texts(voice if isinstance(voice, dict) else {}),
    }


def load_voice_dump_json(path: Path) -> list[dict[str, Any]]:
    data = json.loads(path.read_text(encoding="utf-8"))
    if isinstance(data, dict):
        if "personas" in data and isinstance(data["personas"], list):
            data = data["personas"]
        elif "rows" in data and isinstance(data["rows"], list):
            data = data["rows"]
        else:
            data = [data]
    if not isinstance(data, list):
        raise SystemExit(f"JSON dump must be a list or {{personas|rows: [...]}}: {path}")
    out: list[dict[str, Any]] = []
    for i, item in enumerate(data):
        rec = _normalize_json_record(item, i)
        if rec and rec["id"]:
            out.append(rec)
    return out


def load_id_list_file(path: Path) -> list[str]:
    ids: list[str] = []
    for line in path.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#"):
            continue
        # allow "id,nickname" or whitespace-separated
        token = re.split(r"[\s,;]+", stripped)[0]
        if token.lower() in {"id", "persona_id", "uuid"}:
            continue
        ids.append(token)
    return ids


def load_id_csv(path: Path, id_column: str | None = None) -> list[str]:
    with path.open("r", encoding="utf-8-sig", newline="") as fh:
        sample = fh.read(4096)
        fh.seek(0)
        try:
            dialect = csv.Sniffer().sniff(sample, delimiters=",\t;")
        except csv.Error:
            dialect = csv.excel
        reader = csv.DictReader(fh, dialect=dialect)
        if reader.fieldnames is None:
            fh.seek(0)
            return [row[0].strip() for row in csv.reader(fh) if row and row[0].strip()]

        fields = [f.strip() for f in reader.fieldnames]
        col = id_column
        if col is None:
            for candidate in ("id", "persona_id", "uuid", "ID", "personaId"):
                if candidate in fields:
                    col = candidate
                    break
            if col is None:
                col = fields[0]
        ids: list[str] = []
        for row in reader:
            val = str(row.get(col) or "").strip()
            if val:
                ids.append(val)
        return ids


def find_keyword_hits(text: str, keywords: Iterable[str]) -> list[str]:
    lowered = text.lower()
    hits: list[str] = []
    for kw in keywords:
        needle = kw.lower()
        if needle and needle in lowered:
            hits.append(kw)
    return hits


def scan_contamination(texts: dict[str, list[str]]) -> dict[str, Any]:
    """Heuristic OOD + political hits across style text buckets."""
    ood_hits: dict[str, list[dict[str, Any]]] = {g: [] for g in OOD_KEYWORD_GROUPS}
    political_hits: list[dict[str, Any]] = []
    scanned_fields = 0
    contaminated_fields = 0

    for field, values in texts.items():
        for text in values:
            if not text:
                continue
            scanned_fields += 1
            field_ood = False
            for group, kws in OOD_KEYWORD_GROUPS.items():
                hits = find_keyword_hits(text, kws)
                if hits:
                    field_ood = True
                    ood_hits[group].append(
                        {"field": field, "keywords": hits, "excerpt": text[:120]}
                    )
            pol = find_keyword_hits(text, POLITICAL_KEYWORDS)
            if pol and field == "general_style":
                political_hits.append(
                    {"field": field, "keywords": pol, "excerpt": text[:120]}
                )
            if field_ood:
                contaminated_fields += 1

    total_ood = sum(len(v) for v in ood_hits.values())
    return {
        "scanned_fields": scanned_fields,
        "contaminated_fields": contaminated_fields,
        "ood_hit_count": total_ood,
        "ood_by_group": {k: v for k, v in ood_hits.items() if v},
        "political_hits": political_hits,
        "is_contaminated": total_ood > 0,
        "has_political_style": len(political_hits) > 0,
    }
