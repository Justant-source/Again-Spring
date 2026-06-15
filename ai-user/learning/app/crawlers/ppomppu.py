"""
뽐뿌 크롤러 — 자유게시판
생활·알뜰·존댓말, 30~50대, PPOMPPU Voice
"""
import asyncio
import logging
import random
from typing import List, Dict
from urllib.parse import urljoin

import requests
from bs4 import BeautifulSoup

logger = logging.getLogger(__name__)

PPOMPPU_BASE = "https://www.ppomppu.co.kr"
FREEBOARD_URL = "https://www.ppomppu.co.kr/zboard/zboard.php?id=freeboard"

USER_AGENTS = [
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:121.0) Gecko/20100101 Firefox/121.0",
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
]


async def crawl(daily_limit: int = 250) -> List[Dict]:
    """뽐뿌 자유게시판 크롤링"""
    results = []
    session = requests.Session()
    session.headers.update({
        "User-Agent": random.choice(USER_AGENTS),
        "Referer": PPOMPPU_BASE,
    })

    posts = []
    seen = set()

    # Step 1: 목록에서 게시글 링크 수집
    pages = [0, 1]  # 첫 2 페이지
    for page_idx, page in enumerate(pages):
        try:
            # 페이지 파라미터 추가 (필요시)
            if page_idx == 0:
                url = FREEBOARD_URL
            else:
                url = f"{FREEBOARD_URL}&page={page}"

            resp = session.get(url, timeout=10)
            resp.raise_for_status()
            await asyncio.sleep(random.uniform(0.8, 1.5))

            soup = BeautifulSoup(resp.text, "html.parser")

            # 셀렉터: table.list_table td.title a 또는 .title a
            links = soup.select("table.list_table td.title a")
            if not links:
                links = soup.select("td.title a")

            for link in links:
                href = link.get("href", "")
                if not href or "/zboard/view.php" not in href or "freeboard" not in href:
                    continue

                # 전체 URL 조합
                if href.startswith("http"):
                    full_url = href
                else:
                    full_url = urljoin(PPOMPPU_BASE, href)

                # post_id 추출 (no=숫자)
                if "no=" in full_url:
                    post_id = full_url.split("no=")[-1].split("&")[0]
                    if not post_id or post_id in seen:
                        continue
                    seen.add(post_id)

                    title = link.get_text(strip=True)
                    if not title:
                        continue

                    posts.append({
                        "post_id": post_id,
                        "title": title,
                        "url": full_url,
                    })

            logger.info(f"Page {page}: {len(posts)} posts total")

        except Exception as e:
            logger.warning(f"Failed to fetch page {page}: {e}")
            continue

    logger.info(f"Total posts found: {len(posts)}")

    # Step 2: 각 게시글 상세 페이지에서 내용 추출
    for post in posts:
        if len(results) >= daily_limit:
            break

        try:
            resp = session.get(post["url"], timeout=10)
            resp.raise_for_status()
            await asyncio.sleep(random.uniform(0.8, 1.5))

            soup = BeautifulSoup(resp.text, "html.parser")

            # 셀렉터: div#iboardContent 또는 td.view_content 또는 div.cont
            content_elem = soup.select_one("div#iboardContent")
            if not content_elem:
                content_elem = soup.select_one("td.view_content")
            if not content_elem:
                content_elem = soup.select_one("div.cont")

            if content_elem:
                content = content_elem.get_text("\n", strip=True)

                if content and len(content) >= 30:
                    results.append({
                        "content": content[:2000],
                        "content_type": "POST",
                        "source": "ppomppu",
                        "category": "freeboard",
                        "title": post.get("title"),
                        "source_url": post.get("url"),
                    })
                    logger.debug(f"Post {post['post_id']}: saved {len(content)} chars")

        except Exception as e:
            logger.debug(f"Failed to parse post {post['url']}: {e}")
            continue

    logger.info(f"Ppomppu crawl completed: {len(results)} items")
    return results
