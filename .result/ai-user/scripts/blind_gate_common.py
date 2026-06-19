#!/usr/bin/env python3
"""
blind_gate_common.py — R14 blind automation helpers
"""

from __future__ import annotations

import json
import os
import re
import shutil
import subprocess
import tempfile
from pathlib import Path

from survey_fingerprints import extract_ab_texts, extract_pairs

CODEX_CLI_PATH = os.environ.get("CODEX_BIN", "codex")
CODEX_MODEL = os.environ.get("CODEX_MODEL", "gpt-5.4")

DENY_SIGS = [
    "credit balance", "too low to access", "purchase credits", "plans & billing",
    "usage limit", "reached your usage", "5-hour limit", "rate limit", "rate_limit",
    "overloaded", "invalid_request_error", "authentication_error", "permission_error",
    "api_error", "anthropic api", "insufficient credit", "too many requests",
    "service unavailable", "internal server error",
    "i'm kiro", "i am kiro", "저는 kiro", "kiro입니다",
    "i'm claude", "i am claude", "i'm an ai assistant", "저는 claude",
    "i can't discuss that", "i cannot roleplay", "i'm not able to roleplay",
    "not able to roleplay", "can't roleplay", "cannot roleplay as", "won't roleplay",
    "can't help with this", "cannot help with this", "unable to help with",
    "i can't assist", "cannot assist with", "role-play as", "this is asking me to",
    "이 요청을 도와드릴 수 없", "요청을 도와드릴 수가 없", "죄송하지만 저는 이 요청",
    "이 프롬프트는", "프롬프트 인젝션", "not set up to generate",
    "i need to be direct: i can't", "i need to be direct: i'm",
    "i need to clarify: i'm", "i need to be transparent",
    "i appreciate you", "i'm an ai", "i am an ai", "as an ai", "저는 ai",
]

UNICODE_EMOJI = re.compile(r"[\u2600-\u27BF\U0001F300-\U0001FAFF]")
UNICODE_ELLIPSIS = re.compile(r"[…⋯]")
WEEKDAY_MIDDOT = re.compile(r"[월화수목금토일]·[월화수목금토일]")
REACTION_WORD = re.compile(r"(?:^|\s)(헐|개공감)(?:\s|$)")
ONE_DO_PATTERN = re.compile(r"1도\s+(?:모르겠|이해가 안|생각 안|관심 없|없)")
TOPIC_FIRST_OPENER = re.compile(r"^[^\n]{0,25}(?:얘기|문제|상황|말)이\s+(?:또\s+)?(?:나왔|생겼|터졌|있었)")
CASUAL_TOPIC_FIRST = re.compile(r"^[^\n]{0,30}(?:통장|간병비|데이트 비용|새벽|어제|지난주|남친|여친).{0,20}(?:없음|나왔음|있었음|얘기)")
MANY_DOTS = re.compile(r"\.{3,}")
POLITICAL_NOTICE = re.compile(r"(정치 카테고리|회원님들에게 직접적인 피해|비밀번호를 타 사이트와 다르게 변경)")

STOPWORDS = {
    "그냥", "근데", "진짜", "너무", "이번", "지난", "지금", "제가", "내가", "그게", "이게",
    "뭔가", "거든요", "같은", "있는", "하는", "하는데", "그리고", "정말", "약간", "그런데",
    "because", "would", "could", "should",
}


def load_json(path: str) -> dict:
    return json.loads(Path(path).read_text(encoding="utf-8"))


def dump_json(path: str, payload: dict) -> None:
    Path(path).write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def load_text(path: str) -> str:
    return Path(path).read_text(encoding="utf-8")


def find_codex_cli() -> str | None:
    candidates = [CODEX_CLI_PATH]
    which_result = shutil.which("codex")
    if which_result:
        candidates.append(which_result)
    for path in candidates:
        if path and os.path.isfile(path) and os.access(path, os.X_OK):
            return path
    return None


