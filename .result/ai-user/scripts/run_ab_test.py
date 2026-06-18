#!/usr/bin/env python3
"""
run_ab_test.py — Phase B A-B test driver

갈등 주제 M개 × N=4 POST-style 초안 → /eval/ab-test
MAUVE(rerank_winners, human) vs MAUVE(random_winners, human) 측정.
EvalRun(kind="ab_test") DB 저장 후 delta 보고.

Usage:
    python3 run_ab_test.py --community THEQOO [--n-contexts 10] [--drafts 4] [--dry-run]
"""
import argparse, json, logging, subprocess, sys, time, urllib.request, urllib.error, shutil, glob, os, tempfile
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime

ML_SERVICE_URL = "http://100.115.252.61:8201"
ML_API_TOKEN = "aiuser-ml-api-token-dev-2026"
CODEX_CLI_PATH = os.environ.get("CODEX_BIN", "codex")
CODEX_MODEL = os.environ.get("CODEX_MODEL", "gpt-5.4")

# clcocloud 거절·오류 시그니처 (LlmErrorSignature.java 미러, R0)
# 이 텍스트가 포함된 응답은 거절로 간주 → 재시도 or CLI 폴백
DENY_SIGS = [
    "credit balance", "too low to access", "purchase credits", "plans & billing",
    "usage limit", "reached your usage", "5-hour limit", "rate limit", "rate_limit",
    "overloaded", "invalid_request_error", "authentication_error", "permission_error",
    "api_error", "anthropic api", "insufficient credit", "too many requests",
    "service unavailable", "internal server error",
    "i'm kiro", "i am kiro", "저는 kiro", "kiro입니다",
    "i'm claude", "i am claude", "i'm an ai assistant", "저는 claude",
    "i can't discuss that", "i cannot roleplay", "i'm not able to roleplay",
    "not able to roleplay", "can't roleplay", "cannot roleplay as", "won't roleplay",
    "can't help with this", "cannot help with this", "unable to help with",
    "i can't assist", "cannot assist with", "role-play as", "this is asking me to",
    "이 요청을 도와드릴 수 없", "요청을 도와드릴 수가 없", "죄송하지만 저는 이 요청",
    "이 프롬프트는", "프롬프트 인젝션", "not set up to generate",
    "i need to be direct: i can't", "i need to be direct: i'm",
    "i need to clarify: i'm", "i need to be transparent",
    "i appreciate you", "i'm an ai", "i am an ai", "as an ai", "저는 ai",
]

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
            "남자친구가 내 감정을 무시할 때",
            "직장에서 나만 눈치 보게 만들 때",
            "친구가 내 비밀을 퍼뜨렸을 때",
            "남자친구 친구들이 나를 싫어할 때",
            "부모님이 내 직업을 반대할 때",
            "오빠가 가부장적으로 굴 때",
            "친구가 자꾸 비교하며 기죽일 때",
            "남자친구가 내 외모를 지적할 때",
            "회사 여자 동료가 나를 따돌릴 때",
            "카카오톡 단체방에서 나만 무시당할 때",
            "남자친구 어머니가 나를 싫어하는 것 같을 때",
            "직장 상사가 나에게만 가혹할 때",
            "친구가 내 남자친구를 가로챌 때",
            "사이버불링 당했을 때",
            "썸남이 갑자기 연락을 끊었을 때",
            "남자친구가 일을 핑계로 데이트를 자꾸 취소할 때",
            "친정 부모님이 남자친구를 못 마땅해할 때",
            "직장 후배가 나보다 빨리 승진했을 때",
            "베프 남자친구가 나한테 이상하게 굴 때",
            "남자친구가 내 돈을 자꾸 빌릴 때",
            "친구 모임에서 나만 왕따당하는 것 같을 때",
            "남자친구가 내 친구들을 싫어할 때",
            "회사에서 성희롱 당했을 때",
            "언니와 사이가 나빠졌을 때",
            "남자친구가 전 여자친구와 연락할 때",
            "직장에서 실수를 했는데 혼자 뒤집어썼을 때",
            "집주인이 보증금을 안 돌려줄 때",
            "온라인 쇼핑 환불 거절당했을 때",
            "남자친구가 내 이야기를 다른 사람들에게 퍼뜨릴 때",
            "회사 술자리에서 괜히 취급당할 때",
            "친구가 내 물건을 허락 없이 빌릴 때",
            "남자친구가 내 의견을 무시하고 자기 생각만 고집할 때",
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
            "시어머니가 우리 집에 너무 자주 올 때",
            "남편이 가족 행사만 되면 술을 마실 때",
            "아이 양육 방식을 두고 남편과 갈등이 생겼을 때",
            "친정엄마와 남편이 갈등을 빚을 때",
            "아이 유치원 엄마들 사이에서 소외감이 들 때",
            "남편이 내 친정에 가기 싫어할 때",
            "직장 다니면서 육아까지 혼자 다 할 때",
            "시어머니가 내 요리를 매번 비교할 때",
            "아파트 층간소음 때문에 이웃과 싸웠을 때",
            "남편이 용돈을 줄이겠다고 했을 때",
            "아이 교육비 문제로 남편과 다툴 때",
            "가족 카톡방에서 시댁 식구들에게 무시당할 때",
            "남편이 내 직장 동료를 의심할 때",
            "친척들이 아이 교육에 간섭할 때",
            "아이 학교 선생님과 갈등이 생겼을 때",
            "남편이 집안일을 도와달라는 말을 무시할 때",
            "시부모님과 명절 집안일 갈등",
            "남편 형제들이 재산 문제로 다툴 때",
            "아이 친구 부모와 갈등이 생겼을 때",
            "경력단절 후 재취업하려는데 남편이 반대할 때",
            "시어머니가 손자녀 교육을 마음대로 할 때",
            "남편이 친구들과 너무 자주 어울릴 때",
            "이사 문제로 남편과 갈등이 생겼을 때",
            "남편 직장 동료 부인이 나를 무시할 때",
            "가사 도우미 문제로 시어머니와 갈등이 생겼을 때",
            "남편이 내 건강 문제를 심각하게 여기지 않을 때",
            "친정 부모님과 남편 사이에서 양쪽 눈치 볼 때",
            "유치원 학부모 단체채팅방에서 왕따당할 때",
            "남편이 나한테 사과를 절대 안 할 때",
            "아이가 학교에서 따돌림 당한다는 것을 알게 됐을 때",
            "남편과 섹스 횟수 차이로 갈등할 때",
            "아이 학원비 때문에 남편과 싸울 때",
            "시어머니가 내 자식들을 차별할 때",
            "남편이 명절 고향 내려가는 것을 강요할 때",
        ],
    },
}

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
)
log = logging.getLogger(__name__)


