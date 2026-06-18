#!/usr/bin/env python3
"""
build_h2h_survey.py — R13 head-to-head 블라인드 설문 생성기

N draft 생성 → /rerank top-1 선택 → (top-1, random) 쌍 → survey.md 출력.

Usage:
    python3 build_h2h_survey.py --community CLIEN [--n-contexts 20] [--drafts 4] [--dry-run]
"""
import argparse, json, logging, subprocess, sys, time, urllib.request, urllib.error, shutil, glob, os, random
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime

ML_SERVICE_URL = "http://100.115.252.61:8201"
ML_API_TOKEN = "aiuser-ml-api-token-dev-2026"
CLAUDE_CLI_PATH = "/home/justant/.nvm/versions/node/v24.14.1/bin/claude"
CLAUDE_MODEL = "claude-haiku-4-5-20251001"

# clcocloud 거절·오류 시그니처 (LlmErrorSignature.java 미러, R0)
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
        ],
    },
}

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
)
log = logging.getLogger(__name__)


def find_claude_cli():
    """Try to find claude CLI binary with fallback paths."""
    candidates = [CLAUDE_CLI_PATH]
    which_result = shutil.which('claude')
    if which_result:
        candidates.append(which_result)
    home = os.path.expanduser('~')
    nvm_base = os.path.join(home, '.nvm/versions/node')
    if os.path.exists(nvm_base):
        glob_patterns = [os.path.join(nvm_base, 'v*/bin/claude')]
        for pattern in glob_patterns:
            candidates.extend(glob.glob(pattern))
    for path in candidates:
        if path and os.path.isfile(path) and os.access(path, os.X_OK):
            log.info(f"Using claude CLI: {path}")
            return path
    log.error(f"claude CLI not found in any fallback path")
    return None


def api(method, path, data=None):
    """HTTP API call to ML service."""
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
    """clcocloud API 우선 생성."""
    api_key  = os.environ.get("ANTHROPIC_API_KEY", "")
    base_url = os.environ.get("ANTHROPIC_BASE_URL", "https://api.anthropic.com").rstrip("/")
    if not api_key:
        log.debug("ANTHROPIC_API_KEY 미설정 — API 경로 스킵")
        return None

    body = json.dumps({
        "model": CLAUDE_MODEL,
        "max_tokens": 512,
        "messages": [{
            "role": "user",
            "content": f"<instructions>\n{prompt}\n</instructions>",
        }],
    }).encode("utf-8")

    headers = {
        "x-api-key": api_key,
        "anthropic-version": "2023-06-01",
        "content-type": "application/json",
    }

    for attempt in range(max_retries):
        try:
            req = urllib.request.Request(base_url + "/v1/messages", data=body, headers=headers, method="POST")
            with urllib.request.urlopen(req, timeout=45) as r:
                resp = json.loads(r.read().decode("utf-8"))
            text = (resp.get("content") or [{}])[0].get("text", "").strip()
            if not text:
                log.warning(f"API 빈 텍스트 (attempt {attempt+1}/{max_retries})")
                if attempt < max_retries - 1:
                    time.sleep(1)
                continue
            text_lower = text.lower()
            if any(sig in text_lower for sig in DENY_SIGS):
                log.warning(f"API 거절 감지 (attempt {attempt+1}/{max_retries}): {text[:80]}")
                if attempt < max_retries - 1:
                    time.sleep(1)
                continue
            log.info(f"API 생성 성공 (attempt {attempt+1})")
            return text
        except urllib.error.HTTPError as e:
            log.warning(f"API HTTP 에러 {e.code} (attempt {attempt+1}/{max_retries})")
            if attempt < max_retries - 1:
                time.sleep(2)
        except Exception as e:
            log.warning(f"API 예외 (attempt {attempt+1}/{max_retries}): {e}")
            if attempt < max_retries - 1:
                time.sleep(1)
    log.warning("API 경로 소진 → CLI 폴백")
    return None


