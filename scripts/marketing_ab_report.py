#!/usr/bin/env python3
"""YouTube Shorts 훅 A/B 리포트 — 코호트(훅 규칙 v1/v2 x 렌더 프로필)별 성과 비교.

데이터 원천: WSL ASM DB (ssh justant@100.115.252.61 -> 컨테이너 again-spring-marketing-asm-db-1).
  - 계정 비밀번호는 컨테이너 env(MARIADB_ROOT_PASSWORD)를 컨테이너 안에서만 참조한다.
    이 스크립트는 비밀번호를 읽지도, 출력하지도, 저장하지도 않는다.
  - 원격 임시 스크립트는 scp 로 올려 실행하고 즉시 삭제한다.

비교 규칙(필수):
  - 같은 일령 비교. 게시(publication.updated_at, PUBLISHED) 후 N시간 부근 스냅샷끼리만 비교한다.
    각 잡에서 [N*(1-tol), N] 시간 구간의 마지막 스냅샷 1개만 사용한다(기본 N=24h, tol=0.25).
    누적 조회수를 일령이 다른 잡끼리 비교하지 않는다(메모리 project_v2_render_profile_tradeoff).
  - 코호트 표본 < 10 이면 결론을 내지 않고 "표본 부족"으로 표시한다. v2 표본이 0 이면 "v2 표본 0".
  - hook_rule 은 ASM job.brief_json 최상위 `hook_rule` 키. 없으면 v1.
  - render_profile 은 job.options_json 의 `render_profile`. 없으면 (미지정).
  - brief_json 에 한글이 \\uXXXX 로 이중 이스케이프돼 있을 수 있으므로 문자열은 decode_korean() 으로 복원한다
    (hook_rule/render_profile 은 ASCII 라 영향 없음. --list 의 제목 표시에 사용).

사용:
  python3 scripts/marketing_ab_report.py                 # 24h 일령 기준
  python3 scripts/marketing_ab_report.py --age-hours 48
  python3 scripts/marketing_ab_report.py --list          # 잡 단위 행도 출력
  python3 scripts/marketing_ab_report.py --since 2026-10-01   # 이 날짜(UTC) 이후 생성 잡만

주의: 가설 검정이 아니라 기술 통계다. 판정은 사용자의 /admin/marketing 테스트 탭 시청 + 이 표를 함께 본다.
"""
from __future__ import annotations

import argparse
import csv
import io
import math
import re
import statistics
import subprocess
import sys
import tempfile
import os
from collections import defaultdict
from typing import Optional

SSH_HOST = "justant@100.115.252.61"
DB_CONTAINER = "again-spring-marketing-asm-db-1"
MIN_COHORT = 10  # 코호트당 최소 표본
VIEW_TARGET = 1000

FIELDS = ["job_id", "hook_rule", "render_profile", "age_min", "views", "likes", "comments",
          "avg_view_duration_sec", "title"]


# --------------------------------------------------------------------------- 한글 디코드

_U_ESC = re.compile(r"\\+u([0-9a-fA-F]{4})")


def decode_korean(text: Optional[str]) -> str:
    """\\uXXXX / \\\\uXXXX (이중 이스케이프) 를 실제 문자로 복원한다. surrogate pair 도 처리."""
    if not text:
        return ""
    if "\\u" not in text:
        return text

    def _sub(m: "re.Match[str]") -> str:
        return chr(int(m.group(1), 16))

    out = _U_ESC.sub(_sub, text)
    # surrogate pair(이모지 등) 결합
    try:
        out = out.encode("utf-16", "surrogatepass").decode("utf-16")
    except UnicodeError:
        pass
    return out


# --------------------------------------------------------------------------- 조회

