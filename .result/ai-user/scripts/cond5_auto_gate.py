#!/usr/bin/env python3
"""
cond5_auto_gate.py — Three-state automated cond5 verdict using calibrated proxy upper bound + veto signals.

Usage:
  python3 .result/ai-user/scripts/cond5_auto_gate.py \
    --survey .result/ai-user/blind/r15-cond5-theqoo-claude-survey.md \
    --answers .result/ai-user/blind/r15-cond5-theqoo-claude-answers-template.json \
    --ensemble .result/ai-user/blind/r15-cond5-theqoo-claude-survey-ensemble-judge.json \
    --tell-scan .result/ai-user/blind/r15-cond5-theqoo-claude-survey-auto-tell-scan.json \
    [--registry .result/ai-user/blind/survey-fingerprint-registry.json] \
    [--community THEQOO]
"""

from __future__ import annotations

import argparse
import json
from datetime import datetime
from pathlib import Path

from blind_gate_common import load_json, tokenize, jaccard, parse_survey_pairs
from survey_fingerprints import (
    collect_text_fingerprints_from_survey,
    load_registry,
    extract_ab_texts,
    extract_pairs,
)

GAP_HI = 0.54  # updated: 4-judge (micro_tell) calibration on r14 THEQOO (0.842-0.30=0.54)
# NOTE: r9-blind2 proxy=0.88 vs human=0.40 → gap=-0.48 (opposite direction!)
# Cross-era calibration FAILED (two opposite-direction points). gap_hi=0.54 used as
# conservative upper bound from the one case where proxy UNDER-detects humans (Codex era).
# This likely over-penalizes Claude-era content; see D-101.


def compute_human_est_upper(proxy_accuracy: float) -> float:
    """Compute conservative upper bound on human detection rate."""
    return min(1.0, proxy_accuracy + GAP_HI)


def auto_detect_tell_scan_path(survey_path: str) -> str:
    """Auto-detect *-auto-tell-scan.json from survey path stem."""
    stem = survey_path[:-3] if survey_path.endswith(".md") else survey_path
    return stem + "-auto-tell-scan.json"


def load_ensemble_judge(path: str) -> dict:
    """Load ensemble judge JSON."""
    return load_json(path)


def load_tell_scan(path: str) -> dict | None:
    """Load auto-tell-scan JSON; return None if not found."""
    p = Path(path)
    if not p.exists():
        return None
    return load_json(path)


def extract_tell_metrics(tell_scan: dict | None) -> tuple[int, int]:
    """Extract top_score and rep_pairs from auto-tell-scan JSON."""
    if not tell_scan:
        return 0, 0
    ai_rows = tell_scan.get("ai_rows", [])
    top_score = max((row.get("score", 0) for row in ai_rows), default=0)
    rep_pairs = 0
    for row in ai_rows:
        if row.get("hits") and "repetition" in row.get("hits", []):
            rep_pairs += 1
    return top_score, rep_pairs


def compute_topic_overlap(
    survey_path: str,
    answers_path: str,
    registry_path: str | None = None,
) -> int:
    """
    Count AI items with topic fingerprint overlap against all past surveys in registry.
    Uses tokenized text + Jaccard similarity (threshold=0.35).
    """
    if not registry_path or not Path(registry_path).exists():
        return 0

    pairs = parse_survey_pairs(survey_path, answers_path)
    registry = load_registry(registry_path)

    overlap_count = 0
    for pair in pairs:
        for side in ("a", "b"):
            label = pair[f"{side}_label"]
            if label != "ai":
                continue
            ai_text = pair[f"{side}_text"]
            ai_tokens = tokenize(ai_text)
            if not ai_tokens:
                continue

            has_overlap = False
            for past_test in registry.get("tests", []):
                past_pairs = past_test.get("pairs", [])
                for past_pair in past_pairs:
                    for past_side in ("a", "b"):
                        past_label = past_pair.get(f"{past_side}_label")
                        if past_label != "ai":
                            continue
                        past_text = past_pair.get(f"{past_side}_text", "")
                        past_tokens = tokenize(past_text)
                        if not past_tokens:
                            continue
                        sim = jaccard(ai_tokens, past_tokens)
                        if sim >= 0.35:
                            has_overlap = True
                            break
                    if has_overlap:
                        break
                if has_overlap:
                    break
            if has_overlap:
                overlap_count += 1

    return overlap_count


def compute_confused_frac(ensemble: dict) -> float:
    """
    Compute fraction of pairs where |score_a - score_b| <= 3 (judges confused).
    """
    pairs = ensemble.get("pairs", [])
    if not pairs:
        return 0.0

    confused_count = 0
    for pair in pairs:
        score_a = pair.get("score_a", 0)
        score_b = pair.get("score_b", 0)
        if abs(score_a - score_b) <= 3:
            confused_count += 1

    return confused_count / len(pairs) if pairs else 0.0


