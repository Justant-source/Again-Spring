#!/usr/bin/env python3
"""
calibration_agreement.py — Compute proxy↔human agreement from ensemble judge + answers

Measures proxy bias by comparing ensemble judge results against human answers.
Outputs confusion matrix and per-pair accuracy gaps.

Usage:
  python3 calibration_agreement.py \\
    --ensemble r14-cond5-theqoo-survey-ensemble-judge.json \\
    --answers r14-cond5-theqoo-answers-template.json \\
    --label r14-theqoo \\
    [--ensemble ... --answers ... --label ...] \\
    --output-dir blind/
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import NamedTuple


class SurveyTriple(NamedTuple):
    """Bundle of ensemble result, answers, and label."""

    ensemble_path: str
    answers_path: str
    label: str


def load_json(path: str) -> dict:
    return json.loads(Path(path).read_text(encoding="utf-8"))


def dump_json(path: str, payload: dict) -> None:
    Path(path).write_text(
        json.dumps(payload, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )


def compute_agreement(
    ensemble: dict, answers: dict, label: str
) -> dict:
    """
    Compute per-pair agreement between proxy and human.

    Returns:
        {
            "survey": label,
            "community": answers["community"],
            "proxy_accuracy": float,
            "human_accuracy": float,
            "gap": float,
            "n_valid": int,
            "confusion": {
                "both_correct": int,
                "human_only": int,
                "proxy_only": int,
                "both_wrong": int,
            },
            "human_only_pairs": [int, ...],
            "miss_margins": [int, ...],  # score margins for human_only cases
        }
    """
    ensemble_pairs = ensemble.get("pairs", [])
    label_map = answers.get("label_map", {})
    responses = answers.get("responses", {})
    owner_responses = responses.get("owner", {})

    n_pairs = answers.get("n_pairs", len(ensemble_pairs))

    confusion = {
        "both_correct": 0,
        "human_only": 0,
        "proxy_only": 0,
        "both_wrong": 0,
    }

    human_only_pairs = []
    miss_margins = []

    proxy_correct_count = 0
    human_correct_count = 0
    valid_pair_count = 0

    for ep in ensemble_pairs:
        pair_num = ep.get("pair", 0)
        pair_key = str(pair_num - 1)

        # Get proxy result
        proxy_is_correct = ep.get("is_correct", False)
        proxy_correct_count += 1 if proxy_is_correct else 0

        # Get human result — label_map uses 0-indexed key; responses.owner uses 1-indexed
        labels = label_map.get(pair_key, {})
        human_response = owner_responses.get(str(pair_num), {})
        human_choice = (
            str(human_response.get("choice", "")).strip().upper()
            if isinstance(human_response, dict)
            else str(human_response or "").strip().upper()
        )

        # Determine if human is correct
        human_is_correct = False
        if human_choice in {"A", "B"}:
            chosen_label = labels.get(human_choice)
            human_is_correct = chosen_label == "ai"
            human_correct_count += 1 if human_is_correct else 0
            valid_pair_count += 1

            # Classify into confusion matrix
            if proxy_is_correct and human_is_correct:
                confusion["both_correct"] += 1
            elif human_is_correct and not proxy_is_correct:
                confusion["human_only"] += 1
                human_only_pairs.append(pair_num)
                # Calculate margin as (proxy_score_a - proxy_score_b)
                score_a = ep.get("score_a", 0)
                score_b = ep.get("score_b", 0)
                margin = abs(score_a - score_b)
                miss_margins.append(margin)
            elif proxy_is_correct and not human_is_correct:
                confusion["proxy_only"] += 1
            else:
                confusion["both_wrong"] += 1

    proxy_accuracy = (
        proxy_correct_count / len(ensemble_pairs)
        if ensemble_pairs
        else 0.0
    )
    human_accuracy = (
        human_correct_count / valid_pair_count if valid_pair_count else 0.0
    )
    gap = human_accuracy - proxy_accuracy

    return {
        "survey": label,
        "community": answers.get("community", "unknown"),
        "proxy_accuracy": round(proxy_accuracy, 4),
        "human_accuracy": round(human_accuracy, 4),
        "gap": round(gap, 4),
        "n_valid": valid_pair_count,
        "confusion": confusion,
        "human_only_pairs": human_only_pairs,
        "miss_margins": miss_margins,
    }


def render_markdown(
    surveys_results: list[dict],
) -> str:
    """Render markdown report with confusion matrices and caveats."""
    md = """# Calibration Agreement Report

## Summary

Cross-era proxy bias analysis: R9-era Codex survey on R14 Claude-generated content.