def build_sql(age_hours: float, tol: float, since: Optional[str]) -> str:
    lo = int(age_hours * (1 - tol) * 60)
    hi = int(age_hours * 60)
    since_clause = f"AND j.created_at >= '{since}'" if since else ""
    # 잡마다 [lo, hi] 분 구간의 마지막 스냅샷 1개. partial/에러 스냅샷 및 views NULL 제외.
    return f"""
SELECT j.id,
       COALESCE(JSON_VALUE(j.brief_json, '$.hook_rule'), 'v1'),
       COALESCE(JSON_VALUE(j.options_json, '$.render_profile'), '(미지정)'),
       TIMESTAMPDIFF(MINUTE, REPLACE(LEFT(p.updated_at, 19), 'T', ' '), s.collected_at),
       JSON_VALUE(s.metrics_json, '$.views'),
       COALESCE(JSON_VALUE(s.metrics_json, '$.likes'), 0),
       COALESCE(JSON_VALUE(s.metrics_json, '$.comments'), 0),
       JSON_VALUE(s.metrics_json, '$.avg_view_duration_sec'),
       LEFT(JSON_VALUE(j.brief_json, '$.title'), 300)
FROM publication p
JOIN job j ON j.id = p.job_id
JOIN publication_stat s ON s.job_id = p.job_id AND s.platform = p.platform
WHERE p.platform = 'youtube_shorts' AND p.state = 'PUBLISHED'
  AND COALESCE(s.partial, 0) = 0 AND s.error IS NULL
  AND JSON_VALUE(s.metrics_json, '$.views') IS NOT NULL
  AND TIMESTAMPDIFF(MINUTE, REPLACE(LEFT(p.updated_at, 19), 'T', ' '), s.collected_at) BETWEEN {lo} AND {hi}
  {since_clause}
  AND s.id = (
      SELECT MAX(s2.id) FROM publication_stat s2
      WHERE s2.job_id = p.job_id AND s2.platform = p.platform
        AND COALESCE(s2.partial, 0) = 0 AND s2.error IS NULL
        AND JSON_VALUE(s2.metrics_json, '$.views') IS NOT NULL
        AND TIMESTAMPDIFF(MINUTE, REPLACE(LEFT(p.updated_at, 19), 'T', ' '), s2.collected_at) BETWEEN {lo} AND {hi})
ORDER BY j.created_at;
""".strip()


def fetch_rows(sql: str) -> list[dict]:
    """SQL 을 원격 임시 스크립트로 올려 컨테이너의 mariadb 로 실행한다(비밀번호는 원격 env 에서만 사용)."""
    # 원격 스크립트: SQL 은 here-doc 이 아니라 별도 파일로 올려 인용 문제를 피한다.
    remote_sh = (
        "#!/bin/sh\n"
        'f="$1"\n'
        f"docker exec -i {DB_CONTAINER} sh -c "
        "'mariadb -uroot -p\"$MARIADB_ROOT_PASSWORD\" asm --batch --raw --skip-column-names' < \"$f\"\n"
    )
    with tempfile.TemporaryDirectory() as td:
        sh_path = os.path.join(td, "ab_run.sh")
        sql_path = os.path.join(td, "ab_query.sql")
        with open(sh_path, "w", encoding="utf-8") as f:
            f.write(remote_sh)
        with open(sql_path, "w", encoding="utf-8") as f:
            f.write(sql + "\n")
        subprocess.run(["scp", "-q", sh_path, sql_path, f"{SSH_HOST}:/tmp/"], check=True)
    try:
        res = subprocess.run(
            ["ssh", SSH_HOST, "sh /tmp/ab_run.sh /tmp/ab_query.sql"],
            check=True, capture_output=True, text=True, timeout=120,
        )
    finally:
        subprocess.run(["ssh", SSH_HOST, "rm -f /tmp/ab_run.sh /tmp/ab_query.sql"],
                       capture_output=True, text=True)
    rows = []
    for rec in csv.reader(io.StringIO(res.stdout), delimiter="\t", quoting=csv.QUOTE_NONE):
        if len(rec) < len(FIELDS):
            continue
        d = dict(zip(FIELDS, rec))
        rows.append(d)
    return rows


# --------------------------------------------------------------------------- 통계

def _num(v: Optional[str]) -> Optional[float]:
    if v is None or v in ("", "NULL"):
        return None
    try:
        return float(v)
    except ValueError:
        return None


def quantile(sorted_vals: list[float], q: float) -> float:
    """선형 보간 분위수."""
    if not sorted_vals:
        return float("nan")
    if len(sorted_vals) == 1:
        return sorted_vals[0]
    pos = (len(sorted_vals) - 1) * q
    lo, hi = int(math.floor(pos)), int(math.ceil(pos))
    return sorted_vals[lo] + (sorted_vals[hi] - sorted_vals[lo]) * (pos - lo)


