#!/usr/bin/env python3
"""Rebuild the public community feed from latest Blind/NatePann crawl data.

This script is designed for one-shot curation/reconstruction work:

1. Pick 20 source stories per plaza from latest `example_bank` crawl rows.
2. Redistribute those 120 posts across a fixed KST day window so the feed
   appears steadily active every day.
3. Regenerate posts from scratch with Sonnet via clcocloud.
4. Generate comments/replies with Haiku and simulate votes/likes using the
   same persona bias rules used by the orchestrator.
5. Hard-delete the previous PUBLIC feed and insert only the rebuilt dataset.

Default mode is safe: selection + schedule only. Use `--generate` to call the
LLM, and `--apply` to write to the database.
"""

from __future__ import annotations

import argparse
import json
import random
import re
import sys
import time
import uuid
from collections import Counter, defaultdict
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass, field
from datetime import date, datetime, time as dt_time, timedelta, timezone
from pathlib import Path
from typing import Any
from zoneinfo import ZoneInfo

import pymysql
import requests

ROOT = Path(__file__).resolve().parents[2]
LEARNING_SERVICES = ROOT / "ai-user" / "learning" / "app" / "services"
if str(LEARNING_SERVICES) not in sys.path:
    sys.path.append(str(LEARNING_SERVICES))

from ngram_guard import passes_ngram_guard  # type: ignore

ROOT = Path(__file__).resolve().parents[2]
KST = ZoneInfo("Asia/Seoul")
UTC = timezone.utc

PLAZAS = ["COUPLE", "FAMILY", "FRIEND", "MARRIED", "WORK", "OTHER"]
DEFAULT_PER_CATEGORY = 20
DEFAULT_MAX_WORKERS = 8
DEFAULT_POST_MODEL = "claude-sonnet-4-6"
DEFAULT_COMMENT_MODEL = "claude-haiku-4-5-20251001"
SIMILARITY_THRESHOLD = 0.72

SOURCE_CATEGORY_MAP = {
    ("BLIND", "romance"): "COUPLE",
    ("BLIND", "marriage"): "MARRIED",
    ("BLIND", "workplace"): "WORK",
    ("natepan", "COUPLE"): "COUPLE",
    ("natepan", "FAMILY"): "FAMILY",
    ("natepan", "FRIEND"): "FRIEND",
    ("natepan", "MARRIED"): "MARRIED",
    ("natepan", "WORK"): "WORK",
    ("natepan", "OTHER"): "OTHER",
}

CATEGORY_GUIDES = {
    "COUPLE": "연인 관계 갈등만. 가족·직장·친구 갈등으로 새면 안 된다. 갈등 구조와 감정선은 유지하되, 직업·나이·지역·구체적 사물·브랜드명·정확한 금액 등 식별 가능한 디테일은 반드시 다른 값으로 바꿔라.",
    "FAMILY": "가족(부모·형제·친척) 갈등만. 연애·직장 이야기로 흐리지 않는다. 갈등 구조와 감정선은 유지하되, 직업·나이·지역·구체적 사물·브랜드명·정확한 금액 등 식별 가능한 디테일은 반드시 다른 값으로 바꿔라.",
    "FRIEND": "친구·지인 관계 갈등만. 연인·가족·직장 갈등은 제외한다. 갈등 구조와 감정선은 유지하되, 직업·나이·지역·구체적 사물·브랜드명·정확한 금액 등 식별 가능한 디테일은 반드시 다른 값으로 바꿔라.",
    "MARRIED": "부부·배우자·시댁·처가 갈등만. 미혼 연애 서사로 쓰면 안 된다. 갈등 구조와 감정선은 유지하되, 직업·나이·지역·구체적 사물·브랜드명·정확한 금액 등 식별 가능한 디테일은 반드시 다른 값으로 바꿔라.",
    "WORK": "직장·업무·상사·동료 갈등만. 다른 광장 이야기로 새면 안 된다. 갈등 구조와 감정선은 유지하되, 직업·나이·지역·구체적 사물·브랜드명·정확한 금액 등 식별 가능한 디테일은 반드시 다른 값으로 바꿔라.",
    "OTHER": "기타 일상·사회생활·생활비·진로·애매한 인간관계 등으로 처리한다. 갈등 구조와 감정선은 유지하되, 직업·나이·지역·구체적 사물·브랜드명·정확한 금액 등 식별 가능한 디테일은 반드시 다른 값으로 바꿔라.",
}

BLIND_STOP_PATTERNS = [
    "직장인끼리 소개팅하러 가기",
    "지금 바로 전문가에게",
    "골드박스",
    "님들 이제 주차어찌할꺼임",
    "장기주차장",
    "우리 팀 회식하면 보통 밤 10시쯤 끝남",
    "메신저하다가 화가난다고 자리에서 뭐 던지던뎅",
    "매일 아침 7시 OPEN",
]

HARD_REJECT_PATTERNS = [
    "정신침략",
    "내란죄",
    "연어술파티",
    "대북송금",
    "청와대",
    "부산경찰",
    "경찰청장",
    "국회의원",
    "조현병",
    "좌빨",
    "전광훈",
]

HARD_REJECT_REGEXES = [
    re.compile(r"어느 .+에서 벌어진 100%실화"),
    re.compile(r"몇몇은 학폭 당해서 자살"),
]

TITLE_PREFIXES = [
    "회사생활:",
    "연애:",
    "결혼/시집/친정:",
    "결혼:",
    "가족:",
    "친구:",
]

POST_TIME_SLOTS = [
    (8, 18),
    (10, 7),
    (12, 46),
    (15, 18),
    (18, 11),
    (20, 43),
    (22, 16),
]

EXTRA_DAY_INDICES = [0, 3, 6, 9, 12, 15, 1, 4, 7, 10, 13, 16]

MIN_SOURCE_BY_CATEGORY = {
    "COUPLE": {"BLIND": 5},
    "MARRIED": {"BLIND": 5},
    "WORK": {"BLIND": 10},
}

COMMENT_MODE_HINTS = {
    "REACTION_ONLY": ("반응만: 감정 한 마디만, 최대 20자", 20),
    "SHORT_AGREE": ("짧은 동조: 한마디 맞장구만, 최대 15자", 15),
    "QUESTION": ("되묻기: 궁금한 점만 묻기, 최대 25자", 25),
    "DISAGREE": ("다른 시각: 공격 없이 짧게 반대 의견 한 줄, 최대 35자", 35),
    "EXPERIENCE": ("경험담: 비슷한 경험 한두 문장만, 최대 50자", 50),
    "ADVICE": ("훈수: 조언을 한마디로, 최대 30자", 30),
    "TANGENT": ("사족: 혼잣말·드립 한 줄, 최대 20자", 20),
}

COMMENT_COUNT_WEIGHTS = {
    2: 2,
    3: 4,
    4: 6,
    5: 7,
    6: 9,
    7: 10,
    8: 10,
    9: 8,
    10: 7,
    11: 5,
    12: 4,
    13: 3,
    14: 2,
    15: 1,
}


@dataclass
class DbConfig:
    host: str
    port: int
    user: str
    password: str
    database: str


@dataclass
class ApiConfig:
    api_key: str
    base_url: str
    post_model: str
    comment_model: str
    max_workers: int


@dataclass
class SourceExample:
    id: int
    source: str
    raw_category: str
    category: str
    title: str
    body: str
    source_url: str | None
    posted_at: datetime | None
    created_at: datetime
    author_key: str | None
    quality_score: float
    title_key: str
    signature: str
    trigrams: set[str]
    popularity_pct: float | None = None  # 같은 source+나이구간 내 백분위, 0~1, NULL 허용

    @property
    def recency_ts(self) -> datetime:
        return self.posted_at or self.created_at


@dataclass
class Persona:
    id: str
    nickname: str
    archetype: str
    interests: dict[str, float]
    bias_profile: dict[str, float]
    voice_profile: dict[str, Any]
    slang_level: float
    created_at: datetime

    @property
    def voice_type(self) -> str:
        return str(self.voice_profile.get("voice_type", "GENERAL") or "GENERAL").strip().upper()

    @property
    def formality(self) -> str:
        raw = str(self.voice_profile.get("formality", "casual") or "casual").strip().lower()
        return "polite" if raw == "polite" else "casual"

    @property
    def like_score(self) -> float:
        return clamp(float_value(self.voice_profile.get("like_score"), 0.45), 0.05, 0.95)

    @property
    def vote_score(self) -> float:
        return clamp(float_value(self.voice_profile.get("vote_score"), 0.30), 0.05, 0.95)

    @property
    def age(self) -> str:
        return str(self.voice_profile.get("age", "") or "").strip()

    @property
    def gender(self) -> str:
        return str(self.voice_profile.get("gender", "") or "").strip().upper()


@dataclass
class CommentSlot:
    index: int
    author: Persona
    parent_index: int | None
    stance: str
    mode_hint: str
    max_chars: int
    created_at_kst: datetime
    body: str = ""

    @property
    def is_reply(self) -> bool:
        return self.parent_index is not None


