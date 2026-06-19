#!/usr/bin/env python3
"""
summarize_h2h_results.py — R13 h2h answers JSON -> markdown summary

Usage:
    python3 summarize_h2h_results.py \
      --answers .result/ai-user/blind/r13-h2h-theqoo-answers-template.json
"""

import argparse
import json
import os
from datetime import datetime

VALID_CHOICES = {"A", "B"}
SKIP_CHOICES = {"", "답변불가", "판단불가", "미응답", "skip", "SKIP"}


def load_json(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def normalize_entry(raw):
    if isinstance(raw, dict):
        choice = str(raw.get("choice", "")).strip()
        reason = str(raw.get("reason", "")).strip()
    else:
        choice = str(raw or "").strip()
        reason = ""

    if choice.lower() in {"a", "b"}:
        choice = choice.upper()

    if choice in SKIP_CHOICES:
        return (choice or "미응답"), reason
    if choice in VALID_CHOICES:
        return choice, reason
    return (choice or "미응답"), reason


def resolve_pair_key(raw_key, label_map, n_pairs):
    key = str(raw_key).strip()
    try:
        pair_num = int(key)
    except ValueError:
        return key if key in label_map else None

    # Prefer the documented 1-based input form for human-edited answers.
    if 1 <= pair_num <= n_pairs and str(pair_num - 1) in label_map:
        return str(pair_num - 1)
    if key in label_map:
        return key
    return None


def normalize_responses(raw_responses, label_map, n_pairs):
    normalized = {}
    for raw_key, raw_value in (raw_responses or {}).items():
        pair_key = resolve_pair_key(raw_key, label_map, n_pairs)
        if pair_key is None:
            continue
        normalized[pair_key] = normalize_entry(raw_value)
    return normalized


def summarize_respondent(respondent, answers, label_map, n_pairs):
    rows = []
    valid = 0
    rerank_hits = 0
    random_hits = 0

    for idx in range(n_pairs):
        pair_key = str(idx)
        choice, reason = answers.get(pair_key, ("미응답", ""))
        labels = label_map[pair_key]
        ai_pick = "—"
        if choice in VALID_CHOICES:
            ai_pick = labels[choice]
            valid += 1
            if ai_pick == "rerank":
                rerank_hits += 1
            elif ai_pick == "random":
                random_hits += 1
        rows.append(
            {
                "pair": idx + 1,
                "choice": choice,
                "label_a": labels["A"],
                "label_b": labels["B"],
                "ai_pick": ai_pick,
                "reason": reason,
            }
        )

    if valid == 0:
        verdict = "PENDING"
    elif rerank_hits <= random_hits:
        verdict = "PASS"
    else:
        verdict = "FAIL"

    return {
        "respondent": respondent,
        "rows": rows,
        "valid": valid,
        "total": n_pairs,
        "invalid": n_pairs - valid,
        "invalid_rate": ((n_pairs - valid) / n_pairs) if n_pairs else None,
        "rerank_hits": rerank_hits,
        "random_hits": random_hits,
        "rerank_rate": (rerank_hits / valid) if valid else None,
        "random_rate": (random_hits / valid) if valid else None,
        "verdict": verdict,
    }


def combine_summaries(community, summaries):
    valid = sum(item["valid"] for item in summaries)
    total = sum(item["total"] for item in summaries)
    rerank_hits = sum(item["rerank_hits"] for item in summaries)
    random_hits = sum(item["random_hits"] for item in summaries)
    if valid == 0:
        verdict = "PENDING"
    elif rerank_hits <= random_hits:
        verdict = "PASS"
    else:
        verdict = "FAIL"
    return {
        "respondent": f"{community}-combined",
        "valid": valid,
        "total": total,
        "invalid": total - valid,
        "invalid_rate": ((total - valid) / total) if total else None,
        "rerank_hits": rerank_hits,
        "random_hits": random_hits,
        "rerank_rate": (rerank_hits / valid) if valid else None,
        "random_rate": (random_hits / valid) if valid else None,
        "verdict": verdict,
    }


def fmt_rate(value):
    return "—" if value is None else f"{value * 100:.1f}%"


def render_summary(data, answers_path):
    community = data["community"]
    summaries = data["summaries"]
    combined = data["combined"]
    now = datetime.now().strftime("%Y-%m-%d %H:%M:%S")

    md = f"""# R13 h2h 결과 — {community}
> 생성: {now}
> answers: `{answers_path}`
> D-68 선등록 임계: 리랭커 탐지율 ≤ random 탐지율 (do-no-harm)

---

## 응답 현황

| 응답자 | 유효 응답 | 무효 응답 | rerank 탐지율 | random 탐지율 | 판정 |
|---|---:|---:|---:|---:|---|
"""
    for item in summaries:
        md += f"| {item['respondent']} | {item['valid']}/{item['total']} | {item['invalid']}/{item['total']} | {fmt_rate(item['rerank_rate'])} | {fmt_rate(item['random_rate'])} | {item['verdict']} |\n"
    md += f"| combined | {combined['valid']}/{combined['total']} | {combined['invalid']}/{combined['total']} | {fmt_rate(combined['rerank_rate'])} | {fmt_rate(combined['random_rate'])} | {combined['verdict']} |\n"

    for item in summaries:
        md += f"""

---

## {item['respondent']} 상세

| 번호 | 사용자 답 | A 레이블 | B 레이블 | AI 지목 | 이유 |
|---|---|---|---|---|---|
"""
        for row in item["rows"]:
            reason = row["reason"].replace("\n", " ").strip()
            md += f"| {row['pair']} | {row['choice']} | {row['label_a']} | {row['label_b']} | {row['ai_pick']} | {reason} |\n"
        md += f"""

- 유효 응답: {item['valid']}/{item['total']}
- 무효 응답: {item['invalid']}/{item['total']} = **{fmt_rate(item['invalid_rate'])}**
- rerank 탐지: {item['rerank_hits']}/{item['valid']} = **{fmt_rate(item['rerank_rate'])}**
- random 탐지: {item['random_hits']}/{item['valid']} = **{fmt_rate(item['random_rate'])}**
- D-68 판정: **{item['verdict']}**
"""

    md += f"""

---

## combined 판정

- 유효 응답 합산: {combined['valid']}/{combined['total']}
- 무효 응답 합산: {combined['invalid']}/{combined['total']} = **{fmt_rate(combined['invalid_rate'])}**
- rerank 탐지율: **{fmt_rate(combined['rerank_rate'])}**
- random 탐지율: **{fmt_rate(combined['random_rate'])}**
- 최종 상태: **{combined['verdict']}**
"""
    return md.lstrip()


def default_output_path(answers_path):
    if answers_path.endswith("-answers-template.json"):
        return answers_path.replace("-answers-template.json", "-results.md")
    if answers_path.endswith(".json"):
        return answers_path[:-5] + "-results.md"
    return answers_path + "-results.md"


def main():
    parser = argparse.ArgumentParser(description="Summarize R13 h2h answers into markdown")
    parser.add_argument("--answers", required=True, help="Path to h2h answers template json")
    parser.add_argument("--output", default=None, help="Optional markdown output path")
    args = parser.parse_args()

    payload = load_json(args.answers)
    community = payload["community"]
    label_map = payload["label_map"]
    n_pairs = int(payload["n_pairs"])
    responses = payload.get("responses", {})

    respondent_order = ["owner", "friend"]
    summaries = []
    for respondent in respondent_order:
        normalized = normalize_responses(responses.get(respondent, {}), label_map, n_pairs)
        summaries.append(summarize_respondent(respondent, normalized, label_map, n_pairs))

    combined = combine_summaries(community, summaries)
    output = args.output or default_output_path(args.answers)
    markdown = render_summary(
        {
            "community": community,
            "summaries": summaries,
            "combined": combined,
        },
        args.answers,
    )

    with open(output, "w", encoding="utf-8") as f:
        f.write(markdown)

    print(json.dumps({"community": community, "output": output, "combined_verdict": combined["verdict"]}, ensure_ascii=False))


if __name__ == "__main__":
    main()
