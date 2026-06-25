#!/usr/bin/env python3
"""Rewrite prod public feed by reusing historical synthetic post shells.

This script does not insert new prod rows. Instead it:

1. Selects 120 historical synthetic post shells from prod across a fixed 14-day
   window, preserving their original createdAt timestamps.
2. Rewrites titles/bodies/comments from scratch using the latest Blind/NatePann
   crawl corpus and prod personas via clcocloud.
3. Deletes unselected synthetic posts and non-human guest-like public posts.

Default mode is safe: it only generates a selection/generation manifest.
Use --apply to perform admin API mutations on prod.
"""

from __future__ import annotations

import argparse
import importlib.util
import json
import random
import sys
import time
from collections import Counter, defaultdict
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass
from datetime import date, datetime, timezone
from pathlib import Path
from types import SimpleNamespace
from typing import Any
from zoneinfo import ZoneInfo

import jwt
import pymysql
import requests

ROOT = Path(__file__).resolve().parents[2]
KST = ZoneInfo("Asia/Seoul")
UTC = timezone.utc

PLAZAS = ["COUPLE", "FAMILY", "FRIEND", "MARRIED", "WORK", "OTHER"]
DEFAULT_DAY_START = "2026-06-09"
DEFAULT_DAY_END = "2026-06-22"
DEFAULT_PER_CATEGORY = 20
DEFAULT_MAX_WORKERS = 8
MAX_COMMENT_KEEP = 10
TRUE_HUMAN_EMAIL_SUFFIX_DENY = "@againspring.internal"


@dataclass
class ShellComment:
    id: int
    author_id: str
    parent_comment_id: int | None
    created_at_kst: datetime
    like_count: int
    body: str


@dataclass
class ShellPost:
    id: str
    author_id: str
    created_at_kst: datetime
    day_key: str
    current_category: str
    title: str
    body_raw: str
    view_count: int
    comment_shells: list[ShellComment]
    delete_comment_ids: list[int]
    score: float


@dataclass
class GeneratedComment:
    id: int
    parent_comment_id: int | None
    body: str
    created_at_kst: datetime


@dataclass
class GeneratedAssignment:
    shell: ShellPost
    category: str
    source_example_id: int
    source_community: str
    source_title: str
    source_body: str
    rewritten_title: str
    rewritten_body: str
    comments: list[GeneratedComment]
    delete_comment_ids: list[int]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dev-env-file", default="env/.env.dev")
    parser.add_argument("--prod-env-file", default="env/.env.prod")
    parser.add_argument("--prod-base-url", default="http://localhost:8091")
    parser.add_argument("--admin-user-id", default="cbba96d2d9f4417287d6da9ec4")
    parser.add_argument("--admin-email", default="againspring2026@gmail.com")
    parser.add_argument("--date-start", default=DEFAULT_DAY_START)
    parser.add_argument("--date-end", default=DEFAULT_DAY_END)
    parser.add_argument("--per-category", type=int, default=DEFAULT_PER_CATEGORY)
    parser.add_argument("--max-workers", type=int, default=DEFAULT_MAX_WORKERS)
    parser.add_argument("--post-model", default="claude-sonnet-4-6")
    parser.add_argument("--comment-model", default="claude-haiku-4-5-20251001")
    parser.add_argument("--seed", type=int, default=20260624)
    parser.add_argument("--output-dir", default="ai-user/tools/reports")
    parser.add_argument("--apply", action="store_true")
    return parser.parse_args()


def load_dotenv(path: str) -> dict[str, str]:
    values: dict[str, str] = {}
    for raw in Path(path).read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip()
    return values


def load_rebuilder() -> Any:
    module_path = Path(__file__).with_name("rebuild_public_feed_from_crawled_sources.py")
    spec = importlib.util.spec_from_file_location("rebuilder", module_path)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"Could not load module from {module_path}")
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    original_call = module.call_clcocloud

    def bounded_call(
        api: Any,
        model: str,
        prompt: str,
        max_tokens: int,
        temperature: float = 0.55,
        timeout_sec: int = 180,
    ) -> str:
        capped_timeout = min(timeout_sec, 45 if max_tokens >= 1000 else 30)
        return original_call(api, model, prompt, max_tokens, temperature, capped_timeout)

    module.call_clcocloud = bounded_call
    return module