def find_codex_cli():
    """
    Try to find codex CLI binary with fallback paths.
    """
    candidates = [
        CODEX_CLI_PATH,
    ]

    # Try PATH lookup
    which_result = shutil.which('codex')
    if which_result:
        candidates.append(which_result)

    for path in candidates:
        if path and os.path.isfile(path) and os.access(path, os.X_OK):
            log.info(f"Using codex CLI: {path}")
            return path

    log.error(f"codex CLI not found in any fallback path. Tried: {candidates}")
    return None


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


def _api_generate(prompt: str, max_retries: int = 2) -> str | None:
    log.info("API generation path disabled — Codex CLI bridge only")
    return None


def _cli_generate(prompt: str, max_retries: int = 2) -> str | None:
    """Codex CLI bridge 생성."""
    codex_path = find_codex_cli()
    if not codex_path:
        log.error("Codex CLI를 찾을 수 없음")
        return None

    for attempt in range(max_retries):
        try:
            with tempfile.NamedTemporaryFile(prefix="ab-codex-", suffix=".txt", delete=False) as tmp:
                out_path = tmp.name
            r = subprocess.run(
                [
                    codex_path,
                    "exec",
                    "--skip-git-repo-check",
                    "--sandbox", "read-only",
                    "--cd", "/tmp",
                    "--color", "never",
                    "--output-last-message", out_path,
                    "--model", CODEX_MODEL,
                    prompt + "\n\n중요: 결과 본문만 출력하고 설명은 쓰지 마.",
                ],
                capture_output=True, text=True, timeout=40,
            )
            text = ""
            if os.path.exists(out_path):
                text = open(out_path, encoding="utf-8").read().strip()
                os.unlink(out_path)
            if text and r.returncode == 0:
                if any(sig in text.lower() for sig in DENY_SIGS):
                    log.warning(f"Codex CLI 거절 감지 (attempt {attempt+1}/{max_retries}): {text[:80]}")
                    if attempt < max_retries - 1:
                        time.sleep(1)
                    continue
                log.info(f"Codex CLI 생성 성공 (attempt {attempt+1})")
                return text
            log.warning(f"Codex CLI returncode={r.returncode} stderr={r.stderr[:100]}")
            if attempt < max_retries - 1:
                time.sleep(1)
        except subprocess.TimeoutExpired:
            log.error(f"Codex CLI timeout (attempt {attempt+1}/{max_retries})")
            if attempt < max_retries - 1:
                time.sleep(1)
        except FileNotFoundError as e:
            log.error(f"Codex binary not found: {e}")
            return None
        except Exception as e:
            log.error(f"Codex CLI error: {e}")
            if attempt < max_retries - 1:
                time.sleep(1)
    return None


