"""
클리앙 크롤러 — 자유게시판
IT·매너형, 30~50대, 정중한 존댓말, CLIEN Voice
"""
import asyncio
import logging
import random
from typing import List, Dict

import requests
from bs4 import BeautifulSoup

logger = logging.getLogger(__name__)

CLIEN_BASE = "https://www.clien.net"
FREEBOARD_URL = "https://www.clien.net/service/board/park"

USER_AGENTS = [
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:121.0) Gecko/20100101 Firefox/121.0",
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
]

# 댓글 수집 (문체 현실화 S2 — AI 댓글 문체 앵커용 코퍼스, 존댓말 레지스터 커버)
COMMENT_LIMIT = 200       # 일일 댓글 수집 상한
COMMENTS_PER_POST = 10    # 글당 상위 댓글 수


def _extract_comments(soup) -> List[str]:
    """상세 페이지에서 댓글 텍스트 추출 — 서버 렌더링 `.comment_row .comment_view` (2026-06-11 검증)."""
    texts = []
    for el in soup.select(".comment_row .comment_view")[:COMMENTS_PER_POST]:
        txt = el.get_text(" ", strip=True)
        if txt and 5 <= len(txt) <= 500:
            texts.append(txt)
    return texts


async def crawl(daily_limit: int = 250) -> List[Dict]:
    """클리앙 자유게시판 크롤링"""
    results = []
    session = requests.Session()
    session.headers.update({"User-Agent": random.choice(USER_AGENTS)})

    posts = []
    seen = set()

    # Step 1: 목록에서 게시글 링크 수집
    pages = [0, 1]  # 첫 2 페이지
    for page in pages:
        try:
            url = f"{FREEBOARD_URL}?po={page}"
            resp = session.get(url, timeout=10)
            resp.raise_for_status()
            await asyncio.sleep(random.uniform(1.0, 2.0))

            soup = BeautifulSoup(resp.text, "html.parser")

            # 셀렉터: a.list_subject (현행 DOM: a.list_subject > span.subject_fixed — 2026-06-11 수정)
            links = soup.select("a.list_subject")
            if not links:
                links = soup.select("span.subject_fixed a")  # 구버전 폴백
            if not links:
                links = soup.select("div.list_subject a")

            for link in links:
                href = link.get("href", "")
                if not href or "/service/board/park/" not in href:
                    continue

                # href 정규화
                if href.startswith("/"):
                    full_url = CLIEN_BASE + href
                else:
                    full_url = href

                # post_id 추출
                if "/service/board/park/" in full_url:
                    post_id = full_url.split("/service/board/park/")[-1].split("?")[0]
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

    # Step 2: 각 게시글 상세 페이지에서 내용 추출 (+ 같은 페이지에서 댓글 수집)
    post_count = 0
    comment_count = 0
    for post in posts:
        if post_count >= daily_limit:
            break

        try:
            resp = session.get(post["url"], timeout=10)
            resp.raise_for_status()
            await asyncio.sleep(random.uniform(1.0, 2.0))

            soup = BeautifulSoup(resp.text, "html.parser")

            # 셀렉터: div.post_content article 또는 div.post_article 또는 div[id='div_content']
            content_elem = soup.select_one("div.post_content article")
            if not content_elem:
                content_elem = soup.select_one("div.post_article")
            if not content_elem:
                content_elem = soup.select_one("div#div_content")

            if content_elem:
                content = content_elem.get_text("\n", strip=True)

                if content and len(content) >= 30:
                    results.append({
                        "content": content[:2000],
                        "content_type": "POST",
                        "source": "clien",
                        "category": "freeboard",
                        "title": post.get("title"),
                        "source_url": post.get("url"),
                    })
                    post_count += 1
                    logger.debug(f"Post {post['post_id']}: saved {len(content)} chars")

            # Step 2b: 댓글 추출 — 실패해도 글 수집에 영향 없음 (문체 현실화 S2)
            if comment_count < COMMENT_LIMIT:
                try:
                    for txt in _extract_comments(soup):
                        if comment_count >= COMMENT_LIMIT:
                            break
                        results.append({
                            "content": txt,
                            "content_type": "COMMENT",
                            "source": "clien",
                            "category": "freeboard",
                            "source_url": post.get("url"),
                        })
                        comment_count += 1
                except Exception as ce:
                    logger.debug(f"Comment parse failed {post['url']}: {ce}")

        except Exception as e:
            logger.debug(f"Failed to parse post {post['url']}: {e}")
            continue

    logger.info(f"Clien crawl completed: {post_count} posts + {comment_count} comments")
    return results