def _cli_generate(prompt: str, max_retries: int = 2) -> str | None:
    """CLI 폴백 생성."""
    claude_path = find_claude_cli()
    if not claude_path:
        log.error("Claude CLI를 찾을 수 없음")
        return None

    for attempt in range(max_retries):
        try:
            r = subprocess.run([claude_path, "-p", prompt, "--model", CLAUDE_MODEL],
                              capture_output=True, text=True, timeout=40)
            text = r.stdout.strip()
            if text and r.returncode == 0:
                if any(sig in text.lower() for sig in DENY_SIGS):
                    log.warning(f"CLI 거절 감지 (attempt {attempt+1}/{max_retries}): {text[:80]}")
                    if attempt < max_retries - 1:
                        time.sleep(1)
                    continue
                log.info(f"CLI 생성 성공 (attempt {attempt+1})")
                return text
            log.warning(f"CLI returncode={r.returncode}")
            if attempt < max_retries - 1:
                time.sleep(1)
        except subprocess.TimeoutExpired:
            log.error(f"CLI timeout (attempt {attempt+1}/{max_retries})")
            if attempt < max_retries - 1:
                time.sleep(1)
        except Exception as e:
            log.error(f"CLI error: {e}")
            if attempt < max_retries - 1:
                time.sleep(1)
    return None


