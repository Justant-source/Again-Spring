#!/usr/bin/env python3
"""Curate legacy synthetic posts into 6 plazas and optionally rewrite/apply them."""

from __future__ import annotations

import argparse
import json
import math
import re
import sys
from collections import Counter, defaultdict
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Any
from zoneinfo import ZoneInfo

import pymysql
import requests

ROOT = Path(__file__).resolve().parents[2]
LEARNING_SERVICES = ROOT / "ai-user" / "learning" / "app" / "services"
if str(LEARNING_SERVICES) not in sys.path:
    sys.path.append(str(LEARNING_SERVICES))

from plaza_classifier import _normalize_text, _score_plaza, classify_plaza  # type: ignore

PLAZAS = ["COUPLE", "FAMILY", "FRIEND", "MARRIED", "WORK", "OTHER"]
PRIMARY_PLAZAS = ["COUPLE", "FAMILY", "FRIEND", "MARRIED", "WORK"]
KST = ZoneInfo("Asia/Seoul")
DEFAULT_KEEP_PER_CATEGORY = 20
DEFAULT_MAX_WORKERS = 8
SIMILARITY_THRESHOLD = 0.72
PLACEHOLDER_PATTERNS = [
    "placeholder",
    "debug",
    "lorem ipsum",
    "todo",
    "tbd",
    "sample text",
    "credit balance is too low",
    "api error",
]


@dataclass
class DbConfig:
    host: str
    port: int
    user: str
    password: str
    database: str


@dataclass
class PostRecord:
    id: str
    author_id: str
    synthetic: bool
    nickname: str
    current_category: str
    title: str
    user_title: str
    body_raw: str
    body_published: str
    status: str
    visibility: str
    created_at: datetime
    vote_count: int
    comment_count: int
    like_count: int
    archetype: str
    slang_level: float
    voice_profile: dict[str, Any]
    deleted_reason: str | None = None
    predicted_category: str = "OTHER"
    category_scores: dict[str, int] = field(default_factory=dict)
    classifier_gap: int = 0
    body_len: int = 0
    kst_day: str = ""
    time_bucket: str = "mid"
    engagement_score: float = 0.0
    persona_count_hint: int = 0
    normalized_title: str = ""
    signature_text: str = ""
    trigrams: set[str] = field(default_factory=set)

    @property
    def effective_body(self) -> str:
        return (self.body_published or self.body_raw or "").strip()

    @property
    def effective_title(self) -> str:
        return (self.title or self.user_title or "").strip()

    @property
    def formality(self) -> str:
        return "polite" if str(self.voice_profile.get("formality", "")).lower() == "polite" else "casual"

    @property
    def voice_type(self) -> str:
        return str(self.voice_profile.get("voice_type", "GENERAL") or "GENERAL")

    @property
    def demographic(self) -> str | None:
        parts: list[str] = []
        age = str(self.voice_profile.get("age", "")).strip()
        age_map = {
            "20s": "20s",
            "20s_early": "early 20s",
            "20s_late": "late 20s",
            "30s": "30s",
            "30s_early": "early 30s",
            "30s_late": "late 30s",
            "40s": "40s",
            "50s": "50s",
        }
        gender = str(self.voice_profile.get("gender", "")).strip().upper()
        political = str(self.voice_profile.get("political_orientation", "")).strip().lower()
        if age in age_map:
            parts.append(age_map[age])
        if gender in {"M", "MALE"}:
            parts.append("male")
        elif gender in {"F", "FEMALE"}:
            parts.append("female")
        if political in {"progressive", "moderate", "conservative"}:
            parts.append(political)
        return ", ".join(parts) if parts else None

    @property
    def correction_cautions(self) -> str | None:
        cautions = self.voice_profile.get("correction_cautions")
        if not isinstance(cautions, list):
            return None
        lines: list[str] = []
        for item in cautions:
            if isinstance(item, dict) and item.get("active") is True:
                text = str(item.get("text", "")).strip()
                if text:
                    lines.append(f"- {text}")
        return "\n".join(lines) if lines else None