def connect_dev_db(env_values: dict[str, str]) -> pymysql.connections.Connection:
    return pymysql.connect(
        host="127.0.0.1",
        port=3309,
        user=env_values["MARIADB_USER"],
        password=env_values["MARIADB_PASSWORD"],
        database=env_values["MARIADB_DATABASE"],
        charset="utf8mb4",
        cursorclass=pymysql.cursors.DictCursor,
        autocommit=False,
    )


def parse_ts(value: str | None) -> datetime | None:
    if not value:
        return None
    return datetime.fromisoformat(value.replace("Z", "+00:00"))


def to_kst(value: str | None) -> datetime:
    parsed = parse_ts(value)
    if parsed is None:
        raise RuntimeError(f"Missing timestamp: {value!r}")
    return parsed.astimezone(KST)


def write_json(output_dir: Path, prefix: str, data: Any) -> Path:
    def default_serializer(value: Any) -> Any:
        if isinstance(value, datetime):
            return value.isoformat()
        if isinstance(value, date):
            return value.isoformat()
        return str(value)

    output_dir.mkdir(parents=True, exist_ok=True)
    stamp = datetime.now(UTC).astimezone(KST).strftime("%Y%m%d-%H%M%S")
    path = output_dir / f"{prefix}-{stamp}.json"
    path.write_text(
        json.dumps(data, ensure_ascii=False, indent=2, default=default_serializer),
        encoding="utf-8",
    )
    return path


def forge_admin_token(prod_env: dict[str, str], user_id: str, email: str) -> str:
    secret = prod_env.get("JWT_SECRET")
    if not secret:
        raise RuntimeError("JWT_SECRET not found in prod env")
    payload = {
        "sub": user_id,
        "email": email,
        "role": "ADMIN",
    }
    return jwt.encode(payload, secret, algorithm="HS256")


class AdminClient:
    def __init__(self, base_url: str, bearer_token: str) -> None:
        self.base_url = base_url.rstrip("/")
        self.session = requests.Session()
        self.session.headers.update({"Authorization": f"Bearer {bearer_token}"})

    def _get_paged(self, path: str, params: dict[str, Any], page_size: int) -> list[dict[str, Any]]:
        items: list[dict[str, Any]] = []
        page = 0
        while True:
            query = dict(params)
            query.update({"page": page, "size": page_size})
            response = self.session.get(self.base_url + path, params=query, timeout=60)
            response.raise_for_status()
            payload = response.json()
            content = list(payload.get("content") or [])
            if not content:
                break
            items.extend(content)
            if payload.get("last", False):
                break
            page += 1
        return items

    def list_posts(self, synthetic: bool | None = None) -> list[dict[str, Any]]:
        params: dict[str, Any] = {}
        if synthetic is not None:
            params["synthetic"] = str(synthetic).lower()
        return self._get_paged("/api/admin/content/posts", params, page_size=200)

    def list_comments(self) -> list[dict[str, Any]]:
        return self._get_paged("/api/admin/content/comments", {"status": "ACTIVE"}, page_size=500)

    def list_users(self) -> list[dict[str, Any]]:
        return self._get_paged("/api/admin/users", {}, page_size=200)

    def patch_post(self, post_id: str, payload: dict[str, Any]) -> None:
        response = self.session.patch(
            self.base_url + f"/api/admin/content/posts/{post_id}",
            json=payload,
            timeout=60,
        )
        response.raise_for_status()

    def patch_comment(self, comment_id: int, payload: dict[str, Any]) -> None:
        response = self.session.patch(
            self.base_url + f"/api/admin/content/comments/{comment_id}",
            json=payload,
            timeout=60,
        )
        response.raise_for_status()

    def delete_post(self, post_id: str) -> None:
        response = self.session.delete(self.base_url + f"/api/admin/content/posts/{post_id}", timeout=60)
        response.raise_for_status()

    def delete_comment(self, comment_id: int) -> None:
        response = self.session.delete(self.base_url + f"/api/admin/content/comments/{comment_id}", timeout=60)
        response.raise_for_status()

    def public_counts(self) -> dict[str, int]:
        response = requests.get(self.base_url + "/api/community/posts/counts", timeout=30)
        response.raise_for_status()
        return dict(response.json())