def gate_verdict(
    proxy_accuracy: float,
    tell_top_score: int,
    rep_pairs: int,
    topic_overlap: int,
    confused_frac: float,
) -> dict:
    """
    Three-state automated cond5 verdict with calibrated conservative upper bound + veto signals.
    """
    human_est_upper = compute_human_est_upper(proxy_accuracy)
    fail_reasons = []

    if human_est_upper > 0.60:
        fail_reasons.append(f"human_est_upper={human_est_upper:.3f} > 0.60")
    if tell_top_score > 3:
        fail_reasons.append(f"tell_top_score={tell_top_score} > 3")
    if rep_pairs > 0:
        fail_reasons.append(f"rep_pairs={rep_pairs} > 0")
    if topic_overlap > 0:
        fail_reasons.append(f"topic_overlap={topic_overlap} > 0")
    if confused_frac > 0.15:
        fail_reasons.append(f"confused_frac={confused_frac:.3f} > 0.15")

    if fail_reasons:
        verdict = "PROXY-FAIL"
    else:
        verdict = "PROXY-INCONCLUSIVE"  # leaning-pass but NOT human PASS

    return {
        "verdict": verdict,
        "estimated_human_detection_upper_bound": round(human_est_upper, 3),
        "proxy_accuracy": round(proxy_accuracy, 3),
        "gap_hi_applied": GAP_HI,
        "fail_reasons": fail_reasons,
        "caveat": (
            "proxy 파생·cross-era(Codex→Claude)·Claude 컨텐츠 미검증. "
            "PROXY-INCONCLUSIVE는 사람 PASS 아님. "
            "stateless LLM은 주제 과사용·출처 정보비대칭 감지 불가(미검증 unrecoverable gap)."
        ),
    }


def render_markdown(
    community: str,
    survey_stem: str,
    result: dict,
    tell_top_score: int,
    rep_pairs: int,
    topic_overlap: int,
    confused_frac: float,
) -> str:
    """Render Markdown gate report."""
    now = datetime.now().isoformat()

    verdict = result["verdict"]
    human_est_upper = result["estimated_human_detection_upper_bound"]
    proxy_accuracy = result["proxy_accuracy"]
    fail_reasons = result["fail_reasons"]
    caveat = result["caveat"]

    def check_mark(passed: bool) -> str:
        return "✅" if passed else "❌"

    md = f"""# cond5 자동 게이트 — {community} {survey_stem}
> 생성: {now}
> 방법: 보정형 proxy upper-bound + veto 복합 (D-101)
> `AI_USER_ML_ENABLED` 변경 금지 — 활성화는 사람 수동

## 판정: {verdict}

| 지표 | 값 | 기준 | 결과 |
|---|---|---|---|
| estimated_human_detection_upper_bound | {human_est_upper:.3f} | ≤ 0.60 | {check_mark(human_est_upper <= 0.60)} |
| proxy_accuracy | {proxy_accuracy:.3f} | 참고값 | — |
| tell_top_score | {tell_top_score} | ≤ 3 | {check_mark(tell_top_score <= 3)} |
| rep_pairs | {rep_pairs} | == 0 | {check_mark(rep_pairs == 0)} |
| topic_overlap | {topic_overlap} | == 0 | {check_mark(topic_overlap == 0)} |
| confused_frac | {confused_frac:.3f} | ≤ 0.15 | {check_mark(confused_frac <= 0.15)} |

## 캐비엇
{caveat}
"""

    if fail_reasons:
        md += "\n## 실패 사유\n\n"
        for reason in fail_reasons:
            md += f"- {reason}\n"

    return md.lstrip()


def main():
    parser = argparse.ArgumentParser(description="Three-state automated cond5 gate verdict")
    parser.add_argument("--survey", required=True)
    parser.add_argument("--answers", required=True)
    parser.add_argument("--ensemble", required=True)
    parser.add_argument("--tell-scan", default=None)
    parser.add_argument("--registry", default=None)
    parser.add_argument("--community", default=None)
    args = parser.parse_args()

    # Auto-detect tell-scan if not provided
    tell_scan_path = args.tell_scan or auto_detect_tell_scan_path(args.survey)

    # Auto-detect registry if not provided
    registry_path = args.registry or Path(args.survey).parent / "survey-fingerprint-registry.json"

    # Determine community
    community = args.community or Path(args.answers).stem.split("-")[2].upper()

    # Determine survey stem
    survey_stem = Path(args.survey).stem

    # Load ensemble judge
    ensemble = load_ensemble_judge(args.ensemble)
    proxy_accuracy = ensemble.get("accuracy", 0.0)

    # Load tell-scan
    tell_scan = load_tell_scan(tell_scan_path)
    tell_top_score, rep_pairs = extract_tell_metrics(tell_scan)

    # Compute topic overlap
    topic_overlap = compute_topic_overlap(args.survey, args.answers, str(registry_path))

    # Compute confused fraction
    confused_frac = compute_confused_frac(ensemble)

    # Gate verdict
    verdict_result = gate_verdict(
        proxy_accuracy=proxy_accuracy,
        tell_top_score=tell_top_score,
        rep_pairs=rep_pairs,
        topic_overlap=topic_overlap,
        confused_frac=confused_frac,
    )

    # Render markdown
    markdown = render_markdown(
        community=community,
        survey_stem=survey_stem,
        result=verdict_result,
        tell_top_score=tell_top_score,
        rep_pairs=rep_pairs,
        topic_overlap=topic_overlap,
        confused_frac=confused_frac,
    )

    # Output paths
    output_md = f"{survey_stem}-cond5-gate.md"
    output_json = f"{survey_stem}-cond5-gate.json"

    # Write outputs
    payload = {
        "community": community,
        "survey": args.survey,
        "answers": args.answers,
        "ensemble": args.ensemble,
        "tell_scan": tell_scan_path,
        **verdict_result,
        "tell_top_score": tell_top_score,
        "rep_pairs": rep_pairs,
        "topic_overlap": topic_overlap,
        "confused_frac": round(confused_frac, 3),
    }
    Path(output_json).write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    Path(output_md).write_text(markdown, encoding="utf-8")

    # Print JSON to stdout
    print(json.dumps({
        "community": community,
        "survey_stem": survey_stem,
        "verdict": verdict_result["verdict"],
        "output_md": output_md,
        "output_json": output_json,
    }, ensure_ascii=False))


if __name__ == "__main__":
    main()
