#!/usr/bin/env python3
"""clcocloud 프록시의 프롬프트 캐싱 패스스루 프로브 (캐싱 복원 P0).

4가지 요청 변형 × 각 2회 연속 호출로 판정:
  v1  단일 user 블록, cache_control 없음            — 대조군 (현행 방식)
  v2  user content 2블록 + block1 cache_control(5m) — GA, beta 헤더 없음
  v3  v2 + anthropic-beta(extended-cache-ttl) + ttl:"1h"
  v4  system 필드 방식                               — Kiro 오라우팅 재현 확인용

판정: v2/v3의 2차 호출 usage.cache_read_input_tokens > 0 → 분기 A (캐싱 복원 가능)
키/베이스URL은 dev DB system_setting → 환경변수 순으로 내부 로드 (stdout 미출력).

사용: ai-user/learning/venv/bin/python ai-user/tools/cache-probe.py
비용: 8회 × ~5k input ≈ 4만 토큰 (Haiku 등가 ~$0.04)
"""
import json
import pathlib
import subprocess
import sys
import time
import urllib.error
import urllib.request

REPO = pathlib.Path(__file__).resolve().parent.parent.parent
MODEL = "claude-haiku-4-5-20251001"
API_PATH = "/v1/messages"
BETA_1H = "extended-cache-ttl-2025-04-11"


def fetch_setting(key: str) -> str | None:
    """dev DB system_setting에서 값 로드 (docker exec — 자격은 컨테이너 env)."""
    try:
        out = subprocess.run(
            ["docker", "exec", "againspring-mariadb-dev", "sh", "-c",
             'mariadb -N -u"$MARIADB_USER" -p"$MARIADB_PASSWORD" "$MARIADB_DATABASE" '
             f'-e "SELECT setting_value FROM system_setting WHERE setting_key = \'{key}\';"'],
            capture_output=True, text=True, timeout=15)
        val = out.stdout.strip()
        return val if val else None
    except Exception:
        return None


def static_prefix(nonce: str) -> str:
    """실제 프롬프트와 동일한 성격의 정적 텍스트 (~5k+ 토큰). 변형·실행별 nonce로 캐시 분리."""
    guide_dir = REPO / "ai-user/llm/src/main/resources/voice"
    body = (guide_dir / "comment.md").read_text(encoding="utf-8") + \
           "\n\n" + (guide_dir / "post.md").read_text(encoding="utf-8")
    return f"[cache-probe {nonce}]\n당신은 한국 갈등 커뮤니티의 일반 사용자입니다.\n다음 가이드를 따르세요.\n\n{body}"


def call(base_url: str, api_key: str, body: dict, beta: str | None) -> dict:
    req = urllib.request.Request(
        base_url.rstrip("/") + API_PATH,
        data=json.dumps(body).encode("utf-8"),
        headers={
            "x-api-key": api_key,
            "anthropic-version": "2023-06-01",
            "content-type": "application/json",
            **({"anthropic-beta": beta} if beta else {}),
        }, method="POST")
    try:
        with urllib.request.urlopen(req, timeout=60) as res:
            data = json.loads(res.read())
            usage = data.get("usage", {})
            return {
                "status": res.status,
                "model": data.get("model", "?"),
                "input": usage.get("input_tokens"),
                "cache_write": usage.get("cache_creation_input_tokens", 0),
                "cache_read": usage.get("cache_read_input_tokens", 0),
                "text_head": (data.get("content") or [{}])[0].get("text", "")[:40].replace("\n", " "),
            }
    except urllib.error.HTTPError as e:
        return {"status": e.code, "model": "-", "input": None, "cache_write": 0,
                "cache_read": 0, "text_head": e.read().decode("utf-8", "replace")[:80].replace("\n", " ")}
    except Exception as e:
        return {"status": "ERR", "model": "-", "input": None, "cache_write": 0,
                "cache_read": 0, "text_head": str(e)[:80]}


