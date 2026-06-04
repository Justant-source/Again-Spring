"""
보배드림 크롤러 v2 — requests + BeautifulSoup, URL 패턴 필터링
"""
import asyncio
import logging
import random
import re
from typing import List, Dict

import requests
from bs4 import BeautifulSoup

logger = logging.getLogger(__name__)

USER_AGENTS = [
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36",
]


async def crawl(daily_limit: int = 300) -> List[Dict]:
    """보배드림 자유게시판 크롤링"""
    results = []
    session = requests.Session()
    session.headers.update({"User-Agent": random.choice(USER_AGENTS)})

    board_url = "https://www.bobaedream.co.kr/board/list/freeb?sort=recommend"

    try:
        resp = session.get(board_url, timeout=15)
        resp.raise_for_status()
        await asyncio.sleep(random.uniform(2, 3))

        soup = BeautifulSoup(resp.text, "html.parser")

        # 모든 <a> 태그에서 게시글 링크 찾기 (URL 패턴 필터)
        posts_found = 0
        for link in soup.find_all("a"):
            if len(results) >= daily_limit:
                break

            href = link.get("href", "")
            # 보배드림 게시글 URL 패턴: /board/view.php?... 또는 /board/?...
            if not href or "/view" not in href or "board" not in href:
                continue

            post_url = href if href.startswith("http") else f"https://www.bobaedream.co.kr{href}"

            try:
                post_resp = session.get(post_url, timeout=10)
                post_resp.raise_for_status()
                await asyncio.sleep(random.uniform(1, 2))

                post_soup = BeautifulSoup(post_resp.text, "html.parser")

                # 원글 내용 추출 (다양한 셀렉터 시도)
                content = (
                    post_soup.select_one("div.article_content") or
                    post_soup.select_one("div[class*='content']") or
                    post_soup.select_one("article") or
                    post_soup.select_one("div.bbs_content")
                )

                if content:
                    post_text = content.get_text(strip=True)
                    if post_text and len(post_text) > 10:
                        results.append({
                            "content": post_text[:1500],
                            "content_type": "POST",
                            "source": "bobaedream",
                            "category": "freeb",
                        })
                        logger.debug(f"Bobaedream post: saved")
                        posts_found += 1

            except Exception as e:
                logger.debug(f"Failed to fetch bobaedream post: {e}")
                continue

        logger.info(f"Bobaedream: {posts_found} posts fetched")

    except Exception as e:
        logger.warning(f"Failed to fetch Bobaedream: {e}")

    logger.info(f"Bobaedream crawl completed: {len(results)} items")
    return results
