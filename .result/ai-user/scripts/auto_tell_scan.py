#!/usr/bin/env python3
"""
auto_tell_scan.py — blind survey의 AI tell / 반복 신호 자동 스캔

Usage:
  python3 .result/ai-user/scripts/auto_tell_scan.py \
    --survey .result/ai-user/blind/r14-cond5-theqoo-survey.md \
    --answers .result/ai-user/blind/r14-cond5-theqoo-answers-template.json
"""

from __future__ import annotations

import argparse
import json
from collections import Counter
from datetime import datetime
from pathlib import Path

from blind_gate_common import ai_text_rows_from_pairs, analyze_text, jaccard, parse_survey_pairs, tokenize


def default_paths(survey_path: str) -> tuple[str, str]:
    stem = survey_path[:-3] if survey_path.endswith(".md") else survey_path
    return stem + "-auto-tell-scan.md", stem + "-auto-tell-scan.json"


def build_similarity_rows(rows: list[dict], threshold: float) -> list[dict]:
    sims = []
    for idx, left in enumerate(rows):
        for right in rows[idx + 1:]:
            score = jaccard(left["analysis"]["tokens"], right["analysis"]["tokens"])
            if score >= threshold:
                sims.append(
                    {
                        "left_pair": left["pair"],
                        "left_side": left["side"],
                        "right_pair": right["pair"],
                        "right_side": right["side"],
                        "similarity": round(score, 4),
                    }
                )
    sims.sort(key=lambda x: x["similarity"], reverse=True)
    return sims


def render_markdown(community: str, survey_path: str, answers_path: str, rows: list[dict], sims: list[dict]) -> str:
    now = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    hit_counter = Counter()
    for row in rows:
        hit_counter.update(row["analysis"]["hits"])
    md = f"""# Auto Tell Scan — {community}
> 생성: {now}
> survey: `{survey_path}`
> answers: `{answers_path}`

## 요약

- scanned ai texts: **{len(rows)}**
- repeated-topic pairs (jaccard>=0.35): **{len(sims)}**

## Hit Counts

| signal | count |
|---|---:|
"""
    for name, count in sorted(hit_counter.items(), key=lambda item: (-item[1], item[0])):
        md += f"| {name} | {count} |\n"

    md += """

## Highest Risk Texts

| pair | side | score | chars | lines | hits |
|---|---|---:|---:|---:|---|
"""
    for row in sorted(rows, key=lambda item: (-item["analysis"]["score"], -item["analysis"]["length_chars"], item["pair"]))[:12]:
        info = row["analysis"]
        md += f"| {row['pair']} | {row['side']} | {info['score']} | {info['length_chars']} | {info['line_count']} | {', '.join(info['hits']) or '0'} |\n"

    md += "\n## Repetition Risk\n\n| left | right | similarity |\n|---|---|---:|\n"
    if sims:
        for sim in sims[:15]:
            md += f"| {sim['left_pair']}{sim['left_side']} | {sim['right_pair']}{sim['right_side']} | {sim['similarity']:.2f} |\n"
    else:
        md += "| — | — | — |\n"

    md += "\n## Text Notes\n\n"
    for row in sorted(rows, key=lambda item: (-item["analysis"]["score"], item["pair"]))[:8]:
        text = row["text"].replace("\n", " ").strip()
        md += f"### {row['pair']}{row['side']} score={row['analysis']['score']}\n\n"
        md += f"- hits: {', '.join(row['analysis']['hits']) or '0'}\n"
        md += f"- text: {text[:500]}\n\n"
    return md.lstrip()


def main():
    parser = argparse.ArgumentParser(description="Scan blind survey texts for likely AI tell signals")
    parser.add_argument("--survey", required=True)
    parser.add_argument("--answers", required=True)
    parser.add_argument("--community", default=None)
    parser.add_argument("--similarity-threshold", type=float, default=0.35)
    parser.add_argument("--output", default=None)
    parser.add_argument("--json-output", default=None)
    args = parser.parse_args()

    pairs = parse_survey_pairs(args.survey, args.answers)
    rows = [row for row in ai_text_rows_from_pairs(pairs) if row["label"] == "ai"]
    if not rows:
        raise SystemExit("No ai-labeled rows found in survey+answers")

    community = args.community or Path(args.answers).stem.split("-")[2].upper()
    for row in rows:
        row["analysis"] = analyze_text(community, row["text"])
    sims = build_similarity_rows(rows, args.similarity_threshold)

    output_md, output_json = default_paths(args.survey)
    if args.output:
        output_md = args.output
    if args.json_output:
        output_json = args.json_output

    payload = {
        "community": community,
        "survey": args.survey,
        "answers": args.answers,
        "ai_rows": [
            {
                "pair": row["pair"],
                "side": row["side"],
                "score": row["analysis"]["score"],
                "length_chars": row["analysis"]["length_chars"],
                "line_count": row["analysis"]["line_count"],
                "hits": row["analysis"]["hits"],
                "preview": row["text"][:240],
            }
            for row in rows
        ],
        "repetition_pairs": sims,
    }
    Path(output_json).write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    Path(output_md).write_text(render_markdown(community, args.survey, args.answers, rows, sims), encoding="utf-8")
    print(json.dumps({
        "community": community,
        "output": output_md,
        "json_output": output_json,
        "ai_rows": len(rows),
        "repetition_pairs": len(sims),
        "top_score": max(row["analysis"]["score"] for row in rows),
    }, ensure_ascii=False))


if __name__ == "__main__":
    main()
