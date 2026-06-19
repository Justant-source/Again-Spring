#!/usr/bin/env python3
"""
import_survey_answers.py — survey markdown의 정답/이유를 answers template json으로 반영

지원 포맷:
- h2h survey
- cond5 blind survey

예시:
  python3 import_survey_answers.py \
    --survey .result/ai-user/blind/r14-cond5-theqoo-survey.md \
    --answers .result/ai-user/blind/r14-cond5-theqoo-answers-template.json \
    --respondent owner
"""

import argparse
import json
import re
from pathlib import Path


PAIR_HEADER_RE = re.compile(r"^##\s+(\d+)번\s*$", re.MULTILINE)


def load_text(path: str) -> str:
    return Path(path).read_text(encoding="utf-8")


def load_json(path: str) -> dict:
    return json.loads(Path(path).read_text(encoding="utf-8"))


def dump_json(path: str, payload: dict) -> None:
    Path(path).write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def extract_pairs(text: str) -> list[tuple[int, str]]:
    matches = list(PAIR_HEADER_RE.finditer(text))
    pairs = []
    for idx, match in enumerate(matches):
        pair_num = int(match.group(1))
        start = match.end()
        end = matches[idx + 1].start() if idx + 1 < len(matches) else len(text)
        pairs.append((pair_num, text[start:end]))
    return pairs


def strip_md_label(s: str) -> str:
    return s.strip().strip("*").strip()


def extract_section(block: str, header: str, next_header: str | None) -> str:
    header_pat = re.escape(header)
    if next_header:
        next_pat = re.escape(next_header)
        pattern = re.compile(
            header_pat + r"\s*(.*?)\s*" + next_pat,
            re.DOTALL,
        )
    else:
        pattern = re.compile(header_pat + r"\s*(.*)", re.DOTALL)
    m = pattern.search(block)
    if not m:
        return ""
    return m.group(1).strip()


def normalize_choice(raw: str) -> str:
    raw = strip_md_label(raw).strip()
    if not raw:
        return ""
    first_line = raw.splitlines()[0].strip()
    if first_line.lower() in {"a", "b"}:
        return first_line.upper()
    if first_line in {"답변불가", "판단불가", "미응답"}:
        return first_line
    return first_line


def normalize_reason(raw: str) -> str:
    lines = [line.rstrip() for line in raw.splitlines()]
    while lines and not lines[0].strip():
        lines.pop(0)
    while lines and not lines[-1].strip():
        lines.pop()
    return "\n".join(lines).strip()


def parse_survey(survey_text: str) -> dict[str, dict[str, str]]:
    parsed = {}
    for pair_num, block in extract_pairs(survey_text):
        answer_raw = extract_section(block, "**정답:**", "**이유:**")
        reason_raw = extract_section(block, "**이유:**", "---")
        choice = normalize_choice(answer_raw)
        reason = normalize_reason(reason_raw)
        if choice or reason:
            parsed[str(pair_num)] = {
                "choice": choice or "미응답",
                "reason": reason,
            }
    return parsed


def merge_answers(payload: dict, respondent: str, parsed: dict[str, dict[str, str]], overwrite: bool) -> tuple[int, int]:
    responses = payload.setdefault("responses", {})
    target = responses.setdefault(respondent, {})
    imported = 0
    skipped = 0
    for pair_num, item in parsed.items():
        if not overwrite and str(pair_num) in target:
            skipped += 1
            continue
        target[str(pair_num)] = item
        imported += 1
    return imported, skipped


def main():
    parser = argparse.ArgumentParser(description="Import markdown survey answers into template json")
    parser.add_argument("--survey", required=True, help="Survey markdown path")
    parser.add_argument("--answers", required=True, help="Answers template json path")
    parser.add_argument("--respondent", choices=["owner", "friend"], required=True)
    parser.add_argument("--overwrite", action="store_true", help="Overwrite existing respondent entries")
    args = parser.parse_args()

    survey_text = load_text(args.survey)
    payload = load_json(args.answers)
    parsed = parse_survey(survey_text)
    imported, skipped = merge_answers(payload, args.respondent, parsed, args.overwrite)
    dump_json(args.answers, payload)
    print(json.dumps({
        "survey": args.survey,
        "answers": args.answers,
        "respondent": args.respondent,
        "pairs_detected": len(extract_pairs(survey_text)),
        "pairs_parsed": len(parsed),
        "imported": imported,
        "skipped": skipped,
    }, ensure_ascii=False))


if __name__ == "__main__":
    main()
