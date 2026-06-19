#!/usr/bin/env python3
"""
adversarial_generate_and_filter.py — 대량 초안 생성 후 tell-heavy 샘플 shortlist

Usage:
  python3 .result/ai-user/scripts/adversarial_generate_and_filter.py \
    --community THEQOO --samples-per-theme 3 --workers 8 --generator cli
"""

from __future__ import annotations

import argparse
import json
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime
from pathlib import Path

from blind_gate_common import analyze_text, codex_exec
from build_h2h_survey import COMMUNITY_CFG, generate_post


def proxy_judge_reason(community: str, text: str) -> dict:
    prompt = f"""너는 한국 온라인 커뮤니티 글에서 AI 티를 찾는 검사자다.
커뮤니티: {community}
아래 글이 사람이 쓴 것 같은지 AI가 쓴 것 같은지 짧게 판단하라.
반드시 JSON만 출력:
{{"label":"human"|"ai","confidence":1-5,"reason":"짧은 한국어"}}

글:
{text}
"""
    raw = codex_exec(prompt + "\n\nJSON만 출력해.", timeout=50)
    if not raw:
        return {"label": "unknown", "confidence": 1, "reason": ""}
    try:
        payload = json.loads(raw)
    except json.JSONDecodeError:
        payload = {"label": "unknown", "confidence": 1, "reason": raw[:120]}
    label = str(payload.get("label", "unknown")).strip().lower()
    if label not in {"human", "ai"}:
        label = "unknown"
    try:
        confidence = int(payload.get("confidence", 1))
    except (TypeError, ValueError):
        confidence = 1
    confidence = max(1, min(confidence, 5))
    reason = str(payload.get("reason", "")).strip()
    return {"label": label, "confidence": confidence, "reason": reason}


def default_paths(community: str) -> tuple[str, str]:
    base = f"/home/justant/Data/Again-Spring/.result/ai-user/blind/r14-adversarial-{community.lower()}"
    return base + ".md", base + ".json"


def render_markdown(community: str, generator: str, samples: list[dict]) -> str:
    now = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    md = f"""# R14 Adversarial Generate/Filter — {community}
> 생성: {now}
> generator: `{generator}`

## Summary

- generated: **{len(samples)}**
- shortlisted: **{min(12, len(samples))}**

## Shortlist

| rank | theme | score | chars | hits | proxy |
|---|---|---:|---:|---|---|
"""
    for rank, row in enumerate(samples[:12], start=1):
        analysis = row["analysis"]
        proxy = row["proxy"]
        md += f"| {rank} | {row['theme'][:28]} | {analysis['score']} | {analysis['length_chars']} | {', '.join(analysis['hits']) or '0'} | {proxy['label']}:{proxy['confidence']} |\n"

    md += "\n## Samples\n\n"
    for rank, row in enumerate(samples[:12], start=1):
        analysis = row["analysis"]
        proxy = row["proxy"]
        md += f"### {rank}. {row['theme']}\n\n"
        md += f"- score: {analysis['score']}\n"
        md += f"- hits: {', '.join(analysis['hits']) or '0'}\n"
        md += f"- proxy: {proxy['label']} ({proxy['confidence']}) — {proxy['reason'] or '[no reason]'}\n\n"
        md += row["text"] + "\n\n"
    return md.lstrip()


def generate_one(community: str, theme: str, generator: str, strict_runtime: bool) -> dict | None:
    cfg = COMMUNITY_CFG[community]
    text, source = generate_post(
        theme,
        community,
        cfg,
        dry_run=False,
        generator=generator,
        strict_runtime=strict_runtime,
    )
    if not text:
        return None
    analysis = analyze_text(community, text)
    proxy = proxy_judge_reason(community, text)
    score = analysis["score"] + (2 if proxy["label"] == "ai" else 0) + (proxy["confidence"] - 1 if proxy["label"] == "ai" else 0)
    return {
        "theme": theme,
        "text": text,
        "source": source,
        "analysis": analysis,
        "proxy": proxy,
        "combined_score": score,
    }


def main():
    parser = argparse.ArgumentParser(description="Generate many drafts and shortlist likely-AI outputs")
    parser.add_argument("--community", required=True, choices=sorted(COMMUNITY_CFG))
    parser.add_argument("--samples-per-theme", type=int, default=2)
    parser.add_argument("--themes", type=int, default=8)
    parser.add_argument("--workers", type=int, default=8)
    parser.add_argument("--generator", default="cli", choices=["cli", "runtime"])
    parser.add_argument("--strict-runtime", action="store_true")
    parser.add_argument("--output", default=None)
    parser.add_argument("--json-output", default=None)
    args = parser.parse_args()

    community = args.community.upper()
    themes = COMMUNITY_CFG[community]["themes"][:args.themes]
    tasks = [theme for theme in themes for _ in range(args.samples_per_theme)]
    samples = []
    with ThreadPoolExecutor(max_workers=args.workers) as pool:
        futures = [pool.submit(generate_one, community, theme, args.generator, args.strict_runtime) for theme in tasks]
        for future in as_completed(futures):
            result = future.result()
            if result:
                samples.append(result)

    samples.sort(
        key=lambda row: (
            -row["combined_score"],
            -row["analysis"]["score"],
            -row["analysis"]["length_chars"],
        )
    )

    output_md, output_json = default_paths(community)
    if args.output:
        output_md = args.output
    if args.json_output:
        output_json = args.json_output

    payload = {
        "community": community,
        "generator": args.generator,
        "generated": len(samples),
        "samples": samples,
    }
    Path(output_json).write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    Path(output_md).write_text(render_markdown(community, args.generator, samples), encoding="utf-8")
    print(json.dumps({
        "community": community,
        "output": output_md,
        "json_output": output_json,
        "generated": len(samples),
        "top_combined_score": samples[0]["combined_score"] if samples else None,
    }, ensure_ascii=False))


if __name__ == "__main__":
    main()