@dataclass
class SelectionEntry:
    post: PostRecord
    action: str
    final_category: str
    selection_reason: str
    rewrite_instruction: str = ""
    rewritten_title: str | None = None
    rewritten_body: str | None = None
    rewrite_error: str | None = None
    apply_error: str | None = None
    delete_error: str | None = None


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--env-file", default="env/.env.dev", help="dotenv file for DB defaults")
    parser.add_argument("--db-host", default="127.0.0.1")
    parser.add_argument("--db-port", type=int, default=3309)
    parser.add_argument("--db-user", default=None)
    parser.add_argument("--db-password", default=None)
    parser.add_argument("--db-name", default=None)
    parser.add_argument("--base-url", default=None, help="backend base URL, e.g. http://localhost:8090")
    parser.add_argument("--llm-url", default=None, help="llm-ai-user base URL, e.g. http://localhost:8092")
    parser.add_argument("--bearer-token", default=None)
    parser.add_argument("--login-email", default=None)
    parser.add_argument("--login-password", default=None)
    parser.add_argument("--output-dir", default="ai-user/tools/reports")
    parser.add_argument("--keep-per-category", type=int, default=DEFAULT_KEEP_PER_CATEGORY)
    parser.add_argument("--max-workers", type=int, default=DEFAULT_MAX_WORKERS)
    parser.add_argument("--rewrite", action="store_true", help="rewrite selected posts through llm service")
    parser.add_argument("--apply", action="store_true", help="PATCH selected posts and DELETE non-selected AI posts")
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
    return pymysql.connect(
        host=config.host,
        port=config.port,
        user=config.user,
        password=config.password,
        database=config.database,
        cursorclass=pymysql.cursors.DictCursor,
        autocommit=True,
        charset="utf8mb4",
    )


def bit_to_bool(value: Any) -> bool:
    if isinstance(value, (bytes, bytearray)):
        return value != b"\x00"
    return bool(value)


def json_loads(value: Any) -> dict[str, Any]:
    if not value:
        return {}
    if isinstance(value, dict):
        return value
    try:
        parsed = json.loads(value)
    except Exception:
        return {}
    return parsed if isinstance(parsed, dict) else {}


def query_global_rules(conn: pymysql.connections.Connection) -> str | None:
    sql = """
        SELECT rule_text
        FROM ai_global_rules
        WHERE active = 1
          AND scope IN ('ALL', 'POST')
        ORDER BY id ASC
    """
    with conn.cursor() as cur:
        cur.execute(sql)
        rows = cur.fetchall()
    rules = [f"- {str(row['rule_text']).strip()}" for row in rows if str(row["rule_text"]).strip()]
    return "\n".join(rules) if rules else None


