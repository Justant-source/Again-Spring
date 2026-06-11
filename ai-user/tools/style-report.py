#!/usr/bin/env python3
"""AI 유저 문체 리포트 — 페르소나 히스토리에서 반복도·상투구·길이 분포를 측정.

용도: 문체 개선 작업의 before/after 비교 (stdlib only).

  python3 ai-user/tools/style-report.py                  # 사람용 요약
  python3 ai-user/tools/style-report.py --json out.json  # 기계용 덤프 (diff 비교)
  python3 ai-user/tools/style-report.py -n 30 --type comments

히스토리 포맷 (ActionExecutor.writeHistory 와 동기):
  comments.md: | ts | 댓글 | postId | preview |  +  "> 본문"  +  "---"
  posts.md:    | ts | cat | postId | preview | POSTED |  +  "### ts — cat" + 본문  +  "---"
"""
import argparse
import json
import re
import statistics
import sys
from collections import Counter
from pathlib import Path

# 상투 토큰 — 출현 "엔트리 비율"을 측정 (AI투 지표)
CLICHE_TOKENS = ["진짜", "정말", "공감", "ㅠ", "ㅋ", "근데", "어휴", "헐",
                 "힘내", "응원", "화이팅", "그거", "나도"]


def parse_entries(path: Path, kind: str) -> list[str]:
    """히스토리 파일 → 본문 리스트 (오래된 것 → 최신 순)."""
    try:
        raw = path.read_text(encoding="utf-8")
    except OSError:
        return []
    entries = []
    for block in raw.split("\n---"):
        block = block.strip()
        if not block:
            continue
        if kind == "comments":
            # 첫 "> " 이후 전체가 본문 (본문 내 개행은 > 접두사 없음)
            m = re.search(r"(?:^|\n)> ", block)
            if not m:
                continue
            body = block[m.end():].strip()
        else:  # posts
            m = re.search(r"(?:^|\n)### [^\n]*\n", block)
            if not m:
                continue
            body = block[m.end():].strip()
        if body:
            entries.append(body)
    return entries


def char_bigrams(text: str) -> set:
    t = re.sub(r"\s+", "", text)
    return {t[i:i + 2] for i in range(len(t) - 1)} if len(t) > 1 else set()


def jaccard(a: set, b: set) -> float:
    if not a or not b:
        return 0.0
    return len(a & b) / len(a | b)


def opener(text: str) -> str:
    return re.sub(r"\s+", " ", text.strip())[:8]


def first_word(text: str) -> str:
    parts = text.strip().split()
    return parts[0] if parts else ""


def percentile(values: list, p: float) -> float:
    if not values:
        return 0.0
    s = sorted(values)
    idx = min(int(len(s) * p), len(s) - 1)
    return float(s[idx])


def analyze(entries: list[str]) -> dict:
    n = len(entries)
    if n == 0:
        return {}
    openers = [opener(e) for e in entries]
    opener_counts = Counter(openers)
    dup_openers = sum(c for c in opener_counts.values() if c >= 2)
    fw_counts = Counter(first_word(e) for e in entries)

    cliche = {}
    for tok in CLICHE_TOKENS:
        hit = sum(1 for e in entries if tok in e)
        cliche[tok] = round(hit / n, 3)
    jinja_per_entry = statistics.mean(e.count("진짜") + e.count("정말") for e in entries)

    lengths = [len(e) for e in entries]
    grams = [char_bigrams(e) for e in entries]
    adj = [jaccard(grams[i], grams[i + 1]) for i in range(len(grams) - 1)]

    return {
        "entries": n,
        "opener_dup_rate": round(dup_openers / n, 3),
        "top_first_words": fw_counts.most_common(3),
        "cliche_rates": cliche,
        "jinja_jeongmal_per_entry": round(jinja_per_entry, 2),
        "len_p10": percentile(lengths, 0.10),
        "len_p50": percentile(lengths, 0.50),
        "len_p90": percentile(lengths, 0.90),
        "adj_jaccard_mean": round(statistics.mean(adj), 3) if adj else 0.0,
        "adj_jaccard_max": round(max(adj), 3) if adj else 0.0,
    }