@dataclass
class PostPlan:
    category: str
    source_example: SourceExample
    author: Persona
    created_at_kst: datetime
    title: str = ""
    body: str = ""
    comments: list[CommentSlot] = field(default_factory=list)
    votes: list[dict[str, Any]] = field(default_factory=list)
    post_likes: list[dict[str, Any]] = field(default_factory=list)
    comment_likes: list[dict[str, Any]] = field(default_factory=list)
    view_count: int = 0
    day_key: str = ""
    post_id: str | None = None


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--env-file", default="env/.env.dev")
    parser.add_argument("--db-host", default="127.0.0.1")
    parser.add_argument("--db-port", type=int, default=3309)
    parser.add_argument("--db-user", default=None)
    parser.add_argument("--db-password", default=None)
    parser.add_argument("--db-name", default=None)
    parser.add_argument("--output-dir", default="ai-user/tools/reports")
    parser.add_argument("--per-category", type=int, default=DEFAULT_PER_CATEGORY)
    parser.add_argument("--post-model", default=DEFAULT_POST_MODEL)
    parser.add_argument("--comment-model", default=DEFAULT_COMMENT_MODEL)
    parser.add_argument("--max-workers", type=int, default=DEFAULT_MAX_WORKERS)
    parser.add_argument("--date-start", default="2026-06-06")
    parser.add_argument("--date-end", default="2026-06-23")
    parser.add_argument("--seed", type=int, default=20260624)
    parser.add_argument("--generate", action="store_true", help="call Sonnet/Haiku and build full dataset")
    parser.add_argument("--apply", action="store_true", help="hard-delete current public feed and insert rebuilt dataset")
    return parser.parse_args()


def load_dotenv(path: str) -> dict[str, str]:
    values: dict[str, str] = {}
    env_path = ROOT / path
    if not env_path.exists():
        return values
    for line in env_path.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#") or "=" not in stripped:
            continue
        key, value = stripped.split("=", 1)
        values[key.strip()] = value.strip()
    return values


def build_db_config(args: argparse.Namespace, env_values: dict[str, str]) -> DbConfig:
    return DbConfig(
        host=args.db_host,
        port=args.db_port,
        user=args.db_user or env_values.get("MARIADB_USER", "againspring"),
        password=args.db_password or env_values.get("MARIADB_PASSWORD", ""),
        database=args.db_name or env_values.get("MARIADB_DATABASE", "againspring_dev"),
    )


def connect_db(config: DbConfig) -> pymysql.connections.Connection:
    conn = pymysql.connect(
        host=config.host,
        port=config.port,
        user=config.user,
        password=config.password,
        database=config.database,
        cursorclass=pymysql.cursors.DictCursor,
        charset="utf8mb4",
        autocommit=False,
    )
    with conn.cursor() as cur:
        cur.execute("SET time_zone = '+00:00'")
    return conn


def clamp(value: float, low: float, high: float) -> float:
    return max(low, min(high, value))


def float_value(value: Any, fallback: float) -> float:
    try:
        return float(value)
    except Exception:
        return fallback


def parse_json_map(value: Any) -> dict[str, Any]:
    if isinstance(value, dict):
        return value
    if not value:
        return {}
    try:
        parsed = json.loads(value)
    except Exception:
        return {}
    return parsed if isinstance(parsed, dict) else {}


def parse_ts(value: Any) -> datetime | None:
    if value is None:
        return None
    if isinstance(value, datetime):
        return value.replace(tzinfo=UTC) if value.tzinfo is None else value.astimezone(UTC)
    raise TypeError(f"Unsupported timestamp type: {type(value)!r}")


def normalize_spaces(text: str) -> str:
    return re.sub(r"[ \t]+", " ", text).strip()


def normalize_key(text: str) -> str:
    text = text.lower()
    text = re.sub(r"\s+", " ", text)
    text = re.sub(r"[^0-9a-z가-힣 ]+", "", text)
    return text.strip()


def make_trigrams(text: str) -> set[str]:
    normalized = normalize_key(text).replace(" ", "")
    if len(normalized) < 3:
        return {normalized} if normalized else set()
    return {normalized[i:i + 3] for i in range(len(normalized) - 2)}


def jaccard(a: set[str], b: set[str]) -> float:
    if not a or not b:
        return 0.0
    intersection = len(a & b)
    union = len(a | b)
    return intersection / union if union else 0.0


def category_from_source(source: str, raw_category: str) -> str | None:
    return SOURCE_CATEGORY_MAP.get((source, raw_category))


def clean_title(title: str) -> str:
    title = normalize_spaces(title.replace("\n", " ").replace("\r", " "))
    for prefix in TITLE_PREFIXES:
        if title.startswith(prefix):
            title = normalize_spaces(title[len(prefix):])
            break
    return title


def clean_source_body(source: str, title: str, body: str) -> str:
    text = body.replace("\r\n", "\n").replace("\r", "\n")
    text = text.replace("\u200b", "").replace("\ufeff", "")
    lines = [normalize_spaces(line) for line in text.split("\n")]
    cleaned: list[str] = []
    title_key = normalize_key(title)
    for line in lines:
        if not line:
            continue
        if source == "BLIND" and any(pattern in line for pattern in BLIND_STOP_PATTERNS):
            break
        if title_key and normalize_key(line) == title_key:
            continue
        cleaned.append(line)
        if source == "BLIND" and len(cleaned) >= 6:
            break
    joined = "\n".join(cleaned).strip()
    joined = re.sub(r"\n{3,}", "\n\n", joined)
    return joined[:2000]


def looks_like_reject(text: str) -> bool:
    lowered = text.lower()
    if any(pattern.lower() in lowered for pattern in HARD_REJECT_PATTERNS):
        return True
    return any(regex.search(text) for regex in HARD_REJECT_REGEXES)


def quality_score_for_source(example: SourceExample, latest_ts: datetime) -> float:
    body_len = len(example.body)
    age_hours = (latest_ts - example.recency_ts).total_seconds() / 3600.0
    recency = max(0.0, 24.0 - min(age_hours, 24.0)) / 24.0
    length_bonus = 1.0 if 180 <= body_len <= 900 else 0.6 if 90 <= body_len <= 1200 else 0.2
    title_bonus = 0.35 if example.title else 0.0
    source_bonus = 0.0
    if example.category == "WORK" and example.source == "BLIND":
        source_bonus += 0.5
    if example.category in {"COUPLE", "MARRIED"} and example.source == "BLIND":
        source_bonus += 0.25
    return recency * 1.8 + length_bonus + title_bonus + source_bonus


def query_global_rules(conn: pymysql.connections.Connection) -> str | None:
    sql = """
        SELECT rule_text
        FROM ai_global_rules
        WHERE active = 1
          AND scope IN ('ALL', 'POST', 'COMMENT')
        ORDER BY id ASC
    """
    with conn.cursor() as cur:
        cur.execute(sql)
        rows = cur.fetchall()
    rules = [f"- {str(row['rule_text']).strip()}" for row in rows if str(row["rule_text"]).strip()]
    return "\n".join(rules) if rules else None


def build_api_config(
    conn: pymysql.connections.Connection,
    env_values: dict[str, str],
    args: argparse.Namespace,
) -> ApiConfig:
    settings: dict[str, str] = {}
    with conn.cursor() as cur:
        cur.execute(
            """
            SELECT setting_key, setting_value
            FROM system_setting
            WHERE setting_key IN ('ANTHROPIC_API_KEY', 'ANTHROPIC_BASE_URL')
            """
        )
        for row in cur.fetchall():
            settings[str(row["setting_key"])] = str(row["setting_value"])
    api_key = settings.get("ANTHROPIC_API_KEY") or env_values.get("ANTHROPIC_API_KEY")
    base_url = settings.get("ANTHROPIC_BASE_URL") or env_values.get("ANTHROPIC_BASE_URL") or "https://api.anthropic.com"
    if not api_key:
        raise RuntimeError("ANTHROPIC_API_KEY not found in system_setting or env")
    return ApiConfig(
        api_key=api_key,
        base_url=base_url.rstrip("/"),
        post_model=args.post_model,
        comment_model=args.comment_model,
        max_workers=args.max_workers,
    )


def query_personas(conn: pymysql.connections.Connection) -> list[Persona]:
    sql = """
        SELECT
            p.id,
            u.nickname,
            p.archetype,
            p.interests,
            p.bias_profile,
            p.voice_profile,
            p.slang_level,
            p.created_at
        FROM personas p
        JOIN users u ON u.id = p.id
        WHERE p.active = 1
          AND u.synthetic = 1
          AND u.deleted_at IS NULL
        ORDER BY p.id ASC
    """
    personas: list[Persona] = []
    with conn.cursor() as cur:
        cur.execute(sql)
        for row in cur.fetchall():
            personas.append(
                Persona(
                    id=str(row["id"]),
                    nickname=str(row["nickname"] or "익명"),
                    archetype=str(row["archetype"] or ""),
                    interests={k: float(v) for k, v in parse_json_map(row["interests"]).items()},
                    bias_profile={k: float(v) for k, v in parse_json_map(row["bias_profile"]).items()},
                    voice_profile=parse_json_map(row["voice_profile"]),
                    slang_level=float_value(row["slang_level"], 0.45),
                    created_at=parse_ts(row["created_at"]) or datetime.now(UTC),
                )
            )
    if len(personas) < len(PLAZAS) * 10:
        raise RuntimeError(f"Too few active synthetic personas: {len(personas)}")
    return personas


