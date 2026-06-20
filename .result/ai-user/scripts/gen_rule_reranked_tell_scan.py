#!/usr/bin/env python3
"""
gen_rule_reranked_tell_scan.py — Step 91 r17

Generate 20 THEQOO posts via Best-of-4 + RULE-BASED naturalness reranking.
No ML service calls, no LLM judgment for selection.

Rule reranker scores each draft on Korean casualness:
  + informal markers, short sentences, typo/ellipsis patterns
  - formal endings (-습니다), connector words (그러나/따라서), verbose style

Usage:
  python3 gen_rule_reranked_tell_scan.py [--dry-run]

Output:
  .result/ai-user/blind/r17-rule-reranked-theqoo-survey.json
  .result/ai-user/blind/r17-rule-reranked-theqoo-survey.md
"""

import json
import re
import sys
import time
import logging
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime
import urllib.request
import urllib.parse

# ── Constants ──────────────────────────────────────────────────────────────
GENERATION_URL = "http://againspring-llm-ai-user:8092/generate/post"
ML_CORPUS_URL = "http://100.115.252.61:8201/corpus/export/blind"
ML_API_TOKEN = "aiuser-ml-api-token-dev-2026"

N_THEMES = 20
N_DRAFTS = 4
WORKERS = 8
MIN_SUCCESSFUL_PAIRS = 15

THEQOO_THEMES = [
    "남자친구가 약속을 또 어겼을 때",
    "친구가 내 비밀을 다른 사람에게 말했을 때",
    "직장 동료가 내 성과를 가로챘을 때",
    "부모님이 내 선택을 계속 무시할 때",
    "오랜 친구가 갑자기 차갑게 변했을 때",
    "남자친구가 나한테만 엄격한 것 같을 때",
    "친구가 항상 늦고 약속을 안 지킬 때",
    "회사에서 나만 야근을 강요받을 때",
    "룸메이트가 집안일을 전혀 안 할 때",
    "가족이 내 직업을 무시할 때",
    "남자친구 친구들이 나를 무시하는 것 같을 때",
    "친한 친구가 내 험담을 했다는 걸 알았을 때",
    "팀장이 항상 내 의견만 무시할 때",
    "남자친구가 나한테 거짓말을 했을 때",
    "친구가 내 물건을 허락 없이 쓸 때",
    "가족이 내 연애를 반대할 때",
    "직장 선배가 나한테만 텃세를 부릴 때",
    "남자친구가 여사친이랑 계속 연락할 때",
    "친구가 힘들 때만 연락할 때",
    "상사가 부당한 지시를 내릴 때",
]

ROUND_ID = "r17"
OUTPUT_PREFIX = "r17-rule-reranked-theqoo"

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
log = logging.getLogger(__name__)


# ── Rule-based naturalness scorer ──────────────────────────────────────────

INFORMAL_MARKERS = [
    "ㅋ", "ㄷㄷ", "ㄹㅇ", "ㅠ", "ㅜ", "헐", "개공감",
    "남친", "여친", "남사친", "여사친",
    "1도 ", "뭐임", "뭐야", "어이없", "황당", "억울",
    "모르겠", "어떡하지", "어떡해", "짜증", "열받",
    "왜이래", "왜이럼", "왜임",
    "....", "...", ";;", "ㅜㅜ", "ㅠㅠ",
    "진짜로", "진짜 너무", "나 지금", "이게 뭐야", "미치겠",
    "암만", "아무튼", "그냥 너무",
]

FORMAL_PENALTIES = [
    ("습니다", 2.5),
    ("입니다", 2.5),
    ("하였습니다", 3.0),
    ("되었습니다", 2.5),
    ("였습니다", 2.5),
    ("이에 따라", 2.0),
    ("따라서", 1.5),
    ("그러나", 1.5),
    ("이처럼", 2.0),
    ("결론적으로", 2.0),
    ("이러한", 1.5),
    ("본인은", 2.0),
    ("귀하", 2.0),
    ("진지하게 고민", 2.0),
    ("조언을 구합니다", 2.5),
    ("어떻게 대처해야", 1.5),
    ("심각한 갈등", 1.5),
    ("상황에 처해", 1.5),
    ("감정적으로 상처", 1.5),
    ("깊은 실망감", 1.5),
    ("공개적인 자리에서", 1.5),
    ("연속으로", 1.0),
    ("이로 인해", 1.5),
    ("저는 오늘", 2.0),
    ("저는 현재", 2.0),
    ("저는 매우", 2.0),
]


