"""
더쿠(theqoo) 크롤러 — 연예·일상·20~30대 여초 커뮤니티
"""
import asyncio
import logging
import random
import re
from typing import List, Dict

import requests
from bs4 import BeautifulSoup

logger = logging.getLogger(__name__)

BASE_URLS = [
    "https://theqoo.net/hot",
    "https://theqoo.net/square?category=hot",
]

USER_AGENTS = [
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
]


async def crawl(daily_limit: int = 300) -> List[Dict]:
    """더쿠 크롤링 — 핫게시물 수집"""
    results = []
    session = requests.Session()
    session.headers.update({
        "User-Agent": random.choice(USER_AGENTS),
        "Referer": "https://theqoo.net",
    })

    posts = []
    seen = set()

    # Step 1: 목록에서 게시글 링크 수집
    for base_url in BASE_URLS:
        try:
            resp = session.get(base_url, timeout=10)
            resp.raise_for_status()
            await asyncio.sleep(random.uniform(1.0, 2.0))

            soup = BeautifulSoup(resp.text, "html.parser")

            # 셀렉터 시도: td.title a (주) 또는 tr.li td.title a (대체)
            links = soup.select("td.title a")
            if not links:
                links = soup.select("tr.li td.title a")
            if not links:
                links = soup.select("a.title")

            for link in links:
                href = link.get("href", "")
                # /square/숫자 또는 /숫자 패턴 추출
                match = re.search(r"/square/(\d+)", href)
                if not match:
                    match = re.search(r"/(\d+)/?$", href)
                if not match:
                    continue

                origin_id = match.group(1)
                if origin_id in seen:
                    continue
                seen.add(origin_id)

                title = link.get_text(strip=True)
                if not title:
                    continue

                posts.append({
                    "origin_id": origin_id,
                    "title": title,
                    "url": f"https://theqoo.net/square/{origin_id}",
                })

            logger.info(f"theqoo hot page: {len(posts)} posts collected")

        except Exception as e:
            logger.warning(f"Failed to fetch theqoo list: {e}")
            continue

    logger.info(f"Total theqoo posts found: {len(posts)}")

    # Step 2: 각 게시글 상세 페이지에서 내용 추출
    for post in posts:
        if len(results) >= daily_limit:
            break

        try:
            resp = session.get(post["url"], timeout=10)
            resp.raise_for_status()
            await asyncio.sleep(random.uniform(1.0, 2.0))

            soup = BeautifulSoup(resp.text, "html.parser")

            # 본문 셀렉터: .xe_content (주) 또는 div[class*='content'] (대체)
            content_area = soup.select_one(".xe_content")
            if not content_area:
                content_area = soup.select_one("div.xe_content")
            if not content_area:
                content_area = soup.select_one("div[class*='content']")

            if content_area:
                content = content_area.get_text("\n", strip=True)

                if content and len(content) >= 30:
                    results.append({
                        "content": content[:2000],
                        "content_type": "POST",
                        "source": "theqoo",
                        "category": "hot",
                        "title": post.get("title"),
                        "source_url": post.get("url"),
                    })
                    logger.debug(f"theqoo {post['origin_id']}: saved {len(content)} chars")

        except Exception as e:
            logger.debug(f"Failed to parse theqoo post {post['url']}: {e}")
            continue

    logger.info(f"theqoo crawl completed: {len(results)} items")
    return results