def is_true_human_author(user: dict[str, Any] | None) -> bool:
    if not user:
        return False
    if bool(user.get("synthetic")):
        return False
    if bool(user.get("guest")):
        return False
    email = str(user.get("email") or "")
    if not email or email.endswith(TRUE_HUMAN_EMAIL_SUFFIX_DENY):
        return False
    return True


def comment_score(kept_count: int, deleted_count: int, top_level_count: int, view_count: int) -> float:
    target = 7
    base = 120.0
    base -= abs(min(kept_count, MAX_COMMENT_KEEP) - target) * 5.0
    base -= deleted_count * 1.5
    base += min(top_level_count, 6) * 1.8
    base += min(view_count, 500) * 0.08
    return base


def trim_comment_threads(
    valid_comments: list[ShellComment],
    max_keep: int,
) -> list[ShellComment]:
    by_id = {comment.id: comment for comment in valid_comments}
    tops: list[ShellComment] = []
    children: dict[int, list[ShellComment]] = defaultdict(list)
    for comment in valid_comments:
        if comment.parent_comment_id is None:
            tops.append(comment)
        elif comment.parent_comment_id in by_id:
            children[comment.parent_comment_id].append(comment)
    tops.sort(key=lambda item: (item.created_at_kst, item.id))
    for rows in children.values():
        rows.sort(key=lambda item: (item.created_at_kst, item.id))

    groups = [[top, *children.get(top.id, [])] for top in tops]
    if sum(len(group) for group in groups) <= max_keep:
        kept: list[ShellComment] = []
        for group in groups:
            kept.extend(group)
        return kept

    selected_ids: list[int] = []
    depth = 0
    while len(selected_ids) < max_keep:
        advanced = False
        for group in groups:
            if depth < len(group) and len(selected_ids) < max_keep:
                selected_ids.append(group[depth].id)
                advanced = True
        if not advanced:
            break
        depth += 1

    selected_set = set(selected_ids)
    kept = []
    for group in groups:
        for comment in group:
            if comment.id in selected_set:
                kept.append(comment)
    return kept


def build_shells(
    synthetic_posts: list[dict[str, Any]],
    comments: list[dict[str, Any]],
    personas_by_id: dict[str, Any],
    day_start: str,
    day_end: str,
) -> list[ShellPost]:
    comments_by_post: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for comment in comments:
        post_id = str(comment.get("postId") or "")
        if post_id:
            comments_by_post[post_id].append(comment)

    shells: list[ShellPost] = []
    for post in synthetic_posts:
        post_id = str(post["id"])
        created_at_kst = to_kst(post["createdAt"])
        day_key = created_at_kst.date().isoformat()
        if not (day_start <= day_key <= day_end):
            continue
        author_id = str(post["authorId"])
        if author_id not in personas_by_id:
            continue

        raw_comments = comments_by_post.get(post_id, [])
        shell_comments: list[ShellComment] = []
        valid_comments: list[ShellComment] = []
        for row in raw_comments:
            shell = ShellComment(
                id=int(row["id"]),
                author_id=str(row.get("authorId") or ""),
                parent_comment_id=int(row["parentCommentId"]) if row.get("parentCommentId") is not None else None,
                created_at_kst=to_kst(row["createdAt"]),
                like_count=int(row.get("likeCount") or 0),
                body=str(row.get("body") or ""),
            )
            shell_comments.append(shell)
            if bool(row.get("synthetic")) and shell.author_id in personas_by_id and not shell.author_id.startswith("anon_"):
                valid_comments.append(shell)

        kept_comments = trim_comment_threads(valid_comments, MAX_COMMENT_KEEP)
        if len(kept_comments) < 2:
            continue

        kept_ids = {comment.id for comment in kept_comments}
        top_level_count = sum(1 for comment in kept_comments if comment.parent_comment_id is None)
        delete_comment_ids = [comment.id for comment in shell_comments if comment.id not in kept_ids]
        shells.append(
            ShellPost(
                id=post_id,
                author_id=author_id,
                created_at_kst=created_at_kst,
                day_key=day_key,
                current_category=str(post.get("category") or ""),
                title=str(post.get("title") or ""),
                body_raw=str(post.get("bodyRaw") or ""),
                view_count=int(post.get("viewCount") or 0),
                comment_shells=kept_comments,
                delete_comment_ids=delete_comment_ids,
                score=comment_score(len(kept_comments), len(delete_comment_ids), top_level_count, int(post.get("viewCount") or 0)),
            )
        )
    return shells


