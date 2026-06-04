"""
DCInside 크롤러 — WaggleBot 코드 기반 (직접 포팅)
가장 신뢰할 수 있는 크롤러
"""
import asyncio
import logging
import re
from typing import List, Dict

import requests
from bs4 import BeautifulSoup

logger = logging.getLogger(__name__)

BASE_URL = "https://gall.dcinside.com"
COMMENT_API_URL = "https://m.dcinside.com/ajax/response-comment"

# WaggleBot과 동일한 섹션
SECTIONS = [
    {"name": "실시간 베스트 (실베)", "url": "https://gall.dcinside.com/board/lists/?id=dcbest"},
    {"name": "HIT 갤러리 (힛갤)", "url": "https://gall.dcinside.com/board/lists/?id=hit"},
]

USER_AGENTS = [
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36",
]


def parse_board_href(href: str) -> tuple:
    """WaggleBot: href에서 (id, no) 추출"""
    id_m = re.search(r"[?&]id=([^&#]+)", href)
    no_m = re.search(r"[?&]no=(\d+)", href)
    if id_m and no_m:
        return id_m.group(1), no_m.group(1)
    return "", ""


def clean_listing_title(raw: str) -> str:
    """WaggleBot: 갤러리 접두어·아이콘 제거"""
    text = re.sub(r"^\[[^\]]{1,20}\]\s*", "", raw.strip())
    return text.strip()


def iter_post_rows(soup: BeautifulSoup):
    """WaggleBot: 테이블·리스트 양쪽 레이아웃 처리"""
    # 테이블 기반 레이아웃
    rows = soup.select("table.gall_list tbody tr.us-post")
    if not rows:
        rows = soup.select("tr.ub-content")

    if rows:
        return (
            r for r in rows
            if "notice" not in " ".join(r.get("class", []))
            and "ad" not in " ".join(r.get("class", []))
        )

    # 리스트 기반 레이아웃
    all_li = soup.select("ul.gall-list li") or soup.select("ul li")
    return (li for li in all_li if li.find("a", href=re.compile(r"/board/view/")))


async def crawl(daily_limit: int = 450) -> List[Dict]:
    """DCInside 크롤링 — WaggleBot 포팅"""
    results = []
    session = requests.Session()
    session.headers.update({
        "Referer": "https://www.dcinside.com/",
        "User-Agent": USER_AGENTS[0],
    })

    posts = []
    seen = set()

    # Step 1: 목록에서 게시글 링크 수집
    for section in SECTIONS:
        try:
            resp = session.get(section["url"], timeout=10)
            resp.raise_for_status()
            await asyncio.sleep(0.5)

            soup = BeautifulSoup(resp.text, "html.parser")
            section_count = 0

            for row in iter_post_rows(soup):
                # WaggleBot: 3가지 폴백 셀렉터
                link = (
                    row.select_one("td.gall_tit a:first-child")
                    or row.select_one("a.newtxt")
                    or row.select_one("a[href*='/board/view/']")
                )

                if not link:
                    continue

                href = link.get("href", "")
                gall_id, post_no = parse_board_href(href)
                if not gall_id or not post_no:
                    continue

                origin_id = f"{gall_id}_{post_no}"
                if origin_id in seen:
                    continue
                seen.add(origin_id)

                title = clean_listing_title(link.get_text(strip=True))
                if not title:
                    continue

                url = BASE_URL + href if href.startswith("/") else href
                posts.append({
                    "origin_id": origin_id,
                    "title": title,
                    "url": url,
                    "gall_id": gall_id,
                    "post_no": post_no,
                })
                section_count += 1

            logger.info(f"Section '{section['name']}': {section_count} posts")

        except Exception as e:
            logger.warning(f"Failed to fetch section: {e}")
            continue

    logger.info(f"Total posts found: {len(posts)}")

    # Step 2: 각 게시글 상세 페이지에서 내용 추출
    for post in posts:
        if len(results) >= daily_limit:
            break

        try:
            resp = session.get(post["url"], timeout=10)
            resp.raise_for_status()
            await asyncio.sleep(0.5)

            soup = BeautifulSoup(resp.text, "html.parser")

            # 본문 추출
            body_el = soup.select_one("div.writing_view_box")
            if body_el:
                content = body_el.get_text("\n", strip=True)
                # 출처 표기 제거
                content = re.sub(r"출처\s*:.*?(?:\[원본\s*보기\])?$", "", content, flags=re.MULTILINE).strip()

                if content and len(content) > 10:
                    results.append({
                        "content": content[:2000],
                        "content_type": "POST",
                        "source": "dcinside",
                        "category": post["gall_id"],
                    })
                    logger.debug(f"Post {post['post_no']}: saved {len(content)} chars")

        except Exception as e:
            logger.debug(f"Failed to parse post {post['url']}: {e}")
            continue

    logger.info(f"DCInside crawl completed: {len(results)} items")
    return results
