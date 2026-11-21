#!/usr/bin/env python3
"""실제 글 vs 생성 글 블라인드 판별 테스트 생성기 / 채점기 (표준 라이브러리만, prod DB SELECT 전용).

생성:
  python3 scripts/story_turing_test.py --since 2026-10-21 --n 20 --comments 20 [--seed 1] [--out DIR]
채점 (test.md에 운영자가 표시한 뒤):
  python3 scripts/story_turing_test.py --score DIR

* 출력 기본 위치: .temp/story-turing-test/<오늘날짜>/  (test.md, answer-key.json)
* answer-key.json 은 채점 전에 열지 말 것.
* DB: `docker exec -i againspring-mariadb-prod mariadb` — 비밀번호는 env/.env.prod 의
  MARIADB_PASSWORD 를 읽어 자식 프로세스 환경변수로만 넘긴다(인자·화면·파일에 남기지 않음).
* 실제 글 기준(원문 품질)은 ai-user/learning/app/services/source_quality.py 의 assess_source 를
  같은 규칙으로 간단히 재현했다(광고·사이드바 상용구·중복행·미리보기 잘림·150자 미만 제외).
"""
from __future__ import annotations

import argparse
import datetime as dt
import json
import os
import random
import re
import subprocess
import sys
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ENV_FILE = ROOT / "env" / ".env.prod"
CONTAINER = "againspring-mariadb-prod"

# ───────────────────────── DB (SELECT 전용) ─────────────────────────


def _read_env(key: str, default: str | None = None) -> str:
    try:
        for line in ENV_FILE.read_text(encoding="utf-8").splitlines():
            if line.startswith(key + "="):
                return line.split("=", 1)[1].strip().strip("'\"")
    except OSError:
        pass
    if default is None:
        sys.exit(f"{ENV_FILE} 에서 {key} 를 읽을 수 없다")
    return default


_ESC = {"n": "\n", "t": "\t", "0": "\0", "\\": "\\", "r": "\r"}


def _unescape(v: str) -> str | None:
    if v == "NULL":
        return None
    return re.sub(r"\\(.)", lambda m: _ESC.get(m.group(1), m.group(1)), v)


def query(sql: str) -> list[list[str | None]]:
    if not re.match(r"\s*select\b", sql, re.I):
        sys.exit("SELECT 만 허용된다")
    env = dict(os.environ, MYSQL_PWD=_read_env("MARIADB_PASSWORD"))
    cmd = [
        "docker", "exec", "-i", "-e", "MYSQL_PWD", CONTAINER, "mariadb",
        "-u", _read_env("MARIADB_USER", "againspring"), "-N", "-B",
        "--default-character-set=utf8mb4", _read_env("MARIADB_DATABASE", "againspring_prod"),
    ]
    r = subprocess.run(cmd, input=sql, capture_output=True, text=True, env=env)
    if r.returncode != 0:
        err = r.stderr.strip().splitlines()
        sys.exit("DB 조회 실패: " + (err[-1][:200] if err else "알 수 없음"))
    return [[_unescape(c) for c in line.split("\t")] for line in r.stdout.split("\n") if line != ""]


# ───────────────────────── source_quality 재현 ─────────────────────────

_ELLIPSIS_END = re.compile(r"(\.\.\.|…)\s*$")
_AD_LINE = re.compile(r"^\(?광고\)")
_BOILER = re.compile("|".join([
    r"소개팅하러 가기", r"가연\S*\s*(?:에서\s*)?(?:솔로 탈출|미팅파티)", r"무료 가연 미팅파티",
    r"쿠팡 특가", r"로켓프레시", r"쿠팡판매1위", r"단 하루 특가", r"브랜드관에서",
    r"지금\s*예매하기", r"직장인 맞춤 DB", r"셀소 주간베스트", r"^작성자가 삭제한 댓글입니다",
]))
_COMPANY = re.compile(r"^\S.{0,30}\s·\s*$")
MIN_CHARS = 150


def source_ok(content: str, source: str) -> bool:
    text = (content or "").strip()
    if len(text) < MIN_CHARS:
        return False
    lines = [ln.strip() for ln in text.split("\n") if ln.strip()]
    if source.lower() == "blind" and "\n" not in text.rstrip():
        if _ELLIPSIS_END.search(text) and 245 <= len(text) <= 262:
            return False
    if any(_AD_LINE.match(ln) for ln in lines):
        return False
    if any(_BOILER.search(ln) or (_COMPANY.match(ln) and len(lines) > 1) for ln in lines):
        return False
    cnt = Counter(ln for ln in lines if len(ln) >= 8)
    dups = [ln for ln, n in cnt.items() if n > 1]
    if dups and (len(dups) >= 2 or sum(len(x) for x in dups) >= 0.25 * max(1, len(text))):
        return False
    return True