def query_source_examples(conn: pymysql.connections.Connection) -> list[SourceExample]:
    sql = """
        SELECT
            id,
            title,
            content,
            category,
            source,
            source_url,
            posted_at,
            created_at,
            author_id,
            COALESCE(popularity_pct, 0.5) as popularity_pct
        FROM example_bank
        WHERE content_type = 'POST'
          AND source IN ('BLIND', 'natepan')
        ORDER BY COALESCE(posted_at, created_at) DESC, id DESC
    """
    rows: list[dict[str, Any]]
    with conn.cursor() as cur:
        cur.execute(sql)
        rows = list(cur.fetchall())
    latest_ts = parse_ts(rows[0]["posted_at"] or rows[0]["created_at"]) if rows else None
    if latest_ts is None:
        raise RuntimeError("No crawl rows found in example_bank")

    examples: list[SourceExample] = []
    for row in rows:
        source = str(row["source"])
        raw_category = str(row["category"] or "")
        category = category_from_source(source, raw_category)
        if category is None:
            continue
        raw_title = clean_title(str(row["title"] or ""))
        raw_body = clean_source_body(source, raw_title, str(row["content"] or ""))
        if not raw_body or len(raw_body) < 80:
            continue
        if looks_like_reject(raw_title + "\n" + raw_body):
            continue
        title = raw_title or normalize_spaces(raw_body.split("\n", 1)[0])[:48]
        title_key = normalize_key(title)
        signature = normalize_key(f"{title}\n{raw_body[:220]}")
        trigrams = make_trigrams(f"{title}\n{raw_body[:220]}")
        popularity_pct = float_value(row["popularity_pct"], 0.5)
        example = SourceExample(
            id=int(row["id"]),
            source=source,
            raw_category=raw_category,
            category=category,
            title=title,
            body=raw_body,
            source_url=str(row["source_url"]) if row["source_url"] else None,
            posted_at=parse_ts(row["posted_at"]),
            created_at=parse_ts(row["created_at"]) or latest_ts,
            author_key=str(row["author_id"]) if row["author_id"] else None,
            quality_score=0.0,
            title_key=title_key,
            signature=signature,
            trigrams=trigrams,
            popularity_pct=popularity_pct,
        )
        example.quality_score = quality_score_for_source(example, latest_ts)
        examples.append(example)
    return examples


