#!/usr/bin/env python3
"""
backfill_ai_negatives.py — Step 9
기존 AI 봇 작성 글/댓글/대댓글을 WSL ML corpus에 백필 (label=ai)

실행:
  python3 .result/ai-user/scripts/backfill_ai_negatives.py [--env dev|prod]

기본: dev만 실행. prod는 --env prod 또는 --env both.

멱등: text SHA-256 dedup → 재실행·live 중복 자동 skip.
롤백: DELETE FROM corpus_item WHERE source='BACKFILL_SELF_GENERATED';
"""

import subprocess, json, sys, os, time, http.client
from collections import defaultdict

# ── 설정 ───────────────────────────────────────────────────────────────
ML_HOST     = "100.115.252.61"
ML_PORT     = 8201
ML_TOKEN    = "aiuser-ml-api-token-dev-2026"
BATCH_SIZE  = 500

# DB 환경별 설정 — 비밀번호는 환경변수에서 읽음 (절대 하드코딩 금지)
# 실행 전: export BACKFILL_DB_DEV_PASSWORD=<.env.dev의 MARIADB_PASSWORD>
#           export BACKFILL_DB_PROD_PASSWORD=<.env.prod의 MARIADB_PASSWORD>
DB_CONFIGS = {
    "dev": {
        "container": "againspring-mariadb-dev",
        "user":      "againspring",
        "password":  os.environ.get("BACKFILL_DB_DEV_PASSWORD", ""),
        "db":        "againspring_dev",
    },
    "prod": {
        "container": "againspring-mariadb-prod",
        "user":      "againspring",
        "password":  os.environ.get("BACKFILL_DB_PROD_PASSWORD", ""),
        "db":        os.environ.get("BACKFILL_DB_PROD_DBNAME", "againspring"),  # 실제 DB명 확인
    },
}

VALID_VOICE_TYPES = {
    "NATEPAN","BLIND","DCINSIDE","GENERAL","FMKOREA","RULIWEB",
    "THEQOO","ARCALIVE","INVEN","MLBPARK","PPOMPPU","CLIEN"
}

# LlmErrorSignature 미러 (ai-user/llm/.../service/LlmErrorSignature.java)
DENY_SIGS = [
    "credit balance","too low to access","purchase credits","plans & billing",
    "usage limit","reached your usage","5-hour limit","rate limit","rate_limit",
    "overloaded","invalid_request_error","authentication_error","permission_error",
    "api_error","anthropic api","insufficient credit","too many requests",
    "service unavailable","internal server error",
    "i'm kiro","i am kiro","저는 kiro","kiro입니다",
    "i'm claude","i am claude","i'm an ai assistant","저는 claude",
    "i can't discuss that","i cannot roleplay","i'm not able to roleplay",
    "not able to roleplay","can't roleplay","cannot roleplay as","won't roleplay",
    "can't help with this","cannot help with this","unable to help with",
    "i can't assist","cannot assist with","role-play as","this is asking me to",
    "이 요청을 도와드릴 수 없","요청을 도와드릴 수가 없","죄송하지만 저는 이 요청",
    "이 프롬프트는","프롬프트 인젝션","not set up to generate",
    "i need to be direct: i can't","i need to be direct: i'm",
    "i need to clarify: i'm","i need to be transparent",
    "i appreciate you","i'm an ai","i am an ai","as an ai","저는 ai",
]

# ── SQL ────────────────────────────────────────────────────────────────
VT_IN = "','".join(VALID_VOICE_TYPES)

SQL_POSTS = f"""
SELECT JSON_OBJECT(
  'community',   JSON_UNQUOTE(JSON_EXTRACT(pe.voice_profile,'$.voice_type')),
  'contentType', 'POST',
  'text',        COALESCE(p.body_published, p.body_raw),
  'label',       'ai',
  'source',      'BACKFILL_SELF_GENERATED'
) AS row_json
FROM posts p
JOIN users    u  ON p.author_id = u.id  AND u.synthetic = 1
JOIN personas pe ON pe.id        = p.author_id
WHERE p.deleted_at IS NULL
  AND COALESCE(p.body_published, p.body_raw) <> ''
  AND JSON_UNQUOTE(JSON_EXTRACT(pe.voice_profile,'$.voice_type'))
      IN ('{VT_IN}');
"""

SQL_COMMENTS = f"""
SELECT JSON_OBJECT(
  'community',   JSON_UNQUOTE(JSON_EXTRACT(pe.voice_profile,'$.voice_type')),
  'contentType', 'COMMENT',
  'text',        pc.body,
  'label',       'ai',
  'source',      'BACKFILL_SELF_GENERATED'
) AS row_json
FROM post_comments pc
JOIN users    u  ON pc.author_id = u.id  AND u.synthetic = 1
JOIN personas pe ON pe.id         = pc.author_id
WHERE pc.deleted_at IS NULL
  AND pc.body <> ''
  AND JSON_UNQUOTE(JSON_EXTRACT(pe.voice_profile,'$.voice_type'))
      IN ('{VT_IN}');
"""

