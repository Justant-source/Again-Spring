#!/usr/bin/env python3
"""
build_h2h_survey.py — R13 head-to-head 블라인드 설문 생성기

N draft 생성 → /rerank top-1 선택 → (top-1, random) 쌍 → survey.md 출력.

Usage:
    python3 build_h2h_survey.py --community CLIEN [--n-contexts 20] [--drafts 4] [--generator runtime|cli] [--dry-run]
"""
import argparse, json, logging, subprocess, sys, time, urllib.request, urllib.error, shutil, os, random, tempfile, re
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime

ML_SERVICE_URL = "http://100.115.252.61:8201"
ML_API_TOKEN = "aiuser-ml-api-token-dev-2026"
LLM_AI_USER_URL = os.environ.get("LLM_AI_USER_URL", "http://localhost:8092")
CODEX_CLI_PATH = os.environ.get("CODEX_BIN", "codex")
CODEX_MODEL = os.environ.get("CODEX_MODEL", "gpt-5.4")

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

THEQOO_TRAILING_REACTION = re.compile(r"\s+(?:헐|개공감)(?:[~….!?ㅋㅠ; ]*)$")
THEQOO_REACTION_AFTER_PUNCT = re.compile(r"([.?!…~]+)\s*헐\s+")
THEQOO_STANDALONE_HEOL = re.compile(r"\s헐\s+(?=(?:제가|내가|이게|그게|근데|그냥|뭔가|싶(?:음|은|은데|어|어서)|같(?:음|아)|느낌|기분))")
UNICODE_EMOJI = re.compile(r"[\u2600-\u27BF\U0001F300-\U0001FAFF]")
UNICODE_ELLIPSIS = re.compile(r"[…⋯]+")
THEQOO_TRASH_PHRASE = re.compile(r"쓰레기 차도")
THEQOO_BROTHER_DAUGHTER_PHRASE = re.compile(r"집에서는 딸이 더 조심해야")

