"""
엠엘비파크(MLBPARK) 크롤러
토론·진지형, 30~50대 남초, 장문 논쟁, MLBPARK Voice
"""
import asyncio
import logging
import random
import re
from typing import List, Dict

import requests
from bs4 import BeautifulSoup

logger = logging.getLogger(__name__)

BASE_URL = "http://mlbpark.donga.com"
BULLPEN_URL = "http://mlbpark.donga.com/mp/b.php?b=bullpen"

USER_AGENTS = [
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
]


async def crawl(daily_limit: int = 200) -> List[Dict]:
    """엠엘비파크 불펜(자유게시판) 크롤링"""
    results = []
    session = requests.Session()
    session.headers.update({"User-Agent": random.choice(USER_AGENTS)})

    posts = []
    seen = set()

    # Step 1: 목록 페이지에서 게시글 링크 수집
    # 페이지네이션: &page=1, &page=2 등
    for page in range(1, 6):  # 처음 5페이지에서 수집
        try:
            page_url = f"{BULLPEN_URL}&page={page}"
            resp = session.get(page_url, timeout=10)
            resp.raise_for_status()
            await asyncio.sleep(random.uniform(1.0, 2.0))

            soup = BeautifulSoup(resp.text, "html.parser")

            # 엠엘비파크 게시판 셀렉터들 (폴백 처리)
            # 시도 1: table 기반 레이아웃
            rows = soup.select("table tbody tr")
            if not rows:
                # 시도 2: div 기반 리스트
                rows = soup.select("div.list-container div.list-item")

            for row in rows:
                # 링크 찾기 - 여러 셀렉터 시도
                link = None
                for selector in ["td.title a", "a.subject", "div.title a", ".subject a"]:
                    link = row.select_one(selector)
                    if link:
                        break

                if not link:
                    continue

                href = link.get("href", "")
                # /mp/b.php?b=bullpen&id=숫자 패턴 확인
                match = re.search(r"[?&]id=(\d+)", href)
                if not match:
                    continue

                origin_id = match.group(1)
                if origin_id in seen:
                    continue
                seen.add(origin_id)

                title = link.get_text(strip=True)
                if not title:
                    continue

                # 전체 URL 구성
                if href.startswith("http"):
                    post_url = href
                elif href.startswith("/"):
                    post_url = BASE_URL + href
                else:
                    # 상대 경로인 경우
                    post_url = BASE_URL + "/mp/" + href if not href.startswith("b.php") else BASE_URL + "/mp/" + href

                posts.append({
                    "origin_id": origin_id,
                    "title": title,
                    "url": post_url,
                })

            logger.info(f"MLBPARK page {page}: {len(posts)} posts collected")

        except Exception as e:
            logger.warning(f"Failed to fetch mlbpark page {page}: {e}")
            continue

    logger.info(f"Total mlbpark posts found: {len(posts)}")

    # Step 2: 각 게시글 상세 페이지에서 내용 추출
    for post in posts:
        if len(results) >= daily_limit:
            break

        try:
            resp = session.get(post["url"], timeout=10)
            resp.raise_for_status()
            await asyncio.sleep(random.uniform(1.0, 2.0))

            soup = BeautifulSoup(resp.text, "html.parser")

            # 본문 추출 - 여러 셀렉터 시도
            content = None
            for selector in ["div#body", "div.post_body", "td#view_content", "div.article_body", "div[class*='content']"]:
                content_el = soup.select_one(selector)
                if content_el:
                    content = content_el.get_text("\n", strip=True)
                    break

            if not content:
                continue

            # 최소 길이 확인 (40자 이상 - 장문 사이트)
            if len(content) < 40:
                continue

            results.append({
                "content": content[:2000],  # 최대 2000자
                "content_type": "POST",
                "source": "mlbpark",
                "category": "bullpen",
            })
            logger.debug(f"Post {post['origin_id']}: saved {len(content)} chars")

        except Exception as e:
            logger.debug(f"Failed to parse post {post['url']}: {e}")
            continue

    logger.info(f"MLBPARK crawl completed: {len(results)} items")
    return results