def mann_whitney_p(a: list[float], b: list[float]) -> float:
    """양측 Mann-Whitney U (정규 근사, 동점 보정 생략). n>=10 일 때만 참고용."""
    n1, n2 = len(a), len(b)
    allv = sorted([(v, 0) for v in a] + [(v, 1) for v in b])
    ranks = [0.0] * len(allv)
    i = 0
    while i < len(allv):
        j = i
        while j + 1 < len(allv) and allv[j + 1][0] == allv[i][0]:
            j += 1
        avg = (i + j) / 2 + 1
        for k in range(i, j + 1):
            ranks[k] = avg
        i = j + 1
    r1 = sum(r for r, (_, g) in zip(ranks, allv) if g == 0)
    u1 = r1 - n1 * (n1 + 1) / 2
    mu = n1 * n2 / 2
    sigma = math.sqrt(n1 * n2 * (n1 + n2 + 1) / 12)
    if sigma == 0:
        return 1.0
    z = (u1 - mu) / sigma
    return math.erfc(abs(z) / math.sqrt(2))


def fmt(v: float, nd: int = 0) -> str:
    return "-" if v is None or (isinstance(v, float) and math.isnan(v)) else f"{v:,.{nd}f}"


def summarize(rows: list[dict]) -> dict:
    views = sorted(x for x in (_num(r["views"]) for r in rows) if x is not None)
    likes = [_num(r["likes"]) or 0.0 for r in rows]
    comments = [_num(r["comments"]) or 0.0 for r in rows]
    durs = [x for x in (_num(r["avg_view_duration_sec"]) for r in rows) if x is not None]
    n = len(views)
    return {
        "n": n,
        "views": views,
        "med": statistics.median(views) if views else float("nan"),
        "p25": quantile(views, 0.25),
        "p75": quantile(views, 0.75),
        "ge1k": (sum(1 for v in views if v >= VIEW_TARGET) / n) if n else float("nan"),
        "likes_med": statistics.median(likes) if likes else float("nan"),
        "comments_med": statistics.median(comments) if comments else float("nan"),
        "dur_med": statistics.median(durs) if durs else float("nan"),
    }


def print_table(title: str, groups: dict[str, list[dict]]) -> dict[str, dict]:
    print(f"\n## {title}")
    header = (f"{'코호트':<34}{'n':>5}{'조회 중앙':>10}{'p25':>8}{'p75':>8}"
              f"{'>=1,000':>9}{'좋아요 중앙':>11}{'댓글 중앙':>9}{'평균시청(s)':>11}  비고")
    print(header)
    print("-" * len(header))
    stats = {}
    for key in sorted(groups):
        s = summarize(groups[key])
        stats[key] = s
        note = "표본 0" if s["n"] == 0 else ("표본 부족" if s["n"] < MIN_COHORT else "")
        ge = "-" if math.isnan(s["ge1k"]) else f"{s['ge1k'] * 100:.0f}%"
        print(f"{key:<34}{s['n']:>5}{fmt(s['med']):>10}{fmt(s['p25']):>8}{fmt(s['p75']):>8}"
              f"{ge:>9}{fmt(s['likes_med'], 1):>11}{fmt(s['comments_med'], 1):>9}"
              f"{fmt(s['dur_med'], 1):>11}  {note}")
    return stats


def verdict(a_name: str, b_name: str, a: Optional[dict], b: Optional[dict]) -> str:
    """b(실험) 를 a(기준) 와 비교. 표본이 모자라면 결론을 내지 않는다."""
    if not b or b["n"] == 0:
        return f"{b_name} 표본 0 — 비교 불가 (아직 해당 코호트 잡이 이 일령 구간에 없음)"
    if not a or a["n"] == 0:
        return f"{a_name} 표본 0 — 비교 불가"
    if a["n"] < MIN_COHORT or b["n"] < MIN_COHORT:
        return (f"표본 부족 ({a_name} n={a['n']}, {b_name} n={b['n']}; 코호트당 {MIN_COHORT} 미만) "
                f"— 결론 보류")
    ratio = b["med"] / a["med"] if a["med"] else float("nan")
    p = mann_whitney_p(a["views"], b["views"])
    sig = "유의(p<0.05)" if p < 0.05 else "유의하지 않음"
    return (f"{b_name} 조회 중앙값 / {a_name} = {ratio:.2f}배 "
            f"(Mann-Whitney p={p:.3f}, {sig}; 참고용 근사)")


