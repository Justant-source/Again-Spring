"""
블라인드 크롤러 v2 — 향상된 반봇 우회
"""
import asyncio
import logging
import random
from typing import List, Dict

import requests
from bs4 import BeautifulSoup

logger = logging.getLogger(__name__)

# 더 정교한 헤더 spoofing
BROWSER_PROFILES = [
    {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
        "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7",
        "Accept-Language": "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7",
        "Accept-Encoding": "gzip, deflate, br",
        "Referer": "https://www.google.com/",
        "Sec-Ch-Ua": '"Google Chrome";v="131", "Chromium";v="131", "Not_A Brand";v="24"',
        "Sec-Ch-Ua-Mobile": "?0",
        "Sec-Ch-Ua-Platform": '"Windows"',
        "Sec-Fetch-Dest": "document",
        "Sec-Fetch-Mode": "navigate",
        "Sec-Fetch-Site": "none",
    }
]


async def crawl(daily_limit: int = 240) -> List[Dict]:
    """블라인드 크롤링 - 향상된 반봇 우회"""
    results = []
    session = requests.Session()

    # 프로필에서 헤더 선택
    profile = random.choice(BROWSER_PROFILES)
    session.headers.update(profile)

    topics_url = "https://www.teamblind.com/kr/topics"

    try:
        # 커스텀 retry 로직과 더 나은 타임아웃
        resp = None
        for attempt in range(3):
            try:
                resp = session.get(topics_url, timeout=15)
                if resp.status_code == 200:
                    break
                elif resp.status_code == 403:
                    logger.warning(f"Blind: 403 Forbidden (attempt {attempt+1}/3)")
                    await asyncio.sleep(random.uniform(3, 5))
                    continue
                else:
                    logger.warning(f"Blind: Status {resp.status_code}")
                    break
            except requests.Timeout:
                logger.warning(f"Blind: Timeout (attempt {attempt+1}/3)")
                await asyncio.sleep(random.uniform(2, 3))
                continue

        if not resp or resp.status_code != 200:
            logger.warning(f"Failed to fetch Blind after retries")
            return results

        resp.raise_for_status()
        await asyncio.sleep(random.uniform(2, 3))

        soup = BeautifulSoup(resp.text, "html.parser")

        # 모든 <a> 태그에서 게시글 링크 찾기
        posts_found = 0
        for link in soup.find_all("a"):
            if len(results) >= daily_limit:
                break

            href = link.get("href", "")
            # Blind 게시글 URL 패턴: /kr/post/... 또는 /kr/articles/...
            if not href or ("/kr/post/" not in href and "/kr/articles/" not in href):
                continue

            post_url = href if href.startswith("http") else f"https://www.teamblind.com{href}"

            try:
                # 각 게시글에 대해서도 헤더 업데이트
                session.headers.update(random.choice(BROWSER_PROFILES))
                post_resp = session.get(post_url, timeout=10)

                if post_resp.status_code == 403:
                    logger.debug(f"Post blocked by Blind")
                    continue

                post_resp.raise_for_status()
                await asyncio.sleep(random.uniform(1, 2))

                post_soup = BeautifulSoup(post_resp.text, "html.parser")

                # 원글 내용 추출
                content = post_soup.select_one("article") or post_soup.select_one("div[class*='post']") or post_soup.select_one("div[class*='content']")

                if content:
                    post_text = content.get_text(strip=True)
                    if post_text and len(post_text) > 10:
                        results.append({
                            "content": post_text[:1500],
                            "content_type": "POST",
                            "source": "blind",
                            "category": "workplace",
                        })
                        logger.debug(f"Blind post: saved")
                        posts_found += 1

            except Exception as e:
                logger.debug(f"Failed to fetch blind post: {e}")
                continue

        logger.info(f"Blind: {posts_found} posts fetched")

    except Exception as e:
        logger.warning(f"Failed to fetch Blind: {e}")

    logger.info(f"Blind crawl completed: {len(results)} items")
    return results
