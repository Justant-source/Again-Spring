#!/usr/bin/env python3
"""
summarize_cond5_results.py — cond5 blind answers -> markdown summary
"""

import argparse
import json
from datetime import datetime

VALID_CHOICES = {"A", "B"}
SKIP_CHOICES = {"", "답변불가", "판단불가", "미응답", "skip", "SKIP"}


def load_json(path):
    try:
        with open(path, encoding="utf-8") as f:
            return json.load(f)
    except FileNotFoundError as e:
        raise SystemExit(f"Answers json not found: {path}") from e


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


def summarize_one(name, answers, label_map, n_pairs):
    rows = []
    valid = 0
    correct_ai = 0
    for idx in range(n_pairs):
        pair_key = str(idx)
        choice, reason = answers.get(pair_key, ("미응답", ""))
        labels = label_map[pair_key]
        chosen_label = labels.get(choice) if choice in VALID_CHOICES else None
        is_correct = chosen_label == "ai"
        if choice in VALID_CHOICES:
            valid += 1
            if is_correct:
                correct_ai += 1
        rows.append({
            "pair": idx + 1,
            "choice": choice,
            "a_label": labels["A"],
            "b_label": labels["B"],
            "chosen_label": chosen_label or "—",
            "is_correct": is_correct if choice in VALID_CHOICES else None,
            "reason": reason,
        })
    accuracy = (correct_ai / valid) if valid else None
    verdict = "PENDING"
    if accuracy is not None:
        verdict = "PASS" if accuracy <= 0.60 else "FAIL"
    return {
        "respondent": name,
        "rows": rows,
        "valid": valid,
        "invalid": n_pairs - valid,
        "total": n_pairs,
        "correct_ai": correct_ai,
        "accuracy": accuracy,
        "verdict": verdict,
    }


def combine(community, items):
    valid = sum(x["valid"] for x in items)
    total = sum(x["total"] for x in items)
    correct_ai = sum(x["correct_ai"] for x in items)
    accuracy = (correct_ai / valid) if valid else None
    verdict = "PENDING" if accuracy is None else ("PASS" if accuracy <= 0.60 else "FAIL")
    return {
        "respondent": f"{community}-combined",
        "valid": valid,
        "invalid": total - valid,
        "total": total,
        "correct_ai": correct_ai,
        "accuracy": accuracy,
        "verdict": verdict,
    }


def pct(value):
    return "—" if value is None else f"{value * 100:.1f}%"


def render(payload, answers_path, summaries, combined):
    now = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    community = payload["community"]
    md = f"""# R14 cond5 결과 — {community}
> 생성: {now}
> answers: `{answers_path}`
> 기준: AI 탐지 정확도 `<= 60%` = PASS

## 응답 현황

| 응답자 | 유효 응답 | 무효 응답 | AI 탐지 정확도 | 판정 |
|---|---:|---:|---:|---|
"""
    for item in summaries:
        md += f"| {item['respondent']} | {item['valid']}/{item['total']} | {item['invalid']}/{item['total']} | {pct(item['accuracy'])} | {item['verdict']} |\n"
    md += f"| combined | {combined['valid']}/{combined['total']} | {combined['invalid']}/{combined['total']} | {pct(combined['accuracy'])} | {combined['verdict']} |\n"

    for item in summaries:
        md += f"""

---

## {item['respondent']} 상세

| 번호 | 사용자 답 | A 레이블 | B 레이블 | 선택 해석 | O/X | 이유 |
|---|---|---|---|---|---|---|
"""
        for row in item["rows"]:
            ox = "O" if row["is_correct"] else ("X" if row["is_correct"] is not None else "—")
            reason = row["reason"].replace("\n", " ").strip()
            md += f"| {row['pair']} | {row['choice']} | {row['a_label']} | {row['b_label']} | {row['chosen_label']} | {ox} | {reason} |\n"
        md += f"""

- 유효 응답: {item['valid']}/{item['total']}
- 무효 응답: {item['invalid']}/{item['total']}
- AI 탐지 정확도: **{pct(item['accuracy'])}**
- cond5 판정: **{item['verdict']}**
"""
    md += f"""

---

## combined 판정

- 유효 응답 합산: {combined['valid']}/{combined['total']}
- 무효 응답 합산: {combined['invalid']}/{combined['total']}
- AI 탐지 정확도: **{pct(combined['accuracy'])}**
- 최종 상태: **{combined['verdict']}**
"""
    return md.lstrip()


def default_output_path(answers_path):
    if answers_path.endswith("-answers-template.json"):
        return answers_path.replace("-answers-template.json", "-results.md")
    return answers_path.replace(".json", "-results.md")


def main():
    parser = argparse.ArgumentParser(description="Summarize cond5 blind answers")
    parser.add_argument("--answers", required=True)
    parser.add_argument("--output", default=None)
    args = parser.parse_args()

    payload = load_json(args.answers)
    community = payload["community"]
    n_pairs = int(payload["n_pairs"])
    label_map = payload["label_map"]
    responses = payload.get("responses") or {}

    summaries = []
    for respondent in ["owner", "friend"]:
        normalized = normalize_responses(responses.get(respondent, {}), label_map, n_pairs)
        summaries.append(summarize_one(respondent, normalized, label_map, n_pairs))
    combined = combine(community, summaries)
    output = args.output or default_output_path(args.answers)
    with open(output, "w", encoding="utf-8") as f:
        f.write(render(payload, args.answers, summaries, combined))
    print(json.dumps({
        "community": community,
        "output": output,
        "combined_verdict": combined["verdict"],
        "combined_accuracy": combined["accuracy"],
    }, ensure_ascii=False))


if __name__ == "__main__":
    main()
