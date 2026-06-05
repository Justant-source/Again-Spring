"""
루리웹(RULIWEB) 크롤러
게임+일상 혼합, 20~40대, 반말+존댓말 혼용, RULIWEB Voice
"""
import asyncio
import logging
import random
import re
from typing import List, Dict

import requests
from bs4 import BeautifulSoup

logger = logging.getLogger(__name__)

BASE_URL = "https://bbs.ruliweb.com"
BOARD_URL = "https://bbs.ruliweb.com/community/board/300143"

USER_AGENTS = [
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
]


async def crawl(daily_limit: int = 300) -> List[Dict]:
    """루리웹 자유게시판 크롤링"""
    results = []
    session = requests.Session()
    session.headers.update({"User-Agent": random.choice(USER_AGENTS)})

    posts = []
    seen = set()

    # Step 1: 목록 페이지에서 게시글 링크 수집
    # 페이지네이션: ?page=1, ?page=2 등
    for page in range(1, 5):  # 처음 4페이지에서 수집
        try:
            page_url = f"{BOARD_URL}?page={page}"
            resp = session.get(page_url, timeout=10)
            resp.raise_for_status()
            await asyncio.sleep(random.uniform(0.8, 1.5))

            soup = BeautifulSoup(resp.text, "html.parser")

            # 루리웹 게시판 셀렉터들 (폴백 처리)
            # 시도 1: table 기반 레이아웃
            rows = soup.select("table tbody tr")
            if not rows:
                # 시도 2: div 기반 리스트
                rows = soup.select("div.list-container div.list-item")

            for row in rows:
                # 링크 찾기 - 여러 셀렉터 시도
                link = None
                for selector in ["a.subject_link", "a.title", "td.subject a", "div.title a"]:
                    link = row.select_one(selector)
                    if link:
                        break

                if not link:
                    continue

                href = link.get("href", "")
                # /community/board/300143/read/숫자 패턴 확인
                match = re.search(r"/community/board/300143/read/(\d+)", href)
                if not match:
                    # 상대 경로일 수 있음
                    if "/read/" in href and href.startswith("/"):
                        match = re.search(r"/read/(\d+)", href)
                    else:
                        continue

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
                    post_url = BASE_URL + "/" + href

                posts.append({
                    "origin_id": origin_id,
                    "title": title,
                    "url": post_url,
                })

            logger.info(f"Ruliweb page {page}: {len(posts)} posts collected")

        except Exception as e:
            logger.warning(f"Failed to fetch ruliweb page {page}: {e}")
            continue

    logger.info(f"Total ruliweb posts found: {len(posts)}")

    # Step 2: 각 게시글 상세 페이지에서 내용 추출
    for post in posts:
        if len(results) >= daily_limit:
            break

        try:
            resp = session.get(post["url"], timeout=10)
            resp.raise_for_status()
            await asyncio.sleep(random.uniform(0.8, 1.5))

            soup = BeautifulSoup(resp.text, "html.parser")

            # 본문 추출 - 여러 셀렉터 시도
            content = None
            for selector in ["div.view_content", "div#content", "article.article_body", "div[class*='content']"]:
                content_el = soup.select_one(selector)
                if content_el:
                    content = content_el.get_text("\n", strip=True)
                    break

            if not content:
                continue

            # 최소 길이 확인 (30자 이상)
            if len(content) < 30:
                continue

            results.append({
                "content": content[:2000],  # 최대 2000자
                "content_type": "POST",
                "source": "ruliweb",
                "category": "freeboard",
            })
            logger.debug(f"Post {post['origin_id']}: saved {len(content)} chars")

        except Exception as e:
            logger.debug(f"Failed to parse post {post['url']}: {e}")
            continue

    logger.info(f"Ruliweb crawl completed: {len(results)} items")
    return results
