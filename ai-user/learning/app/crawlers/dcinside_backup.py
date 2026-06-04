"""
DCInside 크롤러 v2 — WaggleBot 셀렉터 기반
"""
import asyncio
import logging
import random
import re
from typing import List, Dict

import requests
from bs4 import BeautifulSoup

logger = logging.getLogger(__name__)

BASE_URL = "https://gall.dcinside.com"
COMMENT_API_URL = "https://m.dcinside.com/ajax/response-comment"

SECTIONS = [
    ("life_incident", "https://gall.dcinside.com/board/lists/?id=life_incident&sort_type=recomm"),
    ("love", "https://gall.dcinside.com/board/lists/?id=love&sort_type=recomm"),
]

USER_AGENTS = [
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36",
]


async def crawl(daily_limit: int = 450) -> List[Dict]:
    """DCInside 크롤링 - WaggleBot 셀렉터 기반"""
    results = []
    session = requests.Session()
    session.headers.update({
        "Referer": "https://www.dcinside.com/",
        "User-Agent": random.choice(USER_AGENTS),
    })

    for section_name, section_url in SECTIONS:
        if len(results) >= daily_limit:
            break

        try:
            resp = session.get(section_url, timeout=10)
            resp.raise_for_status()
            await asyncio.sleep(random.uniform(2, 3))

            soup = BeautifulSoup(resp.text, "html.parser")

            # WaggleBot 방식: 테이블·리스트 양쪽 레이아웃 처리
            rows = soup.select("table.gall_list tbody tr.us-post")
            if not rows:
                rows = soup.select("tr.ub-content")

            if not rows:
                logger.warning(f"No rows found in {section_name}")
                continue

            logger.info(f"Found {len(rows)} rows in {section_name}")

            for row in rows:
                if len(results) >= daily_limit:
                    break

                # WaggleBot: 제목 링크 추출
                link = (
                    row.select_one("td.gall_tit a:first-child")
                    or row.select_one("a.newtxt")
                    or row.select_one("a[href*='/board/view/']")
                )

                if not link:
                    continue

                href = link.get("href", "")
                if not href:
                    continue

                # 갤러리 ID와 게시글 번호 추출
                match = re.search(r"[?&]id=([^&]+).*[?&]no=(\d+)", href)
                if not match:
                    continue

                gall_id, post_no = match.group(1), int(match.group(2))
                post_url = f"{BASE_URL}/board/view/?id={gall_id}&no={post_no}"

                try:
                    post_resp = session.get(post_url, timeout=10)
                    post_resp.raise_for_status()
                    await asyncio.sleep(random.uniform(1, 2))

                    post_soup = BeautifulSoup(post_resp.text, "html.parser")

                    # 원글 내용
                    post_content = post_soup.select_one(".write_div")
                    if post_content:
                        post_text = post_content.get_text(strip=True)
                        if post_text and len(post_text) > 10:
                            results.append({
                                "content": post_text,
                                "content_type": "POST",
                                "source": "dcinside",
                                "category": section_name,
                            })
                            logger.debug(f"Post {post_no}: saved POST")

                except Exception as e:
                    logger.debug(f"Failed to fetch post {post_no}: {e}")
                    continue

        except Exception as e:
            logger.warning(f"Failed to fetch section {section_name}: {e}")
            continue

    logger.info(f"DCInside crawl completed: {len(results)} items")
    return results
