#!/usr/bin/env python3
"""
build_cond5_blind.py — per-community cond5 blind survey builder

입력:
1. `/corpus/export/blind` raw response json (corpus mode)
2. 또는 endpoint 직접 fetch (corpus mode)
3. 또는 runtime 직접 생성 (runtime mode — AI 항목을 Claude runtime에서 생성)

Usage (runtime mode):
    python3 build_cond5_blind.py --community THEQOO --generator runtime \
        --strict-runtime --n-pairs 20 --drafts 1 --workers 8

출력:
- survey markdown
- answers template json
"""

import argparse
import json
import logging
import os
import random
import re
import time
import urllib.error
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime
from survey_fingerprints import load_registry, merge_unique, text_fingerprint, upsert_test_entry, update_registry

DEFAULT_API_BASE = os.environ.get("AI_USER_ML_BASE_URL", "http://100.115.252.61:8201")
DEFAULT_API_TOKEN = os.environ.get("AI_USER_ML_API_TOKEN", "aiuser-ml-api-token-dev-2026")
DEFAULT_OUTPUT_DIR = "/home/justant/Data/Again-Spring/.result/ai-user/blind"
LLM_AI_USER_URL = os.environ.get("LLM_AI_USER_URL", "http://localhost:8092")

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
log = logging.getLogger(__name__)

DENY_SIGS = [
    "credit balance", "rate limit", "overloaded", "authentication_error",
    "i'm kiro", "i'm claude", "저는 claude", "나는 claude",
    "cannot roleplay", "can't help with this", "i can't help with this request",
    "이 요청은 수행할 수 없습니다", "실제 온라인 커뮤니티", "가짜 페르소나",
]

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
            "직장 다니면서 육아까지 혼자 다 할 때",
            "아파트 층간소음 때문에 이웃과 싸웠을 때",
            "남편이 용돈을 줄이겠다고 했을 때",
            "시어머니가 내 요리를 매번 비교할 때",
            "시어머니가 명절 준비를 혼자 다 시킬 때",
            "남편이 내 의견을 무시할 때",
            "시댁 식구들이 우리 집에서 눌러앉을 때",
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
}


def llm_api_generate_post(llm_url, payload, timeout=60):
    url = llm_url.rstrip("/") + "/generate/post"
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


def generate_one_runtime(llm_url, theme, community, cfg, strict_runtime, idx, max_retries=2):
    payload = {
        "personaId": f"cond5-{community.lower()}-{idx}",
        "archetype": "일반갈등",
        "voiceProfile": cfg.get("voice_profile", cfg["trait"]),
        "tier": "REGULAR",
        "slangLevel": cfg.get("slang_level", 0.4),
        "category": cfg.get("category", "OTHER"),
        "topicSeed": theme,
        "formality": cfg.get("formality", "casual"),
        "demographic": f"{community} 커뮤니티 사용자",
        "lengthTier": "MEDIUM",
        "correlationId": f"cond5-{community.lower()}-{idx}-{int(time.time() * 1000)}",
        "timeoutMs": 90000,
        "backend": "CLI",
        "voiceType": community,
        "postKind": "CONFLICT",
    }
    for attempt in range(max_retries):
        try:
            text = llm_api_generate_post(llm_url, payload)
            if text and not any(s in text.lower() for s in DENY_SIGS):
                log.info("[%d] runtime 생성 성공 (attempt %d): %s…", idx + 1, attempt + 1, text[:60])
                return text
            if text:
                log.warning("[%d] deny-sig 감지, 재시도", idx + 1)
        except Exception as e:
            log.warning("[%d] runtime 실패 (attempt %d/%d): %s", idx + 1, attempt + 1, max_retries, e)
        if attempt < max_retries - 1:
            time.sleep(1)
    if strict_runtime:
        log.error("[%d] strict_runtime — CLI fallback 금지, failed", idx + 1)
        return None
    return None