def build_body(variant: str, prefix: str) -> tuple[dict, str | None]:
    """(요청 body, beta 헤더) 구성. 모든 변형의 user 질문부는 동일."""
    question = "이 가이드에 따라 댓글 톤으로 한 단어만 답하세요: 좋다 또는 싫다"
    if variant == "v1":  # 현행: 단일 user 블록 평탄화
        body = {"model": MODEL, "max_tokens": 16, "messages": [
            {"role": "user", "content": f"<instructions>\n{prefix}\n</instructions>\n\n{question}"}]}
        return body, None
    if variant == "v2":  # 2블록 + cache_control (GA, 5m)
        body = {"model": MODEL, "max_tokens": 16, "messages": [
            {"role": "user", "content": [
                {"type": "text", "text": f"<instructions>\n{prefix}\n</instructions>",
                 "cache_control": {"type": "ephemeral"}},
                {"type": "text", "text": question}]}]}
        return body, None
    if variant == "v3":  # 2블록 + 1h TTL (beta)
        body = {"model": MODEL, "max_tokens": 16, "messages": [
            {"role": "user", "content": [
                {"type": "text", "text": f"<instructions>\n{prefix}\n</instructions>",
                 "cache_control": {"type": "ephemeral", "ttl": "1h"}},
                {"type": "text", "text": question}]}]}
        return body, BETA_1H
    if variant == "v4":  # system 필드 — Kiro 오라우팅 재현 확인
        body = {"model": MODEL, "max_tokens": 16,
                "system": [{"type": "text", "text": prefix,
                            "cache_control": {"type": "ephemeral"}}],
                "messages": [{"role": "user", "content": question}]}
        return body, None
    raise ValueError(variant)


def main():
    import os
    api_key = fetch_setting("ANTHROPIC_API_KEY") or os.getenv("ANTHROPIC_API_KEY")
    base_url = fetch_setting("ANTHROPIC_BASE_URL") or os.getenv("ANTHROPIC_BASE_URL") \
        or "https://api.anthropic.com"
    if not api_key:
        sys.exit("API 키를 DB/env에서 찾지 못함")
    print(f"base_url: {base_url}  (키는 미출력, 길이 {len(api_key)})")

    run_nonce = str(int(time.time()))
    results = {}
    for variant in ["v1", "v2", "v3", "v4"]:
        prefix = static_prefix(f"{run_nonce}-{variant}")
        body, beta = build_body(variant, prefix)
        for attempt in (1, 2):
            r = call(base_url, api_key, body, beta)
            results[(variant, attempt)] = r
            print(f"{variant}#{attempt}  status={r['status']:<4} model={r['model']:<28} "
                  f"input={str(r['input']):<6} cache_write={r['cache_write']:<6} "
                  f"cache_read={r['cache_read']:<6} | {r['text_head']}")
            time.sleep(2)

    # ── 판정 ──
    print("\n── 판정 ──")
    v2_hit = results[("v2", 2)]["cache_read"] or 0
    v3_hit = results[("v3", 2)]["cache_read"] or 0
    v4_model = str(results[("v4", 1)]["model"])
    if v3_hit > 0:
        print("✅ 분기 A — user-block 캐싱 패스스루 + 1h TTL 사용 가능")
    elif v2_hit > 0:
        print("✅ 분기 A — user-block 캐싱 패스스루 (5m TTL만, beta 미통과 → keepalive 검토)")
    else:
        print("❌ 분기 B — 프록시가 캐싱 미지원 (cache_read 0) → 토큰 다이어트로 전환")
    if not v4_model.startswith("claude"):
        print(f"⚠️ v4(system 필드): model={v4_model} — Kiro 오라우팅 재현됨 (system 필드 금지 유지)")
    else:
        print(f"ℹ️ v4(system 필드): model={v4_model} — 오라우팅 미재현 (그래도 user-block 방식 유지 권장)")


if __name__ == "__main__":
    main()
