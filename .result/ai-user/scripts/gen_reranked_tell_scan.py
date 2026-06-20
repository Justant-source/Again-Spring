#!/usr/bin/env python3
"""
gen_reranked_tell_scan.py

Generate 20 THEQOO posts via Best-of-4 drafts + ML reranking.
Build blind survey JSON + markdown for ensemble_blind_judge.py.
Replicate ActionExecutor.java pipeline for cond5 Phase 2.

Usage:
  python3 gen_reranked_tell_scan.py [--dry-run]

Output files:
  .result/ai-user/blind/r16-ml-reranked-theqoo-survey.json
  .result/ai-user/blind/r16-ml-reranked-theqoo-survey.md
  .result/ai-user/blind/r16-ml-reranked-theqoo-corpus.json (metadata)
"""

import json
import sys
import time
import random
import logging
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime
import hashlib
import urllib.request
import urllib.error
import urllib.parse

# Constants
GENERATION_URL = "http://againspring-llm-ai-user:8092/generate/post"
ML_RERANK_URL = "http://100.115.252.61:8201/rerank"
ML_CORPUS_URL = "http://100.115.252.61:8201/corpus/export/blind"
ML_API_TOKEN = "aiuser-ml-api-token-dev-2026"

N_THEMES = 20
N_DRAFTS = 4  # Best-of-4
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

# Setup logging
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
)
log = logging.getLogger(__name__)