def generate_post(theme: str, trait: str, dry_run: bool = False, max_retries: int = 2) -> str | None:
    """clcocloud API 우선 → CLI 폴백으로 갈등 사연 POST 생성."""
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
    result = _api_generate(prompt, max_retries=max_retries)
    if result:
        return result
    log.info("CLI 폴백으로 재시도...")
    return _cli_generate(prompt, max_retries=max_retries)


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
    log.info(f"H2H survey: {community} | {len(themes)} contexts × {n_drafts} drafts = {total} LLM calls | workers={workers}")

    # Generate all drafts in parallel
    tasks = [(i, j, theme, trait, dry_run) for i, theme in enumerate(themes) for j in range(n_drafts)]
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

    # Collect valid drafts
    drafts_by_context = []
    for i, theme in enumerate(themes):
        drafts = [t for t in results[i] if t]
        if len(drafts) >= 2:
            drafts_by_context.append({
                "contextId": f"ctx_{i}_{community.lower()}",
                "theme": theme,
                "drafts": drafts,
            })
        else:
            log.warning(f"Context {i+1} ({theme[:30]}): only {len(drafts)} drafts — skip")

    if len(drafts_by_context) < 10:
        log.error(f"Too few valid contexts ({len(drafts_by_context)}) — need ≥10. Aborting")
        return None

    log.info(f"Collected {len(drafts_by_context)} valid contexts")

    if dry_run:
        log.info(f"[DRY RUN] Would create {len(drafts_by_context)} pairs from reranking")
        return None

    # Call /rerank for each context
    pairs = []
    label_map = {}
    seed_label_rng = random.Random(2026)

    for pair_idx, ctx_info in enumerate(drafts_by_context):
        drafts = ctx_info["drafts"]
        ctx_id = ctx_info["contextId"]
        theme = ctx_info["theme"]

        # Build rerank request
        candidates = [{"id": f"d{i}", "text": d} for i, d in enumerate(drafts)]
        log.info(f"Reranking pair {pair_idx+1}/{len(drafts_by_context)}: {ctx_id} with {len(drafts)} drafts")

        try:
            resp = api("POST", "/rerank", {
                "community": community,
                "contentType": "POST",
                "candidates": candidates,
            })
            degraded = resp.get("degraded", False)
            winner_id = resp.get("winnerId")  # camelCase from API

            if degraded or not winner_id:
                log.warning(f"  Pair {pair_idx+1}: degraded={degraded}, skipping (no valid rerank)")
                continue

            # Get top-1 (rerank winner)
            winner_idx = int(winner_id.replace("d", ""))
            text_top1 = drafts[winner_idx]

            # Select random from non-winner drafts
            other_indices = [i for i in range(len(drafts)) if i != winner_idx]
            random_idx = random.Random(42).choice(other_indices)
            text_random = drafts[random_idx]

            # Random label assignment (seed=2026+pair_idx for reproducibility)
            label_rng = random.Random(2026 + pair_idx)
            labels = ["rerank", "random"]
            label_rng.shuffle(labels)
            label_map[str(pair_idx)] = {
                "A": labels[0],
                "B": labels[1],
            }

            # Assign texts to A/B based on shuffled labels
            text_a = text_top1 if labels[0] == "rerank" else text_random
            text_b = text_random if labels[0] == "rerank" else text_top1

            pairs.append({
                "pair_id": pair_idx,
                "context_id": ctx_id,
                "theme": theme,
                "text_a": text_a,
                "text_b": text_b,
            })
            log.info(f"  Pair {pair_idx+1}: created ({len(text_a)} + {len(text_b)} chars)")

        except RuntimeError as e:
            log.error(f"  Pair {pair_idx+1}: /rerank failed: {e}")
            continue

    if len(pairs) < 10:
        log.error(f"Too few valid pairs ({len(pairs)}) after reranking — need ≥10. Aborting")
        return None

    log.info(f"Successfully created {len(pairs)} pairs for survey")

    # Generate survey markdown
    now = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    survey_md = f"""# R13 h2h 설문 — {community}
> 생성: {now} | n_contexts={len(drafts_by_context)} | drafted={len(pairs)} | seed_label=2026
> 지시: 각 번호에서 A/B 중 **사람이 쓴 글에 더 가까운 것**을 골라 `1-A 이유한줄` 형식으로 답하세요.

---

"""
    for pair in pairs:
        pair_id = pair["pair_id"]
        text_a = pair["text_a"]
        text_b = pair["text_b"]
        survey_md += f"""## {pair_id+1}번
**[A]**
{text_a}

**[B]**
{text_b}

---

"""

    # Generate answers template
    answers_template = {
        "community": community,
        "generated_at": now,
        "n_pairs": len(pairs),
        "label_map": label_map,
        "responses": {
            "friend": {},
            "owner": {},
        },
    }

    # Save files
    output_dir = "/home/justant/Data/Again-Spring/.result/ai-user/blind"
    if not os.path.exists(output_dir):
        os.makedirs(output_dir, exist_ok=True)
    survey_path = os.path.join(output_dir, f"r13-h2h-{community.lower()}-survey.md")
    answers_path = os.path.join(output_dir, f"r13-h2h-{community.lower()}-answers-template.json")

    with open(survey_path, "w", encoding="utf-8") as f:
        f.write(survey_md)
    log.info(f"Survey saved: {survey_path}")

    with open(answers_path, "w", encoding="utf-8") as f:
        json.dump(answers_template, f, ensure_ascii=False, indent=2)
    log.info(f"Answers template saved: {answers_path}")

    return {
        "n_pairs": len(pairs),
        "survey_path": survey_path,
        "answers_path": answers_path,
        "label_map_sample": dict(list(label_map.items())[:3]),
    }


def main():
    p = argparse.ArgumentParser(description="R13 head-to-head blind survey builder")
    p.add_argument("--community", default="CLIEN",
                   help=f"Community ({'/'.join(COMMUNITY_CFG)})")
    p.add_argument("--n-contexts", type=int, default=20,
                   help="Number of conflict themes to test (default: 20)")
    p.add_argument("--drafts", type=int, default=4,
                   help="Drafts per context (default: 4)")
    p.add_argument("--dry-run", action="store_true",
                   help="Generate drafts without calling /rerank or saving files")
    p.add_argument("--workers", type=int, default=8,
                   help="Parallel LLM workers (default: 8)")
    args = p.parse_args()

    result = run(args.community.upper(), args.n_contexts, args.drafts, args.dry_run, args.workers)
    if result is None:
        if not args.dry_run:
            sys.exit(1)
    else:
        log.info(f"=== RESULT ===")
        log.info(f"  n_pairs       : {result['n_pairs']}")
        log.info(f"  survey_path   : {result['survey_path']}")
        log.info(f"  answers_path  : {result['answers_path']}")


if __name__ == "__main__":
    main()