def query_posts(conn: pymysql.connections.Connection) -> list[PostRecord]:
    sql = """
        SELECT
            p.id,
            p.author_id,
            p.title,
            p.user_title,
            p.body_raw,
            p.body_published,
            p.category,
            p.status,
            p.visibility,
            p.created_at,
            COALESCE(v.vote_count, 0) AS vote_count,
            COALESCE(c.comment_count, 0) AS comment_count,
            COALESCE(l.like_count, 0) AS like_count,
            u.synthetic,
            u.nickname,
            COALESCE(per.archetype, '') AS archetype,
            COALESCE(per.slang_level, 0.5) AS slang_level,
            COALESCE(per.voice_profile, '{}') AS voice_profile
        FROM posts p
        JOIN users u ON u.id = p.author_id
        LEFT JOIN personas per ON per.id = p.author_id
        LEFT JOIN (
            SELECT post_id, COUNT(*) AS vote_count
            FROM votes
            GROUP BY post_id
        ) v ON v.post_id = p.id
        LEFT JOIN (
            SELECT post_id, COUNT(*) AS comment_count
            FROM post_comments
            WHERE deleted_at IS NULL AND status = 'ACTIVE'
            GROUP BY post_id
        ) c ON c.post_id = p.id
        LEFT JOIN (
            SELECT post_id, COUNT(*) AS like_count
            FROM post_likes
            WHERE comment_id IS NULL
            GROUP BY post_id
        ) l ON l.post_id = p.id
        WHERE p.deleted_at IS NULL
          AND p.visibility = 'PUBLIC'
        ORDER BY p.created_at ASC, p.id ASC
    """
    with conn.cursor() as cur:
        cur.execute(sql)
        rows = cur.fetchall()

    posts: list[PostRecord] = []
    for row in rows:
        created_at = row["created_at"]
        if created_at.tzinfo is None:
            created_at = created_at.replace(tzinfo=timezone.utc)
        body_published = str(row["body_published"] or "")
        body_raw = str(row["body_raw"] or "")
        title = str(row["title"] or "")
        user_title = str(row["user_title"] or "")
        voice_profile = json_loads(row["voice_profile"])
        record = PostRecord(
            id=str(row["id"]),
            author_id=str(row["author_id"]),
            synthetic=bit_to_bool(row["synthetic"]),
            nickname=str(row["nickname"] or ""),
            current_category=str(row["category"] or "OTHER"),
            title=title,
            user_title=user_title,
            body_raw=body_raw,
            body_published=body_published,
            status=str(row["status"] or ""),
            visibility=str(row["visibility"] or ""),
            created_at=created_at,
            vote_count=int(row["vote_count"] or 0),
            comment_count=int(row["comment_count"] or 0),
            like_count=int(row["like_count"] or 0),
            archetype=str(row["archetype"] or ""),
            slang_level=float(row["slang_level"] or 0.5),
            voice_profile=voice_profile,
        )
        decorate_post(record)
        posts.append(record)
    assign_persona_hints(posts)
    return posts


def decorate_post(post: PostRecord) -> None:
    body = post.effective_body
    title = post.effective_title
    post.body_len = len(body.strip())
    post.kst_day = post.created_at.astimezone(KST).date().isoformat()
    normalized_body = _normalize_text(body)
    normalized_title = _normalize_text(title)
    post.category_scores = {
        plaza: int(_score_plaza(normalized_body, normalized_title, plaza))
        for plaza in PRIMARY_PLAZAS
    }
    post.predicted_category = classify_plaza(body, title)
    ranked = sorted(post.category_scores.values(), reverse=True)
    post.classifier_gap = (ranked[0] - ranked[1]) if len(ranked) >= 2 else 0
    post.engagement_score = (
        post.vote_count * 1.0
        + post.comment_count * 1.8
        + post.like_count * 0.35
        + min(post.body_len, 500) / 120.0
    )
    post.normalized_title = normalize_loose(title)
    post.signature_text = normalize_loose(f"{title} {body[:160]}")
    post.trigrams = trigram_set(post.signature_text)


def assign_persona_hints(posts: list[PostRecord]) -> None:
    counts = Counter(post.author_id for post in posts if post.synthetic)
    timestamps = sorted(post.created_at for post in posts if post.synthetic)
    if not timestamps:
        return
    start = timestamps[0].timestamp()
    end = timestamps[-1].timestamp()
    span = max(end - start, 1.0)
    for post in posts:
        post.persona_count_hint = counts[post.author_id]
        ratio = (post.created_at.timestamp() - start) / span
        if ratio < 0.33:
            post.time_bucket = "early"
        elif ratio < 0.66:
            post.time_bucket = "mid"
        else:
            post.time_bucket = "late"


def normalize_loose(text: str) -> str:
    normalized = (text or "").lower()
    normalized = re.sub(r"[^0-9a-z가-힣\s]", " ", normalized)
    normalized = re.sub(r"\s+", " ", normalized).strip()
    return normalized