def codex_exec(prompt: str, timeout: int = 60) -> str | None:
    codex_path = find_codex_cli()
    if not codex_path:
        return None
    with tempfile.NamedTemporaryFile(prefix="blind-gate-", suffix=".txt", delete=False) as tmp:
        out_path = tmp.name
    try:
        result = subprocess.run(
            [
                codex_path,
                "exec",
                "--skip-git-repo-check",
                "--sandbox", "read-only",
                "--cd", "/tmp",
                "--color", "never",
                "--output-last-message", out_path,
                "--model", CODEX_MODEL,
                prompt,
            ],
            capture_output=True,
            text=True,
            timeout=timeout,
        )
        text = ""
        if os.path.exists(out_path):
            text = Path(out_path).read_text(encoding="utf-8").strip()
        if result.returncode != 0 or not text:
            return None
        lowered = text.lower()
        if any(sig in lowered for sig in DENY_SIGS):
            return None
        return text
    finally:
        if os.path.exists(out_path):
            os.unlink(out_path)


def tokenize(text: str) -> list[str]:
    tokens = re.findall(r"[0-9A-Za-z가-힣]{2,}", text.lower())
    return [tok for tok in tokens if tok not in STOPWORDS]


def jaccard(tokens_a: list[str], tokens_b: list[str]) -> float:
    set_a = set(tokens_a)
    set_b = set(tokens_b)
    if not set_a or not set_b:
        return 0.0
    return len(set_a & set_b) / len(set_a | set_b)


def parse_survey_pairs(survey_path: str, answers_path: str) -> list[dict]:
    survey_text = load_text(survey_path)
    answers = load_json(answers_path)
    label_map = answers["label_map"]
    pairs = []
    ab_pairs = []
    for pair_num, block in extract_pairs(survey_text):
        ab = extract_ab_texts(block)
        if not ab:
            continue
        ab_pairs.append((pair_num, ab[0], ab[1]))
    for pair_num, text_a, text_b in ab_pairs:
        labels = label_map[str(pair_num - 1)]
        pairs.append(
            {
                "pair": pair_num,
                "a_text": text_a,
                "b_text": text_b,
                "a_label": labels["A"],
                "b_label": labels["B"],
            }
        )
    return pairs


def ai_text_rows_from_pairs(pairs: list[dict]) -> list[dict]:
    rows = []
    for pair in pairs:
        for side in ("a", "b"):
            label = pair[f"{side}_label"]
            rows.append(
                {
                    "pair": pair["pair"],
                    "side": side.upper(),
                    "label": label,
                    "text": pair[f"{side}_text"],
                }
            )
    return rows


def analyze_text(community: str, text: str) -> dict:
    tokens = tokenize(text)
    lines = [line for line in text.splitlines() if line.strip()]
    hit_map = {
        "reaction_word": bool(REACTION_WORD.search(text)),
        "unicode_ellipsis": bool(UNICODE_ELLIPSIS.search(text)),
        "unicode_emoji": bool(UNICODE_EMOJI.search(text)),
        "one_do_pattern": bool(ONE_DO_PATTERN.search(text)),
        "weekday_middot": bool(WEEKDAY_MIDDOT.search(text)),
        "topic_first_opener": bool(TOPIC_FIRST_OPENER.search(text) or CASUAL_TOPIC_FIRST.search(text)),
        "many_dots": bool(MANY_DOTS.search(text)),
        "political_notice_style": bool(POLITICAL_NOTICE.search(text)),
    }
    length_chars = len(text)
    long_threshold = {"THEQOO": 280, "NATEPAN": 420, "CLIEN": 330}.get(community, 320)
    score = 0
    score += 2 if hit_map["reaction_word"] else 0
    score += 2 if hit_map["one_do_pattern"] else 0
    score += 1 if hit_map["weekday_middot"] else 0
    score += 1 if hit_map["unicode_ellipsis"] else 0
    score += 1 if hit_map["unicode_emoji"] else 0
    score += 2 if hit_map["political_notice_style"] else 0
    score += 1 if hit_map["topic_first_opener"] else 0
    score += 1 if length_chars > long_threshold else 0
    score += 1 if len(lines) > 8 else 0
    return {
        "length_chars": length_chars,
        "line_count": len(lines),
        "token_count": len(tokens),
        "tokens": tokens,
        "hits": [name for name, matched in hit_map.items() if matched],
        "score": score,
    }


def try_parse_json_response(text: str) -> dict | None:
    text = (text or "").strip()
    if not text:
        return None
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        pass
    match = re.search(r"\{.*\}", text, re.DOTALL)
    if match:
        try:
            return json.loads(match.group(0))
        except json.JSONDecodeError:
            return None
    return None

