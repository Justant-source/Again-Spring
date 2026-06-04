"""
네이트판 크롤러 v2 — WaggleBot 셀렉터 기반
"""
import asyncio
import logging
import random
import re
from typing import List, Dict

import requests
from bs4 import BeautifulSoup

logger = logging.getLogger(__name__)

SECTIONS = [
    ("베스트", "https://pann.nate.com/talk/ranking"),
    ("인기글", "https://pann.nate.com/talk/ranking/best"),
]

USER_AGENTS = [
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36",
]


async def crawl(daily_limit: int = 400) -> List[Dict]:
    """네이트판 크롤링 - WaggleBot 셀렉터 기반"""
    results = []
    session = requests.Session()

    for section_name, section_url in SECTIONS:
        if len(results) >= daily_limit:
            break

        try:
            resp = session.get(section_url, timeout=10, headers={"User-Agent": random.choice(USER_AGENTS)})
            resp.raise_for_status()
            await asyncio.sleep(random.uniform(2, 3))

            soup = BeautifulSoup(resp.text, "html.parser")

            # WaggleBot 방식
            posts_found = 0
            for li in soup.select("div.cntList ul.post_wrap li"):
                if len(results) >= daily_limit:
                    break

                # WaggleBot: "dl dt h2 a" 셀렉터
                link = li.select_one("dl dt h2 a")
                if not link:
                    continue

                href = link.get("href", "")
                match = re.search(r"/talk/(\d+)", href)
                if not match:
                    continue

                post_id = match.group(1)
                post_url = f"https://pann.nate.com/talk/{post_id}"

                try:
                    post_resp = session.get(post_url, timeout=10, headers={"User-Agent": random.choice(USER_AGENTS)})
                    post_resp.raise_for_status()
                    await asyncio.sleep(random.uniform(1, 2))

                    post_soup = BeautifulSoup(post_resp.text, "html.parser")

                    # 원글 추출
                    content = post_soup.select_one("div.talk-content div.text")
                    if content:
                        post_text = content.get_text(strip=True)
                        if post_text and len(post_text) > 10:
                            results.append({
                                "content": post_text,
                                "content_type": "POST",
                                "source": "natepan",
                                "category": "talk",
                            })
                            logger.debug(f"Post {post_id}: saved POST")
                            posts_found += 1

                except Exception as e:
                    logger.debug(f"Failed to fetch post {post_id}: {e}")
                    continue

            logger.info(f"Section '{section_name}': {posts_found} posts")

        except Exception as e:
            logger.warning(f"Failed to fetch section {section_name}: {e}")
            continue

    logger.info(f"Nate Pann crawl completed: {len(results)} items")
    return results