def target_day_counts(day_start: str, day_end: str, total_posts: int, available_counts: dict[str, int]) -> dict[str, int]:
    start = date.fromisoformat(day_start)
    end = date.fromisoformat(day_end)
    days: list[str] = []
    current = start
    while current <= end:
        days.append(current.isoformat())
        current = current.fromordinal(current.toordinal() + 1)

    capacity = {day: int(available_counts.get(day, 0)) for day in days}
    if sum(capacity.values()) < total_posts:
        raise RuntimeError(f"Not enough total shell capacity: need {total_posts}, have {sum(capacity.values())}")

    base = total_posts // len(days)
    counts = {day: min(capacity[day], base) for day in days}
    assigned = sum(counts.values())
    remaining = total_posts - assigned

    while remaining > 0:
        candidates = [day for day in days if counts[day] < capacity[day]]
        if not candidates:
            raise RuntimeError(f"Could not allocate remaining day counts: {remaining}")
        candidates.sort(key=lambda day: (counts[day], -(capacity[day] - counts[day]), day))
        chosen = candidates[0]
        counts[chosen] += 1
        remaining -= 1
    return counts


def select_shells(shells: list[ShellPost], day_targets: dict[str, int]) -> list[ShellPost]:
    by_day: dict[str, list[ShellPost]] = defaultdict(list)
    for shell in shells:
        by_day[shell.day_key].append(shell)

    selected: list[ShellPost] = []
    author_use: Counter[str] = Counter()
    for day_key in sorted(day_targets):
        target = day_targets[day_key]
        candidates = list(by_day.get(day_key, []))
        if len(candidates) < target:
            raise RuntimeError(f"Not enough reusable shells for {day_key}: need {target}, have {len(candidates)}")

        picked: list[ShellPost] = []
        day_author_use: Counter[str] = Counter()
        remaining = list(candidates)
        while len(picked) < target:
            remaining.sort(
                key=lambda shell: (
                    shell.score - author_use[shell.author_id] * 7.5 - day_author_use[shell.author_id] * 20.0,
                    shell.view_count,
                    len(shell.comment_shells),
                    shell.id,
                ),
                reverse=True,
            )
            chosen = remaining.pop(0)
            picked.append(chosen)
            author_use[chosen.author_id] += 1
            day_author_use[chosen.author_id] += 1
        selected.extend(picked)

    selected.sort(key=lambda shell: (shell.created_at_kst, shell.id))
    return selected


def build_story_queue(selected_sources: dict[str, list[Any]]) -> list[tuple[str, Any]]:
    queue: list[tuple[str, Any]] = []
    per_category = len(next(iter(selected_sources.values())))
    for round_index in range(per_category):
        order = PLAZAS[round_index % len(PLAZAS):] + PLAZAS[: round_index % len(PLAZAS)]
        for category in order:
            queue.append((category, selected_sources[category][round_index]))
    return queue


def build_comment_slots(
    rebuilder: Any,
    shell: ShellPost,
    target_category: str,
    personas_by_id: dict[str, Any],
    seed: int,
) -> tuple[list[Any], list[Any], dict[int, int]]:
    rng = random.Random(seed)
    comment_id_to_index: dict[int, int] = {}
    top_slots: list[Any] = []
    reply_slots: list[Any] = []

    for ordinal, comment in enumerate(shell.comment_shells, start=1):
        persona = personas_by_id[comment.author_id]
        if comment.parent_comment_id is None:
            stance = rebuilder.pick_comment_stance(persona, target_category, rng)
            mode = rebuilder.pick_comment_mode(persona, stance, rng)
            mode_hint, max_chars = rebuilder.COMMENT_MODE_HINTS[mode]
            slot = rebuilder.CommentSlot(
                index=ordinal,
                author=persona,
                parent_index=None,
                stance=stance,
                mode_hint=mode_hint,
                max_chars=max_chars,
                created_at_kst=comment.created_at_kst,
            )
            top_slots.append(slot)
            comment_id_to_index[comment.id] = ordinal
            continue

        parent_index = comment_id_to_index.get(comment.parent_comment_id)
        if parent_index is None:
            continue
        stance = rebuilder.pick_reply_stance(persona, rng)
        mode_hint, max_chars = rebuilder.reply_length_hint(rng)
        slot = rebuilder.CommentSlot(
            index=ordinal,
            author=persona,
            parent_index=parent_index,
            stance=stance,
            mode_hint=mode_hint,
            max_chars=max_chars,
            created_at_kst=comment.created_at_kst,
        )
        reply_slots.append(slot)
        comment_id_to_index[comment.id] = ordinal

    if len(top_slots) + len(reply_slots) < 2:
        raise RuntimeError(f"Shell {shell.id} collapsed below 2 reusable comments")
    return top_slots, reply_slots, comment_id_to_index