def conflict_naturalness_score(text: str) -> float:
    """
    Higher score = more natural human conflict story (casual Korean internet style).
    Lower score = more formal/AI-like.
    """
    score = 0.0

    # Informal markers: each occurrence adds +1
    for marker in INFORMAL_MARKERS:
        if marker in text:
            score += 1.0

    # Formal patterns: each occurrence subtracts penalty
    text_lower = text.lower()
    for marker, penalty in FORMAL_PENALTIES:
        if marker in text:
            score -= penalty

    # Sentence length: shorter avg → more natural
    sentences = [s.strip() for s in re.split(r"[.!?。\n]+", text) if len(s.strip()) > 3]
    if sentences:
        avg_len = sum(len(s) for s in sentences) / len(sentences)
        if avg_len < 25:
            score += 3.0
        elif avg_len < 40:
            score += 1.5
        elif avg_len < 60:
            score += 0.0
        elif avg_len < 80:
            score -= 1.5
        else:
            score -= 3.0

    # Total text length: AI tends to write longer
    tlen = len(text)
    if tlen < 200:
        score += 1.5
    elif tlen < 400:
        score += 0.5
    elif tlen > 700:
        score -= 1.5
    elif tlen > 500:
        score -= 0.5

    # Connector word density (AI overuses)
    connector_kw = ["그러나", "따라서", "하지만", "그렇지만", "그리하여", "이에", "또한", "아울러"]
    connector_count = sum(1 for c in connector_kw if c in text)
    score -= connector_count * 0.8

    # Short abrupt endings typical of casual Korean
    casual_endings = ["ㄹ듯", "인듯", "임", "함", "음", "ㄴ데", "겠다", "더라", "냐", "뭐"]
    ending_hits = sum(1 for e in casual_endings if text.endswith(e) or f"{e}\n" in text or f"{e} " in text)
    score += min(ending_hits, 3) * 0.5

    return score


def rank_and_select(candidates: list[dict]) -> tuple[str, str]:
    """Select the candidate with highest naturalness score. Returns (id, text)."""
    if not candidates:
        return None, None
    scored = [(c["id"], c["text"], conflict_naturalness_score(c["text"])) for c in candidates]
    scored.sort(key=lambda x: x[2], reverse=True)
    best_id, best_text, best_score = scored[0]
    log.debug(f"Scores: {[(c[0], round(c[2], 2)) for c in scored]}")
    return best_id, best_text


# ── Generation client ───────────────────────────────────────────────────────

class GenerationClient:
    def __init__(self, url, timeout_ms=120000):
        self.url = url
        self.timeout_ms = timeout_ms

    def generate(self, theme, draft_idx, dry_run=False):
        if dry_run:
            return f"[생성 텍스트 {draft_idx}] {theme}"
        payload = {
            "personaId": f"rule-reranked-{draft_idx}",
            "archetype": "일반갈등",
            "voiceProfile": "더쿠 스타일 사용자. 짧은 구어체, 반말 위주, 공감형, 갈등 사연 중심",
            "tier": "REGULAR",
            "slangLevel": 0.48,
            "category": "OTHER",
            "topicSeed": theme,
            "formality": "casual",
            "demographic": "THEQOO 커뮤니티 사용자",
            "lengthTier": "MEDIUM",
            "correlationId": f"rule-reranked-{draft_idx}-{int(time.time() * 1000)}",
            "timeoutMs": self.timeout_ms,
            "backend": "CLI",
            "voiceType": "THEQOO",
            "postKind": "CONFLICT",
        }
        try:
            body = json.dumps(payload).encode("utf-8")
            req = urllib.request.Request(
                self.url, data=body,
                headers={"Content-Type": "application/json"},
                method="POST",
            )
            with urllib.request.urlopen(req, timeout=self.timeout_ms / 1000.0) as resp:
                data = json.loads(resp.read().decode("utf-8"))
            return data.get("text") or data.get("content") or data.get("post")
        except Exception as e:
            log.error(f"Generation failed for '{theme}', draft {draft_idx}: {e}")
            return None