COMMUNITY_CFG = {
    "THEQOO": {
        "trait": "여성 중심 커뮤니티, 짧고 구어체, 반말 위주, 공감형",
        "voice_profile": "더쿠 스타일 사용자. 짧은 구어체, 반말 위주, 공감형, 갈등 사연 중심",
        "slang_level": 0.48,
        "formality": "casual",
        "category": "OTHER",
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
        "voice_profile": "클리앙 스타일 사용자. 논리적 서술, 구어 존댓말, IT 직장인 톤",
        "slang_level": 0.18,
        "formality": "polite",
        "category": "WORK",
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
        "voice_profile": "네이트판 스타일 사용자. 감정 서술 위주, 공감형, 사연 커뮤니티 톤",
        "slang_level": 0.52,
        "formality": "polite",
        "category": "OTHER",
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


def find_codex_cli():
    """Try to find codex CLI binary with fallback paths."""
    candidates = [CODEX_CLI_PATH]
    which_result = shutil.which("codex")
    if which_result:
        candidates.append(which_result)
    for path in candidates:
        if path and os.path.isfile(path) and os.access(path, os.X_OK):
            log.info(f"Using codex CLI: {path}")
            return path
    log.error(f"codex CLI not found in any fallback path. Tried: {candidates}")
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


def llm_api_generate_post(payload, timeout=45):
    url = LLM_AI_USER_URL.rstrip("/") + "/generate/post"
    req = urllib.request.Request(
        url,
        data=json.dumps(payload).encode(),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=timeout) as r:
        data = json.loads(r.read().decode())
    text = (data or {}).get("text")
    return text.strip() if isinstance(text, str) and text.strip() else None


def cleanup_theqoo_text(text: str | None, community: str) -> str | None:
    if community != "THEQOO" or not text:
        return text
    s = UNICODE_EMOJI.sub("", text)
    s = UNICODE_ELLIPSIS.sub("...", s)
    s = THEQOO_TRASH_PHRASE.sub("쓰레기통이 차도", s)
    s = THEQOO_BROTHER_DAUGHTER_PHRASE.sub("집에서는 여자가 더 조심해야", s)
    s = THEQOO_REACTION_AFTER_PUNCT.sub(r"\1 ", s)
    s = THEQOO_STANDALONE_HEOL.sub(" ", s)
    s = THEQOO_TRAILING_REACTION.sub("", s)
    s = re.sub(r" {2,}", " ", s)
    s = re.sub(r"\n{3,}", "\n\n", s)
    return s.strip()


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
            with tempfile.NamedTemporaryFile(prefix="h2h-codex-", suffix=".txt", delete=False) as tmp:
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
                capture_output=True,
                text=True,
                timeout=40,
            )
            text = ""
            if os.path.exists(out_path):
                with open(out_path, encoding="utf-8") as f:
                    text = f.read().strip()
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


def generate_post(theme: str, community: str, cfg: dict, dry_run: bool = False,
                  max_retries: int = 2, generator: str = "runtime",
                  strict_runtime: bool = False) -> tuple[str | None, str]:
    """런타임 /generate/post 우선, 실패 시 Codex CLI fallback."""
    trait = cfg["trait"]
    prompt = (
        f"당신은 한국 온라인 커뮤니티 사용자입니다. 커뮤니티 특성: {trait}\n"
        f"아래 상황에 처한 사람이 커뮤니티에 올리는 갈등 사연 글을 써주세요.\n"
        f"- 길이: 100~300자\n"
        f"- 문체: 커뮤니티 특성에 맞춤\n"
        f"- 출력: 사연 본문만 (제목 없이)\n\n"
        f"[상황]\n{theme}"
    )
    if dry_run:
        return f"[DRY RUN] {theme[:30]}…", "dry-run"
    if generator == "runtime":
        payload = {
            "personaId": f"h2h-{community.lower()}",
            "archetype": "일반갈등",
            "voiceProfile": cfg.get("voice_profile", trait),
            "tier": "REGULAR",
            "slangLevel": cfg.get("slang_level", 0.4),
            "category": cfg.get("category", "OTHER"),
            "topicSeed": theme,
            "formality": cfg.get("formality", "casual"),
            "demographic": f"{community} 커뮤니티 사용자",
            "lengthTier": "MEDIUM",
            "correlationId": f"h2h-{community.lower()}-{int(time.time() * 1000)}",
            "timeoutMs": 120000,
            "backend": "CLI",
            "voiceType": community,
            "postKind": "CONFLICT",
        }
        for attempt in range(max_retries):
            try:
                text = llm_api_generate_post(payload)
                if text:
                    text = cleanup_theqoo_text(text, community)
                    log.info("Runtime /generate/post 생성 성공 (attempt %s)", attempt + 1)
                    return text, "runtime"
            except Exception as e:
                log.warning("Runtime /generate/post 실패 (attempt %s/%s): %s",
                            attempt + 1, max_retries, e)
                if attempt < max_retries - 1:
                    time.sleep(1)
        if strict_runtime:
            log.error("Runtime 생성 실패 — strict_runtime enabled, CLI fallback 금지")
            return None, "failed"
        log.warning("Runtime 생성 실패 — Codex CLI fallback 사용")
    log.info("Codex CLI bridge로 생성...")
    text = _cli_generate(prompt, max_retries=max_retries)
    return cleanup_theqoo_text(text, community), ("cli" if text else "failed")


def _gen_draft_task(args):
    """Thread-pool worker: (context_idx, draft_idx, theme, community, cfg, dry_run, generator, strict_runtime)."""
    ctx_i, draft_i, theme, community, cfg, dry_run, generator, strict_runtime = args
    text, source = generate_post(
        theme,
        community,
        cfg,
        dry_run,
        generator=generator,
        strict_runtime=strict_runtime,
    )
    return ctx_i, draft_i, text, source


def run(community, n_contexts, n_drafts, dry_run, workers=8, generator="runtime",
        strict_runtime=False):
    cfg = COMMUNITY_CFG.get(community)
    if not cfg:
        log.error(f"Unknown community: {community}. Available: {list(COMMUNITY_CFG)}")
        return None

    themes = cfg["themes"][:n_contexts]
    total = len(themes) * n_drafts
    log.info(f"H2H survey: {community} | {len(themes)} contexts × {n_drafts} drafts = {total} LLM calls | workers={workers}")

    # Generate all drafts in parallel
    tasks = [(i, j, theme, community, cfg, dry_run, generator, strict_runtime)
             for i, theme in enumerate(themes) for j in range(n_drafts)]
    results = [[None] * n_drafts for _ in range(len(themes))]
    source_counts = {"runtime": 0, "cli": 0, "failed": 0, "dry-run": 0}
    done = 0

    with ThreadPoolExecutor(max_workers=workers) as pool:
        futures = {pool.submit(_gen_draft_task, t): t for t in tasks}
        for fut in as_completed(futures):
            ctx_i, draft_i, text, source = fut.result()
            results[ctx_i][draft_i] = text
            source_counts[source] = source_counts.get(source, 0) + 1
            done += 1
            theme_short = themes[ctx_i][:20]
            status = f"{text[:60]}…" if text else "FAILED"
            log.info(f"[{done}/{total}] ctx={ctx_i+1} draft={draft_i+1} ({theme_short}) [{source}]: {status}")

    log.info("Draft source summary: runtime=%s cli=%s failed=%s",
             source_counts.get("runtime", 0),
             source_counts.get("cli", 0),
             source_counts.get("failed", 0))

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
> generator_requested={generator} | strict_runtime={str(strict_runtime).lower()} | runtime_drafts={source_counts.get("runtime", 0)} | cli_fallbacks={source_counts.get("cli", 0)} | failed={source_counts.get("failed", 0)}
> 공식 runtime 측정 조건: `generator=runtime` + `strict_runtime=true` + `cli_fallbacks=0`
> 응답 규칙: 각 번호에서 **AI가 쓴 것처럼 느껴지는 쪽**을 `A` 또는 `B`로 적고 바로 아래 `이유`를 한 줄 이상 적으세요. 애매하면 `판단불가`라고 적으세요.
> 유효 응답 집계: `A/B`만 유효, 빈칸/판단불가/기타 응답은 무효 처리됩니다.

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

**정답:**

**이유:**

---

"""

    # Generate answers template
    answers_template = {
        "community": community,
        "generated_at": now,
        "n_pairs": len(pairs),
        "label_map": label_map,
        "response_instructions": {
            "accepted_keys": "responses.<respondent> 에는 pair 번호를 1-based(1..N) 또는 0-based(0..N-1)로 넣을 수 있음",
            "accepted_values": ["A", "B", "답변불가", "판단불가", "미응답"],
            "validity_rule": "공식 집계는 A/B만 유효. 판단불가/미응답/기타는 invalid로 계산됨",
            "recommended_shape": {
                "1": {
                    "choice": "A",
                    "reason": "문체가 더 부자연스러움",
                }
            },
        },
        "generation_meta": {
            "requested_generator": generator,
            "strict_runtime": strict_runtime,
            "source_counts": source_counts,
            "official_runtime_measurement": generator == "runtime" and strict_runtime and source_counts.get("cli", 0) == 0,
        },
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
        "source_counts": source_counts,
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
    p.add_argument("--generator", choices=["runtime", "cli"], default="runtime",
                   help="Draft generation path (default: runtime, fallback to cli)")
    p.add_argument("--strict-runtime", action="store_true",
                   help="When generator=runtime, abort/fail instead of falling back to cli")
    args = p.parse_args()

    result = run(args.community.upper(), args.n_contexts, args.drafts, args.dry_run,
                 args.workers, args.generator, args.strict_runtime)
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
