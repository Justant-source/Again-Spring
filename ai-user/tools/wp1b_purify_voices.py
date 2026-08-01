#!/usr/bin/env python3
"""WP1B — register 단일화(NATEPAN|BLIND) + voice_profile 정화.

Deterministic (no LLM). Corpus-anchored examples from example_bank where
source IN (natepan, blind) and NOT SELF_GENERATED. Prefer COMMENT rows.

Usage (from repo root):

  # dry-run against snapshot + corpus TSV (no DB writes)
  python3 ai-user/tools/wp1b_purify_voices.py \\
      --snapshot ai-user/tools/snapshots/wp1b-voice_profiles-latest.json \\
      --corpus ai-user/tools/snapshots/wp1b-corpus-anchors.tsv \\
      --plan-out ai-user/tools/snapshots/wp1b-plan.json

  # apply all 150 to prod (batched transactions)
  python3 ai-user/tools/wp1b_purify_voices.py \\
      --snapshot ai-user/tools/snapshots/wp1b-voice_profiles-latest.json \\
      --corpus ai-user/tools/snapshots/wp1b-corpus-anchors.tsv \\
      --apply --batch-size 20 --sync-yaml

  # apply one batch (offset/limit by reassignment order)
  python3 ai-user/tools/wp1b_purify_voices.py ... --apply --limit 20 --offset 0

  # restore from snapshot
  python3 ai-user/tools/wp1b_purify_voices.py \\
      --restore ai-user/tools/snapshots/wp1b-voice_profiles-YYYYMMDD-HHMMSS.json
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import random
import re
import sys
from collections import Counter
from copy import deepcopy
from pathlib import Path
from typing import Any

sys.path.insert(0, str(Path(__file__).resolve().parent))
from baseline_lib import (  # noqa: E402
    ALLOWED_VOICE_TYPES,
    OOD_KEYWORD_GROUPS,
    POLITICAL_KEYWORDS,
    DEFAULT_PROFILES_DIR,
    collect_style_texts,
    find_keyword_hits,
    read_yaml,
    scan_contamination,
)

REPO_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_SNAPSHOT = REPO_ROOT / "ai-user/tools/snapshots/wp1b-voice_profiles-latest.json"
DEFAULT_CORPUS = REPO_ROOT / "ai-user/tools/snapshots/wp1b-corpus-anchors.tsv"

# Soft overall target ≈ 3:1 NATEPAN:BLIND among the 119 reassign candidates
# (already-OK personas keep their register).
REASSIGN_BLIND_SHARE = 0.22  # ≈26/119 → overall ~112:38 ≈ 3:1

WORK_JOBS_STRONG = frozenset({"직장인"})
WORK_JOBS_SOFT = frozenset({"프리랜서", "자영업자"})
HOME_JOBS = frozenset({"주부", "학생"})

REGISTER_DEFAULTS: dict[str, dict[str, Any]] = {
    "NATEPAN": {
        "slang": (0.40, 0.65),
        "formality_hint": "casual",  # mixed in prompts; keep existing if set
        "lexicon": {
            "signature_phrases": ["~잖아요", "어떡해요", "제가 잘못한 건가요?", "ㅠㅠ", "사이다"],
            "typing_habit": "감정을 줄바꿈으로 끊고 ㅠㅠ·…를 자주 씀",
        },
        "writing_quirks": {
            "spelling_level": "mid",
            "consistent_errors": ["돼/되 혼동"],
            "mobile_typos": True,
        },
        "comment_style": "네이트판 스타일 댓글 — 공감·감정 서술, 존댓말·반말 혼용",
        "reply_style": "짧고 공감적인 반응",
        "post_style": "사연형 서술 — 상황·감정을 길게 풀어씀",
    },
    "BLIND": {
        "slang": (0.20, 0.40),
        "formality_hint": "casual",
        "lexicon": {
            "signature_phrases": ["이직각", "노답", "증거 남겨", "그 정도면 선방", "퇴사각"],
            "typing_habit": "결론부터 짧게, 손익·리스크 중심",
        },
        "writing_quirks": {
            "spelling_level": "mid",
            "consistent_errors": [],
            "mobile_typos": True,
        },
        "comment_style": "블라인드 스타일 댓글 — 현실 계산·직설, ~함/~임",
        "reply_style": "짧고 직접적인 반응",
        "post_style": "직장·손익 중심 분석형 서술",
    },
}

# §3.3 conflict value axes — replace political general_style
VALUE_AXIS_STYLES: dict[str, list[str]] = {
    "FAMILY": [
        "가족관과 세대 기대를 중심에 두고, 책임과 체면의 균형을 따지는 말투",
        "가족 갈등에서 안정성과 역할을 먼저 보는 현실적인 서술",
    ],
    "COUPLE": [
        "연애·관계에서 개인 경계와 상호 존중을 우선하는 공감형 말투",
        "연인 갈등에서 감정보다 서로의 선과 책임을 짚는 서술",
    ],
    "MARRIED": [
        "부부·가사 분담의 공정성과 실용성을 중시하는 현실 조언형 말투",
        "결혼 생활 갈등에서 역할·체면·안정성을 함께 보는 서술",
    ],
    "FRIEND": [
        "친구 관계의 경계와 신뢰를 중시하며 공감과 직설을 섞는 말투",
        "우정 갈등에서 책임 소재를 차분히 따지는 서술",
    ],
    "WORK": [
        "직장 갈등의 공정성·증거·실리를 우선하는 현실 계산형 말투",
        "업무 갈등에서 개인 경계와 책임을 분리해 보는 직설 서술",
    ],
    "OTHER": [
        "일상 갈등의 공정성과 개인 경계를 중심으로 보는 균형형 말투",
        "실용성과 안정성을 기준으로 상황을 정리하는 서술",
    ],
}

POLITICAL_FIELD_KEYS = (
    "political_orientation",
    "political_strength",
    "political_voice_notes",
)


def seeded_rng(persona_id: str, salt: str = "") -> random.Random:
    digest = hashlib.sha256(f"{persona_id}:{salt}".encode()).hexdigest()
    return random.Random(int(digest[:16], 16))


def is_ood_text(text: str) -> bool:
    for kws in OOD_KEYWORD_GROUPS.values():
        if find_keyword_hits(text, kws):
            return True
    return False


def is_political_text(text: str) -> bool:
    return bool(find_keyword_hits(text or "", POLITICAL_KEYWORDS))


def _as_str_list(value: Any) -> list[str]:
    if value is None:
        return []
    if isinstance(value, str):
        t = value.strip()
        return [t] if t else []
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


def clean_example(text: str) -> str:
    t = re.sub(r"\s+", " ", text).strip()
    t = re.sub(r"(?<![.?!])\.\s*$", "", t)
    return t.replace('"', "").strip()


def load_snapshot(path: Path) -> list[dict[str, Any]]:
    data = json.loads(path.read_text(encoding="utf-8"))
    if isinstance(data, dict) and "personas" in data:
        return data["personas"]
    if isinstance(data, list):
        return data
    raise SystemExit(f"unexpected snapshot shape: {path}")


def load_corpus(path: Path) -> dict[str, dict[str, list[str]]]:
    """{source: {COMMENT|POST: [texts…]}} — OOD filtered."""
    pools: dict[str, dict[str, list[str]]] = {
        "natepan": {"COMMENT": [], "POST": []},
        "blind": {"COMMENT": [], "POST": []},
    }
    for line in path.read_text(encoding="utf-8", errors="replace").splitlines():
        parts = line.split("\t", 4)
        if len(parts) < 5:
            continue
        _id, source, ctype, _q, content = parts
        source = source.strip().lower()
        ctype = ctype.strip().upper()
        if source not in pools or ctype not in pools[source]:
            continue
        text = clean_example(content)
        if not text or is_ood_text(text):
            continue
        if 15 <= len(text) <= 280:
            pools[source][ctype].append(text)
    # dedupe preserving order
    for src in pools:
        for ct in pools[src]:
            pools[src][ct] = list(dict.fromkeys(pools[src][ct]))
    return pools


def blind_score(row: dict[str, Any]) -> float:
    vp = row.get("voice_profile") if isinstance(row.get("voice_profile"), dict) else {}
    interests = row.get("interests") if isinstance(row.get("interests"), dict) else {}
    job = (vp.get("job") or "").strip()
    age = (vp.get("age") or "").strip()
    work = float(interests.get("WORK") or 0.0)

    score = 0.0
    if job in WORK_JOBS_STRONG:
        score += 2.5
    elif job in WORK_JOBS_SOFT:
        score += 1.2
    elif job in HOME_JOBS:
        score -= 1.2

    if work >= 0.55:
        score += 2.0
    elif work >= 0.40:
        score += 1.0
    elif work >= 0.30:
        score += 0.4
    else:
        score -= 0.3

    if age.startswith("30") or age.startswith("40"):
        score += 0.6
    elif age.startswith("20"):
        score -= 0.2
    elif age.startswith("50") or age.startswith("60"):
        score -= 0.1

    # soft break ties with id hash
    score += (int(hashlib.md5(row["id"].encode()).hexdigest()[:4], 16) % 100) / 1000.0
    return score


def assign_registers(rows: list[dict[str, Any]]) -> dict[str, str]:
    """Return {persona_id: NATEPAN|BLIND} for all rows."""
    mapping: dict[str, str] = {}
    candidates: list[tuple[float, str]] = []
    for row in rows:
        vp = row.get("voice_profile") if isinstance(row.get("voice_profile"), dict) else {}
        vt = str(vp.get("voice_type") or "").strip().upper()
        if vt in ALLOWED_VOICE_TYPES:
            mapping[row["id"]] = vt
        else:
            candidates.append((blind_score(row), row["id"]))

    candidates.sort(key=lambda x: (-x[0], x[1]))
    n_blind = max(1, round(len(candidates) * REASSIGN_BLIND_SHARE)) if candidates else 0
    for i, (_score, pid) in enumerate(candidates):
        mapping[pid] = "BLIND" if i < n_blind else "NATEPAN"
    return mapping


def pick_examples(
    rng: random.Random,
    pool: list[str],
    n: int,
    used: set[str],
) -> list[str]:
    available = [t for t in pool if t not in used]
    if len(available) < n:
        available = list(pool)
    if not available:
        return []
    k = min(n, len(available))
    picked = rng.sample(available, k)
    used.update(picked)
    return picked


def dominant_interest(interests: dict[str, Any] | None) -> str:
    if not interests:
        return "OTHER"
    best_k, best_v = "OTHER", -1.0
    for k, v in interests.items():
        try:
            fv = float(v)
        except (TypeError, ValueError):
            continue
        if fv > best_v and k in VALUE_AXIS_STYLES:
            best_k, best_v = k, fv
    return best_k


def build_general_style(
    rng: random.Random,
    register: str,
    row: dict[str, Any],
    existing: str,
) -> str:
    interests = row.get("interests") if isinstance(row.get("interests"), dict) else {}
    axis = dominant_interest(interests)
    templates = VALUE_AXIS_STYLES.get(axis) or VALUE_AXIS_STYLES["OTHER"]
    base = rng.choice(templates)
    vp = row.get("voice_profile") if isinstance(row.get("voice_profile"), dict) else {}
    age = vp.get("age") or ""
    job = vp.get("job") or ""
    bits = [base]
    if age or job:
        bits.append(f"({age} {job} · {register})".strip())
    # keep non-political existing as secondary hint if short
    if existing and not is_political_text(existing) and not is_ood_text(existing):
        if len(existing) <= 80 and existing not in bits[0]:
            return existing  # already clean enough
    return " ".join(bits)


def filter_keep_examples(texts: list[str], limit: int) -> list[str]:
    out: list[str] = []
    for t in texts:
        c = clean_example(t)
        if not c or is_ood_text(c) or is_political_text(c):
            continue
        if c not in out:
            out.append(c)
        if len(out) >= limit:
            break
    return out


def purify_voice(
    row: dict[str, Any],
    new_register: str,
    corpus: dict[str, dict[str, list[str]]],
    *,
    force_regen_examples: bool,
) -> tuple[dict[str, Any], dict[str, Any]]:
    """Return (new_voice_profile, meta)."""
    old = row.get("voice_profile") if isinstance(row.get("voice_profile"), dict) else {}
    vp = deepcopy(old)
    old_vt = str(old.get("voice_type") or "").strip().upper()
    reassigned = old_vt not in ALLOWED_VOICE_TYPES
    rng = seeded_rng(row["id"], new_register)

    vp["voice_type"] = new_register
    defaults = REGISTER_DEFAULTS[new_register]
    source_key = "natepan" if new_register == "NATEPAN" else "blind"
    src_pool = corpus[source_key]

    # slang_level: preserve if already OK register & in range; else re-roll in band
    lo, hi = defaults["slang"]
    old_slang = row.get("slang_level")
    if isinstance(old.get("slang_level"), (int, float)):
        old_slang = old["slang_level"]
    if reassigned or force_regen_examples or old_slang is None:
        new_slang = round(lo + rng.random() * (hi - lo), 2)
    else:
        try:
            new_slang = float(old_slang)
        except (TypeError, ValueError):
            new_slang = round(lo + rng.random() * (hi - lo), 2)
        new_slang = max(lo, min(hi, new_slang))
    vp["slang_level"] = new_slang

    # lexicon / quirks — replace when reassigned; merge-sanitize when purify-only
    if reassigned or force_regen_examples:
        vp["lexicon"] = deepcopy(defaults["lexicon"])
        vp["writing_quirks"] = deepcopy(defaults["writing_quirks"])
    else:
        lex = vp.get("lexicon") if isinstance(vp.get("lexicon"), dict) else {}
        phrases = filter_keep_examples(_as_str_list(lex.get("signature_phrases")), 8)
        if len(phrases) < 3:
            phrases = list(defaults["lexicon"]["signature_phrases"])
        lex["signature_phrases"] = phrases[:8]
        if not lex.get("typing_habit") or is_ood_text(str(lex.get("typing_habit"))):
            lex["typing_habit"] = defaults["lexicon"]["typing_habit"]
        vp["lexicon"] = lex
        wq = vp.get("writing_quirks") if isinstance(vp.get("writing_quirks"), dict) else {}
        if not wq:
            wq = deepcopy(defaults["writing_quirks"])
        vp["writing_quirks"] = wq

    # general_style
    old_gs = str(vp.get("general_style") or "").strip()
    if reassigned or is_political_text(old_gs) or not old_gs:
        vp["general_style"] = build_general_style(rng, new_register, row, old_gs)
    elif is_ood_text(old_gs):
        vp["general_style"] = build_general_style(rng, new_register, row, "")

    # nested or flat style hints
    if reassigned:
        vp["comment_style"] = defaults["comment_style"]
        vp["reply_style"] = defaults["reply_style"]
        vp["post_style"] = defaults["post_style"]

    # examples — flat keys (runtime / ActionExecutor consume these)
    used: set[str] = set()
    comment_keep_n = 0 if (reassigned or force_regen_examples) else 12
    reply_keep_n = 0 if (reassigned or force_regen_examples) else 8
    opener_keep_n = 0 if (reassigned or force_regen_examples) else 4

    kept_comments = filter_keep_examples(_as_str_list(vp.get("example_comments")), comment_keep_n)
    kept_replies = filter_keep_examples(_as_str_list(vp.get("example_replies")), reply_keep_n)
    kept_openers = filter_keep_examples(_as_str_list(vp.get("example_post_openers")), opener_keep_n)

    comment_need = max(0, 10 - len(kept_comments))
    reply_need = max(0, 6 - len(kept_replies))
    opener_need = max(0, 3 - len(kept_openers))

    comment_cands = src_pool["COMMENT"] or src_pool["POST"]
    reply_cands = [c for c in comment_cands if len(c) <= 100] or comment_cands
    opener_cands = [c for c in (src_pool["POST"] or src_pool["COMMENT"]) if 20 <= len(c) <= 160]

    new_comments = kept_comments + pick_examples(rng, comment_cands, comment_need, used)
    new_replies = kept_replies + pick_examples(rng, reply_cands, reply_need, used)
    new_openers = kept_openers + pick_examples(rng, opener_cands, opener_need, used)

    vp["example_comments"] = new_comments[:12]
    vp["example_replies"] = new_replies[:8]
    vp["example_post_openers"] = new_openers[:4]
    vp["pool_meta"] = {
        "wp1b": True,
        "register": new_register,
        "anchor_source": source_key,
        "curated_comments": 0,
        "curated_replies": 0,
        "curated_openers": 0,
    }

    # Soften political axes in voice text fields (keep key for back-compat but neutralize notes)
    if is_political_text(str(vp.get("political_voice_notes") or "")):
        vp["political_voice_notes"] = f"{new_register} register · 갈등 가치축 중심 (정치 서술 비활성)"
    if is_political_text(str(vp.get("vote_tendency") or "")):
        vp["vote_tendency"] = "갈등 가치축(공정성·경계·책임)에 따른 공감 선택"
    if is_political_text(str(vp.get("like_criteria") or "")):
        vp["like_criteria"] = "공감 가는 경험담, 공정·경계 관련 글"

    # Preserve demographics / scores
    for key in ("age", "gender", "region", "job", "formality", "like_score", "vote_score",
                "hot_buttons", "reactions", "hot_topics", "age_voice_notes"):
        if key in old and key not in vp:
            vp[key] = old[key]
        elif key in old and key in ("age", "gender", "region", "job", "formality",
                                    "like_score", "vote_score", "hot_buttons", "reactions"):
            vp[key] = old[key]

    meta = {
        "id": row["id"],
        "old_voice_type": old_vt or "(empty)",
        "new_voice_type": new_register,
        "reassigned": reassigned,
        "blind_score": round(blind_score(row), 3) if reassigned else None,
        "n_comments": len(vp["example_comments"]),
        "n_replies": len(vp["example_replies"]),
        "n_openers": len(vp["example_post_openers"]),
        "slang_level": new_slang,
        "general_style": vp["general_style"][:120],
    }
    return vp, meta


def connect_prod():
    import pymysql

    host = os.getenv("WP1B_DB_HOST")
    if not host:
        # resolve docker IP of againspring-mariadb-prod
        import subprocess

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
    password = os.getenv("WP1B_DB_PASSWORD")
    if not password:
        import subprocess

        password = subprocess.check_output(
            ["docker", "exec", "againspring-mariadb-prod", "printenv", "MARIADB_ROOT_PASSWORD"],
            text=True,
        ).strip()
    return pymysql.connect(
        host=host,
        user=os.getenv("WP1B_DB_USER", "root"),
        password=password,
        database=os.getenv("WP1B_DB_NAME", "againspring_prod"),
        charset="utf8mb4",
        autocommit=False,
    )


def apply_updates(
    updates: list[tuple[str, dict[str, Any], float | None]],
    *,
    batch_size: int,
) -> int:
    """updates: list of (id, voice_profile, slang_level|None)."""
    if not updates:
        return 0
    conn = connect_prod()
    applied = 0
    try:
        with conn.cursor() as cur:
            for i in range(0, len(updates), batch_size):
                batch = updates[i : i + batch_size]
                for pid, vp, slang in batch:
                    payload = json.dumps(vp, ensure_ascii=False)
                    if slang is not None:
                        cur.execute(
                            "UPDATE personas SET voice_profile=%s, slang_level=%s WHERE id=%s AND active=1",
                            (payload, slang, pid),
                        )
                    else:
                        cur.execute(
                            "UPDATE personas SET voice_profile=%s WHERE id=%s AND active=1",
                            (payload, pid),
                        )
                    applied += cur.rowcount
                conn.commit()
                print(f"  committed batch {i // batch_size + 1}: {len(batch)} rows (total applied≈{applied})")
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()
    return applied


def restore_snapshot(path: Path, batch_size: int = 20) -> int:
    rows = load_snapshot(path)
    updates: list[tuple[str, dict[str, Any], float | None]] = []
    for row in rows:
        vp = row.get("voice_profile")
        if not isinstance(vp, dict):
            continue
        slang = row.get("slang_level")
        try:
            slang_f = float(slang) if slang is not None else None
        except (TypeError, ValueError):
            slang_f = None
        updates.append((row["id"], vp, slang_f))
    print(f"restoring {len(updates)} personas from {path}")
    return apply_updates(updates, batch_size=batch_size)


def sync_yaml(profiles_dir: Path, updates_by_id: dict[str, dict[str, Any]]) -> int:
    if not profiles_dir.is_dir():
        return 0
    try:
        import yaml
    except ImportError:
        print("WARN: PyYAML missing — skip YAML sync", file=sys.stderr)
        return 0

    synced = 0
    for voice_path in sorted(profiles_dir.glob("*/voice.yml")):
        voice = read_yaml(voice_path)
        pid = str(voice.get("persona_id") or "").strip()
        if not pid:
            profile = read_yaml(voice_path.parent / "profile.yml") if (voice_path.parent / "profile.yml").exists() else {}
            pid = str(profile.get("id") or "").strip()
        if pid not in updates_by_id:
            continue
        vp = updates_by_id[pid]
        register = vp["voice_type"]
        voice["voice_type"] = register
        voice["general_style"] = vp.get("general_style")
        if "lexicon" in vp:
            voice["lexicon"] = vp["lexicon"]
        if "writing_quirks" in vp:
            voice["writing_quirks"] = vp["writing_quirks"]
        if "slang_level" in vp:
            voice["slang_level"] = vp["slang_level"]

        # nested examples
        post = voice.get("post") if isinstance(voice.get("post"), dict) else {}
        comment = voice.get("comment") if isinstance(voice.get("comment"), dict) else {}
        reply = voice.get("reply") if isinstance(voice.get("reply"), dict) else {}
        post["example_post_openers"] = vp.get("example_post_openers") or []
        comment["example_comments"] = vp.get("example_comments") or []
        reply["example_replies"] = vp.get("example_replies") or []
        if register == "NATEPAN":
            post.setdefault("style", REGISTER_DEFAULTS["NATEPAN"]["post_style"])
            comment.setdefault("style", REGISTER_DEFAULTS["NATEPAN"]["comment_style"])
            reply.setdefault("style", REGISTER_DEFAULTS["NATEPAN"]["reply_style"])
        else:
            post.setdefault("style", REGISTER_DEFAULTS["BLIND"]["post_style"])
            comment.setdefault("style", REGISTER_DEFAULTS["BLIND"]["comment_style"])
            reply.setdefault("style", REGISTER_DEFAULTS["BLIND"]["reply_style"])
        voice["post"] = post
        voice["comment"] = comment
        voice["reply"] = reply

        # also flat keys for parity with DB shape
        voice["example_comments"] = vp.get("example_comments") or []
        voice["example_replies"] = vp.get("example_replies") or []
        voice["example_post_openers"] = vp.get("example_post_openers") or []

        with voice_path.open("w", encoding="utf-8") as fh:
            yaml.safe_dump(voice, fh, allow_unicode=True, sort_keys=False, width=100)

        profile_path = voice_path.parent / "profile.yml"
        if profile_path.exists():
            profile = read_yaml(profile_path)
            activity = profile.get("activity") if isinstance(profile.get("activity"), dict) else {}
            activity["voice"] = register
            if "slang_level" in vp:
                activity["slang_level"] = vp["slang_level"]
            profile["activity"] = activity
            with profile_path.open("w", encoding="utf-8") as fh:
                yaml.safe_dump(profile, fh, allow_unicode=True, sort_keys=False, width=100)
        synced += 1
    return synced


def voice_type_dist(rows: list[dict[str, Any]], key: str = "voice_type") -> dict[str, int]:
    c: Counter[str] = Counter()
    for r in rows:
        vp = r.get("voice_profile") if isinstance(r.get("voice_profile"), dict) else {}
        c[str(vp.get(key) or "?").upper()] += 1
    return dict(c.most_common())


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--snapshot", type=Path, default=DEFAULT_SNAPSHOT)
    p.add_argument("--corpus", type=Path, default=DEFAULT_CORPUS)
    p.add_argument("--plan-out", type=Path, default=None)
    p.add_argument("--apply", action="store_true", help="write purified profiles to prod DB")
    p.add_argument("--restore", type=Path, default=None, help="restore voice_profile from snapshot")
    p.add_argument("--batch-size", type=int, default=20)
    p.add_argument("--limit", type=int, default=0, help="max personas to apply (0=all)")
    p.add_argument("--offset", type=int, default=0, help="skip first N in apply order")
    p.add_argument("--only-reassign", action="store_true", help="apply only reassigned (was not NATEPAN/BLIND)")
    p.add_argument("--force-regen-examples", action="store_true", help="rebuild examples even for already-OK registers")
    p.add_argument("--sync-yaml", action="store_true")
    p.add_argument("--profiles-dir", type=Path, default=DEFAULT_PROFILES_DIR)
    return p.parse_args()


def main() -> int:
    args = parse_args()

    if args.restore:
        n = restore_snapshot(args.restore, batch_size=args.batch_size)
        print(f"restored rows touched: {n}")
        return 0

    if not args.snapshot.exists():
        print(f"ERROR: snapshot not found: {args.snapshot}", file=sys.stderr)
        return 2
    if not args.corpus.exists():
        print(f"ERROR: corpus not found: {args.corpus}", file=sys.stderr)
        return 2

    rows = load_snapshot(args.snapshot)
    corpus = load_corpus(args.corpus)
    print("corpus pools:")
    for src, buckets in corpus.items():
        for ct, texts in buckets.items():
            print(f"  {src:8} {ct:8} {len(texts)}")

    before = voice_type_dist(rows)
    print("BEFORE voice_type:", before)

    mapping = assign_registers(rows)
    reassigned_ids = {
        r["id"]
        for r in rows
        if str((r.get("voice_profile") or {}).get("voice_type") or "").upper() not in ALLOWED_VOICE_TYPES
    }
    print(f"reassign candidates: {len(reassigned_ids)}")
    print(
        "planned registers:",
        Counter(mapping.values()),
        "among reassigned:",
        Counter(mapping[i] for i in reassigned_ids),
    )

    metas: list[dict[str, Any]] = []
    updates_by_id: dict[str, dict[str, Any]] = {}
    apply_list: list[tuple[str, dict[str, Any], float | None]] = []

    # stable apply order: reassigned first (by blind_score desc), then purify-only
    def sort_key(r: dict[str, Any]) -> tuple:
        pid = r["id"]
        if pid in reassigned_ids:
            return (0, -blind_score(r), pid)
        return (1, 0.0, pid)

    ordered = sorted(rows, key=sort_key)

    for row in ordered:
        register = mapping[row["id"]]
        force = args.force_regen_examples or (row["id"] in reassigned_ids)
        vp, meta = purify_voice(row, register, corpus, force_regen_examples=force)
        # contamination check after
        texts = collect_style_texts(vp)
        cont = scan_contamination(texts)
        meta["post_ood"] = cont["ood_hit_count"]
        meta["post_political"] = cont["has_political_style"]
        metas.append(meta)
        updates_by_id[row["id"]] = vp
        slang = vp.get("slang_level")
        try:
            slang_f = float(slang) if slang is not None else None
        except (TypeError, ValueError):
            slang_f = None
        apply_list.append((row["id"], vp, slang_f))

    after_counter: Counter[str] = Counter(m["new_voice_type"] for m in metas)
    print("AFTER planned voice_type:", dict(after_counter))
    print(
        "post-purify OOD personas:",
        sum(1 for m in metas if m["post_ood"] > 0),
        "political general_style:",
        sum(1 for m in metas if m["post_political"]),
    )

    plan = {
        "before": before,
        "after": dict(after_counter),
        "reassign_count": len(reassigned_ids),
        "purify_only_count": len(rows) - len(reassigned_ids),
        "reassign_blind_share": REASSIGN_BLIND_SHARE,
        "metas": metas,
    }
    if args.plan_out:
        args.plan_out.parent.mkdir(parents=True, exist_ok=True)
        # lighter plan without full voice dumps
        args.plan_out.write_text(json.dumps(plan, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(f"wrote plan {args.plan_out}")

    # full purified dump for audit / resume
    purified_path = args.snapshot.parent / "wp1b-purified-latest.json"
    purified_payload = {
        "purpose": "WP1B purified voice_profiles (ready to apply)",
        "before": before,
        "after": dict(after_counter),
        "personas": [
            {
                "id": pid,
                "voice_profile": vp,
                "slang_level": vp.get("slang_level"),
            }
            for pid, vp in updates_by_id.items()
        ],
    }
    purified_path.write_text(json.dumps(purified_payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"wrote {purified_path}")

    to_apply = apply_list
    if args.only_reassign:
        to_apply = [u for u in to_apply if u[0] in reassigned_ids]
    if args.offset:
        to_apply = to_apply[args.offset :]
    if args.limit and args.limit > 0:
        to_apply = to_apply[: args.limit]

    if args.apply:
        print(f"APPLYING {len(to_apply)} personas (batch_size={args.batch_size})…")
        n = apply_updates(to_apply, batch_size=args.batch_size)
        print(f"DB rows updated: {n}")
        # verify distribution
        conn = connect_prod()
        try:
            with conn.cursor() as cur:
                cur.execute(
                    """
                    SELECT JSON_UNQUOTE(JSON_EXTRACT(voice_profile,'$.voice_type')) vt, COUNT(*)
                    FROM personas WHERE active=1
                    GROUP BY vt ORDER BY COUNT(*) DESC
                    """
                )
                print("PROD NOW:", cur.fetchall())
        finally:
            conn.close()
    else:
        print(f"dry-run only — would apply {len(to_apply)} (use --apply)")

    if args.sync_yaml:
        # sync only personas in this apply slice when applying with limit; else all
        sync_ids = {u[0] for u in to_apply} if (args.apply or args.limit or args.offset or args.only_reassign) else set(updates_by_id)
        subset = {k: v for k, v in updates_by_id.items() if k in sync_ids} if sync_ids != set(updates_by_id) else updates_by_id
        # if dry-run without limit, sync all planned
        if not args.apply and not args.limit and not args.offset and not args.only_reassign:
            subset = updates_by_id
        n = sync_yaml(args.profiles_dir, subset if args.apply else updates_by_id)
        print(f"YAML synced: {n} profiles")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