def trigram_set(text: str) -> set[str]:
    source = text.replace(" ", "")
    if len(source) < 3:
        return {source} if source else set()
    return {source[i:i + 3] for i in range(len(source) - 2)}


def jaccard_similarity(left: PostRecord, right: PostRecord) -> float:
    if not left.trigrams or not right.trigrams:
        return 0.0
    union = left.trigrams | right.trigrams
    if not union:
        return 0.0
    return len(left.trigrams & right.trigrams) / len(union)


def detect_hard_rejects(posts: list[PostRecord]) -> None:
    title_groups: dict[str, list[PostRecord]] = defaultdict(list)
    for post in posts:
        if post.synthetic and len(post.normalized_title) >= 6:
            title_groups[post.normalized_title].append(post)

    duplicate_tail_ids: set[str] = set()
    for group in title_groups.values():
        if len(group) <= 1:
            continue
        ranked = sorted(group, key=lambda item: (-item.engagement_score, -item.body_len, item.id))
        keeper = ranked[0]
        for candidate in ranked[1:]:
            similarity = jaccard_similarity(keeper, candidate)
            if similarity >= 0.62 or candidate.body_len < 220:
                duplicate_tail_ids.add(candidate.id)

    for post in posts:
        if not post.synthetic:
            continue
        body = post.effective_body.lower()
        title = post.effective_title.lower()
        if post.body_len < 60:
            post.deleted_reason = "body_lt_60"
            continue
        if any(pattern in body or pattern in title for pattern in PLACEHOLDER_PATTERNS):
            post.deleted_reason = "placeholder_or_debug"
            continue
        if post.id in duplicate_tail_ids:
            post.deleted_reason = "duplicate_tail"


def is_other_candidate(post: PostRecord) -> bool:
    if post.deleted_reason is not None:
        return False
    max_score = max(post.category_scores.values()) if post.category_scores else 0
    predicted = post.predicted_category
    if predicted == "OTHER":
        return True
    if max_score <= 4 and post.classifier_gap <= 1:
        return True
    if post.current_category != predicted and max_score <= 5 and post.classifier_gap <= 1:
        return True
    return False


def persona_cap_for(category: str) -> int:
    return 4 if category in {"FRIEND", "MARRIED"} else 3


def build_selection_reason(post: PostRecord, target: str, stage: str) -> str:
    return (
        f"{stage}; day={post.kst_day}; persona={post.author_id}; "
        f"eng={post.engagement_score:.2f}; predicted={post.predicted_category}; "
        f"gap={post.classifier_gap}; body_len={post.body_len}"
    )


def candidate_priority(post: PostRecord, target: str, persona_counts: Counter[str], bucket_counts: Counter[str], day_counts: Counter[str]) -> float:
    rarity_bonus = 2.5 / max(post.persona_count_hint, 1)
    bucket_bonus = 1.5 if bucket_counts[post.time_bucket] == 0 else 0.4 / bucket_counts[post.time_bucket]
    day_bonus = 0.8 if day_counts[post.kst_day] == 0 else -0.2 * day_counts[post.kst_day]
    target_bonus = 0.0
    if target == "OTHER":
        target_bonus = 1.8 if post.predicted_category == "OTHER" else 0.7
    else:
        target_bonus = 1.2 if post.current_category == target else 0.0
    persona_penalty = 0.6 * persona_counts[post.author_id]
    return post.engagement_score + rarity_bonus + bucket_bonus + day_bonus + target_bonus - persona_penalty


def conflicts(candidate: PostRecord, selected: list[PostRecord], similarity_threshold: float) -> bool:
    for existing in selected:
        if existing.id == candidate.id:
            return True
        if jaccard_similarity(candidate, existing) >= similarity_threshold:
            return True
    return False