class GenerationClient:
    """LLM generation client."""

    def __init__(self, url, timeout_ms=120000):
        self.url = url
        self.timeout_ms = timeout_ms

    def generate(self, theme, draft_idx, dry_run=False):
        """Generate a single draft post."""
        if dry_run:
            return self._placeholder(theme, draft_idx)

        payload = {
            "personaId": f"gen-reranked-{draft_idx}",
            "archetype": "일반갈등",
            "voiceProfile": "더쿠 스타일 사용자. 짧은 구어체, 반말 위주, 공감형, 갈등 사연 중심",
            "tier": "REGULAR",
            "slangLevel": 0.48,
            "category": "OTHER",
            "topicSeed": theme,
            "formality": "casual",
            "demographic": "THEQOO 커뮤니티 사용자",
            "lengthTier": "MEDIUM",
            "correlationId": f"gen-reranked-{draft_idx}-{int(time.time()*1000)}",
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
            if "text" in data:
                return data["text"]
            elif "content" in data:
                return data["content"]
            elif "post" in data:
                return data["post"]
            else:
                log.warning(f"Unexpected generation response for {theme}: {list(data.keys())}")
                return None
        except Exception as e:
            log.error(f"Generation failed for theme '{theme}', draft {draft_idx}: {e}")
            return None

    @staticmethod
    def _placeholder(theme, draft_idx):
        """Generate a placeholder post for dry-run."""
        return f"[생성 텍스트 {draft_idx}] {theme}에 대한 더쿠식 표현. 이건 placeholder입니다."


class RerankClient:
    """ML rerank service client."""

    def __init__(self, url, api_token):
        self.url = url
        self.api_token = api_token

    def rerank(self, candidates, community="THEQOO", dry_run=False):
        """
        Rerank candidates and return the best one (id).
        candidates: list of {"id": str, "text": str}
        Returns: (best_id, best_text) or (None, None) on failure.
        """
        if dry_run:
            if candidates:
                return candidates[0]["id"], candidates[0]["text"]
            return None, None

        payload = {
            "community": community,
            "contentType": "POST",
            "candidates": candidates,
        }

        try:
            body = json.dumps(payload).encode("utf-8")
            req = urllib.request.Request(
                self.url, data=body,
                headers={"Content-Type": "application/json",
                         "Authorization": f"Bearer {self.api_token}"},
                method="POST",
            )
            with urllib.request.urlopen(req, timeout=30) as resp:
                data = json.loads(resp.read().decode("utf-8"))

            # Response: {"winnerId": "0", "ranked": [...], "degraded": false}
            best_id = data.get("winnerId") or data.get("bestId") or data.get("winner_id")
            if data.get("degraded"):
                log.warning("Rerank returned degraded=True, using first draft")
                return candidates[0]["id"], candidates[0]["text"]
            if best_id is not None:
                for cand in candidates:
                    if cand["id"] == best_id:
                        return best_id, cand["text"]
            return None, None
        except Exception as e:
            log.error(f"Rerank failed: {e}")
            return None, None


class CorpusClient:
    """ML corpus export client."""

    def __init__(self, url, api_token):
        self.url = url
        self.api_token = api_token

    def fetch_human_posts(self, community="THEQOO", n_per_class=20, dry_run=False):
        """
        Fetch human THEQOO posts from ML corpus.
        Returns list of {"text": str}.
        """
        if dry_run:
            return [
                {"text": f"[휴먼 텍스트 {i}] {community} 커뮤니티 사용자의 실제 글입니다."}
                for i in range(n_per_class)
            ]

        try:
            params = urllib.parse.urlencode({"community": community, "n_per_class": n_per_class})
            url = f"{self.url}?{params}"
            req = urllib.request.Request(
                url,
                headers={"Authorization": f"Bearer {self.api_token}"},
                method="GET",
            )
            with urllib.request.urlopen(req, timeout=30) as resp:
                data = json.loads(resp.read().decode("utf-8"))

            # Response: {"blind_items": [{"id": "...", "text": "..."}],
            #            "ground_truth": {"id": "human"|"ai"}, "n_human": N, ...}
            posts = []
            if "blind_items" in data and "ground_truth" in data:
                gt = data["ground_truth"]
                for item in data["blind_items"]:
                    item_id = str(item.get("id", ""))
                    label = gt.get(item_id, "")
                    if label == "human":
                        posts.append({"text": item["text"]})
            elif "pairs" in data:
                for pair in data["pairs"]:
                    if "human_text" in pair:
                        posts.append({"text": pair["human_text"]})
            elif "human_posts" in data:
                for p in data["human_posts"]:
                    posts.append({"text": p} if isinstance(p, str) else p)

            return posts[:n_per_class]
        except Exception as e:
            log.error(f"Corpus fetch failed: {e}")
            return []


def generate_drafts_for_theme(gen_client, theme, theme_idx, dry_run=False):
    """Generate N_DRAFTS for a single theme. Return list of texts."""
    drafts = []
    for draft_idx in range(N_DRAFTS):
        text = gen_client.generate(theme, draft_idx, dry_run=dry_run)
        if text:
            drafts.append(text)
        else:
            log.warning(f"Draft {draft_idx} failed for theme {theme_idx}: {theme}")

    return drafts if len(drafts) > 0 else None


def generate_all_drafts(gen_client, themes, dry_run=False):
    """Generate all drafts for all themes in parallel. Return dict of theme_idx -> list of drafts."""
    results = {}
    with ThreadPoolExecutor(max_workers=WORKERS) as executor:
        futures = {
            executor.submit(
                generate_drafts_for_theme, gen_client, themes[i], i, dry_run
            ): i
            for i in range(len(themes))
        }

        for future in as_completed(futures):
            theme_idx = futures[future]
            try:
                drafts = future.result()
                if drafts:
                    results[theme_idx] = drafts
                    log.info(
                        f"Theme {theme_idx}: generated {len(drafts)}/{N_DRAFTS} drafts"
                    )
                else:
                    log.warning(f"Theme {theme_idx}: all drafts failed")
            except Exception as e:
                log.error(f"Theme {theme_idx}: generation error: {e}")

    return results


def rerank_theme_drafts(rerank_client, theme_idx, drafts, dry_run=False):
    """Rerank drafts for a single theme. Return (theme_idx, best_text) or (theme_idx, None)."""
    candidates = [{"id": str(i), "text": text} for i, text in enumerate(drafts)]
    best_id, best_text = rerank_client.rerank(
        candidates, community="THEQOO", dry_run=dry_run
    )

    if best_text:
        log.info(f"Theme {theme_idx}: selected draft {best_id}")
        return theme_idx, best_text
    else:
        # Fallback: use first draft
        log.warning(f"Theme {theme_idx}: rerank failed, using first draft")
        return theme_idx, drafts[0]


def rerank_all_drafts(rerank_client, theme_drafts_map, dry_run=False):
    """Rerank all theme drafts in parallel. Return dict of theme_idx -> best_text."""
    results = {}
    with ThreadPoolExecutor(max_workers=WORKERS) as executor:
        futures = {
            executor.submit(
                rerank_theme_drafts, rerank_client, theme_idx, drafts, dry_run
            ): theme_idx
            for theme_idx, drafts in theme_drafts_map.items()
        }

        for future in as_completed(futures):
            try:
                theme_idx, best_text = future.result()
                results[theme_idx] = best_text
            except Exception as e:
                log.error(f"Rerank error for theme {theme_idx}: {e}")

    return results


def build_survey(
    generated_posts, human_posts, themes, output_dir, dry_run=False
):
    """
    Build blind survey JSON + markdown.
    generated_posts: dict of theme_idx -> ai_text
    human_posts: list of {"text": str}
    themes: list of theme strings
    """
    # Use absolute path to avoid nested relative path issues
    if not output_dir.startswith('/'):
        output_dir = str(Path.cwd() / output_dir)
    output_dir = Path(output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    # Build pairs: alternate A/B position
    pairs = []
    pair_metadata = []
    label_map = {}

    theme_indices = sorted(generated_posts.keys())
    for pair_idx, theme_idx in enumerate(theme_indices):
        if pair_idx >= len(human_posts):
            log.warning(
                f"Not enough human posts: {len(human_posts)} < {len(theme_indices)}"
            )
            break

        ai_text = generated_posts[theme_idx]
        human_text = human_posts[pair_idx]["text"]
        theme = themes[theme_idx]

        # Alternate positions
        if pair_idx % 2 == 0:
            position_a, position_b = "ai", "human"
            text_a, text_b = ai_text, human_text
        else:
            position_a, position_b = "human", "ai"
            text_a, text_b = human_text, ai_text

        pairs.append(
            {
                "A": text_a,
                "B": text_b,
            }
        )

        label_map[str(pair_idx)] = {
            "A": position_a,
            "B": position_b,
        }

        pair_metadata.append(
            {
                "pair_idx": pair_idx,
                "theme_idx": theme_idx,
                "theme": theme,
                "ai_source": "gen-reranked-best-of-4",
                "human_source": "ml-corpus-blind",
            }
        )

    # Build survey JSON
    survey_data = {
        "type": "cond5_blind",
        "community": "THEQOO",
        "generated_at": datetime.utcnow().isoformat() + "Z",
        "n_pairs": len(pairs),
        "label_map": label_map,
        "provenance": "gen-reranked-v1:best-of-4+ml-rerank",
        "pair_metadata": pair_metadata,
        "responses": {},
    }

    survey_json_path = output_dir / "r16-ml-reranked-theqoo-survey.json"
    with open(survey_json_path, "w", encoding="utf-8") as f:
        json.dump(survey_data, f, ensure_ascii=False, indent=2)
    log.info(f"Survey JSON saved: {survey_json_path}")

    # Build survey markdown for ensemble_blind_judge.py
    # Format must match PAIR_HEADER_RE=r"^##\s+(\d+)번\s*$" and AB_BLOCK_RE=r"\*\*\[A\]\*\*..."
    survey_md_path = output_dir / "r16-ml-reranked-theqoo-survey.md"
    md_lines = ["# Blind Survey: ML-Reranked THEQOO Posts (r16)\n\n"]
    md_lines.append(f"**생성일시**: {survey_data['generated_at']}\n")
    md_lines.append(f"**커뮤니티**: THEQOO\n")
    md_lines.append(f"**생성 방법**: Best-of-4 + ML reranking (AUC=0.9981)\n\n")

    for pair_idx, pair in enumerate(pairs):
        metadata = pair_metadata[pair_idx]
        pair_num = pair_idx + 1
        md_lines.append(f"## {pair_num}번\n\n")
        md_lines.append(f"*{metadata['theme']}*\n\n")
        md_lines.append(f"**[A]** {pair['A']}\n\n")
        md_lines.append(f"**[B]** {pair['B']}\n\n")
        md_lines.append("**정답:** \n\n")

    with open(survey_md_path, "w", encoding="utf-8") as f:
        f.writelines(md_lines)
    log.info(f"Survey markdown saved: {survey_md_path}")

    # Save corpus metadata
    corpus_data = {
        "survey_id": "r16-ml-reranked-theqoo",
        "n_pairs": len(pairs),
        "themes": themes[: len(pair_metadata)],
        "pair_metadata": pair_metadata,
    }
    corpus_json_path = output_dir / "r16-ml-reranked-theqoo-corpus.json"
    with open(corpus_json_path, "w", encoding="utf-8") as f:
        json.dump(corpus_data, f, ensure_ascii=False, indent=2)
    log.info(f"Corpus metadata saved: {corpus_json_path}")

    return len(pairs), survey_json_path, survey_md_path


def main(dry_run=False):
    """Main entry point."""
    log.info(f"Starting gen_reranked_tell_scan (dry_run={dry_run})")
    log.info(f"Themes: {len(THEQOO_THEMES)}, Drafts per theme: {N_DRAFTS}, Workers: {WORKERS}")

    # Initialize clients
    gen_client = GenerationClient(GENERATION_URL)
    rerank_client = RerankClient(ML_RERANK_URL, ML_API_TOKEN)
    corpus_client = CorpusClient(ML_CORPUS_URL, ML_API_TOKEN)

    # Step 1: Generate all drafts
    log.info("Step 1: Generating drafts for all themes...")
    theme_drafts = generate_all_drafts(gen_client, THEQOO_THEMES, dry_run=dry_run)
    log.info(f"Generated drafts for {len(theme_drafts)}/{N_THEMES} themes")

    if len(theme_drafts) == 0:
        log.error("No drafts generated. Exiting.")
        return 1

    # Step 2: Rerank drafts
    log.info("Step 2: Reranking drafts for all themes...")
    generated_posts = rerank_all_drafts(
        rerank_client, theme_drafts, dry_run=dry_run
    )
    log.info(f"Reranked {len(generated_posts)} themes")

    # Step 3: Fetch human posts
    log.info("Step 3: Fetching human posts from ML corpus...")
    human_posts = corpus_client.fetch_human_posts(
        community="THEQOO", n_per_class=len(generated_posts), dry_run=dry_run
    )
    log.info(f"Fetched {len(human_posts)} human posts")

    if len(human_posts) < MIN_SUCCESSFUL_PAIRS:
        log.error(
            f"Not enough human posts ({len(human_posts)} < {MIN_SUCCESSFUL_PAIRS})"
        )
        return 1

    # Step 4: Build survey
    log.info("Step 4: Building blind survey...")
    # Determine output directory (supports running from any location)
    script_dir = Path(__file__).parent
    output_dir = script_dir.parent / "blind"
    n_pairs, json_path, md_path = build_survey(
        generated_posts, human_posts, THEQOO_THEMES, str(output_dir), dry_run=dry_run
    )

    if n_pairs < MIN_SUCCESSFUL_PAIRS:
        log.error(f"Not enough pairs ({n_pairs} < {MIN_SUCCESSFUL_PAIRS})")
        return 1

    log.info(f"Survey built: {n_pairs} pairs")

    # Summary
    print("\n" + "=" * 60)
    print("SUMMARY")
    print("=" * 60)
    print(f"Generated posts: {len(generated_posts)}/{N_THEMES}")
    print(f"Successful pairs: {n_pairs}")
    print(f"Output files:")
    print(f"  - {json_path}")
    print(f"  - {md_path}")
    print("\nNext steps:")
    print(f"  1. Run ensemble_blind_judge.py:")
    print(f"     python3 ensemble_blind_judge.py \\")
    print(f"       --survey {md_path} \\")
    print(f"       --answers {json_path} \\")
    print(f"       --output blind/r16-ml-reranked-theqoo-judge.json \\")
    print(f"       --seed 42")
    print(f"")
    print(f"  2. Run cond5_auto_gate.py on the judge output")
    print("=" * 60 + "\n")

    return 0


if __name__ == "__main__":
    dry_run = "--dry-run" in sys.argv
    sys.exit(main(dry_run=dry_run))