# ── Corpus client ───────────────────────────────────────────────────────────

class CorpusClient:
    def __init__(self, url, api_token):
        self.url = url
        self.api_token = api_token

    def fetch_human_posts(self, community="THEQOO", n=20, dry_run=False):
        if dry_run:
            return [{"text": f"[휴먼 텍스트 {i}]"} for i in range(n)]
        try:
            params = urllib.parse.urlencode({"community": community, "n_per_class": n})
            req = urllib.request.Request(
                f"{self.url}?{params}",
                headers={"Authorization": f"Bearer {self.api_token}"},
            )
            with urllib.request.urlopen(req, timeout=30) as resp:
                data = json.loads(resp.read().decode("utf-8"))
            posts = []
            if "blind_items" in data and "ground_truth" in data:
                gt = data["ground_truth"]
                for item in data["blind_items"]:
                    if gt.get(str(item.get("id", ""))) == "human":
                        posts.append({"text": item["text"]})
            return posts[:n]
        except Exception as e:
            log.error(f"Corpus fetch failed: {e}")
            return []


# ── Core pipeline ───────────────────────────────────────────────────────────

def generate_and_select_theme(gen_client, theme, theme_idx, dry_run=False):
    """Generate N_DRAFTS, score each, return (theme_idx, best_text, scores)."""
    drafts = []
    for i in range(N_DRAFTS):
        text = gen_client.generate(theme, i, dry_run=dry_run)
        if text:
            drafts.append({"id": str(i), "text": text})
        else:
            log.warning(f"Theme {theme_idx} draft {i}: generation failed")

    if not drafts:
        return theme_idx, None, []

    candidates_with_scores = [
        (d["id"], d["text"], conflict_naturalness_score(d["text"]))
        for d in drafts
    ]
    candidates_with_scores.sort(key=lambda x: x[2], reverse=True)
    best_id, best_text, best_score = candidates_with_scores[0]
    scores_summary = [(cid, round(sc, 2)) for cid, _, sc in candidates_with_scores]
    log.info(f"Theme {theme_idx}: generated {len(drafts)}/{N_DRAFTS}, selected draft {best_id} (score={best_score:.2f}), all={scores_summary}")
    return theme_idx, best_text, scores_summary


def run_all_themes(gen_client, themes, dry_run=False):
    results = {}
    with ThreadPoolExecutor(max_workers=WORKERS) as executor:
        futures = {
            executor.submit(generate_and_select_theme, gen_client, themes[i], i, dry_run): i
            for i in range(len(themes))
        }
        for future in as_completed(futures):
            theme_idx = futures[future]
            try:
                idx, best_text, scores = future.result()
                if best_text:
                    results[idx] = best_text
                else:
                    log.warning(f"Theme {idx}: all drafts failed")
            except Exception as e:
                log.error(f"Theme {theme_idx} error: {e}")
    return results


# ── Survey builder ──────────────────────────────────────────────────────────