def select_posts_for_category(target: str, candidates: list[PostRecord], limit: int) -> list[SelectionEntry]:
    if len(candidates) < limit:
        raise RuntimeError(f"{target}: only {len(candidates)} candidates available for {limit} slots")

    persona_cap = persona_cap_for(target)
    selected: list[PostRecord] = []
    selected_ids: set[str] = set()
    persona_counts: Counter[str] = Counter()
    day_counts: Counter[str] = Counter()
    bucket_counts: Counter[str] = Counter()

    by_day: dict[str, list[PostRecord]] = defaultdict(list)
    for post in candidates:
        by_day[post.kst_day].append(post)

    for day in sorted(by_day):
        if len(selected) >= limit:
            break
        best: PostRecord | None = None
        best_score = -math.inf
        for post in by_day[day]:
            if post.id in selected_ids or persona_counts[post.author_id] >= persona_cap:
                continue
            if conflicts(post, selected, SIMILARITY_THRESHOLD):
                continue
            score = candidate_priority(post, target, persona_counts, bucket_counts, day_counts)
            if score > best_score:
                best = post
                best_score = score
        if best is None:
            continue
        selected.append(best)
        selected_ids.add(best.id)
        persona_counts[best.author_id] += 1
        day_counts[best.kst_day] += 1
        bucket_counts[best.time_bucket] += 1

    fill_specs = [
        (2, persona_cap, SIMILARITY_THRESHOLD, "fill-strict"),
        (3, persona_cap, 0.78, "fill-relaxed-day"),
        (4, persona_cap + 1, 0.84, "fill-relaxed-persona"),
        (8, limit, 1.10, "fill-last-resort"),
    ]
    reasons: dict[str, str] = {post.id: build_selection_reason(post, target, "day-first") for post in selected}
    for max_per_day, max_per_persona, similarity_threshold, stage in fill_specs:
        if len(selected) >= limit:
            break
        remaining = [post for post in candidates if post.id not in selected_ids]
        ranked = sorted(
            remaining,
            key=lambda post: (
                -candidate_priority(post, target, persona_counts, bucket_counts, day_counts),
                post.created_at,
                post.id,
            ),
        )
        for post in ranked:
            if len(selected) >= limit:
                break
            if day_counts[post.kst_day] >= max_per_day:
                continue
            if persona_counts[post.author_id] >= max_per_persona:
                continue
            if conflicts(post, selected, similarity_threshold):
                continue
            selected.append(post)
            selected_ids.add(post.id)
            persona_counts[post.author_id] += 1
            day_counts[post.kst_day] += 1
            bucket_counts[post.time_bucket] += 1
            reasons[post.id] = build_selection_reason(post, target, stage)

    if len(selected) != limit:
        raise RuntimeError(f"{target}: failed to fill {limit} slots, got {len(selected)}")

    return [
        SelectionEntry(
            post=post,
            action="keep_reclassify_correct" if target != post.current_category else "keep_correct",
            final_category=target,
            selection_reason=reasons[post.id],
            rewrite_instruction=build_rewrite_instruction(post, target),
        )
        for post in selected
    ]


def build_rewrite_instruction(post: PostRecord, target: str) -> str:
    parts = [
        "Keep the original incident and emotional direction.",
        "Only naturalize awkward phrasing, repetitions, and obvious AI stiffness.",
        "Do not turn it into a brand-new story.",
    ]
    if target == "OTHER":
        parts.append("Reduce overly strong relationship-specific framing if it dominates the current wording.")
    elif target != post.current_category:
        parts.append(f"Make the framing read naturally inside the {target} plaza with minimal edits.")
    return " ".join(parts)