# --------------------------------------------------------------------------- main

def main() -> int:
    ap = argparse.ArgumentParser(description="YouTube Shorts 훅 규칙/렌더 프로필 A/B 리포트")
    ap.add_argument("--age-hours", type=float, default=24.0, help="비교 일령(게시 후 N시간). 기본 24")
    ap.add_argument("--tol", type=float, default=0.25,
                    help="허용 구간 비율: [N*(1-tol), N] 시간의 마지막 스냅샷 사용. 기본 0.25")
    ap.add_argument("--since", help="이 날짜(YYYY-MM-DD, UTC) 이후 생성 잡만")
    ap.add_argument("--list", action="store_true", help="잡 단위 행도 출력")
    args = ap.parse_args()

    if args.since and not re.fullmatch(r"\d{4}-\d{2}-\d{2}", args.since):
        print("--since 는 YYYY-MM-DD 형식이어야 합니다", file=sys.stderr)
        return 2

    rows = fetch_rows(build_sql(args.age_hours, args.tol, args.since))
    lo_h = args.age_hours * (1 - args.tol)
    print(f"# YouTube Shorts A/B 리포트 — 게시 후 {lo_h:.1f}~{args.age_hours:.0f}시간 구간의 마지막 스냅샷 기준 "
          f"(잡당 1개, 같은 일령)")
    print(f"대상 잡 {len(rows)}개 / 코호트당 최소 표본 {MIN_COHORT}")
    if not rows:
        print("해당 일령 구간에 스냅샷이 있는 잡이 없습니다.")
        return 0

    by_hook: dict[str, list[dict]] = defaultdict(list)
    by_profile: dict[str, list[dict]] = defaultdict(list)
    by_both: dict[str, list[dict]] = defaultdict(list)
    for r in rows:
        by_hook[f"hook_rule={r['hook_rule']}"].append(r)
        by_profile[f"profile={r['render_profile']}"].append(r)
        by_both[f"{r['hook_rule']} / {r['render_profile']}"].append(r)
    # v2 가 비어 있어도 항상 행을 보여준다.
    by_hook.setdefault("hook_rule=v2", [])

    hook_stats = print_table("훅 규칙별", by_hook)
    print_table("렌더 프로필별", by_profile)
    print_table("훅 규칙 x 렌더 프로필", by_both)

    print("\n## 판정 (훅 규칙 v1 -> v2, 같은 일령)")
    print("- " + verdict("v1", "v2", hook_stats.get("hook_rule=v1"), hook_stats.get("hook_rule=v2")))

    # 같은 렌더 프로필 안에서의 v1/v2 비교(렌더 프로필 교란 제거)
    profiles = sorted({r["render_profile"] for r in rows})
    for prof in profiles:
        a = summarize([r for r in rows if r["render_profile"] == prof and r["hook_rule"] == "v1"])
        b = summarize([r for r in rows if r["render_profile"] == prof and r["hook_rule"] == "v2"])
        print(f"- [{prof}] " + verdict("v1", "v2", a, b))

    print("\n## 판정 (렌더 프로필 간, 기준=marketing_v2, 훅 규칙 무관)")
    base = summarize([r for r in rows if r["render_profile"] == "marketing_v2"])
    for prof in profiles:
        if prof == "marketing_v2":
            continue
        s = summarize([r for r in rows if r["render_profile"] == prof])
        print(f"- [{prof}] " + verdict("marketing_v2", prof, base, s))

    if args.list:
        print("\n## 잡 단위")
        print(f"{'job_id':<28}{'hook':<6}{'profile':<18}{'age(h)':>7}{'views':>8}{'like':>6}{'cmt':>5}  제목")
        for r in rows:
            age_h = (_num(r["age_min"]) or 0) / 60
            print(f"{r['job_id']:<28}{r['hook_rule']:<6}{r['render_profile']:<18}{age_h:>7.1f}"
                  f"{fmt(_num(r['views'])):>8}{fmt(_num(r['likes'])):>6}{fmt(_num(r['comments'])):>5}  "
                  f"{decode_korean(r['title'])[:30]}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
