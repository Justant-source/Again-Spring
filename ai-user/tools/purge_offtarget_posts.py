#!/usr/bin/env python3
"""AI-user 글 중 50대 이상(연령 이탈) 서사를 분류하고 soft-delete하는 일회성 도구.

배경: persona-diversity-v4 트랙(계약: `.request/persona-diversity-v4/00-shared.md`)에서
AI-user 페르소나 연령을 23~49세로 재구성한다. 기존 글 중 화자가 50대 이상으로 읽히는
서사(정년·손주·환갑 등)는 새 페르소나 연령 분포와 어긋나므로 정리 대상이다.

Usage:
  # 1) 분류만 — DB 쓰기 0, 결과를 stdout(JSONL)에 출력
  python3 ai-user/tools/purge_offtarget_posts.py --env-file env/.env.dev --classify > out.jsonl

  # 2) 분류 결과 파일로 저장
  python3 ai-user/tools/purge_offtarget_posts.py --env-file env/.env.dev --classify --out out.jsonl

  # 3) 표본만 (비용 절약) — 최대 100건
  python3 ai-user/tools/purge_offtarget_posts.py --env-file env/.env.dev --classify --limit 100

  # 4) OFF_TARGET만 soft delete (dev). prod는 --i-mean-it 없이 거부.
  python3 ai-user/tools/purge_offtarget_posts.py --env-file env/.env.dev --apply out.jsonl

분류기: AS 호스트 `claude -p --model claude-haiku-4-5-20251001 --output-format json
--disallowedTools '*'` — 이 도구에 한해 CLI 직접 호출이 예외적으로 허용된다
(`.request/persona-diversity-v4/04-wp4-cleanup-gates.md`). 20건씩 묶어 1회 호출.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import stat
import subprocess
import sys
import tempfile
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable

ROOT = Path(__file__).resolve().parents[2]

CONTAINERS = {
    "dev": "againspring-mariadb-dev",
    "prod": "againspring-mariadb-prod",
}

# prod 판정은 env_name(=--env-file 파일명 추정, 사용자 의도 라벨일 뿐)이 아니라 여기 값으로 한다.
# --db-container/--db-name은 --env-file과 독립적으로 덮어쓸 수 있어(env_name만 보면 우회 가능,
# 2026-09-05 보안 리뷰 실증: --env-file env/.env.dev --db-container againspring-mariadb-prod),
# 실제 쓰기 대상인 container·database를 직접 본다.
PROD_CONTAINERS = {"againspring-mariadb-prod"}
PROD_DATABASES = {"againspring_prod"}

DEFAULT_MODEL = "claude-haiku-4-5-20251001"
DEFAULT_BATCH_SIZE = 20
MAX_LLM_RETRIES = 3  # .claude/rules/llm-safety.md §4 — 수동 재시도 상한 3회

VALID_VERDICTS = {"OFF_TARGET", "ON_TARGET"}
_ID_RE = re.compile(r"^[A-Za-z0-9_]{1,64}$")

PROMPT_TEMPLATE = """각 글의 화자가 20~40대(23~49세)로 읽히는지 판정하라. 다음 중 하나라도 있으면 OFF_TARGET:
결혼 25년 이상·은퇴·정년·손주·자녀의 결혼/취업·환갑·노후 자금·"우리 때는"식 세대 서술·10대 학생 화자.
시부모·처가 갈등 자체는 OK(기혼 30대도 겪는다). 애매하면 ON_TARGET.
출력: [{{"id":"...","verdict":"OFF_TARGET|ON_TARGET","reason":"한 줄"}}] JSON만.