def curate(posts: list[PostRecord], keep_per_category: int) -> tuple[list[SelectionEntry], list[SelectionEntry], list[PostRecord]]:
    detect_hard_rejects(posts)
    human_posts = [post for post in posts if not post.synthetic]
    ai_posts = [post for post in posts if post.synthetic]

    selected_entries: list[SelectionEntry] = []
    selected_ids: set[str] = set()

    other_candidates = [post for post in ai_posts if is_other_candidate(post)]
    other_selected = select_posts_for_category("OTHER", other_candidates, keep_per_category)
    selected_entries.extend(other_selected)
    selected_ids.update(entry.post.id for entry in other_selected)

    for category in PRIMARY_PLAZAS:
        candidates = [
            post for post in ai_posts
            if post.id not in selected_ids
            and post.deleted_reason is None
            and post.current_category == category
        ]
        chosen = select_posts_for_category(category, candidates, keep_per_category)
        selected_entries.extend(chosen)
        selected_ids.update(entry.post.id for entry in chosen)

    delete_entries: list[SelectionEntry] = []
    for post in ai_posts:
        if post.id in selected_ids:
            continue
        action = "delete"
        reason = post.deleted_reason or "non_selected"
        delete_entries.append(
            SelectionEntry(
                post=post,
                action=action,
                final_category=post.current_category,
                selection_reason=reason,
            )
        )

    return selected_entries, delete_entries, human_posts


def voice_block_for_post(voice_profile: dict[str, Any]) -> str:
    parts: list[str] = []
    general_style = str(voice_profile.get("general_style", "")).strip()
    if general_style:
        parts.append(general_style)
    post_openers = voice_profile.get("example_post_openers")
    if isinstance(post_openers, list) and post_openers:
        samples = " / ".join(str(item) for item in post_openers[:2])
        parts.append(f"[post openers] {samples}")
    writing_quirks = voice_profile.get("writing_quirks")
    if isinstance(writing_quirks, dict):
        features = str(writing_quirks.get("features", "")).strip()
        if features:
            parts.append(f"[style pattern] {features}")
        errors = writing_quirks.get("consistent_errors")
        if isinstance(errors, list) and errors:
            parts.append(f"[typo pattern] {' / '.join(str(item) for item in errors[:2])}")
        if writing_quirks.get("mobile_typos") is True:
            parts.append("[typo pattern] mobile typos appear naturally")
    lexicon = voice_profile.get("lexicon")
    if isinstance(lexicon, dict):
        phrases = lexicon.get("signature_phrases")
        if isinstance(phrases, list) and phrases:
            parts.append(f"[signature phrases] {' / '.join(str(item) for item in phrases[:3])}")
    for key, label in (("age_voice_notes", "age"), ("political_voice_notes", "politics")):
        text = str(voice_profile.get(key, "")).strip()
        if text:
            parts.append(f"[{label}] {text}")
    return "\n".join(parts) if parts else "general community user"


def login_for_token(base_url: str, email: str, password: str) -> str:
    response = requests.post(
        f"{base_url.rstrip('/')}/api/auth/login",
        json={"email": email, "password": password},
        timeout=30,
    )
    response.raise_for_status()
    payload = response.json()
    token = payload.get("token", {}).get("accessToken")
    if not token:
        raise RuntimeError("login response does not contain access token")
    return token


def build_rewrite_payload(entry: SelectionEntry, global_rules: str | None) -> dict[str, Any]:
    post = entry.post
    return {
        "postId": post.id,
        "personaId": post.author_id,
        "voiceProfile": voice_block_for_post(post.voice_profile),
        "slangLevel": post.slang_level,
        "category": post.current_category,
        "targetCategory": entry.final_category,
        "formality": post.formality,
        "demographic": post.demographic,
        "timeoutMs": 180000,
        "correctionCautions": post.correction_cautions,
        "globalForbidRules": global_rules,
        "backend": "API",
        "voiceType": post.voice_type,
        "originalTitle": post.effective_title,
        "originalBody": post.effective_body,
        "rewriteInstruction": entry.rewrite_instruction,
    }