def select_sources_by_category(examples: list[SourceExample], per_category: int, conn: pymysql.connections.Connection) -> dict[str, list[SourceExample]]:
    """선별 로직: popularity_pct 하위 30% 제외, 남은 후보 중 popularity_pct 가중치 확률 샘플링"""
    # 이미 재가공된 원본 ID 제외
    already_used: set[int] = set()
    try:
        with conn.cursor() as cur:
            cur.execute("SELECT DISTINCT source_example_id FROM posts WHERE source_example_id IS NOT NULL")
            already_used = {int(row["source_example_id"]) for row in cur.fetchall()}
    except Exception:
        pass  # 테이블 없거나 에러 시 무시

    # 이미 사용된 것 제외
    examples_available = [e for e in examples if e.id not in already_used]

    buckets: dict[str, list[SourceExample]] = defaultdict(list)
    for example in examples_available:
        buckets[example.category].append(example)

    selected: dict[str, list[SourceExample]] = {}
    rng = random.Random()  # 매번 다른 난수 시드(시간 기반)

    for category in PLAZAS:
        required_by_source = MIN_SOURCE_BY_CATEGORY.get(category, {})
        pool = buckets[category]
        chosen: list[SourceExample] = []
        used_titles: set[str] = set()
        author_counts: Counter[str] = Counter()

        def can_take(candidate: SourceExample) -> bool:
            if candidate.title_key and candidate.title_key in used_titles:
                return False
            if candidate.author_key and author_counts[candidate.author_key] >= 2:
                return False
            for existing in chosen:
                if jaccard(candidate.trigrams, existing.trigrams) >= SIMILARITY_THRESHOLD:
                    return False
            return True

        # 1단계: MIN_SOURCE_BY_CATEGORY 최소 개수 먼저 확보 (필수 소스)
        for source_name, minimum in required_by_source.items():
            source_pool = [e for e in pool if e.source == source_name]
            # popularity_pct 하위 30% 제외
            percentile_30 = sorted(e.popularity_pct for e in source_pool)[max(0, len(source_pool) // 3)]
            filtered = [e for e in source_pool if e.popularity_pct >= percentile_30]

            # 가중치 기반 샘플링
            while len([e for e in chosen if e.source == source_name]) < minimum and filtered:
                if not filtered:
                    break
                weights = [e.popularity_pct for e in filtered]
                candidate = rng.choices(filtered, weights=weights, k=1)[0]
                if can_take(candidate):
                    chosen.append(candidate)
                    used_titles.add(candidate.title_key)
                    if candidate.author_key:
                        author_counts[candidate.author_key] += 1
                filtered.remove(candidate)

        # 2단계: 나머지 필요 개수는 전체 풀(popularit_pct 필터링)에서 가중 샘플링
        all_pool_for_extra = pool
        percentile_30_all = sorted(e.popularity_pct for e in all_pool_for_extra)[max(0, len(all_pool_for_extra) // 3)]
        filtered_all = [e for e in all_pool_for_extra if e.popularity_pct >= percentile_30_all]

        while len(chosen) < per_category and filtered_all:
            weights = [e.popularity_pct for e in filtered_all]
            candidate = rng.choices(filtered_all, weights=weights, k=1)[0]
            if can_take(candidate):
                chosen.append(candidate)
                used_titles.add(candidate.title_key)
                if candidate.author_key:
                    author_counts[candidate.author_key] += 1
            filtered_all.remove(candidate)

        if len(chosen) < per_category:
            raise RuntimeError(f"Could not select {per_category} diverse sources for {category}, only {len(chosen)}")
        selected[category] = chosen[:per_category]
    return selected


def daterange(start: date, end: date) -> list[date]:
    days: list[date] = []
    current = start
    while current <= end:
        days.append(current)
        current += timedelta(days=1)
    return days


def kst_datetime(day: date, hh: int, mm: int) -> datetime:
    return datetime.combine(day, dt_time(hour=hh, minute=mm), tzinfo=KST)


def assign_day_slots(
    selected: dict[str, list[SourceExample]],
    personas: list[Persona],
    seed: int,
    start_day: date,
    end_day: date,
) -> list[PostPlan]:
    days = daterange(start_day, end_day)
    if len(days) < 18:
        raise RuntimeError("Date window too short for steady reconstruction")

    planned: list[PostPlan] = []
    author_use_counts: Counter[str] = Counter()
    category_voice_preferences = {
        "WORK": {"BLIND": 0.55, "polite": 0.25},
        "COUPLE": {"NATEPAN": 0.35, "casual": 0.15},
        "MARRIED": {"polite": 0.20},
        "FAMILY": {"polite": 0.15},
    }

    def day_indices_for_count(total: int, category_offset: int) -> list[int]:
        day_count = len(days)
        if total <= 0:
            return []
        if total <= day_count:
            if total == 1:
                return [day_count // 2]
            return [
                int(round(((index + 0.5) * day_count / total) - 0.5))
                for index in range(total)
            ]
        extras_needed = total - day_count
        rotation = category_offset % len(EXTRA_DAY_INDICES)
        rotated = EXTRA_DAY_INDICES[rotation:] + EXTRA_DAY_INDICES[:rotation]
        while len(rotated) < extras_needed:
            rotated.extend(rotated)
        return list(range(day_count)) + rotated[:extras_needed]

    def persona_score(persona: Persona, category: str, source_name: str, local_seed: int) -> float:
        rng = random.Random(local_seed)
        score = persona.interests.get(category, 0.0) * 3.0
        score -= author_use_counts[persona.id] * 4.5
        if source_name == "BLIND" and persona.voice_type == "BLIND":
            score += 0.7
        if source_name == "natepan" and persona.voice_type == "NATEPAN":
            score += 0.45
        pref = category_voice_preferences.get(category, {})
        if persona.formality == "polite":
            score += pref.get("polite", 0.0)
        if persona.formality == "casual":
            score += pref.get("casual", 0.0)
        age = persona.age
        if category in {"MARRIED", "FAMILY"} and age in {"30s", "30s_early", "30s_late", "40s", "50s"}:
            score += 0.35
        if category == "COUPLE" and age in {"20s", "20s_early", "20s_late", "30s", "30s_early"}:
            score += 0.20
        if category == "WORK" and persona.voice_type in {"BLIND", "CLIEN", "PPOMPPU"}:
            score += 0.18
        if category == "OTHER":
            score += persona.interests.get("OTHER", 0.0) * 2.0
            if persona.voice_type in {"NATEPAN", "THEQOO", "DCINSIDE"}:
                score += 0.20
        score += rng.random() * 0.05
        return score

    def choose_author(category: str, source_name: str, ordinal: int) -> Persona:
        ranked = sorted(
            personas,
            key=lambda p: persona_score(p, category, source_name, seed * 1000 + ordinal + hash(p.id) % 997),
            reverse=True,
        )
        return ranked[0]

    day_to_posts: dict[date, list[tuple[str, SourceExample, Persona]]] = defaultdict(list)
    for cat_idx, category in enumerate(PLAZAS):
        day_slots = day_indices_for_count(len(selected[category]), cat_idx * 2)
        if len(day_slots) != len(selected[category]):
            raise RuntimeError(f"Schedule mismatch for {category}: {len(day_slots)} slots vs {len(selected[category])} sources")
        for ordinal, (day_index, source_example) in enumerate(zip(day_slots, selected[category], strict=True)):
            author = choose_author(category, source_example.source, cat_idx * 100 + ordinal)
            author_use_counts[author.id] += 1
            day_to_posts[days[day_index]].append((category, source_example, author))

    for day_index, day in enumerate(days):
        items = day_to_posts[day]
        ordering_rng = random.Random(seed * 17 + day_index)
        ordering_rng.shuffle(items)
        for slot_index, (category, source_example, author) in enumerate(items):
            base_hh, base_mm = POST_TIME_SLOTS[slot_index]
            jitter = random.Random(seed * 31 + slot_index + source_example.id).randint(0, 19)
            created_at = kst_datetime(day, base_hh, min(59, base_mm + jitter))
            planned.append(
                PostPlan(
                    category=category,
                    source_example=source_example,
                    author=author,
                    created_at_kst=created_at,
                    day_key=day.isoformat(),
                )
            )

    planned.sort(key=lambda plan: plan.created_at_kst)
    if all(len(selected[category]) >= len(days) for category in PLAZAS):
        for day in days:
            count = len(day_to_posts[day])
            if count not in {6, 7}:
                raise RuntimeError(f"Unexpected day count for {day}: {count}")
    return planned


def extract_examples(items: Any, count: int) -> str | None:
    if not isinstance(items, list) or not items:
        return None
    picked = [str(item).strip() for item in items if str(item).strip()]
    if not picked:
        return None
    return " / ".join(picked[:count])


def extract_writing_quirks(voice_profile: dict[str, Any]) -> str | None:
    quirks = voice_profile.get("writing_quirks")
    if not isinstance(quirks, dict):
        return None
    parts: list[str] = []
    features = str(quirks.get("features", "") or "").strip()
    if features:
        parts.append(f"문체 패턴: {features}")
    consistent = quirks.get("consistent_errors")
    if isinstance(consistent, list) and consistent:
        parts.append("오탈자 습관: " + " / ".join(str(item).strip() for item in consistent[:2] if str(item).strip()))
    if bool(quirks.get("mobile_typos")):
        parts.append("모바일 오타가 아주 약하게 섞인다")
    return "\n".join(parts) if parts else None


def extract_lexicon(voice_profile: dict[str, Any]) -> str | None:
    lexicon = voice_profile.get("lexicon")
    if not isinstance(lexicon, dict):
        return None
    phrases = lexicon.get("signature_phrases")
    if isinstance(phrases, list) and phrases:
        return "자주 쓰는 표현: " + " / ".join(str(item).strip() for item in phrases[:2] if str(item).strip())
    habit = str(lexicon.get("typing_habit", "") or "").strip()
    return f"타이핑 습관: {habit}" if habit else None


def build_persona_voice_block(persona: Persona, kind: str) -> str:
    vp = persona.voice_profile
    lines: list[str] = []
    general_style = str(vp.get("general_style", "") or "").strip()
    if general_style:
        lines.append(f"기본 말투: {general_style}")
    if kind == "post":
        examples = extract_examples(vp.get("example_post_openers"), 2)
        if examples:
            lines.append(f"글 시작 예시: {examples}")
    elif kind == "comment":
        examples = extract_examples(vp.get("example_comments"), 3)
        if examples:
            lines.append(f"댓글 예시: {examples}")
    else:
        examples = extract_examples(vp.get("example_replies"), 2)
        if examples:
            lines.append(f"대댓글 예시: {examples}")
    quirks = extract_writing_quirks(vp)
    if quirks:
        lines.append(quirks)
    lexicon = extract_lexicon(vp)
    if lexicon:
        lines.append(lexicon)
    age_note = str(vp.get("age_voice_notes", "") or "").strip()
    if age_note:
        lines.append(f"연령톤 참고: {age_note}")
    political = str(vp.get("political_voice_notes", "") or "").strip()
    if political:
        lines.append(f"성향 표현 참고: {political}")
    return "\n".join(lines)


def global_rules_block(global_rules: str | None) -> str:
    return f"\n[전역 금지 규칙]\n{global_rules}\n" if global_rules else ""


def build_post_prompt(plan: PostPlan, global_rules: str | None) -> str:
    persona = plan.author
    source = plan.source_example
    target_len = 260 if plan.category == "OTHER" else 380
    tone = "자연스러운 구어 존댓말" if persona.formality == "polite" else "커뮤니티 반말/반존대 혼합체"
    body = f"""
<instructions>
너는 한국 갈등 커뮤니티 운영용 synthetic 작성자다.
최신 크롤링 사연의 핵심 갈등만 참고하고 문장은 완전히 새로 써라.
{global_rules_block(global_rules)}[광장]
{plan.category}: {CATEGORY_GUIDES[plan.category]}

[작성자 페르소나]
닉네임: {persona.nickname} (본문에 닉네임 노출 금지)
voice_type: {persona.voice_type}
formality: {persona.formality}
slang_level: {persona.slang_level:.2f}
archetype: {persona.archetype}
{build_persona_voice_block(persona, "post")}

[출처]
community: {source.source}
source_title: {source.title or "(제목 없음)"}
source_body:
{source.body}

[작성 지시]
- 출처의 핵심 사건·갈등 구조는 유지하되 문장은 처음부터 새로 쓴다
- 한국 커뮤니티에 실제로 올라온 사연처럼 1인칭 체감형으로 쓴다
- 제목은 12~38자
- 본문은 대략 {target_len}~680자, 최대 950자
- 출처 사이트 이름, 광고문구, 앱홍보, 기사체, 정치 떡밥, 진단/처방 문장 금지
- 줄글 위주로 쓰되 너무 정제된 문장보다 자연스럽게 약간의 군더더기가 있어도 된다
- 마지막 문장은 매번 똑같은 '제가 예민한가요' 패턴으로 끝내지 말고, 답답함·체념·질문 중 하나로 자연스럽게 닫는다
- 따옴표, 목록, 해시태그, 이모지, 메타 설명 금지
- JSON 1개만 출력: {{"title":"...","body":"..."}}
</instructions>
"""
    return body.strip()


def build_comment_prompt(plan: PostPlan, slots: list[CommentSlot], global_rules: str | None) -> str:
    slot_lines = []
    for slot in slots:
        slot_lines.append(
            f"{slot.index}. "
            f"voice_type={slot.author.voice_type}, "
            f"formality={slot.author.formality}, "
            f"slang={slot.author.slang_level:.2f}, "
            f"stance={slot.stance}, "
            f"mode={slot.mode_hint}"
        )
    prompt = f"""
<instructions>
너는 한국 갈등 커뮤니티 댓글 생성기다.
하나의 게시글에 달릴 서로 다른 상위 댓글들을 만들어라.
{global_rules_block(global_rules)}[게시글]
광장: {plan.category}
제목: {plan.title}
본문:
{plan.body}

[댓글 슬롯]
{chr(10).join(slot_lines)}

[지시]
- 각 슬롯은 서로 다른 사람이 쓴 것처럼 말투와 결을 다르게 만든다
- 지나친 분석문, 상담문, 판결문 금지
- 욕설, 인신공격, 협박, 혐오표현, 자해 조장, 범죄 조언 금지
- 반대 의견도 평범한 커뮤니티 댓글 수준에서만 표현하고 과격한 싸움 톤은 피한다
- 길이는 각 슬롯의 mode 제한을 지킨다
- 번호, 닉네임, 따옴표, 이모지, 코드블록 금지
- 결과는 JSON 배열만 출력: [{{"index":1,"body":"..."}}, ...]
</instructions>
"""
    return prompt.strip()


def build_reply_prompt(plan: PostPlan, slots: list[CommentSlot], global_rules: str | None) -> str:
    top_comments = [slot for slot in plan.comments if not slot.is_reply]
    top_lines = []
    for comment in top_comments:
        top_lines.append(f"{comment.index}. {comment.body}")
    reply_lines = []
    for slot in slots:
        reply_lines.append(
            f"{slot.index}. parent={slot.parent_index}, "
            f"voice_type={slot.author.voice_type}, "
            f"formality={slot.author.formality}, "
            f"slang={slot.author.slang_level:.2f}, "
            f"stance={slot.stance}, "
            f"mode={slot.mode_hint}"
        )
    prompt = f"""
<instructions>
너는 한국 갈등 커뮤니티 대댓글 생성기다.
기존 댓글에 붙는 짧은 대댓글만 작성한다.
{global_rules_block(global_rules)}[게시글]
광장: {plan.category}
제목: {plan.title}
본문:
{plan.body}

[기존 상위 댓글]
{chr(10).join(top_lines)}

[대댓글 슬롯]
{chr(10).join(reply_lines)}

[지시]
- parent로 지정된 댓글에만 자연스럽게 이어지는 말만 쓴다
- AGREE는 맞장구, DISAGREE는 공격 없는 짧은 반대, CURIOUS는 되묻기 결로 만든다
- 욕설, 인신공격, 협박, 혐오표현, 자해 조장, 범죄 조언 금지
- 초단문 위주로 짧게 쓴다
- 번호, 닉네임, 이모지, 메타 설명 금지
- 결과는 JSON 배열만 출력: [{{"index":7,"body":"..."}}, ...]
</instructions>
"""
    return prompt.strip()


def build_single_comment_prompt(plan: PostPlan, slot: CommentSlot, global_rules: str | None) -> str:
    return f"""
<instructions>
아래 사연에 붙을 수 있는 짧은 한국어 댓글 문장 한 개만 작성한다.
{global_rules_block(global_rules)}[게시글]
광장: {plan.category}
제목: {plan.title}
본문:
{plan.body}

[댓글 톤]
반응 기조: {slot.stance}
말투: {slot.author.formality}
slang_level: {slot.author.slang_level:.2f}
style hint: {slot.mode_hint}

[안전 규칙]
- 욕설, 인신공격, 협박, 혐오표현, 자해 조장, 범죄 조언 금지
- 복수, 바람, 몰래 떠보기, 보복성 행동을 부추기지 말고 안전한 일반 반응만 쓴다
- 평범한 커뮤니티 반응 수준으로만 쓴다
- 번호, 따옴표, 이모지, 메타 설명 금지
- 댓글 본문 한 개만 출력한다
</instructions>
""".strip()


def build_single_reply_prompt(plan: PostPlan, slot: CommentSlot, parent_body: str, global_rules: str | None) -> str:
    return f"""
<instructions>
아래 댓글에 붙을 수 있는 짧은 한국어 대댓글 문장 한 개만 작성한다.
{global_rules_block(global_rules)}[게시글]
광장: {plan.category}
제목: {plan.title}

[부모 댓글]
{parent_body}

[대댓글 톤]
반응 기조: {slot.stance}
말투: {slot.author.formality}
slang_level: {slot.author.slang_level:.2f}
style hint: {slot.mode_hint}

[안전 규칙]
- 욕설, 인신공격, 협박, 혐오표현, 자해 조장, 범죄 조언 금지
- 복수, 보복, 불법행동을 부추기지 말고 안전한 일반 반응만 쓴다
- 짧은 대댓글 한 개만 쓴다
- 번호, 따옴표, 이모지, 메타 설명 금지
- 대댓글 본문만 출력한다
</instructions>
""".strip()


def extract_json_payload(text: str) -> Any:
    text = text.strip()
    try:
        return json.loads(text)
    except Exception:
        pass
    starts = [("{", "}"), ("[", "]")]
    for left, right in starts:
        s = text.find(left)
        e = text.rfind(right)
        if s >= 0 and e > s:
            try:
                return json.loads(text[s:e + 1])
            except Exception:
                continue
    raise ValueError(f"Could not parse JSON payload from: {text[:160]!r}")


def call_clcocloud(
    api: ApiConfig,
    model: str,
    prompt: str,
    max_tokens: int,
    temperature: float = 0.55,
    timeout_sec: int = 180,
) -> str:
    url = api.base_url + "/v1/messages"
    headers = {
        "x-api-key": api.api_key,
        "anthropic-version": "2023-06-01",
        "content-type": "application/json",
    }
    body = {
        "model": model,
        "max_tokens": max_tokens,
        "temperature": temperature,
        "messages": [{"role": "user", "content": prompt}],
    }
    last_error: Exception | None = None
    for attempt in range(3):
        try:
            response = requests.post(url, headers=headers, json=body, timeout=timeout_sec)
            if response.status_code >= 400:
                raise RuntimeError(f"HTTP {response.status_code}: {response.text[:500]}")
            payload = response.json()
            content = payload.get("content") or []
            text = "".join(part.get("text", "") for part in content if part.get("type") == "text")
            if not text.strip():
                raise RuntimeError(f"Empty content: {payload}")
            return text.strip()
        except Exception as exc:
            last_error = exc
            if attempt < 2:
                time.sleep(1.5 * (attempt + 1))
                continue
            raise RuntimeError(f"clcocloud call failed for model={model}: {exc}") from exc
    raise RuntimeError(f"Unexpected API failure: {last_error}")


def validate_post_json(data: Any) -> tuple[str, str]:
    if not isinstance(data, dict):
        raise ValueError("Post payload is not an object")
    title = normalize_spaces(str(data.get("title", "") or ""))
    body = str(data.get("body", "") or "").strip()
    if not (12 <= len(title) <= 100):
        raise ValueError(f"Invalid title length: {len(title)}")
    if not (180 <= len(body) <= 1000):
        raise ValueError(f"Invalid body length: {len(body)}")
    if any(token in body.lower() for token in ("json", "title", "body", "```")):
        raise ValueError("Meta tokens detected in body")
    return title, body


def validate_comment_array(data: Any, slots: list[CommentSlot]) -> dict[int, str]:
    if not isinstance(data, list):
        raise ValueError("Comment payload is not an array")
    expected = {slot.index: slot for slot in slots}
    found: dict[int, str] = {}
    for row in data:
        if not isinstance(row, dict):
            continue
        try:
            index = int(row.get("index"))
        except Exception:
            continue
        body = str(row.get("body", "") or "").strip()
        if index not in expected or not body:
            continue
        if len(body) > max(120, expected[index].max_chars + 20):
            raise ValueError(f"Comment too long for slot {index}")
        if any(token in body.lower() for token in ("json", "```", "index")):
            raise ValueError(f"Meta tokens detected in slot {index}")
        found[index] = body
    if set(found) != set(expected):
        missing = sorted(set(expected) - set(found))
        raise ValueError(f"Missing comment slots: {missing}")
    return found


def looks_like_refusal(text: str) -> bool:
    lowered = text.lower()
    signatures = [
        "i can't help",
        "i can’t help",
        "i cannot help",
        "can't assist",
        "cannot assist",
        "i'm not able",
        "i’m not able",
        "not able to help",
        "won't help",
        "policy",
        "simulating authentic user comments",
    ]
    return any(signature in lowered for signature in signatures)


def normalize_comment_text(text: str) -> str:
    body = text.strip()
    body = re.sub(r"^['\"“”‘’\-\d\.\)\s]+", "", body)
    body = body.splitlines()[0].strip()
    body = body.strip("'\"“”‘’ ")
    return body


def validate_comment_text(text: str, max_chars: int) -> str:
    body = normalize_comment_text(text)
    if not body:
        raise ValueError("Empty comment body")
    if looks_like_refusal(body):
        raise ValueError(f"Model refusal: {body[:120]}")
    if len(body) > max(120, max_chars + 20):
        raise ValueError(f"Comment too long: {len(body)}")
    if any(token in body.lower() for token in ("```", "json", "index")):
        raise ValueError("Meta tokens detected")
    return body


def pick_comment_count(rng: random.Random) -> int:
    population = list(COMMENT_COUNT_WEIGHTS.keys())
    weights = list(COMMENT_COUNT_WEIGHTS.values())
    return rng.choices(population, weights=weights, k=1)[0]


def weighted_choice(rng: random.Random, options: list[tuple[Any, float]]) -> Any:
    total = sum(max(0.0, weight) for _, weight in options)
    if total <= 0:
        return options[0][0]
    needle = rng.random() * total
    for value, weight in options:
        needle -= max(0.0, weight)
        if needle <= 0:
            return value
    return options[-1][0]


def pick_comment_mode(persona: Persona, stance: str, rng: random.Random) -> str:
    slang = persona.slang_level
    polite = persona.formality == "polite"
    weights = {
        "REACTION_ONLY": 0.22,
        "SHORT_AGREE": 0.12,
        "QUESTION": 0.15,
        "DISAGREE": 0.12,
        "EXPERIENCE": 0.18,
        "ADVICE": 0.15,
        "TANGENT": 0.06,
    }
    if slang >= 0.6:
        weights["DISAGREE"] += 0.05
        weights["TANGENT"] += 0.03
        weights["EXPERIENCE"] -= 0.06
    if polite:
        weights["EXPERIENCE"] += 0.06
        weights["ADVICE"] += 0.04
        weights["TANGENT"] = 0.01
        weights["REACTION_ONLY"] -= 0.05
    if stance == "PARTNER":
        weights["DISAGREE"] += 0.10
        weights["REACTION_ONLY"] -= 0.05
    return weighted_choice(rng, [(name, weight) for name, weight in weights.items()])


def pick_comment_stance(persona: Persona, category: str, rng: random.Random) -> str:
    bias = float(persona.bias_profile.get(category, 0.0))
    author_prob = clamp(0.5 + bias / 2.0, 0.05, 0.95)
    partner_prob = clamp(0.5 - bias / 2.0, 0.05, 0.95)
    neutral_prob = max(0.1, 1.0 - abs(bias) * 0.6)
    return weighted_choice(
        rng,
        [
            ("AUTHOR", author_prob),
            ("PARTNER", partner_prob),
            ("NEUTRAL", neutral_prob),
        ],
    )


def pick_reply_stance(persona: Persona, rng: random.Random) -> str:
    if persona.bias_profile:
        bias = sum(float(value) for value in persona.bias_profile.values()) / len(persona.bias_profile)
    else:
        bias = 0.0
    agree_prob = clamp(0.5 + bias * 0.3, 0.1, 0.8)
    disagree_prob = clamp(0.5 - bias * 0.3, 0.1, 0.8)
    return weighted_choice(
        rng,
        [
            ("AGREE", agree_prob),
            ("DISAGREE", disagree_prob),
            ("CURIOUS", 0.3),
        ],
    )


def reply_length_hint(rng: random.Random) -> tuple[str, int]:
    if rng.random() < 0.6:
        return ("초단문: 8~25자 한마디만", 25)
    return ("짧게: 25~60자", 60)


def choose_distinct_persona(
    personas: list[Persona],
    used_ids: set[str],
    category: str,
    source_name: str,
    seed: int,
) -> Persona:
    candidates = [persona for persona in personas if persona.id not in used_ids]
    if not candidates:
        candidates = list(personas)
    ranked = sorted(
        candidates,
        key=lambda p: (
            p.interests.get(category, 0.0),
            1.0 if source_name == "BLIND" and p.voice_type == "BLIND" else 0.0,
            1.0 if source_name == "natepan" and p.voice_type == "NATEPAN" else 0.0,
            random.Random(seed + hash(p.id) % 1009).random(),
        ),
        reverse=True,
    )
    return ranked[0]


def plan_comment_slots(plan: PostPlan, personas: list[Persona], seed: int, end_day: date) -> tuple[list[CommentSlot], list[CommentSlot]]:
    rng = random.Random(seed)
    total = pick_comment_count(rng)
    top_count = max(2, min(8, int(round(total * rng.uniform(0.55, 0.8)))))
    top_count = min(top_count, total)
    reply_count = total - top_count

    used_ids = {plan.author.id}
    top_slots: list[CommentSlot] = []
    for index in range(1, top_count + 1):
        persona = choose_distinct_persona(personas, used_ids, plan.category, plan.source_example.source, seed + index)
        used_ids.add(persona.id)
        stance = pick_comment_stance(persona, plan.category, rng)
        mode = pick_comment_mode(persona, stance, rng)
        mode_hint, max_chars = COMMENT_MODE_HINTS[mode]
        created = plan.created_at_kst + timedelta(minutes=rng.randint(12, 340))
        if created.date() > end_day:
            created = datetime.combine(end_day, dt_time(23, 40), tzinfo=KST)
        top_slots.append(
            CommentSlot(
                index=index,
                author=persona,
                parent_index=None,
                stance=stance,
                mode_hint=mode_hint,
                max_chars=max_chars,
                created_at_kst=created,
            )
        )

    reply_slots: list[CommentSlot] = []
    next_index = top_count + 1
    for offset in range(reply_count):
        parent = rng.choice(top_slots)
        persona = choose_distinct_persona(
            [row for row in personas if row.id not in {plan.author.id, parent.author.id}],
            set(),
            plan.category,
            plan.source_example.source,
            seed + 200 + offset,
        )
        stance = pick_reply_stance(persona, rng)
        mode_hint, max_chars = reply_length_hint(rng)
        created = parent.created_at_kst + timedelta(minutes=rng.randint(4, 280))
        if created.date() > end_day:
            created = datetime.combine(end_day, dt_time(23, 48), tzinfo=KST)
        reply_slots.append(
            CommentSlot(
                index=next_index + offset,
                author=persona,
                parent_index=parent.index,
                stance=stance,
                mode_hint=mode_hint,
                max_chars=max_chars,
                created_at_kst=created,
            )
        )
    return top_slots, reply_slots


def generate_post_with_retry(api: ApiConfig, plan: PostPlan, global_rules: str | None) -> tuple[str, str]:
    feedback = ""
    for attempt in range(2):
        prompt = build_post_prompt(plan, global_rules)
        if feedback:
            prompt += f"\n\n[직전 실패 피드백]\n{feedback}\n"
        raw = call_clcocloud(api, api.post_model, prompt, max_tokens=1400, temperature=0.68)
        try:
            title, body = validate_post_json(extract_json_payload(raw))
            # WO-CRAWL-01: 표절 방어 — 원본과 문구가 그대로 겹치면 재생성 유도
            # (재시도 루프 재사용: 실패 사유를 feedback으로 넘겨 다음 시도에서 디테일 교체를 강제)
            if not passes_ngram_guard(body, plan.source_example.body):
                raise ValueError(
                    "생성된 본문이 원본 크롤 글과 문구가 과도하게 겹칩니다. "
                    "갈등 구조와 감정선만 유지하고 표현을 완전히 새로 써주세요."
                )
            return title, body
        except Exception as exc:
            feedback = str(exc)
    raise RuntimeError(f"Post generation failed for source #{plan.source_example.id}: {feedback}")


def generate_comment_group_with_retry(
    api: ApiConfig,
    plan: PostPlan,
    slots: list[CommentSlot],
    global_rules: str | None,
    reply_mode: bool = False,
) -> dict[int, str]:
    feedback = ""
    for attempt in range(2):
        prompt = build_reply_prompt(plan, slots, global_rules) if reply_mode else build_comment_prompt(plan, slots, global_rules)
        if feedback:
            prompt += f"\n\n[직전 실패 피드백]\n{feedback}\n"
        raw = call_clcocloud(api, api.comment_model, prompt, max_tokens=1800, temperature=0.62)
        try:
            return validate_comment_array(extract_json_payload(raw), slots)
        except Exception as exc:
            feedback = str(exc)
    kind = "reply" if reply_mode else "comment"
    raise RuntimeError(f"{kind} generation failed for source #{plan.source_example.id}: {feedback}")


def generate_single_comment_with_retry(
    api: ApiConfig,
    prompt: str,
    max_chars: int,
    source_id: int,
    kind: str,
) -> str:
    feedback = ""
    for attempt in range(2):
        prompt_to_send = prompt
        if feedback:
            prompt_to_send += (
                "\n\n[추가 안전 지시]\n"
                "복수, 자해, 범죄, 불법행동, 보복성 행동을 절대 권하지 말고 "
                "감정 공감이나 거리두기 수준의 평범한 댓글 한 줄만 쓸 것\n"
                f"\n[직전 실패 피드백]\n{feedback}\n"
            )
        raw = call_clcocloud(api, api.comment_model, prompt_to_send, max_tokens=160, temperature=0.58, timeout_sec=120)
        try:
            return validate_comment_text(raw, max_chars)
        except Exception as exc:
            feedback = str(exc)
    raise RuntimeError(f"{kind} generation failed for source #{source_id}: {feedback}")


def vote_side_from_bias(persona: Persona, category: str, rng: random.Random) -> str | None:
    bias = float(persona.bias_profile.get(category, 0.0))
    author_prob = clamp(0.5 + bias / 2.0, 0.05, 0.95)
    partner_prob = clamp(0.5 - bias / 2.0, 0.05, 0.95)
    neutral_prob = max(0.1, 1.0 - abs(bias) * 0.6)
    picked = weighted_choice(
        rng,
        [("author", author_prob), ("partner", partner_prob), ("neutral", neutral_prob)],
    )
    if picked == "neutral":
        if rng.random() < 0.65:
            return None
        return "author" if rng.random() < 0.5 else "partner"
    return picked


def sample_viewer_pool(
    personas: list[Persona],
    excluded: set[str],
    category: str,
    source_name: str,
    count: int,
    seed: int,
) -> list[Persona]:
    rng = random.Random(seed)
    candidates = [persona for persona in personas if persona.id not in excluded]
    weighted = sorted(
        candidates,
        key=lambda p: (
            p.interests.get(category, 0.0)
            + (0.25 if source_name == "BLIND" and p.voice_type == "BLIND" else 0.0)
            + (0.15 if source_name == "natepan" and p.voice_type == "NATEPAN" else 0.0)
            + rng.random() * 0.05
        ),
        reverse=True,
    )
    return weighted[:count]


def simulate_engagement(plan: PostPlan, personas: list[Persona], seed: int, end_day: date) -> None:
    rng = random.Random(seed)
    comment_authors = {slot.author.id: slot.author for slot in plan.comments}
    excluded = {plan.author.id, *comment_authors.keys()}
    extra_viewers = sample_viewer_pool(
        personas,
        excluded,
        plan.category,
        plan.source_example.source,
        rng.randint(10, 24),
        seed + 900,
    )
    reactors = list(comment_authors.values()) + extra_viewers
    vote_seen: set[str] = set()
    like_seen: set[str] = set()

    for actor in reactors:
        vote_prob = actor.vote_score * (0.78 if actor.id in comment_authors else 0.48)
        if actor.id not in vote_seen and rng.random() < vote_prob:
            side = vote_side_from_bias(actor, plan.category, rng)
            if side:
                created = plan.created_at_kst + timedelta(minutes=rng.randint(8, 780))
                if created.date() > end_day:
                    created = datetime.combine(end_day, dt_time(23, 45), tzinfo=KST)
                plan.votes.append({"user_id": actor.id, "side": side, "created_at_kst": created})
                vote_seen.add(actor.id)

        like_prob = actor.like_score * (0.52 if actor.id in comment_authors else 0.34)
        if actor.id not in like_seen and rng.random() < like_prob:
            created = plan.created_at_kst + timedelta(minutes=rng.randint(5, 900))
            if created.date() > end_day:
                created = datetime.combine(end_day, dt_time(23, 50), tzinfo=KST)
            plan.post_likes.append({"user_id": actor.id, "created_at_kst": created})
            like_seen.add(actor.id)

    temp_like_counts: Counter[int] = Counter()
    for actor in reactors:
        max_targets = 2 if actor.id in comment_authors else 1
        liked = 0
        shuffled = list(plan.comments)
        rng.shuffle(shuffled)
        for slot in shuffled:
            if liked >= max_targets:
                break
            if slot.author.id == actor.id:
                continue
            if rng.random() > actor.like_score * (0.28 if slot.parent_index is None else 0.20):
                continue
            created = slot.created_at_kst + timedelta(minutes=rng.randint(3, 260))
            if created.date() > end_day:
                created = datetime.combine(end_day, dt_time(23, 52), tzinfo=KST)
            plan.comment_likes.append(
                {"comment_index": slot.index, "user_id": actor.id, "created_at_kst": created}
            )
            temp_like_counts[slot.index] += 1
            liked += 1

    for slot in plan.comments:
        slot.created_at_kst = slot.created_at_kst
    plan.view_count = max(
        24,
        len(plan.votes) * 6 + len(plan.post_likes) * 5 + len(plan.comments) * 4 + rng.randint(18, 110),
    )


def build_post_plan(
    plan: PostPlan,
    personas: list[Persona],
    api: ApiConfig,
    global_rules: str | None,
    seed: int,
    end_day: date,
) -> PostPlan:
    print(
        f"[generate] {plan.day_key} {plan.category} source={plan.source_example.id} author={plan.author.id}",
        flush=True,
    )
    local_seed = seed + plan.source_example.id * 17
    title, body = generate_post_with_retry(api, plan, global_rules)
    plan.title = title
    plan.body = body
    top_slots, reply_slots = plan_comment_slots(plan, personas, local_seed, end_day)
    for slot in top_slots:
        slot.body = generate_single_comment_with_retry(
            api,
            build_single_comment_prompt(plan, slot, global_rules),
            slot.max_chars,
            plan.source_example.id,
            "comment",
        )
    plan.comments.extend(top_slots)

    if reply_slots:
        for slot in reply_slots:
            parent_body = next(comment.body for comment in top_slots if comment.index == slot.parent_index)
            slot.body = generate_single_comment_with_retry(
                api,
                build_single_reply_prompt(plan, slot, parent_body, global_rules),
                slot.max_chars,
                plan.source_example.id,
                "reply",
            )
        plan.comments.extend(reply_slots)

    simulate_engagement(plan, personas, local_seed + 5000, end_day)
    print(
        f"[generated] {plan.day_key} {plan.category} source={plan.source_example.id} comments={len(plan.comments)} votes={len(plan.votes)} likes={len(plan.post_likes)}",
        flush=True,
    )
    return plan


def to_utc_naive(dt: datetime) -> datetime:
    return dt.astimezone(UTC).replace(tzinfo=None)


def timestamp_slug(now: datetime) -> str:
    return now.astimezone(KST).strftime("%Y%m%d-%H%M%S")


def plan_manifest_dict(plans: list[PostPlan]) -> dict[str, Any]:
    by_category: Counter[str] = Counter()
    by_day: Counter[str] = Counter()
    for plan in plans:
        by_category[plan.category] += 1
        by_day[plan.day_key] += 1
    return {
        "summary": {
            "total_posts": len(plans),
            "category_counts": dict(by_category),
            "day_counts": dict(by_day),
        },
        "posts": [
            {
                "category": plan.category,
                "day": plan.day_key,
                "author_id": plan.author.id,
                "author_nickname": plan.author.nickname,
                "voice_type": plan.author.voice_type,
                "source": {
                    "example_id": plan.source_example.id,
                    "community": plan.source_example.source,
                    "raw_category": plan.source_example.raw_category,
                    "url": plan.source_example.source_url,
                    "title": plan.source_example.title,
                    "body": plan.source_example.body,
                },
                "scheduled_at_kst": plan.created_at_kst.isoformat(),
                "title": plan.title or None,
                "body": plan.body or None,
                "comment_count": len(plan.comments),
                "vote_count": len(plan.votes),
                "post_like_count": len(plan.post_likes),
                "comment_like_count": len(plan.comment_likes),
                "view_count": plan.view_count,
                "comments": [
                    {
                        "index": slot.index,
                        "parent_index": slot.parent_index,
                        "author_id": slot.author.id,
                        "author_nickname": slot.author.nickname,
                        "stance": slot.stance,
                        "body": slot.body or None,
                        "created_at_kst": slot.created_at_kst.isoformat(),
                    }
                    for slot in plan.comments
                ],
            }
            for plan in plans
        ],
    }


def write_manifest(output_dir: Path, prefix: str, data: dict[str, Any]) -> Path:
    def default_serializer(value: Any) -> Any:
        if isinstance(value, datetime):
            return value.isoformat()
        if isinstance(value, bytes):
            return value.hex()
        return str(value)

    output_dir.mkdir(parents=True, exist_ok=True)
    path = output_dir / f"{prefix}-{timestamp_slug(datetime.now(UTC))}.json"
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2, default=default_serializer), encoding="utf-8")
    return path


def backup_existing_public_feed(conn: pymysql.connections.Connection, output_dir: Path) -> Path:
    backup: dict[str, Any] = {}
    with conn.cursor() as cur:
        cur.execute(
            """
            SELECT p.*, u.nickname, u.synthetic
            FROM posts p
            JOIN users u ON u.id = p.author_id
            WHERE p.visibility = 'PUBLIC'
            ORDER BY p.created_at ASC, p.id ASC
            """
        )
        posts = cur.fetchall()
        post_ids = [row["id"] for row in posts]
        backup["posts"] = posts
        if not post_ids:
            return write_manifest(output_dir, "public-feed-backup-empty", backup)

        id_list = ",".join(["%s"] * len(post_ids))
        cur.execute(f"SELECT * FROM vote_options WHERE post_id IN ({id_list}) ORDER BY id ASC", post_ids)
        backup["vote_options"] = cur.fetchall()
        cur.execute(f"SELECT * FROM votes WHERE post_id IN ({id_list}) ORDER BY id ASC", post_ids)
        backup["votes"] = cur.fetchall()
        cur.execute(f"SELECT * FROM jurors WHERE post_id IN ({id_list}) ORDER BY id ASC", post_ids)
        backup["jurors"] = cur.fetchall()
        cur.execute(f"SELECT * FROM post_analysis WHERE post_id IN ({id_list}) ORDER BY post_id ASC", post_ids)
        backup["post_analysis"] = cur.fetchall()
        cur.execute(f"SELECT * FROM post_views WHERE post_id IN ({id_list}) ORDER BY id ASC", post_ids)
        backup["post_views"] = cur.fetchall()
        cur.execute(f"SELECT * FROM persona_seen_posts WHERE post_id IN ({id_list})", post_ids)
        backup["persona_seen_posts"] = cur.fetchall()
        cur.execute(f"SELECT * FROM marketing_job WHERE post_id IN ({id_list}) ORDER BY id ASC", post_ids)
        backup["marketing_job"] = cur.fetchall()
        cur.execute(f"SELECT * FROM persona_history_entries WHERE target_post_id IN ({id_list}) ORDER BY id ASC", post_ids)
        backup["persona_history_entries"] = cur.fetchall()
        cur.execute(
            f"SELECT * FROM persona_action_log WHERE target_type = 'POST' AND target_id IN ({id_list}) ORDER BY id ASC",
            post_ids,
        )
        backup["persona_action_log_posts"] = cur.fetchall()
        cur.execute(
            f"SELECT * FROM ai_content_corrections WHERE target_type = 'POST' AND target_id IN ({id_list}) ORDER BY id ASC",
            post_ids,
        )
        backup["ai_content_corrections_posts"] = cur.fetchall()
        cur.execute(
            f"SELECT * FROM community_reports WHERE target_type = 'POST' AND target_id IN ({id_list}) ORDER BY id ASC",
            post_ids,
        )
        backup["community_reports_posts"] = cur.fetchall()

        cur.execute(f"SELECT * FROM post_comments WHERE post_id IN ({id_list}) ORDER BY id ASC", post_ids)
        comments = cur.fetchall()
        backup["post_comments"] = comments
        if comments:
            comment_ids = [row["id"] for row in comments]
            comment_id_list = ",".join(["%s"] * len(comment_ids))
            cur.execute(f"SELECT * FROM post_likes WHERE comment_id IN ({comment_id_list}) ORDER BY id ASC", comment_ids)
            backup["comment_likes"] = cur.fetchall()
            comment_id_strings = [str(value) for value in comment_ids]
            str_id_list = ",".join(["%s"] * len(comment_id_strings))
            cur.execute(
                f"SELECT * FROM persona_action_log WHERE target_type = 'COMMENT' AND target_id IN ({str_id_list}) ORDER BY id ASC",
                comment_id_strings,
            )
            backup["persona_action_log_comments"] = cur.fetchall()
            cur.execute(
                f"SELECT * FROM ai_content_corrections WHERE target_type = 'COMMENT' AND target_id IN ({str_id_list}) ORDER BY id ASC",
                comment_id_strings,
            )
            backup["ai_content_corrections_comments"] = cur.fetchall()
            cur.execute(
                f"SELECT * FROM community_reports WHERE target_type = 'COMMENT' AND target_id IN ({str_id_list}) ORDER BY id ASC",
                comment_id_strings,
            )
            backup["community_reports_comments"] = cur.fetchall()
        cur.execute(f"SELECT * FROM post_likes WHERE post_id IN ({id_list}) ORDER BY id ASC", post_ids)
        backup["post_likes"] = cur.fetchall()
    return write_manifest(output_dir, "public-feed-backup", backup)


def delete_existing_public_feed(conn: pymysql.connections.Connection, post_ids: list[str]) -> None:
    if not post_ids:
        return
    with conn.cursor() as cur:
        id_list = ",".join(["%s"] * len(post_ids))
        cur.execute(f"SELECT id FROM post_comments WHERE post_id IN ({id_list})", post_ids)
        comment_ids = [row["id"] for row in cur.fetchall()]
        if comment_ids:
            comment_id_list = ",".join(["%s"] * len(comment_ids))
            str_comment_ids = [str(value) for value in comment_ids]
            str_comment_id_list = ",".join(["%s"] * len(str_comment_ids))
            cur.execute(f"DELETE FROM post_likes WHERE comment_id IN ({comment_id_list})", comment_ids)
            cur.execute(
                f"DELETE FROM persona_action_log WHERE target_type = 'COMMENT' AND target_id IN ({str_comment_id_list})",
                str_comment_ids,
            )
            cur.execute(
                f"DELETE FROM ai_content_corrections WHERE target_type = 'COMMENT' AND target_id IN ({str_comment_id_list})",
                str_comment_ids,
            )
            cur.execute(
                f"DELETE FROM community_reports WHERE target_type = 'COMMENT' AND target_id IN ({str_comment_id_list})",
                str_comment_ids,
            )
        cur.execute(f"DELETE FROM post_likes WHERE post_id IN ({id_list})", post_ids)
        cur.execute(f"DELETE FROM votes WHERE post_id IN ({id_list})", post_ids)
        cur.execute(f"DELETE FROM jurors WHERE post_id IN ({id_list})", post_ids)
        cur.execute(f"DELETE FROM post_analysis WHERE post_id IN ({id_list})", post_ids)
        cur.execute(f"DELETE FROM post_views WHERE post_id IN ({id_list})", post_ids)
        cur.execute(f"DELETE FROM marketing_job WHERE post_id IN ({id_list})", post_ids)
        cur.execute(f"DELETE FROM persona_seen_posts WHERE post_id IN ({id_list})", post_ids)
        cur.execute(f"DELETE FROM persona_history_entries WHERE target_post_id IN ({id_list})", post_ids)
        cur.execute(f"DELETE FROM persona_action_log WHERE target_type = 'POST' AND target_id IN ({id_list})", post_ids)
        cur.execute(f"DELETE FROM ai_content_corrections WHERE target_type = 'POST' AND target_id IN ({id_list})", post_ids)
        cur.execute(f"DELETE FROM community_reports WHERE target_type = 'POST' AND target_id IN ({id_list})", post_ids)
        cur.execute(f"DELETE FROM vote_options WHERE post_id IN ({id_list})", post_ids)
        cur.execute(f"DELETE FROM post_comments WHERE post_id IN ({id_list})", post_ids)
        cur.execute(f"DELETE FROM posts WHERE id IN ({id_list})", post_ids)


def insert_rebuilt_feed(conn: pymysql.connections.Connection, plans: list[PostPlan]) -> None:
    with conn.cursor() as cur:
        post_ids: list[str] = []
        for plan in plans:
            post_id = uuid.uuid4().hex
            plan.post_id = post_id
            post_ids.append(post_id)
            created_utc = to_utc_naive(plan.created_at_kst)
            vote_close = created_utc + timedelta(days=7)
            cur.execute(
                """
                INSERT INTO posts (
                    id, author_id, session_id, title, user_title, juror_count, invite_token,
                    partner_user_id, partner_body_raw, partner_body_published, partner_answered_at,
                    publish_mode, vote_duration_hours, body_raw, body_published, category,
                    visibility, status, neutralization_passed, vote_close_at, created_at,
                    updated_at, view_count, deleted_at, deleted_by_admin_id, source_community,
                    source_example_id, source_original_body, source_original_title, source_url
                ) VALUES (
                    %s, %s, NULL, %s, %s, 0, NULL,
                    NULL, NULL, NULL, NULL,
                    'PUBLISH_NOW', NULL, %s, %s, %s,
                    'PUBLIC', 'VOTING', 1, %s, %s,
                    %s, %s, NULL, NULL, %s,
                    %s, %s, %s, %s
                )
                """,
                (
                    post_id,
                    plan.author.id,
                    plan.title,
                    plan.title,
                    plan.body,
                    plan.body,
                    plan.category,
                    vote_close,
                    created_utc,
                    created_utc,
                    plan.view_count,
                    plan.source_example.source,
                    plan.source_example.id,
                    plan.source_example.body,
                    plan.source_example.title,
                    plan.source_example.source_url,
                ),
            )
            cur.execute(
                "INSERT INTO vote_options (post_id, label, order_idx) VALUES (%s, '작성자', 0), (%s, '상대방', 1)",
                (post_id, post_id),
            )

        option_map: dict[str, dict[str, int]] = {}
        cur.execute(
            f"SELECT id, post_id, label FROM vote_options WHERE post_id IN ({','.join(['%s'] * len(post_ids))})",
            post_ids,
        )
        for row in cur.fetchall():
            option_map.setdefault(str(row["post_id"]), {})[str(row["label"])] = int(row["id"])

        comment_pk_map: dict[tuple[str, int], int] = {}
        for plan in plans:
            for slot in sorted(plan.comments, key=lambda row: (row.parent_index is not None, row.index)):
                created_utc = to_utc_naive(slot.created_at_kst)
                parent_db_id = comment_pk_map.get((plan.post_id or "", slot.parent_index or -1))
                like_count = sum(1 for row in plan.comment_likes if row["comment_index"] == slot.index)
                cur.execute(
                    """
                    INSERT INTO post_comments (
                        post_id, parent_comment_id, author_id, body, status, like_count,
                        created_at, updated_at, deleted_at, deleted_by_admin_id
                    ) VALUES (%s, %s, %s, %s, 'ACTIVE', %s, %s, %s, NULL, NULL)
                    """,
                    (
                        plan.post_id,
                        parent_db_id,
                        slot.author.id,
                        slot.body,
                        like_count,
                        created_utc,
                        created_utc,
                    ),
                )
                comment_pk_map[(plan.post_id or "", slot.index)] = int(cur.lastrowid)

        vote_rows: list[tuple[str, int, str, datetime]] = []
        post_like_rows: list[tuple[str, str, datetime]] = []
        comment_like_rows: list[tuple[int, str, datetime]] = []
        for plan in plans:
            for vote in plan.votes:
                label = "작성자" if vote["side"] == "author" else "상대방"
                option_id = option_map[plan.post_id or ""].get(label)
                if option_id is None:
                    continue
                vote_rows.append(
                    (
                        plan.post_id or "",
                        option_id,
                        vote["user_id"],
                        to_utc_naive(vote["created_at_kst"]),
                    )
                )
            for like in plan.post_likes:
                post_like_rows.append((plan.post_id or "", like["user_id"], to_utc_naive(like["created_at_kst"])))
            for like in plan.comment_likes:
                comment_id = comment_pk_map.get((plan.post_id or "", like["comment_index"]))
                if comment_id is None:
                    continue
                comment_like_rows.append((comment_id, like["user_id"], to_utc_naive(like["created_at_kst"])))

        if vote_rows:
            cur.executemany(
                "INSERT INTO votes (post_id, option_id, voter_user_id, created_at) VALUES (%s, %s, %s, %s)",
                vote_rows,
            )
        if post_like_rows:
            cur.executemany(
                "INSERT INTO post_likes (post_id, comment_id, user_id, created_at) VALUES (%s, NULL, %s, %s)",
                post_like_rows,
            )
        if comment_like_rows:
            cur.executemany(
                "INSERT INTO post_likes (post_id, comment_id, user_id, created_at) VALUES (NULL, %s, %s, %s)",
                comment_like_rows,
            )


def main() -> None:
    args = parse_args()
    if args.apply and not args.generate:
        raise SystemExit("--apply requires --generate")

    env_values = load_dotenv(args.env_file)
    db_config = build_db_config(args, env_values)
    output_dir = ROOT / args.output_dir
    start_day = date.fromisoformat(args.date_start)
    end_day = date.fromisoformat(args.date_end)
    if start_day > end_day:
        raise SystemExit("date-start must be <= date-end")

    conn = connect_db(db_config)
    try:
        api = build_api_config(conn, env_values, args)
        global_rules = query_global_rules(conn)
        personas = query_personas(conn)
        examples = query_source_examples(conn)
        selected = select_sources_by_category(examples, args.per_category, conn)
        planned = assign_day_slots(selected, personas, args.seed, start_day, end_day)

        selection_manifest = plan_manifest_dict(planned)
        selection_path = write_manifest(output_dir, "rebuilt-feed-selection", selection_manifest)
        print(f"[selection] wrote {selection_path}")

        if args.generate:
            generated_plans: list[PostPlan] = []
            with ThreadPoolExecutor(max_workers=args.max_workers) as pool:
                future_map = {
                    pool.submit(
                        build_post_plan,
                        plan,
                        personas,
                        api,
                        global_rules,
                        args.seed,
                        end_day,
                    ): plan
                    for plan in planned
                }
                for future in as_completed(future_map):
                    generated_plans.append(future.result())
            generated_plans.sort(key=lambda plan: plan.created_at_kst)
            generated_manifest = plan_manifest_dict(generated_plans)
            generated_path = write_manifest(output_dir, "rebuilt-feed-generated", generated_manifest)
            print(f"[generate] wrote {generated_path}")
            planned = generated_plans

        if args.apply:
            backup_path = backup_existing_public_feed(conn, output_dir)
            print(f"[backup] wrote {backup_path}")
            with conn.cursor() as cur:
                cur.execute("SELECT id FROM posts WHERE visibility = 'PUBLIC'")
                existing_post_ids = [row["id"] for row in cur.fetchall()]
            delete_existing_public_feed(conn, existing_post_ids)
            insert_rebuilt_feed(conn, planned)
            conn.commit()
            apply_manifest = {
                "summary": {
                    "deleted_public_posts": len(existing_post_ids),
                    "inserted_posts": len(planned),
                    "categories": dict(Counter(plan.category for plan in planned)),
                }
            }
            apply_path = write_manifest(output_dir, "rebuilt-feed-apply", apply_manifest)
            print(f"[apply] wrote {apply_path}")
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()


if __name__ == "__main__":
    main()