def clamp_comment_text(text: str, max_chars: int) -> str:
    body = " ".join(str(text).split())
    if len(body) <= max_chars:
        return body
    return body[: max(1, max_chars)].rstrip(" .,!?")


def local_comment_fallback(plan: Any, slot: Any, reply_mode: bool) -> str:
    polite = getattr(slot.author, "formality", "casual") == "polite"
    context = {
        "COUPLE": "둘 사이",
        "FAMILY": "가족끼리",
        "FRIEND": "친구 사이",
        "MARRIED": "부부 사이",
        "WORK": "직장에서는",
        "OTHER": "이 상황이면",
    }.get(plan.category, "이 상황이면")
    rng = random.Random(hash((plan.category, plan.title, slot.index, slot.stance, reply_mode)) & 0xFFFFFFFF)

    if reply_mode:
        if slot.stance == "AGREE":
            pool = [
                "저도 그렇게 보여요",
                "맞아요 저도 그 생각했어요",
                "나도 그렇게 느껴짐",
                "맞는 말임 그쪽이 더 커보임",
            ]
        elif slot.stance == "DISAGREE":
            pool = [
                "그렇게까지 보긴 좀 애매해요",
                "그건 조금 다르게 볼 수도 있죠",
                "그건 좀 아닌 듯",
                "거기까지 가는 건 오바 같음",
            ]
        else:
            pool = [
                "그 얘기는 직접 해보셨어요?",
                "그 부분은 먼저 어떻게 말했어요?",
                "근데 그건 먼저 뭐라고 했음?",
                "상대 반응은 정확히 어땠어요?",
            ]
    else:
        if slot.stance == "AUTHOR":
            pool = [
                f"{context} 글쓴이 쪽이 충분히 서운할 만해요",
                f"{context} 이건 글쓴이 입장에서 빡칠 만함",
                f"{context} 저 반응이면 기분 상할 수밖에 없죠",
                f"{context} 글쓴이 쪽 답답함이 이해돼요",
            ]
        elif slot.stance == "PARTNER":
            pool = [
                f"{context} 상대 쪽도 왜 예민해졌는지는 조금 이해돼요",
                f"{context} 상대 입장도 아예 이해 안 되는 건 아니에요",
                f"{context} 상대쪽도 할 말은 있었을 듯",
                f"{context} 상대 반응도 완전 뜬금없는 건 아닌 듯",
            ]
        else:
            pool = [
                f"{context} 감정 상하기 전에 기준부터 맞춰보는 게 나을 듯해요",
                f"{context} 지금은 누가 맞다보다 말이 꼬인 느낌이에요",
                f"{context} 둘 다 한 번 진정하고 다시 얘기해야 할 듯",
                f"{context} 여기선 기준 정리부터 해야 덜 싸울 것 같아요",
            ]

        if polite:
            pool = [text.replace("빡칠 만함", "답답할 만해요").replace("오바", "과한 해석") for text in pool]

    return clamp_comment_text(rng.choice(pool), slot.max_chars)


def local_post_fallback(source_example: Any) -> tuple[str, str]:
    title = " ".join(str(source_example.title).split())
    if len(title) < 12:
        title = title + " 때문에 너무 답답합니다"
    title = title[:38].rstrip(" .,!?")

    raw_body = str(source_example.body or "").strip()
    paragraphs = [part.strip() for part in raw_body.splitlines() if part.strip()]
    if not paragraphs:
        paragraphs = [raw_body]

    selected: list[str] = []
    total_len = 0
    for paragraph in paragraphs:
        if total_len >= 700:
            break
        piece = paragraph
        if len(piece) > 260:
            piece = piece[:260].rstrip(" .,!?")
        selected.append(piece)
        total_len += len(piece)

    body = "\n\n".join(selected).strip()
    if len(body) < 180:
        tail = "이런 식으로 계속 겹치니까 제가 예민한 건지 그냥 제가 참고 넘어가야 하는 건지 모르겠어요"
        body = (body + "\n\n" + tail).strip()
    if len(body) > 900:
        body = body[:900].rstrip(" .,!?")
    return title, body