_BOARD_PREFIX = re.compile(r"^[^:\n]{1,10}:\s+")  # "썸·연애: " 같은 블라인드 게시판 접두어


def clean_title(t: str) -> str:
    return _BOARD_PREFIX.sub("", (t or "").strip(), count=1)


# ───────────────────────── 생성 ─────────────────────────


def sql_quote(s: str) -> str:
    if not re.fullmatch(r"[0-9\-]+", s):
        sys.exit("--since 형식 오류 (YYYY-MM-DD)")
    return s


def load_ai_posts(since: str) -> list[dict]:
    rows = query(
        "select p.id, p.title, p.body_published, p.category, p.source_community, p.source_example_id "
        "from posts p join users u on u.id = p.author_id "
        f"where u.synthetic = 1 and p.deleted_at is null and p.created_at >= '{sql_quote(since)}' "
        "and p.body_published is not null and char_length(p.body_published) >= 100 order by p.id"
    )
    return [dict(id=r[0], title=r[1] or "", body=r[2] or "", cat=r[3], comm=r[4], src_ex=r[5]) for r in rows]


def load_real_posts() -> list[dict]:
    rows = query(
        "select e.id, e.source, e.title, e.content, e.category, e.source_url from example_bank e "
        "where e.content_type = 'POST' and e.source in ('blind','natepan') and e.title is not null "
        "and char_length(e.content) between 150 and 2500 "
        "and e.id not in (select source_example_id from posts where source_example_id is not null) "
        "order by e.id"
    )
    used_urls = {r[0] for r in query("select source_url from posts where source_url is not null")}
    out = []
    for r in rows:
        if r[5] and r[5] in used_urls:
            continue
        if source_ok(r[3] or "", r[1] or ""):
            out.append(dict(id=r[0], source=r[1], title=clean_title(r[2] or ""), body=(r[3] or "").strip(), cat=r[4]))
    return [x for x in out if x["title"]]


def load_ai_comments(since: str) -> list[dict]:
    rows = query(
        "select c.id, c.post_id, c.body from post_comments c join users u on u.id = c.author_id "
        f"where u.synthetic = 1 and c.deleted_at is null and c.created_at >= '{sql_quote(since)}' "
        "and char_length(c.body) between 8 and 400 order by c.id"
    )
    return [dict(id=r[0], post_id=r[1], body=r[2] or "") for r in rows]


def load_real_comments() -> list[dict]:
    rows = query(
        "select id, source, content from example_bank where content_type = 'COMMENT' "
        "and source in ('blind','natepan') and char_length(content) between 8 and 400 order by id"
    )
    out = []
    for r in rows:
        t = (r[2] or "").strip()
        if _AD_LINE.search(t) or _BOILER.search(t):
            continue
        out.append(dict(id=r[0], source=r[1], body=t))
    return out


def match_by_length(rng: random.Random, targets: list[dict], pool: list[dict], len_of, prefer=None) -> list[dict]:
    """각 target 길이에 가장 가까운(±35% 우선) 미사용 후보를 고른다. prefer(t, c)=True 인 후보 우선."""
    pool = pool[:]
    rng.shuffle(pool)
    used: set[int] = set()
    picked = []
    for t in targets:
        tl = max(1, len_of(t))
        best, best_key = None, None
        for i, c in enumerate(pool):
            if i in used:
                continue
            ratio = abs(len_of(c) - tl) / tl
            pref = 0 if (prefer and prefer(t, c)) else 1
            key = (0 if ratio <= 0.35 else 1, pref, ratio if ratio > 0.35 else 0, i)
            if best_key is None or key < best_key:
                best, best_key = i, key
        if best is None:
            break
        used.add(best)
        picked.append(pool[best])
    return picked


def quote(text: str) -> str:
    return "\n".join(("> " + ln) if ln.strip() else ">" for ln in text.strip().split("\n"))


ANSWER_BLOCK = "판정: [ ] 사람 [ ] AI\n재미(1~5): "


