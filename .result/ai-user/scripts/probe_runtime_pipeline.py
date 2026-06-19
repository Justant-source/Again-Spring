#!/usr/bin/env python3
"""
probe_runtime_pipeline.py — R14 runtime health/path probe

목적:
1. :8092 health 확인
2. /generate/post 4-draft 생성 확인
3. /rerank winner 선택 확인
4. THEQOO 알려진 신호 + 신규 tell 육안 점검용 샘플 저장

Usage:
    python3 probe_runtime_pipeline.py --community THEQOO --samples 4
    python3 probe_runtime_pipeline.py --community THEQOO --strict-runtime
"""

import argparse
import json
import os
import random
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime

LLM_AI_USER_URL = os.environ.get("LLM_AI_USER_URL", "http://localhost:8092")
ML_SERVICE_URL = os.environ.get("AI_USER_ML_BASE_URL", "http://100.115.252.61:8201")
ML_API_TOKEN = os.environ.get("AI_USER_ML_API_TOKEN", "aiuser-ml-api-token-dev-2026")

KNOWN_THEQOO_TELLS = [
    "헐",
    "😥",
    "🥲",
    "…",
    "쓰레기 차도",
    "집에서는 딸이 더 조심해야",
]

COMMUNITY_CFG = {
    "THEQOO": {
        "voice_profile": "더쿠 스타일 사용자. 짧은 구어체, 반말 위주, 공감형, 갈등 사연 중심",
        "slang_level": 0.48,
        "formality": "casual",
        "category": "OTHER",
        "themes": [
            "남자친구가 약속을 또 어겼을 때",
            "직장 동료가 내 아이디어를 가로챌 때",
            "오빠가 가부장적으로 굴 때",
            "친구가 자꾸 비교하며 기죽일 때",
        ],
    },
    "CLIEN": {
        "voice_profile": "클리앙 스타일 사용자. 논리적 서술, 구어 존댓말, IT 직장인 톤",
        "slang_level": 0.18,
        "formality": "polite",
        "category": "WORK",
        "themes": [
            "팀장이 일정을 무리하게 당겼을 때",
            "동료가 내 코드를 허락 없이 수정했을 때",
            "회의가 너무 많아서 일을 못할 때",
            "상사가 개인 프로젝트를 업무시간에 시킬 때",
        ],
    },
    "NATEPAN": {
        "voice_profile": "네이트판 스타일 사용자. 감정 서술 위주, 공감형, 사연 커뮤니티 톤",
        "slang_level": 0.52,
        "formality": "polite",
        "category": "OTHER",
        "themes": [
            "남편이 육아를 전혀 도와주지 않을 때",
            "시댁이 갑자기 방문한다고 할 때",
            "남편이 내 직장을 그만두라고 강요할 때",
            "친정과 시댁 사이에서 눈치를 봐야 할 때",
        ],
    },
}


def http_json(method, url, data=None, headers=None, timeout=30):
    body = json.dumps(data).encode() if data is not None else None
    req = urllib.request.Request(url, data=body, method=method)
    for k, v in (headers or {}).items():
        req.add_header(k, v)
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return json.loads(r.read().decode())


def health_check():
    try:
        return http_json("GET", LLM_AI_USER_URL.rstrip("/") + "/actuator/health", timeout=10)
    except Exception as e:
        return {"status": "DOWN", "error": str(e)}


def generate_post(community, theme, sample_idx):
    cfg = COMMUNITY_CFG[community]
    payload = {
        "personaId": f"runtime-probe-{community.lower()}",
        "archetype": "일반갈등",
        "voiceProfile": cfg["voice_profile"],
        "tier": "REGULAR",
        "slangLevel": cfg["slang_level"],
        "category": cfg["category"],
        "topicSeed": theme,
        "formality": cfg["formality"],
        "demographic": f"{community} 커뮤니티 사용자",
        "lengthTier": "MEDIUM",
        "correlationId": f"runtime-probe-{community.lower()}-{sample_idx}-{int(time.time() * 1000)}",
        "timeoutMs": 120000,
        "backend": "CLI",
        "voiceType": community,
        "postKind": "CONFLICT",
    }
    data = http_json(
        "POST",
        LLM_AI_USER_URL.rstrip("/") + "/generate/post",
        data=payload,
        headers={"Content-Type": "application/json"},
        timeout=60,
    )
    text = (data or {}).get("text") or ""
    return text.strip()


