#!/usr/bin/env python3
"""
run_ab_test.py — Phase B A-B test driver

갈등 주제 M개 × N=4 POST-style 초안 → /eval/ab-test
MAUVE(rerank_winners, human) vs MAUVE(random_winners, human) 측정.
EvalRun(kind="ab_test") DB 저장 후 delta 보고.

Usage:
    python3 run_ab_test.py --community THEQOO [--n-contexts 10] [--drafts 4] [--dry-run]
"""
import argparse, json, logging, subprocess, sys, time, urllib.request, urllib.error
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime

ML_SERVICE_URL = "http://100.115.252.61:8201"
ML_API_TOKEN = "aiuser-ml-api-token-dev-2026"
CLAUDE_CLI_PATH = "/home/justant/.nvm/versions/node/v24.14.1/bin/claude"
CLAUDE_MODEL = "claude-haiku-4-5-20251001"

COMMUNITY_CFG = {
    "THEQOO": {
        "trait": "여성 중심 커뮤니티, 짧고 구어체, 감탄사(ㅋㅋ/헐/와 등), 이모지 가끔",
        "themes": [
            "남자친구가 약속을 또 어겼을 때",
            "직장 동료가 내 아이디어를 가로챌 때",
            "시어머니가 명절에 모든 걸 강요할 때",
            "룸메이트가 집안일을 안 할 때",
            "베프가 갑자기 연락이 끊겼을 때",
            "남친 가족이 나를 무시할 때",
            "회사에서 나만 야근시킬 때",
            "친구가 돈을 빌려가고 안 갚을 때",
            "남자친구가 게임만 할 때",
            "직장 상사가 일을 자꾸 떠넘길 때",
            "엄마가 내 연애에 지나치게 간섭할 때",
            "자취방 집주인이 갑자기 계약 해지를 요구할 때",
        ],
    },
    "CLIEN": {
        "trait": "IT 직장인, 논리적 서술, 경어 사용, 중간 길이",
        "themes": [
            "팀장이 일정을 무리하게 당겼을 때",
            "동료가 내 코드를 허락 없이 수정했을 때",
            "재택근무 중 상사가 계속 메시지 보낼 때",
            "회사 장비 구매를 계속 거절당할 때",
            "연봉협상에서 부당한 대우를 받았을 때",
            "팀 내 성과를 혼자 독식하는 동료",
            "야근 강요하는 팀 문화",
            "업무 과부하인데 인력이 안 늘어날 때",
            "능력 없는 팀장에게 시달릴 때",
            "회의가 너무 많아서 일을 못할 때",
            "상사가 개인 프로젝트를 업무시간에 시킬 때",
            "팀원이 내 기술적 의견을 무시할 때",
        ],
    },
    "DCINSIDE": {
        "trait": "직설적, 남성 구어체, 은어(ㄹㅇ/ㅇㅈ/개), 짧고 직설적",
        "themes": [
            "친구가 내 물건 허락 없이 쓸 때",
            "알바 사장이 월급을 안 줄 때",
            "이웃이 층간소음을 낼 때",
            "게임 팀원이 트롤할 때",
            "선배가 자꾸 시비 걸 때",
        ],
    },
    "NATEPAN": {
        "trait": "주부/직장 여성, 공감형, 감정 서술 위주, 길고 자세함",
        "themes": [
            "남편이 육아를 전혀 도와주지 않을 때",
            "시댁이 갑자기 방문한다고 할 때",
            "친정엄마와 남편이 사이가 안 좋을 때",
            "아이 학교 엄마들과 갈등이 생겼을 때",
            "남편이 생활비를 줄이겠다고 할 때",
            "남편이 내 직장을 그만두라고 강요할 때",
            "시어머니가 손주 양육에 간섭할 때",
            "남편이 가사는 전혀 안 하면서 지적만 할 때",
            "친정과 시댁 사이에서 눈치를 봐야 할 때",
            "남편이 내 친구 관계를 못마땅해할 때",
        ],
    },
}

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
)
log = logging.getLogger(__name__)