| Survey | Community | Proxy Acc | Human Acc | Gap | N |
|---|---|---:|---:|---:|---:|
"""

    gap_values = [s["gap"] for s in surveys_results if s["gap"] is not None]
    gap_hi = max(gap_values) if gap_values else 0.0
    gap_lo = min(gap_values) if gap_values else 0.0
    gap_mean = (
        sum(gap_values) / len(gap_values) if gap_values else 0.0
    )

    for result in surveys_results:
        community = result["community"]
        survey = result["survey"]
        proxy_acc = f"{result['proxy_accuracy'] * 100:.1f}%"
        human_acc = f"{result['human_accuracy'] * 100:.1f}%"
        gap = f"{result['gap'] * 100:.1f}%"
        n = result["n_valid"]
        md += f"| {survey} | {community} | {proxy_acc} | {human_acc} | {gap} | {n} |\n"

    md += "\n## Detailed Results\n\n"

    for result in surveys_results:
        survey = result["survey"]
        community = result["community"]
        confusion = result["confusion"]
        human_only_pairs = result["human_only_pairs"]
        miss_margins = result["miss_margins"]

        md += f"### {survey} ({community})\n\n"
        md += "#### Confusion Matrix\n\n"
        md += "| Outcome | Count |\n|---|---:|\n"
        md += f"| Both Correct | {confusion['both_correct']} |\n"
        md += f"| Human Only (proxy missed) | {confusion['human_only']} |\n"
        md += f"| Proxy Only (human missed) | {confusion['proxy_only']} |\n"
        md += f"| Both Wrong | {confusion['both_wrong']} |\n"
        md += "\n"

        if human_only_pairs:
            md += f"#### Proxy Blind Spots (Human Caught AI)\n\n"
            md += f"Pairs: {', '.join(map(str, human_only_pairs))}\n\n"
            if miss_margins:
                avg_margin = sum(miss_margins) / len(miss_margins)
                md += f"- Average judge score margin: {avg_margin:.1f}\n"
                md += f"- Min margin: {min(miss_margins)}\n"
                md += f"- Max margin: {max(miss_margins)}\n"
            md += "\n"

    md += """## Calibration Note

**Important Caveat**: The `gap_hi` value ({:.1f}%) is derived from an R9-era Codex survey applied to R14 Claude-generated content. This represents a **cross-era comparison** and may overestimate the true proxy bias on same-era content.

The gap reflects:
1. **Codex detector patterns** (R9-era training) vs. **Claude generation patterns** (R14-era)
2. Human expert knowledge of generation differences across model eras
3. Conservative upper bound for production calibration

**Recommendation**: Use `gap_hi` as a conservative overestimate. The true proxy accuracy on R14 content may be higher than this suggests.
""".format(gap_hi * 100)

    return md.lstrip()


def main():
    parser = argparse.ArgumentParser(
        description="Compute proxy↔human agreement from ensemble judge + answers"
    )
    parser.add_argument(
        "--ensemble",
        action="append",
        required=False,
        help="Ensemble judge JSON (repeatable)",
    )
    parser.add_argument(
        "--answers",
        action="append",
        required=False,
        help="Answers template JSON (repeatable, paired with --ensemble)",
    )
    parser.add_argument(
        "--label",
        action="append",
        required=False,
        help="Survey label (repeatable, paired with --ensemble)",
    )
    parser.add_argument(
        "--output-dir",
        required=True,
        help="Output directory for reports",
    )
    args = parser.parse_args()

    # Validate input triples
    ensembles = args.ensemble or []
    answers_list = args.answers or []
    labels = args.label or []

    if not (ensembles and answers_list and labels):
        raise SystemExit(
            "Must provide at least one --ensemble/--answers/--label triple"
        )

    if not (len(ensembles) == len(answers_list) == len(labels)):
        raise SystemExit(
            f"Mismatched counts: {len(ensembles)} ensembles, "
            f"{len(answers_list)} answers, {len(labels)} labels"
        )

    triples = [
        SurveyTriple(e, a, l)
        for e, a, l in zip(ensembles, answers_list, labels)
    ]

    # Process each triple
    survey_results = []
    for triple in triples:
        ensemble = load_json(triple.ensemble_path)
        answers = load_json(triple.answers_path)
        result = compute_agreement(ensemble, answers, triple.label)
        survey_results.append(result)

    # Compute aggregate statistics
    gap_values = [s["gap"] for s in survey_results]
    gap_hi = max(gap_values) if gap_values else 0.0
    gap_lo = min(gap_values) if gap_values else 0.0
    gap_mean = (
        sum(gap_values) / len(gap_values) if gap_values else 0.0
    )

    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    # Write markdown report
    md_path = output_dir / "calibration-agreement.md"
    md_content = render_markdown(survey_results)
    md_path.write_text(md_content, encoding="utf-8")

    # Write JSON report
    json_path = output_dir / "calibration-agreement.json"
    json_payload = {
        "surveys": survey_results,
        "gap_hi": round(gap_hi, 4),
        "gap_lo": round(gap_lo, 4),
        "gap_mean": round(gap_mean, 4),
        "calibration_note": (
            f"Cross-era gap (R9-Codex→R14-Claude). "
            f"gap_hi={gap_hi * 100:.1f}% used as conservative upper bound."
        ),
    }
    dump_json(str(json_path), json_payload)

    print(
        json.dumps(
            {
                "surveys": len(survey_results),
                "gap_hi": gap_hi,
                "gap_lo": gap_lo,
                "gap_mean": gap_mean,
                "md_output": str(md_path),
                "json_output": str(json_path),
            },
            ensure_ascii=False,
        )
    )


if __name__ == "__main__":
    main()