def report_texts(texts: list[str], label: str):
    """단일 묶음 텍스트 리포트 — DB 추출분 등 외부 소스 비교용 (--texts-file)."""
    stats = analyze(texts)
    if not stats:
        print(f"[{label}] 표본 없음")
        return
    print(f"\n[{label}] 표본 {stats['entries']}건")
    print(f"  opener 중복률 {stats['opener_dup_rate']:.0%} · "
          f"진짜/정말 {stats['jinja_jeongmal_per_entry']}회/건 · "
          f"길이 p50 {stats['len_p50']:.0f}자 (p10 {stats['len_p10']:.0f} / p90 {stats['len_p90']:.0f}) · "
          f"인접 유사도 {stats['adj_jaccard_mean']:.3f}")
    top_cliche = sorted(stats["cliche_rates"].items(), key=lambda x: -x[1])[:8]
    print("  상투 토큰 출현률: " + " · ".join(f"{t} {r:.0%}" for t, r in top_cliche))
    print("  자주 쓰는 첫 단어: " + ", ".join(f"{w}({c})" for w, c in stats["top_first_words"]))


def main():
    ap = argparse.ArgumentParser(description="AI 유저 문체 리포트")
    ap.add_argument("--profiles", default=None, help="profiles 디렉토리 (기본: repo 추정 경로)")
    ap.add_argument("-n", "--recent", type=int, default=20, help="페르소나당 최근 N개 (기본 20)")
    ap.add_argument("--type", choices=["comments", "posts", "both"], default="both")
    ap.add_argument("--json", dest="json_out", default=None, help="JSON 덤프 경로")
    ap.add_argument("--texts-file", default=None,
                    help="한 줄=한 텍스트 파일을 단일 묶음으로 분석 (히스토리 대신 DB 추출분 비교용)")
    args = ap.parse_args()

    if args.texts_file:
        lines = [l.strip().replace("\\n", "\n") for l in
                 Path(args.texts_file).read_text(encoding="utf-8").splitlines() if l.strip()]
        report_texts(lines, args.texts_file)
        return

    base = Path(args.profiles) if args.profiles else \
        Path(__file__).resolve().parent.parent / "docs" / "personas" / "profiles"
    if not base.is_dir():
        sys.exit(f"profiles 디렉토리 없음: {base}")

    kinds = ["comments", "posts"] if args.type == "both" else [args.type]
    report = {"profiles_dir": str(base), "recent_n": args.recent, "kinds": {}}

    for kind in kinds:
        per_persona = {}
        all_recent = []
        for prof_dir in sorted(base.iterdir()):
            hist = prof_dir / "history" / f"{kind}.md"
            if not hist.is_file():
                continue
            recent = parse_entries(hist, kind)[-args.recent:]
            if not recent:
                continue
            stats = analyze(recent)
            per_persona[prof_dir.name] = stats
            all_recent.extend(recent)

        overall = analyze(all_recent[-2000:]) if all_recent else {}
        report["kinds"][kind] = {"overall": overall, "personas": per_persona}

        # ── 사람용 출력 ──
        print(f"\n{'=' * 62}\n[{kind}] 페르소나 {len(per_persona)}명, 표본 {len(all_recent)}건")
        if overall:
            print(f"  전체 — opener 중복률 {overall['opener_dup_rate']:.0%} · "
                  f"진짜/정말 {overall['jinja_jeongmal_per_entry']}회/건 · "
                  f"길이 p50 {overall['len_p50']:.0f}자 (p10 {overall['len_p10']:.0f} / p90 {overall['len_p90']:.0f})")
            top_cliche = sorted(overall["cliche_rates"].items(), key=lambda x: -x[1])[:6]
            print("  상투 토큰 출현률: " + " · ".join(f"{t} {r:.0%}" for t, r in top_cliche))
        worst = sorted(per_persona.items(),
                       key=lambda kv: -(kv[1]["opener_dup_rate"] + kv[1]["adj_jaccard_mean"]))[:8]
        print(f"  {'페르소나':<14} {'건수':>4} {'opener중복':>9} {'인접유사도':>9} {'진짜/정말':>8} {'p50길이':>7}")
        for name, s in worst:
            print(f"  {name:<14} {s['entries']:>4} {s['opener_dup_rate']:>9.0%} "
                  f"{s['adj_jaccard_mean']:>9.3f} {s['jinja_jeongmal_per_entry']:>8.2f} {s['len_p50']:>7.0f}")

    if args.json_out:
        Path(args.json_out).parent.mkdir(parents=True, exist_ok=True)
        Path(args.json_out).write_text(
            json.dumps(report, ensure_ascii=False, indent=1, default=str), encoding="utf-8")
        print(f"\nJSON 저장: {args.json_out}")


if __name__ == "__main__":
    main()