def api(method, path, data=None):
    url = ML_SERVICE_URL + path
    headers = {
        "Authorization": f"Bearer {ML_API_TOKEN}",
        "Content-Type": "application/json",
    }
    body = json.dumps(data).encode() if data else None
    req = urllib.request.Request(url, data=body, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            return json.loads(r.read().decode())
    except urllib.error.HTTPError as e:
        raise RuntimeError(f"HTTP {e.code}: {e.read().decode()[:300]}")


def generate_post(theme, trait, dry_run=False):
    """Generate one POST-style 갈등 사연 using claude CLI."""
    prompt = (
        f"당신은 한국 온라인 커뮤니티 사용자입니다. 커뮤니티 특성: {trait}\n"
        f"아래 상황에 처한 사람이 커뮤니티에 올리는 갈등 사연 글을 써주세요.\n"
        f"- 길이: 100~300자\n"
        f"- 문체: 커뮤니티 특성에 맞춤\n"
        f"- 출력: 사연 본문만 (제목 없이)\n\n"
        f"[상황]\n{theme}"
    )

    if dry_run:
        return f"[DRY RUN] {theme[:30]}…"

    try:
        r = subprocess.run(
            [CLAUDE_CLI_PATH, "-p", prompt, "--model", CLAUDE_MODEL],
            capture_output=True, text=True, timeout=40,
        )
        text = r.stdout.strip()
        if text and r.returncode == 0:
            return text
        log.warning(f"claude returncode={r.returncode} stderr={r.stderr[:100]}")
        return None
    except subprocess.TimeoutExpired:
        log.error("claude timeout")
        return None
    except Exception as e:
        log.error(f"claude error: {e}")
        return None


def _gen_draft_task(args):
    """Thread-pool worker: (context_idx, draft_idx, theme, trait, dry_run) → (ctx_i, draft_i, text|None)."""
    ctx_i, draft_i, theme, trait, dry_run = args
    text = generate_post(theme, trait, dry_run)
    return ctx_i, draft_i, text


def run(community, n_contexts, n_drafts, dry_run, workers=8):
    cfg = COMMUNITY_CFG.get(community)
    if not cfg:
        log.error(f"Unknown community: {community}. Available: {list(COMMUNITY_CFG)}")
        return None

    themes = cfg["themes"][:n_contexts]
    trait = cfg["trait"]
    total = len(themes) * n_drafts
    log.info(f"A-B test: {community} | {len(themes)} contexts × {n_drafts} drafts = {total} LLM calls | workers={workers}")

    # Generate all drafts in parallel
    tasks = [
        (i, j, theme, trait, dry_run)
        for i, theme in enumerate(themes)
        for j in range(n_drafts)
    ]

    # results[ctx_i][draft_i] = text
    results = [[None] * n_drafts for _ in range(len(themes))]
    done = 0
    with ThreadPoolExecutor(max_workers=workers) as pool:
        futures = {pool.submit(_gen_draft_task, t): t for t in tasks}
        for fut in as_completed(futures):
            ctx_i, draft_i, text = fut.result()
            results[ctx_i][draft_i] = text
            done += 1
            theme_short = themes[ctx_i][:20]
            status = f"{text[:60]}…" if text else "FAILED"
            log.info(f"[{done}/{total}] ctx={ctx_i+1} draft={draft_i+1} ({theme_short}): {status}")

    drafts_by_context = []
    for i, theme in enumerate(themes):
        drafts = [t for t in results[i] if t]
        if len(drafts) >= 2:
            drafts_by_context.append({
                "contextId": f"ctx_{i}_{community.lower()}",
                "drafts": drafts,
            })
        else:
            log.warning(f"Context {i+1} ({theme[:30]}): only {len(drafts)} drafts — skip")

    if dry_run:
        log.info(f"[DRY RUN] Would submit {len(drafts_by_context)} contexts to /eval/ab-test")
        return None

    if len(drafts_by_context) < 3:
        log.error(f"Too few valid contexts ({len(drafts_by_context)}) — aborting")
        return None

    idempotency_key = f"ab-{community.lower()}-{datetime.now().strftime('%Y%m%d-%H%M%S')}"
    log.info(f"Submitting {len(drafts_by_context)} contexts to /eval/ab-test …")
    resp = api("POST", "/eval/ab-test", {
        "community": community,
        "draftsByContext": drafts_by_context,
        "idempotencyKey": idempotency_key,
    })
    job_id = resp["job_id"]
    log.info(f"Job queued: {job_id}")

    for tick in range(120):
        time.sleep(5)
        job = api("GET", f"/eval/{job_id}")
        status = job.get("status")
        if status == "DONE":
            result = job.get("result") or {}
            log.info("=== RESULT ===")
            mr = result.get("mauve_rerank")
            mrand = result.get("mauve_random")
            delta = result.get("delta")
            log.info(f"  community    : {community}")
            log.info(f"  mauve_rerank : {mr:.4f}" if mr is not None else "  mauve_rerank : None")
            log.info(f"  mauve_random : {mrand:.4f}" if mrand is not None else "  mauve_random : None")
            log.info(f"  delta        : {delta:+.4f}" if delta is not None else "  delta        : None")
            log.info(f"  n_contexts   : {result.get('n_contexts')}")
            log.info(f"  degraded     : {result.get('degraded')}")
            return result
        elif status in ("FAILED", "ERROR"):
            log.error(f"Job {status}: {job}")
            return None
        else:
            log.info(f"  [{tick*5}s] status={status}")

    log.error("Timeout (600s) waiting for job")
    return None


def main():
    p = argparse.ArgumentParser(description="A-B test driver — rerank vs random MAUVE delta")
    p.add_argument("--community", default="THEQOO",
                   help=f"Community ({'/'.join(COMMUNITY_CFG)})")
    p.add_argument("--n-contexts", type=int, default=10,
                   help="Number of conflict themes to test (default: 10)")
    p.add_argument("--drafts", type=int, default=4,
                   help="Drafts per context (default: 4)")
    p.add_argument("--dry-run", action="store_true",
                   help="Print prompts without calling API")
    p.add_argument("--workers", type=int, default=8,
                   help="Parallel LLM workers for draft generation (default: 8)")
    args = p.parse_args()

    result = run(args.community.upper(), args.n_contexts, args.drafts, args.dry_run, args.workers)
    if result is None and not args.dry_run:
        sys.exit(1)


if __name__ == "__main__":
    main()