def generate(args) -> None:
    rng = random.Random(args.seed)
    out = Path(args.out) if args.out else ROOT / ".temp" / "story-turing-test" / dt.date.today().isoformat()
    out.mkdir(parents=True, exist_ok=True)

    items: list[dict] = []
    # --- 글 ---
    n_ai = args.n // 2 if args.n else 0
    n_real = args.n - n_ai
    if args.n:
        ai = load_ai_posts(args.since)
        used_ex = {a["src_ex"] for a in ai if a["src_ex"]}
        if len(ai) < n_ai:
            print(f"경고: --since 이후 AI 사연 {len(ai)}건 < 필요 {n_ai}건. 있는 만큼만 쓴다.", file=sys.stderr)
        rng.shuffle(ai)
        ai = ai[:n_ai]
        real_pool = [r for r in load_real_posts() if str(r["id"]) not in {str(x) for x in used_ex}]
        real = match_by_length(
            rng, ai, real_pool, lambda x: len(x["body"]),
            prefer=lambda t, c: bool(t.get("cat")) and t["cat"] == c.get("cat"),
        )
        real = real[:n_real]
        for a in ai:
            items.append(dict(kind="post", truth="AI", text=f"**{a['title']}**\n\n{a['body'].strip()}",
                              ref={"post_id": a["id"], "category": a["cat"], "source_community": a["comm"]}))
        for r in real:
            items.append(dict(kind="post", truth="HUMAN", text=f"**{r['title']}**\n\n{r['body']}",
                              ref={"example_id": r["id"], "source": r["source"], "category": r["cat"]}))
    # --- 댓글 ---
    if args.comments:
        c_ai_n = args.comments // 2
        c_real_n = args.comments - c_ai_n
        cai = load_ai_comments(args.since)
        rng.shuffle(cai)
        cai = cai[:c_ai_n]
        creal = match_by_length(rng, cai, load_real_comments(), lambda x: len(x["body"]))[:c_real_n]
        for a in cai:
            items.append(dict(kind="comment", truth="AI", text=a["body"], ref={"comment_id": a["id"], "post_id": a["post_id"]}))
        for r in creal:
            items.append(dict(kind="comment", truth="HUMAN", text=r["body"], ref={"example_id": r["id"], "source": r["source"]}))

    if not items:
        sys.exit("문항이 없다 (조건에 맞는 데이터 없음)")

    # 글 먼저, 댓글 나중. 각 구간 내부는 무작위 섞기
    posts = [i for i in items if i["kind"] == "post"]
    comments = [i for i in items if i["kind"] == "comment"]
    rng.shuffle(posts)
    rng.shuffle(comments)
    key: dict[str, dict] = {}
    md = [
        "# 실제 글 vs 생성 글 블라인드 테스트",
        "",
        f"- 생성일 {dt.date.today().isoformat()} · 생성 사연 기준일 {args.since} 이후 · seed {args.seed}",
        "- 각 문항마다 **사람이 쓴 글**인지 **AI가 쓴 글**인지 `[ ]` 중 하나에 `x`를 표시하고, 재미(1~5)를 숫자로 적는다.",
        "- `answer-key.json`은 채점 전에 열지 않는다. 채점: `python3 scripts/story_turing_test.py --score <이 폴더>`",
        "- 글/댓글 모두 절반은 AI, 절반은 실제 크롤 글(길이 비슷하게 맞춤). 게시판 접두어·출처는 가렸다.",
        "",
    ]
    for label, group, prefix in (("글", posts, "P"), ("댓글", comments, "C")):
        if not group:
            continue
        md += [f"# {label} 문항", ""]
        for i, it in enumerate(group, 1):
            qid = f"{prefix}{i:02d}"
            key[qid] = {"kind": it["kind"], "truth": it["truth"], **it["ref"]}
            md += [f"## {qid}", "", quote(it["text"]), "", ANSWER_BLOCK, "", "---", ""]
    (out / "test.md").write_text("\n".join(md), encoding="utf-8")
    kp = out / "answer-key.json"
    kp.write_text(json.dumps(key, ensure_ascii=False, indent=1), encoding="utf-8")
    try:
        os.chmod(kp, 0o600)
    except OSError:
        pass
    print(f"생성 완료: 글 {len(posts)}문항(AI {sum(1 for i in posts if i['truth']=='AI')}) · "
          f"댓글 {len(comments)}문항(AI {sum(1 for i in comments if i['truth']=='AI')}) -> {out}")


# ───────────────────────── 채점 ─────────────────────────

_ITEM = re.compile(r"^## ([PC]\d+)\s*$", re.M)
_HUMAN = re.compile(r"\[\s*[xXoO✓✔vV*]\s*\]\s*사람")
_AI = re.compile(r"\[\s*[xXoO✓✔vV*]\s*\]\s*AI", re.I)
_FUN = re.compile(r"재미\s*\(1~5\)\s*:\s*([1-5](?:\.\d+)?)")


