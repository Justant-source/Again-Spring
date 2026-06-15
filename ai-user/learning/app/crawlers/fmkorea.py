"""
에펨코리아(FM코리아) 크롤러 — 유머·드립·20~30대 남초 커뮤니티
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
    "https://www.fmkorea.com/best",
    "https://www.fmkorea.com/best?page=2",
]

USER_AGENTS = [
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
]


async def crawl(daily_limit: int = 350) -> List[Dict]:
    """에펨코리아 크롤링 — 베스트/인기글 수집"""
    results = []
    session = requests.Session()
    session.headers.update({
        "User-Agent": random.choice(USER_AGENTS),
        "Referer": "https://www.fmkorea.com",
    })

    posts = []
    seen = set()

    # Step 1: 목록에서 게시글 링크 수집
    for base_url in BASE_URLS:
        try:
            resp = session.get(base_url, timeout=10)
            resp.raise_for_status()
            await asyncio.sleep(random.uniform(0.8, 1.5))

            soup = BeautifulSoup(resp.text, "html.parser")

            # 셀렉터 시도: .li_best a.title (주) 또는 ul.content_list li h3 a (대체)
            links = soup.select(".li_best a.title")
            if not links:
                links = soup.select("ul.content_list li h3 a")
            if not links:
                links = soup.select("a.title")

            for link in links:
                href = link.get("href", "")
                # /숫자 또는 /board/숫자 패턴 추출
                match = re.search(r"/(\d+)/?$", href)
                if not match:
                    match = re.search(r"/board/(\d+)", href)
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
                    "url": f"https://www.fmkorea.com/{origin_id}",
                })

            logger.info(f"FM Korea best page: {len(posts)} posts collected")

        except Exception as e:
            logger.warning(f"Failed to fetch FM Korea list: {e}")
            continue

    logger.info(f"Total FM Korea posts found: {len(posts)}")

    # Step 2: 각 게시글 상세 페이지에서 내용 추출
    for post in posts:
        if len(results) >= daily_limit:
            break

        try:
            resp = session.get(post["url"], timeout=10)
            resp.raise_for_status()
            await asyncio.sleep(random.uniform(0.8, 1.5))

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
                        "source": "fmkorea",
                        "category": "best",
                        "title": post.get("title"),
                        "source_url": post.get("url"),
                    })
                    logger.debug(f"FM Korea {post['origin_id']}: saved {len(content)} chars")

        except Exception as e:
            logger.debug(f"Failed to parse FM Korea post {post['url']}: {e}")
            continue

    logger.info(f"FM Korea crawl completed: {len(results)} items")
    return results
