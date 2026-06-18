"""더쿠 게시판 크롤링 → ML corpus 인제스트.

Usage:
  THEQOO_ML_API_KEY=... python crawl_theqoo.py --dry-run
  THEQOO_ML_API_KEY=... python crawl_theqoo.py --boards square hot ktalk beauty --pages 10

Defaults: boards=square,hot,ktalk,beauty / pages=5 / page_start=1
API default: http://100.115.252.61:8201/corpus/ingest
"""
import argparse
import html
import os
import re
import time
import logging
import requests
from typing import Optional

logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")
log = logging.getLogger(__name__)

DEFAULT_BOARDS = ["square", "hot", "ktalk", "beauty"]
ML_API = os.getenv("THEQOO_ML_API", "http://100.115.252.61:8201")
ML_API_KEY = os.getenv("THEQOO_ML_API_KEY", "")
COMMUNITY = os.getenv("THEQOO_COMMUNITY", "THEQOO")
SOURCE = os.getenv("THEQOO_SOURCE", "theqoo")
HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Accept-Language": "ko-KR,ko;q=0.9",
    "Referer": "https://theqoo.net/",
}
DELAY = float(os.getenv("THEQOO_DELAY_SECONDS", "1.2"))  # 요청 간격(초) — 서버 부하 방지
MIN_LEN = int(os.getenv("THEQOO_MIN_LEN", "80"))
MIN_KO_RATIO = float(os.getenv("THEQOO_MIN_KO_RATIO", "0.10"))
BATCH_SIZE = int(os.getenv("THEQOO_BATCH_SIZE", "50"))


def ko_ratio(text: str) -> float:
    stripped = text.replace(" ", "")
    if not stripped:
        return 0.0
    return sum(1 for c in stripped if "가" <= c <= "힣") / len(stripped)


def extract_text(raw_html: str) -> Optional[str]:
    """더쿠 본문 후보 블록에서 텍스트를 추출한다."""
    patterns = [
        r'itemprop="articleBody"[^>]*>(.*?)</article>',
        r'<div[^>]+class="[^"]*\bxe_content\b[^"]*"[^>]*>(.*?)</div>',
        r'<article[^>]*>(.*?)</article>',
    ]
    inner = None
    for pattern in patterns:
        match = re.search(pattern, raw_html, re.DOTALL | re.IGNORECASE)
        if match:
            inner = match.group(1)
            break

    if not inner:
        return None

    text = re.sub(r"<script.*?</script>", " ", inner, flags=re.DOTALL | re.IGNORECASE)
    text = re.sub(r"<style.*?</style>", " ", text, flags=re.DOTALL | re.IGNORECASE)
    text = re.sub(r"<[^>]+>", " ", text)
    text = html.unescape(text)
    text = re.sub(r"\s+", " ", text).strip()
    return text or None


def build_board_url(board: str, page: int) -> str:
    base = f"https://theqoo.net/{board}"
    return base if page <= 1 else f"{base}?page={page}"


def get_post_links(session: requests.Session, board: str, page: int) -> list[str]:
    url = build_board_url(board, page)
    try:
        r = session.get(url, timeout=10)
        r.raise_for_status()
    except Exception as e:
        log.warning("board fetch failed %s p%d: %s", board, page, e)
        return []

    candidates = re.findall(r'href="(/[^"#?]+/\d+)"', r.text)
    normalized: list[str] = []
    seen = set()
    for path in candidates:
        if not path.startswith("/") or path in seen:
            continue
        if not re.search(r"/\d+$", path):
            continue
        seen.add(path)
        normalized.append(path)
    return normalized


def fetch_post(session: requests.Session, path: str) -> Optional[str]:
    url = f"https://theqoo.net{path}"
    try:
        r = session.get(url, timeout=10)
        r.raise_for_status()
    except Exception as e:
        log.warning("post fetch failed %s: %s", path, e)
        return None
    return extract_text(r.text)


def ingest_batch(items: list[dict], dry_run: bool) -> tuple[int, int]:
    if dry_run:
        log.info("[dry-run] would ingest %d items", len(items))
        return len(items), 0
    if not ML_API_KEY:
        log.error("THEQOO_ML_API_KEY is required when --dry-run is not set")
        return 0, len(items)
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
    parser.add_argument("--boards", nargs="+", default=DEFAULT_BOARDS)
    parser.add_argument("--pages", type=int, default=5)
    parser.add_argument("--page-start", type=int, default=1)
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    session = requests.Session()
    session.headers.update(HEADERS)

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
            links = get_post_links(session, board, page)
            log.info("  p%d: %d links", page, len(links))
            for path in links:
                time.sleep(DELAY)
                text = fetch_post(session, path)
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