def generate_runtime_ai_items(community, n_items, drafts_per_item, workers, strict_runtime, llm_url):
    cfg = COMMUNITY_CFG.get(community)
    if not cfg:
        raise SystemExit(f"Unknown community: {community}")
    themes = cfg["themes"]
    if n_items > len(themes):
        themes = (themes * ((n_items // len(themes)) + 1))[:n_items]
    else:
        themes = themes[:n_items]
    log.info("Runtime cond5 AI 생성: %s | %d items × %d drafts | workers=%d",
             community, n_items, drafts_per_item, workers)
    tasks = [(i, themes[i]) for i in range(n_items) for _ in range(drafts_per_item)]
    best_per_item = {}

    def _task(args):
        idx, theme = args
        text = generate_one_runtime(llm_url, theme, community, cfg, strict_runtime, idx)
        return idx, theme, text

    with ThreadPoolExecutor(max_workers=workers) as pool:
        futures = {pool.submit(_task, t): t for t in tasks}
        for fut in as_completed(futures):
            idx, theme, text = fut.result()
            if text and idx not in best_per_item:
                best_per_item[idx] = (theme, text)

    items = []
    failed = 0
    for i in range(n_items):
        if i in best_per_item:
            theme, text = best_per_item[i]
            items.append({
                "id": f"runtime-{community.lower()}-{i}",
                "text": text,
                "label": "ai",
                "meta": {"theme": theme, "generator": "runtime", "community": community},
            })
        else:
            failed += 1
            log.error("Item %d 생성 실패", i)
    log.info("Runtime 생성 완료: success=%d failed=%d", len(items), failed)
    return items


def load_json(path):
    try:
        with open(path, encoding="utf-8") as f:
            return json.load(f)
    except FileNotFoundError as e:
        raise SystemExit(f"Input json not found: {path}") from e


def fetch_export(api_base, api_token, community, n_per_class, seed):
    query = urllib.parse.urlencode({
        "community": community,
        "nPerClass": n_per_class,
        "seed": seed,
    })
    req = urllib.request.Request(
        f"{api_base.rstrip('/')}/corpus/export/blind?{query}",
        headers={"Authorization": f"Bearer {api_token}"},
        method="GET",
    )
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.loads(r.read().decode())


def extract_items(payload):
    blind_items = payload.get("blind_items") or []
    ground_truth = payload.get("ground_truth") or {}
    humans = []
    ais = []
    meta_stats = {"human_with_meta": 0, "human_without_meta": 0, "ai_with_meta": 0, "ai_without_meta": 0}
    for item in blind_items:
        item_id = str(item.get("id"))
        label = ground_truth.get(item_id)
        text = (item.get("text") or "").strip()
        if not text or label not in {"human", "ai"}:
            continue
        meta = item.get("meta") or {}
        normalized = {
            "id": item_id,
            "text": text,
            "label": label,
            "meta": meta,
        }
        key = f"{label}_{'with_meta' if meta else 'without_meta'}"
        meta_stats[key] += 1
        if label == "human":
            humans.append(normalized)
        else:
            ais.append(normalized)
    return humans, ais, meta_stats


def filter_used(items, used_ids, used_text_fingerprints, kind):
    if not used_ids and not used_text_fingerprints:
        return items, 0, 0, 0
    kept = []
    skipped = 0
    unfilterable = 0
    skipped_by_text = 0
    for item in items:
        fp = text_fingerprint(item.get("text") or "")
        if fp in used_text_fingerprints:
            skipped_by_text += 1
            continue
        meta = item.get("meta") or {}
        candidate_id = meta.get("ai_corpus_id") if kind == "ai" else meta.get("human_post_id")
        if candidate_id is None:
            unfilterable += 1
            kept.append(item)
            continue
        if candidate_id in used_ids:
            skipped += 1
            continue
        kept.append(item)
    return kept, skipped, unfilterable, skipped_by_text


def pair_items(community, humans, ais, n_pairs, seed):
    rng = random.Random(seed)
    rng.shuffle(humans)
    rng.shuffle(ais)
    pairs = []
    label_map = {}
    for idx, (human, ai) in enumerate(zip(humans[:n_pairs], ais[:n_pairs])):
        labels = ["human", "ai"]
        rng2 = random.Random(seed + idx + 7)
        rng2.shuffle(labels)
        if labels[0] == "human":
            text_a, text_b = human["text"], ai["text"]
            meta_a, meta_b = human, ai
        else:
            text_a, text_b = ai["text"], human["text"]
            meta_a, meta_b = ai, human
        label_map[str(idx)] = {"A": labels[0], "B": labels[1]}
        pairs.append({
            "pair": idx + 1,
            "text_a": text_a,
            "text_b": text_b,
            "meta_a": meta_a,
            "meta_b": meta_b,
        })
    return pairs, label_map


def write_outputs(community, pairs, label_map, output_prefix, provenance, generation_meta):
    os.makedirs(DEFAULT_OUTPUT_DIR, exist_ok=True)
    survey_path = os.path.join(DEFAULT_OUTPUT_DIR, f"{output_prefix}-survey.md")
    answers_path = os.path.join(DEFAULT_OUTPUT_DIR, f"{output_prefix}-answers-template.json")
    now = datetime.now().strftime("%Y-%m-%d %H:%M:%S")

    survey = f"""# R14 cond5 blind — {community}
> 생성: {now}
> provenance: `{provenance}`
> 지시: 각 번호에서 **AI가 쓴 것처럼 느껴지는 쪽**을 `A` 또는 `B`로 적고, 바로 아래 이유를 적으세요.
> 유효 응답: `A/B`만 집계. `판단불가`/빈칸/기타 응답은 무효 처리됩니다.
> source metadata coverage: human_with_meta={generation_meta['meta_stats']['human_with_meta']} / ai_with_meta={generation_meta['meta_stats']['ai_with_meta']}
> 주의: export가 source id 메타를 비우면 `used-corpus-ids` 중복 필터는 완전하게 작동하지 않습니다.
> 응답 후 import: `python3 .result/ai-user/scripts/import_survey_answers.py --survey <survey.md> --answers <answers.json> --respondent owner`

---

"""
    for pair in pairs:
        survey += f"""## {pair['pair']}번
**[A]**
{pair['text_a']}

**[B]**
{pair['text_b']}

**정답:**

**이유:**

---

"""

    answers = {
        "type": "cond5_blind",
        "community": community,
        "generated_at": now,
        "n_pairs": len(pairs),
        "label_map": label_map,
        "provenance": provenance,
        "generation_meta": generation_meta,
        "response_instructions": {
            "accepted_keys": "responses.<respondent> 에는 pair 번호를 1-based(1..N) 또는 0-based(0..N-1)로 넣을 수 있음",
            "accepted_values": ["A", "B", "답변불가", "판단불가", "미응답"],
            "validity_rule": "공식 집계는 A/B만 유효. 나머지는 invalid",
        },
        "pair_metadata": [
            {
                "pair": pair["pair"],
                "a_label": label_map[str(pair["pair"] - 1)]["A"],
                "b_label": label_map[str(pair["pair"] - 1)]["B"],
                "a_meta": pair["meta_a"].get("meta") or {},
                "b_meta": pair["meta_b"].get("meta") or {},
            }
            for pair in pairs
        ],
        "responses": {
            "friend": {},
            "owner": {},
        },
    }

    with open(survey_path, "w", encoding="utf-8") as f:
        f.write(survey)
    with open(answers_path, "w", encoding="utf-8") as f:
        json.dump(answers, f, ensure_ascii=False, indent=2)
    return survey_path, answers_path


def reserve_registry(registry_path, test_id, survey_path, answers_path, generation_meta):
    pair_fingerprints = generation_meta.get("pair_fingerprints") or []
    text_fingerprints = []
    for row in pair_fingerprints:
        text_fingerprints.extend([row["a_fingerprint"], row["b_fingerprint"]])
    entry = {
        "test_id": test_id,
        "date": datetime.now().strftime("%Y-%m-%d"),
        "survey_path": survey_path,
        "answers_path": answers_path,
        "ai_corpus_ids": [],
        "human_post_ids": [],
        "text_fingerprints": sorted(set(text_fingerprints)),
        "pair_fingerprints": pair_fingerprints,
        "note": generation_meta.get("warning") or "reserved by build_cond5_blind",
    }
    def mutator(registry):
        upsert_test_entry(registry, entry)
        registry["all_used_text_fingerprints"] = merge_unique(
            registry.get("all_used_text_fingerprints", []) + entry["text_fingerprints"]
        )

    update_registry(registry_path, mutator)


def main():
    parser = argparse.ArgumentParser(description="Build per-community cond5 blind survey")
    parser.add_argument("--community", required=True)
    parser.add_argument("--generator", default="corpus", choices=["corpus", "runtime"],
                        help="AI 항목 소스: corpus(기본) 또는 runtime(Claude :8092 직접 생성)")
    parser.add_argument("--strict-runtime", action="store_true",
                        help="runtime 모드에서 CLI fallback 금지")
    parser.add_argument("--drafts", type=int, default=1,
                        help="runtime 모드: 항목당 시도 횟수 (best-of-N)")
    parser.add_argument("--workers", type=int, default=8,
                        help="runtime 모드: 병렬 워커 수")
    parser.add_argument("--llm-url", default=LLM_AI_USER_URL,
                        help="runtime 모드: LLM AI User URL")
    parser.add_argument("--export-json", default=None)
    parser.add_argument("--fetch-export", action="store_true")
    parser.add_argument("--api-base", default=DEFAULT_API_BASE)
    parser.add_argument("--api-token", default=DEFAULT_API_TOKEN)
    parser.add_argument("--n-per-class", type=int, default=20)
    parser.add_argument("--n-pairs", type=int, default=20)
    parser.add_argument("--seed", type=int, default=2026)
    parser.add_argument("--used-ids", default=None, help="Optional used-corpus-ids.json")
    parser.add_argument("--output-prefix", default=None)
    parser.add_argument("--reserve-used", action="store_true",
                        help="Reserve generated pair text fingerprints into used registry")
    args = parser.parse_args()

    community = args.community.upper()

    # runtime 모드: AI 항목은 직접 생성, human 항목은 corpus에서 fetch
    if args.generator == "runtime":
        if not args.fetch_export and not args.export_json:
            raise SystemExit("runtime 모드에서는 --fetch-export 또는 --export-json으로 human corpus를 지정해야 합니다")
        # human corpus 로드
        if args.fetch_export:
            try:
                payload = fetch_export(args.api_base, args.api_token, community, args.n_per_class, args.seed)
            except Exception as e:
                raise SystemExit(f"Failed to fetch blind export: {e}") from e
        else:
            payload = load_json(args.export_json)
        humans, _corpus_ais, meta_stats = extract_items(payload)
        # AI 항목은 runtime에서 직접 생성
        runtime_ais = generate_runtime_ai_items(
            community=community,
            n_items=args.n_pairs,
            drafts_per_item=args.drafts,
            workers=args.workers,
            strict_runtime=args.strict_runtime,
            llm_url=args.llm_url,
        )
        if len(runtime_ais) < args.n_pairs:
            raise SystemExit(f"runtime 생성 부족: {len(runtime_ais)}/{args.n_pairs}")
        ais = runtime_ais
        meta_stats["ai_with_meta"] = len(ais)
        meta_stats["ai_without_meta"] = 0
        provenance = f"runtime:{args.llm_url}"
    else:
        if bool(args.export_json) == bool(args.fetch_export):
            raise SystemExit("Choose exactly one of --export-json or --fetch-export")
        if args.fetch_export:
            try:
                payload = fetch_export(args.api_base, args.api_token, community, args.n_per_class, args.seed)
            except Exception as e:
                raise SystemExit(f"Failed to fetch blind export: {e}") from e
            provenance = f"{args.api_base.rstrip('/')}/corpus/export/blind?community={community}&nPerClass={args.n_per_class}&seed={args.seed}"
        else:
            payload = load_json(args.export_json)
            provenance = os.path.abspath(args.export_json)
        humans, ais, meta_stats = extract_items(payload)
    used_ai_ids = set()
    used_human_ids = set()
    used_text_fingerprints = set()
    if args.used_ids:
        used = load_registry(args.used_ids)
        used_ai_ids = set(used.get("all_used_ai_corpus_ids") or [])
        used_human_ids = set(used.get("all_used_human_post_ids") or [])
        used_text_fingerprints = set(used.get("all_used_text_fingerprints") or [])
        ais, skipped_ai, unfilterable_ai, skipped_ai_text = filter_used(ais, used_ai_ids, used_text_fingerprints, "ai")
        humans, skipped_human, unfilterable_human, skipped_human_text = filter_used(humans, used_human_ids, used_text_fingerprints, "human")
    else:
        skipped_ai = skipped_human = 0
        unfilterable_ai = unfilterable_human = 0
        skipped_ai_text = skipped_human_text = 0

    if len(humans) < args.n_pairs or len(ais) < args.n_pairs:
        raise SystemExit(
            f"Not enough items after filtering: humans={len(humans)} ais={len(ais)} need={args.n_pairs}"
        )

    pairs, label_map = pair_items(community, humans, ais, args.n_pairs, args.seed)
    prefix = args.output_prefix or f"r14-cond5-{community.lower()}"
    generation_meta = {
        "meta_stats": meta_stats,
        "used_ids_filter_requested": bool(args.used_ids),
        "filtered_used_ai": skipped_ai,
        "filtered_used_human": skipped_human,
        "filtered_used_text_ai": skipped_ai_text,
        "filtered_used_text_human": skipped_human_text,
        "unfilterable_ai": unfilterable_ai,
        "unfilterable_human": unfilterable_human,
        "pair_fingerprints": [
            {
                "pair": pair["pair"],
                "a_fingerprint": text_fingerprint(pair["text_a"]),
                "b_fingerprint": text_fingerprint(pair["text_b"]),
            }
            for pair in pairs
        ],
        "warning": (
            "source metadata missing from blind export; used-corpus filtering could not be fully applied"
            if unfilterable_ai or unfilterable_human
            else ""
        ),
    }
    survey_path, answers_path = write_outputs(community, pairs, label_map, prefix, provenance, generation_meta)
    if args.reserve_used and args.used_ids:
        reserve_registry(args.used_ids, prefix, survey_path, answers_path, generation_meta)
    print(json.dumps({
        "community": community,
        "survey_path": survey_path,
        "answers_path": answers_path,
        "pairs": len(pairs),
        "filtered_used_ai": skipped_ai,
        "filtered_used_human": skipped_human,
        "filtered_used_text_ai": skipped_ai_text,
        "filtered_used_text_human": skipped_human_text,
        "unfilterable_ai": unfilterable_ai,
        "unfilterable_human": unfilterable_human,
        "warning": generation_meta["warning"],
    }, ensure_ascii=False))


if __name__ == "__main__":
    main()
