"""
네이트판 크롤러 — WaggleBot 코드 기반
"""
import asyncio
import logging
import re
from typing import List, Dict

import requests
from bs4 import BeautifulSoup

logger = logging.getLogger(__name__)

POST_BASE = "https://pann.nate.com/talk/"

SECTIONS = [
    {"name": "톡톡 베스트", "url": "https://pann.nate.com/talk/ranking"},
    {"name": "톡커들의 선택", "url": "https://pann.nate.com/talk/ranking/best"},
]

USER_AGENTS = [
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
]

# 댓글 수집 (문체 현실화 S2 — AI 댓글 문체 앵커용 코퍼스)
COMMENT_LIMIT = 200       # 일일 댓글 수집 상한
COMMENTS_PER_POST = 10    # 글당 상위 댓글 수


def _extract_comments(soup) -> List[str]:
    """상세 페이지에서 댓글 텍스트 추출 — 서버 렌더링 `.cmt_list dd.usertxt` (2026-06-11 검증)."""
    texts = []
    for el in soup.select(".cmt_list dd.usertxt")[:COMMENTS_PER_POST]:
        txt = el.get_text(" ", strip=True)
        if txt and 5 <= len(txt) <= 500:
            texts.append(txt)
    return texts


async def crawl(daily_limit: int = 400) -> List[Dict]:
    """네이트판 크롤링 — WaggleBot 포팅"""
    results = []
    session = requests.Session()
    session.headers.update({"User-Agent": USER_AGENTS[0]})

    posts = []
    seen = set()

    # Step 1: 목록에서 게시글 링크 수집
    for section in SECTIONS:
        try:
            resp = session.get(section["url"], timeout=10)
            resp.raise_for_status()
            await asyncio.sleep(0.5)

            soup = BeautifulSoup(resp.text, "html.parser")

            # WaggleBot: "div.cntList ul.post_wrap li" → "dl dt h2 a"
            for li in soup.select("div.cntList ul.post_wrap li"):
                link = li.select_one("dl dt h2 a")
                if not link:
                    continue

                href = link.get("href", "")
                match = re.search(r"/talk/(\d+)", href)
                if not match:
                    continue

                origin_id = match.group(1)
                if origin_id in seen:
                    continue
                seen.add(origin_id)

                title = link.get("title") or link.get_text(strip=True)
                if not title:
                    continue

                posts.append({
                    "origin_id": origin_id,
                    "title": title,
                    "url": POST_BASE + origin_id,
                })

            logger.info(f"Section '{section['name']}': {len(posts)} posts")

        except Exception as e:
            logger.warning(f"Failed to fetch section: {e}")
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
            await asyncio.sleep(0.5)

            soup = BeautifulSoup(resp.text, "html.parser")

            # WaggleBot: contentArea에서 본문 추출
            content_area = soup.select_one("div#contentArea")
            if content_area:
                content = content_area.get_text("\n", strip=True)

                if content and len(content) > 10:
                    results.append({
                        "content": content[:2000],
                        "content_type": "POST",
                        "source": "natepan",
                        "category": "talk",
                    })
                    post_count += 1
                    logger.debug(f"Post {post['origin_id']}: saved {len(content)} chars")

            # Step 2b: 댓글 추출 — 실패해도 글 수집에 영향 없음 (문체 현실화 S2)
            if comment_count < COMMENT_LIMIT:
                try:
                    for txt in _extract_comments(soup):
                        if comment_count >= COMMENT_LIMIT:
                            break
                        results.append({
                            "content": txt,
                            "content_type": "COMMENT",
                            "source": "natepan",
                            "category": "talk",
                        })
                        comment_count += 1
                except Exception as ce:
                    logger.debug(f"Comment parse failed {post['url']}: {ce}")

        except Exception as e:
            logger.debug(f"Failed to parse post {post['url']}: {e}")
            continue

    logger.info(f"Nate Pann crawl completed: {post_count} posts + {comment_count} comments")
    return results