def generate_assignment(
    rebuilder: Any,
    api: Any,
    global_rules: str | None,
    personas_by_id: dict[str, Any],
    shell: ShellPost,
    category: str,
    source_example: Any,
    seed: int,
) -> GeneratedAssignment:
    author = personas_by_id[shell.author_id]
    sonnet_comment_api = rebuilder.ApiConfig(
        api.api_key,
        api.base_url,
        api.post_model,
        api.post_model,
        api.max_workers,
    )
    plan = rebuilder.PostPlan(
        category=category,
        source_example=source_example,
        author=author,
        created_at_kst=shell.created_at_kst,
        day_key=shell.day_key,
    )
    try:
        title, body = rebuilder.generate_post_with_retry(api, plan, global_rules)
    except Exception:
        title, body = local_post_fallback(source_example)
    plan.title = title
    plan.body = body

    top_slots, reply_slots, comment_id_to_index = build_comment_slots(
        rebuilder,
        shell,
        category,
        personas_by_id,
        seed,
    )

    def fill_slots(slots: list[Any], reply_mode: bool) -> None:
        if not slots:
            return
        for current_api in (api, sonnet_comment_api):
            try:
                generated = rebuilder.generate_comment_group_with_retry(
                    current_api,
                    plan,
                    slots,
                    global_rules,
                    reply_mode=reply_mode,
                )
                for slot in slots:
                    slot.body = generated[slot.index]
                return
            except Exception:
                continue

        for slot in slots:
            slot.body = local_comment_fallback(plan, slot, reply_mode)

    fill_slots(top_slots, reply_mode=False)
    plan.comments.extend(top_slots)

    if reply_slots:
        fill_slots(reply_slots, reply_mode=True)
        plan.comments.extend(reply_slots)

    index_to_slot = {slot.index: slot for slot in plan.comments}
    generated_comments: list[GeneratedComment] = []
    for comment in shell.comment_shells:
        slot_index = comment_id_to_index.get(comment.id)
        if slot_index is None:
            continue
        slot = index_to_slot[slot_index]
        generated_comments.append(
            GeneratedComment(
                id=comment.id,
                parent_comment_id=comment.parent_comment_id,
                body=slot.body,
                created_at_kst=comment.created_at_kst,
            )
        )

    return GeneratedAssignment(
        shell=shell,
        category=category,
        source_example_id=int(source_example.id),
        source_community=str(source_example.source),
        source_title=str(source_example.title),
        source_body=str(source_example.body),
        rewritten_title=title,
        rewritten_body=body,
        comments=generated_comments,
        delete_comment_ids=list(shell.delete_comment_ids),
    )


def assignment_manifest(assignments: list[GeneratedAssignment]) -> dict[str, Any]:
    by_category = Counter(item.category for item in assignments)
    by_day = Counter(item.shell.day_key for item in assignments)
    return {
        "summary": {
            "total": len(assignments),
            "category_counts": dict(by_category),
            "day_counts": dict(by_day),
        },
        "posts": [
            {
                "post_id": item.shell.id,
                "author_id": item.shell.author_id,
                "day": item.shell.day_key,
                "original_category": item.shell.current_category,
                "final_category": item.category,
                "source_example_id": item.source_example_id,
                "source_community": item.source_community,
                "source_title": item.source_title,
                "source_body": item.source_body,
                "rewritten_title": item.rewritten_title,
                "rewritten_body": item.rewritten_body,
                "kept_comment_count": len(item.comments),
                "delete_comment_count": len(item.delete_comment_ids),
                "comments": [
                    {
                        "comment_id": comment.id,
                        "parent_comment_id": comment.parent_comment_id,
                        "body": comment.body,
                        "created_at_kst": comment.created_at_kst.isoformat(),
                    }
                    for comment in item.comments
                ],
                "delete_comment_ids": item.delete_comment_ids,
            }
            for item in assignments
        ],
    }