# ── 헬퍼 ───────────────────────────────────────────────────────────────
def query_db(cfg, sql):
    """docker exec mariadb → stdout 한 줄씩 JSON."""
    cmd = [
        "docker", "exec", cfg["container"],
        "mariadb", f"-u{cfg['user']}", f"-p{cfg['password']}", cfg["db"],
        "-N", "-B", "-e", sql,
    ]
    result = subprocess.run(cmd, capture_output=True, text=True, timeout=120)
    if result.returncode != 0:
        raise RuntimeError(f"mariadb error: {result.stderr.strip()[:500]}")
    return [ln.strip() for ln in result.stdout.splitlines() if ln.strip()]

def is_clean(text: str) -> bool:
    """오류·거절 시그니처 없으면 True."""
    t = text.lower()
    return not any(sig in t for sig in DENY_SIGS)

def push_batch(items: list) -> dict:
    """POST /corpus/ingest → {inserted, skipped}."""
    body = json.dumps({"items": items}).encode()
    conn = http.client.HTTPConnection(ML_HOST, ML_PORT, timeout=30)
    conn.request("POST", "/corpus/ingest", body, {
        "Authorization": f"Bearer {ML_TOKEN}",
        "Content-Type": "application/json",
    })
    resp = conn.getresponse()
    data = resp.read()
    conn.close()
    if resp.status != 200:
        raise RuntimeError(f"ingest HTTP {resp.status}: {data[:200]}")
    return json.loads(data)

# ── 메인 ───────────────────────────────────────────────────────────────
def run_env(env_name: str, cfg: dict):
    print(f"\n{'='*60}")
    print(f"환경: {env_name} ({cfg['container']} / {cfg['db']})")
    print(f"{'='*60}")

    rows_raw, rows_clean, rows_skip_sig, rows_skip_vt = 0, 0, 0, 0
    items_buf: list = []
    community_inserted: defaultdict = defaultdict(int)
    community_skipped:  defaultdict = defaultdict(int)

    def flush(force=False):
        nonlocal rows_clean
        if not items_buf:
            return
        if not force and len(items_buf) < BATCH_SIZE:
            return
        res = push_batch(list(items_buf))
        ins  = res.get("inserted", 0)
        skip = res.get("skipped",  0)
        # 집계는 개별 아이템 커뮤니티 기준으로 추적이 어려우므로 배치 전체로 누적
        # (세부 커뮤니티 breakdown은 /metrics/readiness 확인)
        print(f"  배치 {len(items_buf)}개 → inserted={ins}, skipped={skip}")
        items_buf.clear()
        rows_clean += ins  # 재사용 (변수명 충돌 피하기)

    for sql_label, sql in [("글(POST)", SQL_POSTS), ("댓글+대댓글(COMMENT)", SQL_COMMENTS)]:
        print(f"\n[{sql_label}] 조회 중...")
        try:
            raw_lines = query_db(cfg, sql)
        except Exception as e:
            print(f"  ⚠️  쿼리 실패: {e}")
            continue

        batch_raw = 0
        for line in raw_lines:
            try:
                obj = json.loads(line)
            except json.JSONDecodeError:
                continue
            rows_raw += 1
            batch_raw += 1

            community = obj.get("community") or ""
            text      = obj.get("text")      or ""

            # 필터 ②: voice_type 화이트리스트 (SQL WHERE로 이미 걸러지나, 이중 확인)
            if community not in VALID_VOICE_TYPES:
                rows_skip_vt += 1
                continue

            # 필터 ④: 오류·거절 시그니처
            if not is_clean(text):
                rows_skip_sig += 1
                print(f"  ⚠️  시그니처 감지 → skip | community={community} | "
                      f"text[:80]={text[:80]!r}")
                continue

            items_buf.append(obj)
            if len(items_buf) >= BATCH_SIZE:
                flush(force=True)
                time.sleep(0.2)  # WSL 부하 분산

        print(f"  조회: {batch_raw}행")

    flush(force=True)  # 나머지

    # 요약
    print(f"\n── {env_name} 요약 ──")
    print(f"  총 조회:        {rows_raw}행")
    print(f"  시그니처 skip:  {rows_skip_sig}행")
    print(f"  voice_type skip:{rows_skip_vt}행")
    print(f"  → 푸시 요청:    {rows_raw - rows_skip_sig - rows_skip_vt}행")


def main():
    envs = ["dev"]
    if "--env" in sys.argv:
        idx = sys.argv.index("--env")
        arg = sys.argv[idx + 1] if idx + 1 < len(sys.argv) else "dev"
        if arg == "both":
            envs = ["dev", "prod"]
        elif arg in ("dev", "prod"):
            envs = [arg]

    for env_name in envs:
        cfg = DB_CONFIGS.get(env_name)
        if not cfg:
            print(f"알 수 없는 환경: {env_name}")
            continue
        if not cfg.get("password"):
            var = f"BACKFILL_DB_{env_name.upper()}_PASSWORD"
            print(f"⛔  {env_name} DB 비밀번호 미설정 — export {var}=<password> 후 재실행")
            continue
        run_env(env_name, cfg)

    print("\n\n✅ 백필 완료. WSL readiness 확인:")
    print("  curl -H 'Authorization: Bearer aiuser-ml-api-token-dev-2026' "
          "http://100.115.252.61:8201/metrics/readiness | python3 -m json.tool")


if __name__ == "__main__":
    main()