def generate_post(theme: str, trait: str, dry_run: bool = False, max_retries: int = 2) -> str | None:
    """
    Codex CLI bridge로 갈등 사연 POST 생성.
    """
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

    log.info("Codex CLI bridge로 생성...")
    return _cli_generate(prompt, max_retries=max_retries)


def _gen_draft_task(args):
    """Thread-pool worker: (context_idx, draft_idx, theme, trait, dry_run) → (ctx_i, draft_i, text|None)."""
    ctx_i, draft_i, theme, trait, dry_run = args
    text = generate_post(theme, trait, dry_run)
    return ctx_i, draft_i, text


def run(community, n_contexts, n_drafts, dry_run, workers=8, source_filter=None):
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
    payload = {
        "community": community,
        "draftsByContext": drafts_by_context,
        "idempotencyKey": idempotency_key,
    }
    if source_filter:
        payload["sourceFilter"] = source_filter
    resp = api("POST", "/eval/ab-test", payload)
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
            mr_mean = result.get("mauve_random_mean")
            mr_std = result.get("mauve_random_std")
            mr_seeds = result.get("mauve_random_seeds", [])
            delta = result.get("delta")
            log.info(f"  community           : {community}")
            log.info(f"  mauve_rerank        : {mr:.4f}" if mr is not None else "  mauve_rerank        : None")
            log.info(f"  mauve_random_mean   : {mr_mean:.4f}" if mr_mean is not None else "  mauve_random_mean   : None")
            log.info(f"  mauve_random_std    : {mr_std:.4f}" if mr_std is not None else "  mauve_random_std    : None")
            if mr_seeds:
                log.info(f"  mauve_random_seeds  : {[round(s, 4) for s in mr_seeds]}")
            log.info(f"  delta (rerank-mean) : {delta:+.4f}" if delta is not None else "  delta (rerank-mean) : None")
            if source_filter:
                log.info(f"  source_filter       : {source_filter}")
            log.info(f"  n_contexts          : {result.get('n_contexts')}")
            log.info(f"  snapshot_size       : {result.get('snapshot_size')}")
            log.info(f"  degraded            : {result.get('degraded')}")
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
    p.add_argument("--source-filter", default=None,
                   help="Optional: filter human corpus by source (e.g. 'theqoo' for real-only)")
    args = p.parse_args()

    result = run(args.community.upper(), args.n_contexts, args.drafts, args.dry_run, args.workers, args.source_filter)
    if result is None and not args.dry_run:
        sys.exit(1)


if __name__ == "__main__":
    main()