def prod_state_manifest(
    posts: list[dict[str, Any]],
    comments: list[dict[str, Any]],
    users: list[dict[str, Any]],
) -> dict[str, Any]:
    return {
        "posts": posts,
        "comments": comments,
        "users": users,
    }


def apply_assignment(client: AdminClient, assignment: GeneratedAssignment) -> dict[str, Any]:
    client.patch_post(
        assignment.shell.id,
        {
            "title": assignment.rewritten_title,
            "bodyRaw": assignment.rewritten_body,
            "category": assignment.category,
            "status": "VOTING",
        },
    )

    for comment in assignment.comments:
        client.patch_comment(comment.id, {"body": comment.body})

    for comment_id in assignment.delete_comment_ids:
        client.delete_comment(comment_id)

    return {
        "post_id": assignment.shell.id,
        "patched_comments": len(assignment.comments),
        "deleted_comments": len(assignment.delete_comment_ids),
    }


def verify_final_state(
    client: AdminClient,
    selected_ids: set[str],
    kept_human_post_ids: set[str],
    users_by_id: dict[str, dict[str, Any]],
    day_targets: dict[str, int],
) -> dict[str, Any]:
    posts = client.list_posts()
    comments = client.list_comments()
    comments_by_post: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for comment in comments:
        comments_by_post[str(comment["postId"])].append(comment)

    active_posts = [post for post in posts if post.get("deletedAt") is None]
    synthetic_selected = [post for post in active_posts if str(post["id"]) in selected_ids]
    human_kept = [post for post in active_posts if str(post["id"]) in kept_human_post_ids]

    synthetic_category_counts = Counter(str(post["category"]) for post in synthetic_selected)
    synthetic_day_counts = Counter(to_kst(post["createdAt"]).date().isoformat() for post in synthetic_selected)
    selected_comment_counts = {
        str(post["id"]): len(
            [
                comment
                for comment in comments_by_post.get(str(post["id"]), [])
                if comment.get("deletedAt") is None
            ]
        )
        for post in synthetic_selected
    }

    nonhuman_remaining = []
    for post in active_posts:
        user = users_by_id.get(str(post["authorId"]))
        if bool(post.get("synthetic")):
            continue
        if str(post["id"]) in kept_human_post_ids:
            continue
        if not is_true_human_author(user):
            nonhuman_remaining.append(str(post["id"]))

    return {
        "public_counts": client.public_counts(),
        "synthetic_selected_count": len(synthetic_selected),
        "human_kept_count": len(human_kept),
        "synthetic_category_counts": dict(synthetic_category_counts),
        "synthetic_day_counts": dict(sorted(synthetic_day_counts.items())),
        "target_day_counts": day_targets,
        "selected_comment_count_range": {
            "min": min(selected_comment_counts.values()) if selected_comment_counts else 0,
            "max": max(selected_comment_counts.values()) if selected_comment_counts else 0,
        },
        "selected_comment_counts": selected_comment_counts,
        "nonhuman_remaining_post_ids": nonhuman_remaining,
    }