def rewrite_selected(entries: list[SelectionEntry], llm_url: str, global_rules: str | None, max_workers: int) -> None:
    endpoint = f"{llm_url.rstrip('/')}/internal/rewrite/post"

    def worker(entry: SelectionEntry) -> tuple[str, str | None, str | None, str | None]:
        payload = build_rewrite_payload(entry, global_rules)
        response = requests.post(endpoint, json=payload, timeout=240)
        response.raise_for_status()
        data = response.json()
        error = data.get("error")
        if error:
            return entry.post.id, None, None, f"{data.get('errorType', 'REWRITE_ERROR')}: {error}"
        return entry.post.id, data.get("title"), data.get("body"), None

    with ThreadPoolExecutor(max_workers=max_workers) as executor:
        future_map = {executor.submit(worker, entry): entry for entry in entries}
        for future in as_completed(future_map):
            entry = future_map[future]
            try:
                post_id, title, body, error = future.result()
                if post_id != entry.post.id:
                    raise RuntimeError("rewrite result post_id mismatch")
                entry.rewritten_title = title
                entry.rewritten_body = body
                entry.rewrite_error = error
            except Exception as exc:
                entry.rewrite_error = str(exc)


def apply_changes(selected_entries: list[SelectionEntry], delete_entries: list[SelectionEntry], base_url: str, bearer_token: str, max_workers: int) -> None:
    headers = {"Authorization": f"Bearer {bearer_token}"}

    def patch_worker(entry: SelectionEntry) -> tuple[str, str | None]:
        if entry.rewrite_error:
            return entry.post.id, "rewrite_failed"
        payload = {
            "title": entry.rewritten_title or entry.post.effective_title,
            "bodyRaw": entry.rewritten_body or entry.post.effective_body,
            "category": entry.final_category,
        }
        response = requests.patch(
            f"{base_url.rstrip('/')}/api/admin/content/posts/{entry.post.id}",
            json=payload,
            headers=headers,
            timeout=60,
        )
        response.raise_for_status()
        return entry.post.id, None

    def delete_worker(entry: SelectionEntry) -> tuple[str, str | None]:
        response = requests.delete(
            f"{base_url.rstrip('/')}/api/admin/content/posts/{entry.post.id}",
            headers=headers,
            timeout=60,
        )
        response.raise_for_status()
        return entry.post.id, None

    with ThreadPoolExecutor(max_workers=max_workers) as executor:
        patch_futures = {executor.submit(patch_worker, entry): entry for entry in selected_entries}
        for future in as_completed(patch_futures):
            entry = patch_futures[future]
            try:
                _, error = future.result()
                entry.apply_error = error
            except Exception as exc:
                entry.apply_error = str(exc)

    with ThreadPoolExecutor(max_workers=max_workers) as executor:
        delete_futures = {executor.submit(delete_worker, entry): entry for entry in delete_entries}
        for future in as_completed(delete_futures):
            entry = delete_futures[future]
            try:
                _, error = future.result()
                entry.delete_error = error
            except Exception as exc:
                entry.delete_error = str(exc)


def manifest_payload(selected_entries: list[SelectionEntry], delete_entries: list[SelectionEntry], human_posts: list[PostRecord]) -> dict[str, Any]:
    selected_by_category = Counter(entry.final_category for entry in selected_entries)
    delete_reason_counts = Counter(entry.selection_reason for entry in delete_entries)
    return {
        "generatedAt": datetime.now(tz=timezone.utc).isoformat(),
        "summary": {
            "human_kept": len(human_posts),
            "ai_kept": len(selected_entries),
            "ai_deleted": len(delete_entries),
            "selected_by_category": dict(selected_by_category),
            "delete_reason_counts": dict(delete_reason_counts),
        },
        "human_posts": [
            {
                "post_id": post.id,
                "category": post.current_category,
                "created_at": post.created_at.isoformat(),
            }
            for post in human_posts
        ],
        "selected": [serialize_entry(entry) for entry in selected_entries],
        "deleted": [serialize_entry(entry) for entry in delete_entries],
    }


