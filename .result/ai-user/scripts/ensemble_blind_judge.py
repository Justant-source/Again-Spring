#!/usr/bin/env python3
"""
ensemble_blind_judge.py — Claude CLI 기반 proxy blind judge ensemble

Usage:
  python3 .result/ai-user/scripts/ensemble_blind_judge.py \
    --survey .result/ai-user/blind/r15-cond5-theqoo-claude-survey.md \
    --answers .result/ai-user/blind/r15-cond5-theqoo-claude-answers-template.json \
    --workers 8
"""

from __future__ import annotations

import argparse
import json
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime
from pathlib import Path

from blind_gate_common import claude_exec as codex_exec, parse_survey_pairs, try_parse_json_response

JUDGES = [
    {
        "id": "style_tells",
        "focus": "문장 안의 AI 티, 부자연스러운 감탄사, 과한 패턴 반복만 본다.",
    },
    {
        "id": "community_fit",
        "focus": "커뮤니티 실제 문체와 얼마나 맞는지, 길이와 말투가 자연스러운지만 본다.",
    },
    {
        "id": "narrative_flow",
        "focus": "사연의 흐름, 문장 연결, topic-first opener, 뻣뻣한 결말을 본다.",
    },
]


def default_output_paths(survey_path: str) -> tuple[str, str]:
    stem = survey_path[:-3] if survey_path.endswith(".md") else survey_path
    return stem + "-ensemble-judge.md", stem + "-ensemble-judge.json"


def build_prompt(community: str, pair: dict, judge: dict) -> str:
    return f"""너는 한국 온라인 커뮤니티 글에서 AI 티를 찾는 블라인드 심사자다.
커뮤니티: {community}
심사 기준: {judge['focus']}

아래 두 글 중 AI가 쓴 것처럼 보이는 쪽을 반드시 하나 고르고, 짧은 이유를 적어라.
반드시 JSON만 출력:
{{"choice":"A"|"B","confidence":1-5,"reason":"짧은 한국어"}}

[A]
{pair['a_text']}

[B]
{pair['b_text']}
"""


def judge_one(community: str, pair: dict, judge: dict) -> dict:
    prompt = build_prompt(community, pair, judge)
    raw = codex_exec(prompt + "\n\n설명 없이 JSON만 출력해.")
    parsed = try_parse_json_response(raw or "")
    choice = str((parsed or {}).get("choice", "")).strip().upper()
    if choice not in {"A", "B"}:
        choice = "?"
    confidence = (parsed or {}).get("confidence", 1)
    try:
        confidence = int(confidence)
    except (TypeError, ValueError):
        confidence = 1
    confidence = max(1, min(confidence, 5))
    reason = str((parsed or {}).get("reason", "")).strip()
    return {
        "pair": pair["pair"],
        "judge_id": judge["id"],
        "choice": choice,
        "confidence": confidence,
        "reason": reason,
        "raw": raw or "",
    }


def aggregate_pair(pair: dict, results: list[dict]) -> dict:
    score_a = sum(item["confidence"] for item in results if item["choice"] == "A")
    score_b = sum(item["confidence"] for item in results if item["choice"] == "B")
    final_choice = "A" if score_a >= score_b else "B"
    predicted_label = pair["a_label"] if final_choice == "A" else pair["b_label"]
    is_correct = predicted_label == "ai"
    return {
        "pair": pair["pair"],
        "a_label": pair["a_label"],
        "b_label": pair["b_label"],
        "final_choice": final_choice,
        "predicted_label": predicted_label,
        "is_correct": is_correct,
        "judge_results": results,
        "score_a": score_a,
        "score_b": score_b,
    }


def render_markdown(community: str, survey_path: str, answers_path: str, aggregates: list[dict], accuracy: float) -> str:
    now = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    md = f"""# Ensemble Blind Judge — {community}
> 생성: {now}
> survey: `{survey_path}`
> answers: `{answers_path}`
> proxy metric: judge ensemble AI detection accuracy

## Summary

- pairs: **{len(aggregates)}**
- proxy accuracy: **{accuracy * 100:.1f}%**

## Pair Results

| pair | final | A | B | predicted | O/X | judge votes |
|---|---|---|---|---|---|---|
"""
    for item in aggregates:
        votes = ", ".join(f"{row['judge_id']}={row['choice']}{row['confidence']}" for row in item["judge_results"])
        md += f"| {item['pair']} | {item['final_choice']} | {item['a_label']} | {item['b_label']} | {item['predicted_label']} | {'O' if item['is_correct'] else 'X'} | {votes} |\n"

    md += "\n## Judge Reasons\n\n"
    for item in aggregates:
        md += f"### Pair {item['pair']}\n\n"
        for row in item["judge_results"]:
            md += f"- `{row['judge_id']}` {row['choice']}{row['confidence']}: {row['reason'] or '[no reason]'}\n"
        md += "\n"
    return md.lstrip()


def main():
    parser = argparse.ArgumentParser(description="Run codex-based proxy blind ensemble judge")
    parser.add_argument("--survey", required=True)
    parser.add_argument("--answers", required=True)
    parser.add_argument("--community", default=None)
    parser.add_argument("--workers", type=int, default=8)
    parser.add_argument("--output", default=None)
    parser.add_argument("--json-output", default=None)
    args = parser.parse_args()

    pairs = parse_survey_pairs(args.survey, args.answers)
    community = args.community or Path(args.answers).stem.split("-")[2].upper()
    tasks = []
    for pair in pairs:
        for judge in JUDGES:
            tasks.append((pair, judge))

    results_by_pair = {pair["pair"]: [] for pair in pairs}
    with ThreadPoolExecutor(max_workers=args.workers) as pool:
        futures = {pool.submit(judge_one, community, pair, judge): (pair, judge) for pair, judge in tasks}
        for future in as_completed(futures):
            result = future.result()
            results_by_pair[result["pair"]].append(result)

    aggregates = [aggregate_pair(pair, sorted(results_by_pair[pair["pair"]], key=lambda item: item["judge_id"])) for pair in pairs]
    correct = sum(1 for item in aggregates if item["is_correct"])
    accuracy = correct / len(aggregates) if aggregates else 0.0

    output_md, output_json = default_output_paths(args.survey)
    if args.output:
        output_md = args.output
    if args.json_output:
        output_json = args.json_output
    payload = {
        "community": community,
        "survey": args.survey,
        "answers": args.answers,
        "accuracy": accuracy,
        "pairs": aggregates,
    }
    Path(output_json).write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    Path(output_md).write_text(render_markdown(community, args.survey, args.answers, aggregates, accuracy), encoding="utf-8")
    print(json.dumps({
        "community": community,
        "output": output_md,
        "json_output": output_json,
        "pairs": len(aggregates),
        "accuracy": accuracy,
    }, ensure_ascii=False))


if __name__ == "__main__":
    main()