def main() -> None:
    args = parse_args()
    output_dir = ROOT / args.output_dir
    prod_env = load_dotenv(args.prod_env_file)
    dev_env = load_dotenv(args.dev_env_file)
    rebuilder = load_rebuilder()

    with connect_dev_db(dev_env) as conn:
        api = rebuilder.build_api_config(
            conn,
            dev_env,
            SimpleNamespace(
                post_model=args.post_model,
                comment_model=args.comment_model,
                max_workers=args.max_workers,
            ),
        )
        global_rules = rebuilder.query_global_rules(conn)
        personas = rebuilder.query_personas(conn)
        examples = rebuilder.query_source_examples(conn)
        selected_sources = rebuilder.select_sources_by_category(examples, args.per_category)

    personas_by_id = {persona.id: persona for persona in personas}
    story_queue = build_story_queue(selected_sources)
    if len(story_queue) != len(PLAZAS) * len(next(iter(selected_sources.values()))):
        raise RuntimeError(f"Story queue mismatch: {len(story_queue)}")

    admin_token = forge_admin_token(prod_env, args.admin_user_id, args.admin_email)
    client = AdminClient(args.prod_base_url, admin_token)

    all_posts = client.list_posts()
    all_comments = client.list_comments()
    all_users = client.list_users()
    users_by_id = {str(user["id"]): user for user in all_users}
    backup_path = write_json(output_dir, "prod-public-feed-backup-api", prod_state_manifest(all_posts, all_comments, all_users))

    true_human_post_ids = {
        str(post["id"])
        for post in all_posts
        if is_true_human_author(users_by_id.get(str(post["authorId"])))
    }
    synthetic_posts = [post for post in all_posts if bool(post.get("synthetic"))]
    nonhuman_public_post_ids = {
        str(post["id"])
        for post in all_posts
        if not bool(post.get("synthetic")) and str(post["id"]) not in true_human_post_ids
    }

    shells = build_shells(synthetic_posts, all_comments, personas_by_id, args.date_start, args.date_end)
    available_by_day = dict(Counter(shell.day_key for shell in shells))
    day_targets = target_day_counts(args.date_start, args.date_end, len(story_queue), available_by_day)
    selected_shells = select_shells(shells, day_targets)
    if len(selected_shells) != len(story_queue):
        raise RuntimeError(f"Shell/story mismatch: {len(selected_shells)} vs {len(story_queue)}")
    print(
        f"[selection] shells={len(selected_shells)} human_keep={len(true_human_post_ids)} nonhuman_delete={len(nonhuman_public_post_ids)} day_targets={day_targets}",
        flush=True,
    )

    assignments: list[GeneratedAssignment] = []
    with ThreadPoolExecutor(max_workers=args.max_workers) as executor:
        futures = []
        for index, (shell, story) in enumerate(zip(selected_shells, story_queue, strict=True)):
            category, source_example = story
            futures.append(
                executor.submit(
                    generate_assignment,
                    rebuilder,
                    api,
                    global_rules,
                    personas_by_id,
                    shell,
                    category,
                    source_example,
                    args.seed + index * 97,
                )
            )
        for future in as_completed(futures):
            assignment = future.result()
            assignments.append(assignment)
            print(
                f"[generated] {assignment.shell.day_key} post={assignment.shell.id} cat={assignment.category} comments={len(assignment.comments)}",
                flush=True,
            )

    assignments.sort(key=lambda item: (item.shell.created_at_kst, item.shell.id))
    manifest = {
        "meta": {
            "backup_path": str(backup_path),
            "true_human_post_ids": sorted(true_human_post_ids),
            "nonhuman_public_post_ids": sorted(nonhuman_public_post_ids),
            "selected_shell_count": len(selected_shells),
            "shell_window": {"start": args.date_start, "end": args.date_end},
        },
        "selection": assignment_manifest(assignments),
    }
    manifest_path = write_json(output_dir, "prod-shell-rewrite-manifest", manifest)
    print(f"[manifest] {manifest_path}", flush=True)

    if not args.apply:
        print("[dry-run] apply skipped", flush=True)
        return

    selected_ids = {assignment.shell.id for assignment in assignments}
    delete_post_ids = [
        str(post["id"])
        for post in synthetic_posts
        if str(post["id"]) not in selected_ids
    ]
    delete_post_ids.extend(sorted(nonhuman_public_post_ids))
    print(f"[apply-start] selected={len(selected_ids)} delete_posts={len(delete_post_ids)}", flush=True)

    with ThreadPoolExecutor(max_workers=args.max_workers) as executor:
        futures = [executor.submit(apply_assignment, client, assignment) for assignment in assignments]
        for future in as_completed(futures):
            result = future.result()
            print(
                f"[applied] post={result['post_id']} comments={result['patched_comments']} deleted_comments={result['deleted_comments']}",
                flush=True,
            )

    with ThreadPoolExecutor(max_workers=args.max_workers) as executor:
        futures = [executor.submit(client.delete_post, post_id) for post_id in delete_post_ids]
        for future in as_completed(futures):
            future.result()
    print(f"[deleted-posts] {len(delete_post_ids)}", flush=True)

    time.sleep(1.5)
    verification = verify_final_state(client, selected_ids, true_human_post_ids, users_by_id, day_targets)
    verify_path = write_json(output_dir, "prod-shell-rewrite-verify", verification)
    print(f"[verify] {verify_path}", flush=True)


if __name__ == "__main__":
    main()
