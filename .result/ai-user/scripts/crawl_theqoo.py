"""더쿠 게시판 크롤링 → ML corpus 인제스트.

Usage:
  python crawl_theqoo.py [--boards square love job talk] [--pages 5] [--dry-run]
  python crawl_theqoo.py --boards square love job talk --pages 10

Defaults: boards=square,love,job,talk, pages=5
API: http://100.115.252.61:8201/corpus/ingest
"""
import argparse
import re
import time
import logging
import requests
from typing import Optional

logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")
log = logging.getLogger(__name__)

ML_API = "http://100.115.252.61:8201"
ML_API_KEY = "aiuser-ml-api-token-dev-2026"
COMMUNITY = "THEQOO"
SOURCE = "theqoo_crawl"
HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Accept-Language": "ko-KR,ko;q=0.9",
    "Referer": "https://theqoo.net/",
}
DELAY = 1.2  # 요청 간격(초) — 서버 부하 방지
MIN_LEN = 80
MIN_KO_RATIO = 0.10
BATCH_SIZE = 50


def ko_ratio(text: str) -> float:
    stripped = text.replace(" ", "")
    if not stripped:
        return 0.0
    return sum(1 for c in stripped if "가" <= c <= "힣") / len(stripped)


def extract_text(html: str) -> Optional[str]:
    """articleBody 안의 텍스트만 추출."""
    m = re.search(r'itemprop="articleBody">(.*?)</article>', html, re.DOTALL)
    if not m:
        return None
    inner = m.group(1)
    # HTML 태그 제거
    text = re.sub(r"<[^>]+>", " ", inner)
    # 엔티티·공백 정리
    text = re.sub(r"&[a-zA-Z]+;", " ", text)
    text = re.sub(r"\s+", " ", text).strip()
    return text or None


def get_post_links(board: str, page: int) -> list[str]:
    url = f"https://theqoo.net/{board}" + (f"?page={page}" if page > 1 else "")
    try:
        r = requests.get(url, headers=HEADERS, timeout=10)
        r.raise_for_status()
    except Exception as e:
        log.warning("board fetch failed %s p%d: %s", board, page, e)
        return []
    links = re.findall(rf"href=\"(/{board}/\d+)\"", r.text)
    return list(dict.fromkeys(links))  # 중복 제거, 순서 유지


def fetch_post(path: str) -> Optional[str]:
    url = f"https://theqoo.net{path}"
    try:
        r = requests.get(url, headers=HEADERS, timeout=10)
        r.raise_for_status()
    except Exception as e:
        log.warning("post fetch failed %s: %s", path, e)
        return None
    return extract_text(r.text)


def ingest_batch(items: list[dict], dry_run: bool) -> tuple[int, int]:
    if dry_run:
        log.info("[dry-run] would ingest %d items", len(items))
        return len(items), 0
    payload = {"items": items}
    try:
        r = requests.post(
            f"{ML_API}/corpus/ingest",
            json=payload,
            headers={"Authorization": f"Bearer {ML_API_KEY}"},
            timeout=30,
        )
        r.raise_for_status()
        d = r.json()
        return d.get("inserted", 0), d.get("skipped", 0)
    except Exception as e:
        log.error("ingest failed: %s", e)
        return 0, len(items)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--boards", nargs="+", default=["square", "hot", "ktalk", "beauty"])
    parser.add_argument("--pages", type=int, default=5)
    parser.add_argument("--page-start", type=int, default=1)
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    total_inserted = total_skipped = total_filtered = 0
    batch: list[dict] = []

    def flush():
        nonlocal total_inserted, total_skipped
        if not batch:
            return
        ins, sk = ingest_batch(batch, args.dry_run)
        total_inserted += ins
        total_skipped += sk
        log.info("batch ingested: +%d skipped=%d", ins, sk)
        batch.clear()

    for board in args.boards:
        log.info("=== board: %s, pages: %d-%d ===", board, args.page_start, args.page_start + args.pages - 1)
        for page in range(args.page_start, args.page_start + args.pages):
            links = get_post_links(board, page)
            log.info("  p%d: %d links", page, len(links))
            for path in links:
                time.sleep(DELAY)
                text = fetch_post(path)
                if not text or len(text) < MIN_LEN:
                    total_filtered += 1
                    continue
                if ko_ratio(text) < MIN_KO_RATIO:
                    total_filtered += 1
                    continue
                batch.append({
                    "community": COMMUNITY,
                    "contentType": "POST",
                    "text": text,
                    "label": "human",
                    "source": SOURCE,
                })
                if len(batch) >= BATCH_SIZE:
                    flush()
            time.sleep(DELAY)

    flush()
    log.info("done — inserted=%d skipped=%d filtered=%d", total_inserted, total_skipped, total_filtered)


if __name__ == "__main__":
    main()