def rerank(community, drafts):
    payload = {
        "community": community,
        "contentType": "POST",
        "candidates": [{"id": f"d{i}", "text": text} for i, text in enumerate(drafts)],
    }
    return http_json(
        "POST",
        ML_SERVICE_URL.rstrip("/") + "/rerank",
        data=payload,
        headers={
            "Content-Type": "application/json",
            "Authorization": f"Bearer {ML_API_TOKEN}",
        },
        timeout=30,
    )


def analyze_tells(text):
    hits = [tell for tell in KNOWN_THEQOO_TELLS if tell in text]
    return {"known_tell_hits": hits}


def build_report(community, theme, drafts, rerank_resp, health, strict_runtime):
    now = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    output_dir = "/home/justant/Data/Again-Spring/.result/ai-user/runtime"
    os.makedirs(output_dir, exist_ok=True)
    report_path = os.path.join(output_dir, f"r14-runtime-probe-{community.lower()}.md")
    unique_count = len({d for d in drafts if d})
    tell_rows = []
    for idx, draft in enumerate(drafts, start=1):
        tell_rows.append(
            {
                "draft": idx,
                "len": len(draft),
                "tell_hits": analyze_tells(draft).get("known_tell_hits", []),
            }
        )

    md = f"""# R14 runtime probe — {community}
> 생성: {now}
> llm_url: `{LLM_AI_USER_URL}`
> strict_runtime: `{str(strict_runtime).lower()}`

## Health

- status: **{health.get("status", "UNKNOWN")}**
- raw: `{json.dumps(health, ensure_ascii=False)}`

## Probe summary

- theme: `{theme}`
- drafts requested: **{len(drafts)}**
- non-empty drafts: **{sum(1 for d in drafts if d)}**
- unique drafts: **{unique_count}**
- rerank winnerId: **{(rerank_resp or {}).get("winnerId", "—")}**
- rerank degraded: **{(rerank_resp or {}).get("degraded", "—")}**

## What this verifies

1. `:8092` is reachable from the current host
2. `/generate/post` returns text 4 times
3. drafts are not all identical
4. `/rerank` returns a winner

## Still must verify on host logs

1. actual backend/model selected
2. `InvokerRouter` path
3. whether any silent proxy/fallback exists outside this script

## Known tell scan

| draft | length | known tell hits |
|---|---:|---|
"""
    for row in tell_rows:
        md += f"| {row['draft']} | {row['len']} | {', '.join(row['tell_hits']) or '0'} |\n"

    md += "\n## Drafts (manual review)\n\n"
    for idx, draft in enumerate(drafts, start=1):
        md += f"### Draft {idx}\n\n{draft or '[EMPTY]'}\n\n"

    with open(report_path, "w", encoding="utf-8") as f:
        f.write(md)

    return report_path


def main():
    parser = argparse.ArgumentParser(description="Probe runtime generation + rerank path")
    parser.add_argument("--community", default="THEQOO", choices=sorted(COMMUNITY_CFG))
    parser.add_argument("--samples", type=int, default=4, help="Number of drafts to request")
    parser.add_argument("--strict-runtime", action="store_true",
                        help="Fail fast if health/generation is unavailable")
    args = parser.parse_args()

    community = args.community.upper()
    theme = random.choice(COMMUNITY_CFG[community]["themes"])
    health = health_check()
    if health.get("status") != "UP":
        print(json.dumps({
            "community": community,
            "status": "HALT",
            "reason": "runtime_down",
            "health": health,
        }, ensure_ascii=False))
        if args.strict_runtime:
            sys.exit(2)
        return

    drafts = []
    for idx in range(args.samples):
        text = generate_post(community, theme, idx)
        drafts.append(text)

    if len([d for d in drafts if d]) < 2:
        print(json.dumps({
            "community": community,
            "status": "HALT",
            "reason": "too_few_drafts",
            "drafts_non_empty": sum(1 for d in drafts if d),
        }, ensure_ascii=False))
        sys.exit(3)

    rerank_resp = rerank(community, [d for d in drafts if d])
    report_path = build_report(community, theme, drafts, rerank_resp, health, args.strict_runtime)
    print(json.dumps({
        "community": community,
        "status": "OK",
        "health": health.get("status"),
        "report_path": report_path,
        "winnerId": rerank_resp.get("winnerId"),
        "degraded": rerank_resp.get("degraded"),
    }, ensure_ascii=False))


if __name__ == "__main__":
    main()