글 목록:
{items_json}
"""


@dataclass
class DbTarget:
    env_name: str  # "dev" | "prod"
    container: str
    user: str
    password: str
    database: str


class ClassifierError(RuntimeError):
    pass


def load_dotenv(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    if not path.exists():
        return values
    for line in path.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#") or "=" not in stripped:
            continue
        key, value = stripped.split("=", 1)
        values[key.strip()] = value.strip().strip('"').strip("'")
    return values


def infer_env_name(env_file: str) -> str:
    name = Path(env_file).name.lower()
    if "prod" in name:
        return "prod"
    if "dev" in name:
        return "dev"
    raise ValueError(
        f"env-file '{env_file}'에서 dev/prod를 추론할 수 없다. --env-name을 명시하라."
    )


def build_db_target(args: argparse.Namespace, env_values: dict[str, str]) -> DbTarget:
    env_name = args.env_name or infer_env_name(args.env_file)
    container = args.db_container or CONTAINERS.get(env_name)
    if not container:
        raise ValueError(f"알 수 없는 env-name: {env_name}")
    return DbTarget(
        env_name=env_name,
        container=container,
        user=args.db_user or env_values.get("MARIADB_USER", "againspring"),
        password=args.db_password or env_values.get("MARIADB_PASSWORD", ""),
        database=args.db_name or env_values.get("MARIADB_DATABASE", "againspring_dev"),
    )


def is_prod_target(target: DbTarget) -> bool:
    """실제 쓰기/조회 대상이 prod인지 판정한다. target.env_name(파일명 추정 라벨)은 보지 않는다 —
    --db-container/--db-name으로 독립적으로 덮어써질 수 있어 그것만 보면 가드를 우회당한다."""
    return target.container in PROD_CONTAINERS or target.database in PROD_DATABASES


def describe_target(target: DbTarget) -> str:
    """실행 시작 시 어느 환경을 실제로 건드리는지 출력하기 위한 문자열. 자격증명(user/password)은
    포함하지 않는다."""
    prod_marker = " [PROD]" if is_prod_target(target) else ""
    mismatch = ""
    if (target.env_name == "prod") != is_prod_target(target):
        mismatch = (
            " ⚠️ env-name(추정)과 실제 대상(container/database) 판정이 불일치한다 — "
            "--db-container/--db-name override 확인할 것"
        )
    return (
        f"env-name(추정)={target.env_name} container={target.container} "
        f"database={target.database}{prod_marker}{mismatch}"
    )


def _mysql_pwd_env_file(password: str) -> str:
    """`docker exec --env-file`에 넘길 임시 파일 하나를 만들어 경로를 반환한다.

    비밀번호를 `-p<password>`처럼 argv에 두면 호스트 `ps auxww`와 `docker top <container>`
    양쪽에 프로세스가 살아있는 동안 평문으로 남는다(2026-09 보안 리뷰). `docker exec -e
    MYSQL_PWD=...` 형태로 바꿔도 그 값 자체가 이 `docker` 클라이언트 프로세스의 argv에
    그대로 남아 호스트 `ps auxww`에서 여전히 보인다 — `-e`는 근본 해결이 아니다.
    `--env-file=<path>`는 argv에 **파일 경로만** 남기고 값 자체는 파일에서만 읽으므로
    ps/docker top 어느 쪽에도 노출되지 않는다. 파일은 0600으로 만들고 호출 직후 삭제한다.
    """
    fd, path = tempfile.mkstemp(prefix="mysql-pwd-")
    try:
        os.chmod(path, stat.S_IRUSR | stat.S_IWUSR)
        with os.fdopen(fd, "w") as f:
            f.write(f"MYSQL_PWD={password}\n")
    except BaseException:
        os.close(fd)
        os.unlink(path)
        raise
    return path


def run_mariadb_sql(target: DbTarget, sql: str) -> str:
    """docker exec로 mariadb 클라이언트를 호출한다 (prod는 호스트 포트가 없어 이 방식만 동작).

    `--raw`(`-r`) 필수: 이게 없으면 -B(batch) 모드 클라이언트가 출력 중 백슬래시를
    한 번 더 이스케이프해서(`\\n` → `\\\\n`) JSON_OBJECT가 만든 유효한 JSON 문자열
    (본문에 실제 줄바꿈·따옴표가 포함된 글)을 깨뜨린다(2026-09-05 실측, char 3695 파싱 실패).

    비밀번호는 argv(`-p<password>`)로 넘기지 않는다 — `_mysql_pwd_env_file` 참고.
    """
    pwd_file = _mysql_pwd_env_file(target.password)
    try:
        result = subprocess.run(
            [
                "docker",
                "exec",
                "-i",
                f"--env-file={pwd_file}",
                target.container,
                "mariadb",
                "--raw",
                "-u",
                target.user,
                target.database,
                "-N",
                "-B",
                "-e",
                sql,
            ],
            capture_output=True,
            text=True,
            timeout=60,
        )
    finally:
        os.unlink(pwd_file)
    if result.returncode != 0:
        raise RuntimeError(f"mariadb 실행 실패({target.container}): {result.stderr[:800]}")
    return result.stdout


def fetch_synthetic_posts(target: DbTarget, limit: int | None) -> list[dict[str, Any]]:
    """synthetic=1 작성자의 미삭제 글을 (id, title, body 앞 600자, author) JSON으로 가져온다."""
    limit_clause = f"LIMIT {int(limit)}" if limit else ""
    sql = (
        "SELECT JSON_ARRAYAGG(JSON_OBJECT("
        "'id', t.id, 'title', t.title, 'body', t.body, 'author', t.author"
        ")) FROM ("
        "SELECT p.id AS id, p.title AS title, LEFT(p.body_published, 600) AS body, "
        "u.nickname AS author "
        "FROM posts p JOIN users u ON u.id = p.author_id "
        "WHERE u.synthetic = 1 AND p.deleted_at IS NULL "
        f"ORDER BY p.created_at {limit_clause}"
        ") t;"
    )
    raw = run_mariadb_sql(target, sql).strip()
    if not raw or raw in ("NULL", "\\N"):
        return []
    return json.loads(raw)


def chunked(items: list[Any], size: int) -> Iterable[list[Any]]:
    for i in range(0, len(items), size):
        yield items[i : i + size]


def strip_code_fence(text: str) -> str:
    text = text.strip()
    text = re.sub(r"^```(?:json)?\s*", "", text)
    text = re.sub(r"\s*```$", "", text)
    return text.strip()


def extract_json_array(text: str) -> str:
    text = strip_code_fence(text)
    start = text.find("[")
    end = text.rfind("]")
    if start == -1 or end == -1 or end < start:
        raise ClassifierError(f"응답에서 JSON 배열을 찾지 못함: {text[:200]!r}")
    return text[start : end + 1]


def parse_classification_response(raw_text: str) -> list[dict[str, str]]:
    """claude -p --output-format json 의 result 필드(마크다운 펜스 포함 가능)를 파싱한다."""
    array_text = extract_json_array(raw_text)
    try:
        data = json.loads(array_text)
    except json.JSONDecodeError as exc:
        raise ClassifierError(f"JSON 파싱 실패: {exc}") from exc
    if not isinstance(data, list):
        raise ClassifierError("응답이 JSON 배열이 아님")
    out: list[dict[str, str]] = []
    for item in data:
        if not isinstance(item, dict):
            raise ClassifierError(f"배열 항목이 객체가 아님: {item!r}")
        verdict = str(item.get("verdict", "")).strip().upper()
        if verdict not in VALID_VERDICTS:
            raise ClassifierError(f"알 수 없는 verdict: {item!r}")
        out.append(
            {
                "id": str(item.get("id", "")),
                "verdict": verdict,
                "reason": str(item.get("reason", "")),
            }
        )
    return out


def call_claude_classifier(
    batch: list[dict[str, Any]],
    *,
    model: str = DEFAULT_MODEL,
    claude_bin: str = "claude",
) -> list[dict[str, str]]:
    items_json = json.dumps(
        [{"id": p["id"], "title": p.get("title") or "", "body": p.get("body") or ""} for p in batch],
        ensure_ascii=False,
    )
    prompt = PROMPT_TEMPLATE.format(items_json=items_json)
    last_error: Exception | None = None
    for attempt in range(1, MAX_LLM_RETRIES + 1):
        try:
            result = subprocess.run(
                [
                    claude_bin,
                    "-p",
                    prompt,
                    "--model",
                    model,
                    "--output-format",
                    "json",
                    "--disallowedTools",
                    "*",
                ],
                capture_output=True,
                text=True,
                timeout=180,
                stdin=subprocess.DEVNULL,
            )
            if result.returncode != 0:
                raise ClassifierError(f"claude CLI 실패(rc={result.returncode}): {result.stderr[:500]}")
            envelope = json.loads(result.stdout)
            if envelope.get("is_error"):
                raise ClassifierError(f"claude CLI 오류 응답: {envelope}")
            return parse_classification_response(envelope["result"])
        except Exception as exc:  # noqa: BLE001 — 재시도 후 상위로 던짐
            last_error = exc
            print(
                f"[warn] 분류 배치 실패(시도 {attempt}/{MAX_LLM_RETRIES}): {exc}",
                file=sys.stderr,
            )
    assert last_error is not None
    raise ClassifierError(f"{MAX_LLM_RETRIES}회 재시도 소진: {last_error}") from last_error


def classify_all(
    posts: list[dict[str, Any]],
    *,
    batch_size: int = DEFAULT_BATCH_SIZE,
    model: str = DEFAULT_MODEL,
    claude_bin: str = "claude",
) -> list[dict[str, Any]]:
    by_id = {p["id"]: p for p in posts}
    rows: list[dict[str, Any]] = []
    for batch in chunked(posts, batch_size):
        try:
            verdicts = call_claude_classifier(batch, model=model, claude_bin=claude_bin)
        except ClassifierError as exc:
            print(f"[error] 배치 분류 중단(미게시 처리): {exc}", file=sys.stderr)
            for p in batch:
                rows.append(
                    {
                        "id": p["id"],
                        "title": p.get("title"),
                        "author_persona": p.get("author"),
                        "verdict": "ERROR",
                        "reason": f"classification_failed: {exc}",
                    }
                )
            continue
        seen = set()
        for v in verdicts:
            post = by_id.get(v["id"])
            if not post:
                continue
            seen.add(v["id"])
            rows.append(
                {
                    "id": v["id"],
                    "title": post.get("title"),
                    "author_persona": post.get("author"),
                    "verdict": v["verdict"],
                    "reason": v["reason"],
                }
            )
        for p in batch:
            if p["id"] not in seen:
                rows.append(
                    {
                        "id": p["id"],
                        "title": p.get("title"),
                        "author_persona": p.get("author"),
                        "verdict": "ERROR",
                        "reason": "classification_failed: id missing from LLM response",
                    }
                )
    return rows


def build_apply_sql(ids: list[str]) -> str:
    """OFF_TARGET id 목록으로 soft-delete SQL을 만든다 (DB 접속 없이 순수 생성, 테스트용)."""
    for pid in ids:
        if not _ID_RE.match(pid):
            raise ValueError(f"안전하지 않은 post id: {pid!r}")
    if not ids:
        return "-- OFF_TARGET 없음, 실행할 SQL 없음\n"
    id_list = ", ".join(f"'{pid}'" for pid in ids)
    return (
        "START TRANSACTION;\n"
        f"UPDATE posts SET deleted_at = NOW(3) WHERE id IN ({id_list}) AND deleted_at IS NULL;\n"
        f"UPDATE post_comments SET deleted_at = NOW(3) WHERE post_id IN ({id_list}) AND deleted_at IS NULL;\n"
        "COMMIT;\n"
    )


def load_jsonl(path: Path) -> list[dict[str, Any]]:
    rows = []
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line:
            continue
        rows.append(json.loads(line))
    return rows


def cmd_classify(args: argparse.Namespace) -> int:
    env_values = load_dotenv(ROOT / args.env_file)
    target = build_db_target(args, env_values)
    print(f"[target] {describe_target(target)}", file=sys.stderr)
    posts = fetch_synthetic_posts(target, args.limit)
    print(f"[info] 대상 글 {len(posts)}건 ({target.env_name}, container={target.container})", file=sys.stderr)
    if not posts:
        return 0
    rows = classify_all(posts, batch_size=args.batch_size, model=args.model, claude_bin=args.claude_bin)

    out_stream = open(args.out, "w", encoding="utf-8") if args.out else sys.stdout
    try:
        for row in rows:
            out_stream.write(json.dumps(row, ensure_ascii=False) + "\n")
    finally:
        if args.out:
            out_stream.close()

    off_target = sum(1 for r in rows if r["verdict"] == "OFF_TARGET")
    on_target = sum(1 for r in rows if r["verdict"] == "ON_TARGET")
    errors = sum(1 for r in rows if r["verdict"] == "ERROR")
    print(
        f"[summary] total={len(rows)} OFF_TARGET={off_target} ON_TARGET={on_target} ERROR={errors}",
        file=sys.stderr,
    )
    return 0


def cmd_apply(args: argparse.Namespace) -> int:
    env_values = load_dotenv(ROOT / args.env_file)
    target = build_db_target(args, env_values)
    print(f"[target] {describe_target(target)}", file=sys.stderr)
    if is_prod_target(target) and not args.i_mean_it:
        print(
            "[refused] prod DB에 --apply를 실행하려면 --i-mean-it 플래그가 필요하다. "
            f"(실제 대상: container={target.container} database={target.database})",
            file=sys.stderr,
        )
        return 1

    rows = load_jsonl(Path(args.apply))
    off_target_ids = [r["id"] for r in rows if r.get("verdict") == "OFF_TARGET"]
    sql = build_apply_sql(off_target_ids)

    if not off_target_ids:
        print("[info] OFF_TARGET 0건 — 실행할 것 없음", file=sys.stderr)
        return 0

    if args.dry_run:
        print(sql)
        print(f"[dry-run] OFF_TARGET {len(off_target_ids)}건, DB 미실행", file=sys.stderr)
        return 0

    run_mariadb_sql(target, sql)
    print(f"[applied] {target.env_name}({target.container})에 OFF_TARGET {len(off_target_ids)}건 soft-delete 완료", file=sys.stderr)
    return 0


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--env-file", default="env/.env.dev", help="dotenv 파일 경로 (DB 자격 + dev/prod 추론)")
    parser.add_argument("--env-name", choices=["dev", "prod"], default=None, help="env-file 이름으로 추론 안 될 때만 명시")
    parser.add_argument("--db-container", default=None, help="docker 컨테이너명 override")
    parser.add_argument("--db-user", default=None)
    parser.add_argument("--db-password", default=None)
    parser.add_argument("--db-name", default=None)

    action = parser.add_mutually_exclusive_group(required=True)
    action.add_argument("--classify", action="store_true", help="분류만 (DB 쓰기 0)")
    action.add_argument("--apply", metavar="JSONL", default=None, help="classify 결과 JSONL로 OFF_TARGET soft delete")

    parser.add_argument("--limit", type=int, default=None, help="classify 대상 글 수 상한 (비용 절약 표본)")
    parser.add_argument("--batch-size", type=int, default=DEFAULT_BATCH_SIZE)
    parser.add_argument("--model", default=DEFAULT_MODEL)
    parser.add_argument("--claude-bin", default="claude")
    parser.add_argument("--out", default=None, help="classify 결과 저장 경로 (기본: stdout)")
    parser.add_argument("--i-mean-it", action="store_true", help="prod에 --apply 실행을 허용하는 명시 플래그")
    parser.add_argument("--dry-run", action="store_true", help="apply SQL만 출력하고 DB 미실행")
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    if args.classify:
        return cmd_classify(args)
    return cmd_apply(args)


if __name__ == "__main__":
    raise SystemExit(main())