def parse_answers(md: str) -> dict[str, dict]:
    parts = _ITEM.split(md)
    res = {}
    for qid, block in zip(parts[1::2], parts[2::2]):
        # 본문 인용(> ...) 줄은 제외하고 답안 줄만 본다
        ans = "\n".join(ln for ln in block.split("\n") if not ln.startswith(">"))
        h, a = bool(_HUMAN.search(ans)), bool(_AI.search(ans))
        guess = "HUMAN" if h and not a else "AI" if a and not h else None
        m = _FUN.search(ans)
        res[qid] = {"guess": guess, "fun": float(m.group(1)) if m else None}
    return res


def _pct(n: int, d: int) -> str:
    return f"{100 * n / d:.1f}% ({n}/{d})" if d else "표본 없음"


def _avg(v: list[float]) -> str:
    return f"{sum(v) / len(v):.2f} (n={len(v)})" if v else "표본 없음"


def score(dir_: str) -> None:
    d = Path(dir_)
    try:
        key = json.loads((d / "answer-key.json").read_text(encoding="utf-8"))
        ans = parse_answers((d / "test.md").read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as e:
        sys.exit(f"읽기 실패: {e}")
    print(f"채점 대상: {d}")
    answered_total = sum(1 for q in key if ans.get(q, {}).get("guess"))
    print(f"전체 {len(key)}문항 중 판정 표시 {answered_total}문항")
    for label, kind in (("글", "post"), ("댓글", "comment"), ("전체", None)):
        qs = [q for q, k in key.items() if kind is None or k["kind"] == kind]
        if not qs:
            continue
        ai_q = [q for q in qs if key[q]["truth"] == "AI" and ans.get(q, {}).get("guess")]
        hu_q = [q for q in qs if key[q]["truth"] == "HUMAN" and ans.get(q, {}).get("guess")]
        ai_hit = sum(1 for q in ai_q if ans[q]["guess"] == "AI")
        hu_as_ai = sum(1 for q in hu_q if ans[q]["guess"] == "AI")
        correct = ai_hit + sum(1 for q in hu_q if ans[q]["guess"] == "HUMAN")
        fun_ai = [ans[q]["fun"] for q in qs if key[q]["truth"] == "AI" and ans.get(q, {}).get("fun") is not None]
        fun_hu = [ans[q]["fun"] for q in qs if key[q]["truth"] == "HUMAN" and ans.get(q, {}).get("fun") is not None]
        print(f"\n[{label}] 판정한 문항 {len(ai_q) + len(hu_q)}")
        print(f"  AI 식별률(AI 글을 AI로 맞힘): {_pct(ai_hit, len(ai_q))}")
        print(f"  사람 글을 AI로 오인한 비율:   {_pct(hu_as_ai, len(hu_q))}")
        print(f"  전체 정답률:                  {_pct(correct, len(ai_q) + len(hu_q))}")
        print(f"  재미 평균  AI {_avg(fun_ai)} / 사람 {_avg(fun_hu)}")
    print(
        "\n해석: 정답률(식별률)이 50%이면 AI와 사람 글을 구분하지 못한다는 뜻(찍은 것과 같음)이다. "
        "AI 식별률이 높고 '사람 글을 AI로 오인한 비율'이 낮으면 생성 글이 티가 난다는 뜻. "
        "두 값이 비슷하게 높으면 '무엇이든 AI라고 찍는' 편향일 뿐이다. 문항 20개 안팎이면 "
        "±20%p 정도는 우연이므로 여러 회차를 누적해서 볼 것."
    )


def main() -> None:
    ap = argparse.ArgumentParser(description="실제 글 vs 생성 글 블라인드 테스트")
    ap.add_argument("--since", default=(dt.date.today() - dt.timedelta(days=30)).isoformat(),
                    help="이 날짜 이후 생성된 AI 사연·댓글만 (YYYY-MM-DD, 기본 30일 전)")
    ap.add_argument("--n", type=int, default=20, help="글 문항 수 (절반 AI)")
    ap.add_argument("--comments", type=int, default=20, help="댓글 문항 수 (절반 AI), 0이면 생략")
    ap.add_argument("--out", help="출력 디렉터리 (기본 .temp/story-turing-test/<오늘>/)")
    ap.add_argument("--seed", type=int, default=None, help="무작위 시드 (기본 무작위)")
    ap.add_argument("--score", metavar="DIR", help="DIR/test.md 의 표시를 DIR/answer-key.json 과 대조해 채점")
    args = ap.parse_args()
    if args.score:
        score(args.score)
        return
    if args.seed is None:
        args.seed = random.SystemRandom().randrange(1, 10**6)
    generate(args)


if __name__ == "__main__":
    main()
