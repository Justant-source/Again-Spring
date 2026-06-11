#!/usr/bin/env python3
"""ai-user LLM API 사용량·캐싱 리포트 (캐싱 P2).

llm 컨테이너 로그의 `API usage:` 라인을 파싱해 일별·모델별로 집계:
  호출 수 · input · cache_read · cache_write · output · 히트율 · 과금등가 input 토큰

과금등가 = input + 0.1×cache_read + write_mult×cache_write  (5m TTL write=1.25배, 1h=2배)
절감률  = 1 − 과금등가 / (input + cache_read + cache_write)   ← 캐싱이 없었다면 전부 정가

사용:
  python3 ai-user/tools/api-usage-report.py                          # dev 컨테이너 전체 로그
  python3 ai-user/tools/api-usage-report.py --container againspring-llm-ai-user-prod --since 24h
  docker logs againspring-llm-ai-user 2>&1 | python3 ai-user/tools/api-usage-report.py --stdin
"""
import argparse
import re
import subprocess
import sys
from collections import defaultdict

LINE = re.compile(
    r"^(?P<date>\d{4}-\d{2}-\d{2})T.*API usage: "
    r"model=(?P<model>\S+) (?:stop=\S+ )?input=(?P<input>\d+) output=(?P<output>\d+) "
    r"cache_read=(?P<read>\d+) cache_write=(?P<write>\d+)")
# model= 필드 도입(2026-06-11) 이전 구형 라인도 집계
LINE_OLD = re.compile(
    r"^(?P<date>\d{4}-\d{2}-\d{2})T.*API usage: "
    r"input=(?P<input>\d+) output=(?P<output>\d+) "
    r"cache_read=(?P<read>\d+) cache_write=(?P<write>\d+)")


def parse(lines, agg):
    for line in lines:
        m = LINE.match(line) or LINE_OLD.match(line)
        if not m:
            continue
        d = m.groupdict()
        key = (d["date"], d.get("model", "(unknown)"))
        a = agg[key]
        a["calls"] += 1
        for f in ("input", "output", "read", "write"):
            a[f] += int(d[f])


def main():
    ap = argparse.ArgumentParser(description="ai-user API 사용량·캐싱 리포트")
    ap.add_argument("--container", default="againspring-llm-ai-user")
    ap.add_argument("--since", default=None, help="docker logs --since (예: 24h, 2026-06-11T00:00:00)")
    ap.add_argument("--stdin", action="store_true", help="stdin에서 로그 읽기")
    ap.add_argument("--write-mult", type=float, default=1.25,
                    help="cache_write 과금 배수 (5m TTL=1.25, 1h TTL=2.0)")
    args = ap.parse_args()

    if args.stdin:
        lines = sys.stdin
    else:
        cmd = ["docker", "logs", args.container]
        if args.since:
            cmd += ["--since", args.since]
        proc = subprocess.run(cmd, capture_output=True, text=True)
        lines = (proc.stdout + proc.stderr).splitlines()

    agg = defaultdict(lambda: defaultdict(int))
    parse(lines, agg)
    if not agg:
        sys.exit("API usage 라인 없음 (API 경로 미사용이거나 로그 롤오버)")

    print(f"{'날짜':<11} {'모델':<28} {'호출':>5} {'input':>9} {'c_read':>9} {'c_write':>8} "
          f"{'output':>7} {'히트율':>6} {'과금등가':>10} {'절감':>5}")
    tot = defaultdict(int)
    for (date, model), a in sorted(agg.items()):
        full = a["input"] + a["read"] + a["write"]
        billed = a["input"] + 0.1 * a["read"] + args.write_mult * a["write"]
        hit = a["read"] / full * 100 if full else 0
        saving = (1 - billed / full) * 100 if full else 0
        print(f"{date:<11} {model:<28} {a['calls']:>5} {a['input']:>9,} {a['read']:>9,} "
              f"{a['write']:>8,} {a['output']:>7,} {hit:>5.0f}% {billed:>10,.0f} {saving:>4.0f}%")
        for k in ("calls", "input", "read", "write", "output"):
            tot[k] += a[k]
    full = tot["input"] + tot["read"] + tot["write"]
    billed = tot["input"] + 0.1 * tot["read"] + args.write_mult * tot["write"]
    print("-" * 105)
    print(f"{'합계':<40} {tot['calls']:>5} {tot['input']:>9,} {tot['read']:>9,} {tot['write']:>8,} "
          f"{tot['output']:>7,} {tot['read'] / full * 100 if full else 0:>5.0f}% "
          f"{billed:>10,.0f} {(1 - billed / full) * 100 if full else 0:>4.0f}%")


if __name__ == "__main__":
    main()