def build_survey(generated_posts, human_posts, themes, output_dir):
    output_dir = Path(output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    pairs, pair_metadata, label_map = [], [], {}
    theme_indices = sorted(generated_posts.keys())

    for pair_idx, theme_idx in enumerate(theme_indices):
        if pair_idx >= len(human_posts):
            break
        ai_text = generated_posts[theme_idx]
        human_text = human_posts[pair_idx]["text"]
        theme = themes[theme_idx]
        if pair_idx % 2 == 0:
            pos_a, pos_b = "ai", "human"
            text_a, text_b = ai_text, human_text
        else:
            pos_a, pos_b = "human", "ai"
            text_a, text_b = human_text, ai_text
        pairs.append({"A": text_a, "B": text_b})
        label_map[str(pair_idx)] = {"A": pos_a, "B": pos_b}
        pair_metadata.append({
            "pair_idx": pair_idx, "theme_idx": theme_idx,
            "theme": theme, "ai_source": f"gen-rule-reranked-best-of-{N_DRAFTS}",
            "human_source": "ml-corpus-blind",
        })

    survey_data = {
        "type": "cond5_blind",
        "community": "THEQOO",
        "round": ROUND_ID,
        "generated_at": datetime.utcnow().isoformat() + "Z",
        "n_pairs": len(pairs),
        "label_map": label_map,
        "provenance": f"gen-rule-reranked-v1:best-of-{N_DRAFTS}+rule-rerank",
        "pair_metadata": pair_metadata,
        "responses": {},
    }

    json_path = output_dir / f"{OUTPUT_PREFIX}-survey.json"
    json_path.write_text(
        json.dumps(survey_data, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    log.info(f"Survey JSON: {json_path}")

    md_path = output_dir / f"{OUTPUT_PREFIX}-survey.md"
    lines = [
        f"# Blind Survey: Rule-Reranked THEQOO Posts ({ROUND_ID})\n\n",
        f"**생성일시**: {survey_data['generated_at']}\n",
        f"**커뮤니티**: THEQOO\n",
        f"**생성 방법**: Best-of-{N_DRAFTS} + Rule-based naturalness reranking (no ML)\n\n",
    ]
    for pair_idx, pair in enumerate(pairs):
        meta = pair_metadata[pair_idx]
        lines += [
            f"## {pair_idx + 1}번\n\n",
            f"*{meta['theme']}*\n\n",
            f"**[A]** {pair['A']}\n\n",
            f"**[B]** {pair['B']}\n\n",
            "**정답:** \n\n",
        ]
    md_path.write_text("".join(lines), encoding="utf-8")
    log.info(f"Survey MD: {md_path}")

    corpus_path = output_dir / f"{OUTPUT_PREFIX}-corpus.json"
    corpus_path.write_text(
        json.dumps({
            "survey_id": OUTPUT_PREFIX,
            "round": ROUND_ID,
            "n_pairs": len(pairs),
            "themes": themes[:len(pair_metadata)],
            "pair_metadata": pair_metadata,
            "rerank_method": "rule_based",
            "n_drafts": N_DRAFTS,
        }, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )

    return len(pairs), json_path, md_path


# ── Main ────────────────────────────────────────────────────────────────────

def main(dry_run=False):
    log.info(f"Starting gen_rule_reranked_tell_scan {ROUND_ID} (dry_run={dry_run})")
    log.info(f"Themes: {N_THEMES}, Drafts per theme: {N_DRAFTS}, Workers: {WORKERS}")

    gen_client = GenerationClient(GENERATION_URL)
    corpus_client = CorpusClient(ML_CORPUS_URL, ML_API_TOKEN)

    log.info("Step 1: Generating + rule-reranking all themes...")
    generated_posts = run_all_themes(gen_client, THEQOO_THEMES, dry_run=dry_run)
    log.info(f"Selected posts: {len(generated_posts)}/{N_THEMES}")

    if not generated_posts:
        log.error("No posts generated.")
        return 1

    log.info("Step 2: Fetching human posts from ML corpus...")
    human_posts = corpus_client.fetch_human_posts(
        community="THEQOO", n=len(generated_posts), dry_run=dry_run
    )
    log.info(f"Fetched {len(human_posts)} human posts")

    if len(human_posts) < MIN_SUCCESSFUL_PAIRS:
        log.error(f"Not enough human posts ({len(human_posts)} < {MIN_SUCCESSFUL_PAIRS})")
        return 1

    log.info("Step 3: Building blind survey...")
    script_dir = Path(__file__).parent
    output_dir = script_dir.parent / "blind"
    n_pairs, json_path, md_path = build_survey(
        generated_posts, human_posts, THEQOO_THEMES, output_dir
    )

    print("\n" + "=" * 60)
    print(f"SUMMARY — {ROUND_ID} Rule-Reranked THEQOO")
    print("=" * 60)
    print(f"Generated posts: {len(generated_posts)}/{N_THEMES}")
    print(f"Pairs: {n_pairs}")
    print(f"Survey: {json_path}")
    print(f"MD: {md_path}")
    print(f"\nNext: python3 ensemble_blind_judge.py \\")
    print(f"        --survey {md_path} \\")
    print(f"        --answers {json_path} \\")
    print(f"        --seed 42")
    print("=" * 60)
    return 0


if __name__ == "__main__":
    dry_run = "--dry-run" in sys.argv
    sys.exit(main(dry_run))