def serialize_entry(entry: SelectionEntry) -> dict[str, Any]:
    post = entry.post
    return {
        "post_id": post.id,
        "author_id": post.author_id,
        "synthetic": post.synthetic,
        "original_category": post.current_category,
        "final_category": entry.final_category,
        "action": entry.action,
        "selection_reason": entry.selection_reason,
        "rewrite_instruction": entry.rewrite_instruction,
        "created_at": post.created_at.isoformat(),
        "kst_day": post.kst_day,
        "votes": post.vote_count,
        "comments": post.comment_count,
        "likes": post.like_count,
        "body_len": post.body_len,
        "predicted_category": post.predicted_category,
        "classifier_gap": post.classifier_gap,
        "persona_id": post.author_id,
        "persona_archetype": post.archetype,
        "voice_type": post.voice_type,
        "original_title": post.effective_title,
        "original_body": post.effective_body,
        "rewritten_title": entry.rewritten_title,
        "rewritten_body": entry.rewritten_body,
        "rewrite_error": entry.rewrite_error,
        "apply_error": entry.apply_error,
        "delete_error": entry.delete_error,
    }


def write_manifest(output_dir: Path, payload: dict[str, Any]) -> Path:
    output_dir.mkdir(parents=True, exist_ok=True)
    stamp = datetime.now(tz=KST).strftime("%Y%m%d-%H%M%S")
    path = output_dir / f"legacy-curation-manifest-{stamp}.json"
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    return path


def print_summary(selected_entries: list[SelectionEntry], delete_entries: list[SelectionEntry], human_posts: list[PostRecord], manifest_path: Path) -> None:
    selected_counts = Counter(entry.final_category for entry in selected_entries)
    delete_counts = Counter(entry.selection_reason for entry in delete_entries)
    rewrite_failures = sum(1 for entry in selected_entries if entry.rewrite_error)
    apply_failures = sum(1 for entry in selected_entries if entry.apply_error)
    delete_failures = sum(1 for entry in delete_entries if entry.delete_error)

    print(f"manifest: {manifest_path}")
    print(f"kept humans: {len(human_posts)}")
    print(f"kept ai: {len(selected_entries)}")
    print(f"deleted ai: {len(delete_entries)}")
    for category in PLAZAS:
        print(f"  {category}: {selected_counts.get(category, 0)}")
    print(f"rewrite failures: {rewrite_failures}")
    print(f"apply failures: {apply_failures}")
    print(f"delete failures: {delete_failures}")
    print("delete reasons:")
    for reason, count in sorted(delete_counts.items()):
        print(f"  {reason}: {count}")


def main() -> int:
    args = parse_args()
    if args.apply:
        args.rewrite = True
    if args.rewrite and not args.llm_url:
        raise SystemExit("--rewrite requires --llm-url")
    if args.apply and not args.base_url:
        raise SystemExit("--apply requires --base-url")

    max_workers = max(1, min(args.max_workers, DEFAULT_MAX_WORKERS))
    env_values = load_dotenv(args.env_file)
    db_config = build_db_config(args, env_values)
    output_dir = ROOT / args.output_dir

    conn = connect_db(db_config)
    try:
        global_rules = query_global_rules(conn)
        posts = query_posts(conn)
    finally:
        conn.close()

    selected_entries, delete_entries, human_posts = curate(posts, args.keep_per_category)

    if args.rewrite:
        rewrite_selected(selected_entries, args.llm_url, global_rules, max_workers)

    if args.apply:
        bearer_token = args.bearer_token
        if not bearer_token and args.login_email and args.login_password:
            bearer_token = login_for_token(args.base_url, args.login_email, args.login_password)
        if not bearer_token:
            raise SystemExit("--apply requires --bearer-token or login credentials")
        apply_changes(selected_entries, delete_entries, args.base_url, bearer_token, max_workers)

    payload = manifest_payload(selected_entries, delete_entries, human_posts)
    manifest_path = write_manifest(output_dir, payload)
    print_summary(selected_entries, delete_entries, human_posts, manifest_path)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
