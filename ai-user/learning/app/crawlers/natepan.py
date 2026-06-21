"""
네이트판 크롤러 v3 — Phase 1 공격 크롤 (2026-06-21)
변경: 다중 섹션 + 페이지네이션 + 작성자/게시시각 캡처 + 갈등 필터
"""
import asyncio
import logging
import random
import re
from datetime import datetime
from typing import List, Dict, Optional

import requests
from bs4 import BeautifulSoup

logger = logging.getLogger(__name__)

# ── 섹션 정의 ──────────────────────────────────────────────────
# 베스트/인기글: conflict-heavy (NATEPAN 특성상 갈등 사연이 상위권)
# 전체글: 가장 많은 volume, 깊은 backfill용
SECTIONS = [
    {"name": "베스트",  "base_url": "https://pann.nate.com/talk/ranking",      "max_pages": 30},
    {"name": "인기글",  "base_url": "https://pann.nate.com/talk/ranking/best", "max_pages": 30},
    {"name": "전체글",  "base_url": "https://pann.nate.com/talk",              "max_pages": 80},
]

USER_AGENTS = [
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:124.0) Gecko/20100101 Firefox/124.0",
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
]

_DROP_KEYWORDS = ["광고", "공지", "이벤트", "혜택안내", "앱 다운", "무료체험"]

MIN_CONTENT_LEN = 80
MAX_CONTENT_LEN = 8000


def _make_session() -> requests.Session:
    s = requests.Session()
    s.headers.update({
        "User-Agent": random.choice(USER_AGENTS),
        "Accept-Language": "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7",
        "Referer": "https://pann.nate.com/",
    })
    return s


def _page_url(base_url: str, page: int) -> str:
    sep = "&" if "?" in base_url else "?"
    return f"{base_url}{sep}page={page}" if page > 1 else base_url


def _extract_author_from_li(li) -> Optional[str]:
    for sel in [
        "dd.dt .nick", "dd .usernick", ".user_name",
        ".nickName", "span[class*='nick']", "em.nick",
        ".writtenBy", ".post_info .nick",
    ]:
        el = li.select_one(sel)
        if el:
            txt = el.get_text(strip=True)
            if txt and len(txt) <= 50:
                return txt
    return None


def _extract_time_from_li(li) -> Optional[str]:
    for sel in [
        "dd.dt .date", "dd .date", ".write_date", ".post_date",
        ".time", "span[class*='date']", "dd.info .date",
    ]:
        el = li.select_one(sel)
        if el:
            txt = el.get_text(strip=True)
            if txt and re.search(r"\d{2,4}", txt):
                return txt
    return None


def _extract_author_from_detail(soup) -> Optional[str]:
    for sel in [
        ".user-info .name", ".user_info .nick", ".user_name",
        ".writer_info .nick", ".nickName", ".user .name",
        ".post_user .nick", "div.user em", ".tit_nick",
    ]:
        el = soup.select_one(sel)
        if el:
            txt = el.get_text(strip=True)
            if txt and 1 <= len(txt) <= 50:
                return txt
    return None


def _extract_time_from_detail(soup) -> Optional[str]:
    for sel in [
        ".post_date", ".write_date", ".writeDate",
        ".date", ".time_info", "span[class*='date']",
        ".info_area .date", ".post_info .date",
    ]:
        el = soup.select_one(sel)
        if el:
            txt = el.get_text(strip=True)
            if txt and re.search(r"\d{2,4}", txt):
                return txt
    return None


def _parse_posted_at(raw: Optional[str]) -> Optional[str]:
    if not raw:
        return None
    raw = raw.strip()
    for fmt in ("%Y.%m.%d %H:%M", "%Y-%m-%d %H:%M", "%Y.%m.%d", "%Y-%m-%d",
                "%m.%d %H:%M", "%m/%d %H:%M"):
        try:
            dt = datetime.strptime(raw, fmt)
            if dt.year == 1900:
                dt = dt.replace(year=datetime.now().year)
            return dt.strftime("%Y-%m-%d %H:%M:%S")
        except ValueError:
            pass
    return raw[:32]


def _extract_content(soup) -> Optional[str]:
    for sel in [
        "div.talk-content div.text",
        "div#contentArea",
        "div.content_area",
        "div.post_content",
        "div.talk_content",
        "article.post_body",
    ]:
        el = soup.select_one(sel)
        if el:
            txt = el.get_text("\n", strip=True)
            if txt and len(txt) >= MIN_CONTENT_LEN:
                return txt[:MAX_CONTENT_LEN]
    return None


def _is_conflict_related(title: str, content: str) -> bool:
    text = (title or "") + (content or "")
    for kw in _DROP_KEYWORDS:
        if kw in text:
            return False
    return True


def _extract_comments(soup, limit: int = 5) -> List[str]:
    texts = []
    for el in soup.select(".cmt_list dd.usertxt")[:limit]:
        txt = el.get_text(" ", strip=True)
        if txt and 10 <= len(txt) <= 500:
            texts.append(txt)
    return texts


async def crawl(daily_limit: int = 1000) -> List[Dict]:
    """네이트판 공격 크롤 — 다중 섹션 + 페이지네이션 + 작성자/시각 캡처."""
    results = []
    seen_ids: set = set()
    session = _make_session()

    post_count = 0
    comment_count = 0

    for section in SECTIONS:
        if post_count >= daily_limit:
            break

        name = section["name"]
        base_url = section["base_url"]
        max_pages = section["max_pages"]
        consecutive_empty = 0

        for page in range(1, max_pages + 1):
            if post_count >= daily_limit:
                break

            page_url = _page_url(base_url, page)
            try:
                session.headers.update({"User-Agent": random.choice(USER_AGENTS)})
                resp = session.get(page_url, timeout=12)
                resp.raise_for_status()
                await asyncio.sleep(random.uniform(0.8, 1.5))

                soup = BeautifulSoup(resp.text, "html.parser")

                post_items = []
                for li in soup.select("div.cntList ul.post_wrap li"):
                    link = li.select_one("dl dt h2 a")
                    if not link:
                        continue
                    href = link.get("href", "")
                    m = re.search(r"/talk/(\d+)", href)
                    if not m:
                        continue
                    origin_id = m.group(1)
                    if origin_id in seen_ids:
                        continue
                    seen_ids.add(origin_id)

                    title = link.get("title") or link.get_text(strip=True)
                    post_items.append({
                        "origin_id": origin_id,
                        "title": title,
                        "url": f"https://pann.nate.com/talk/{origin_id}",
                        "author_listing": _extract_author_from_li(li),
                        "time_listing": _extract_time_from_li(li),
                    })

                if not post_items:
                    consecutive_empty += 1
                    if consecutive_empty >= 2:
                        logger.info(f"Section '{name}' p{page}: 연속 빈 페이지 → 섹션 종료")
                        break
                    continue
                consecutive_empty = 0

                logger.debug(f"Section '{name}' p{page}: {len(post_items)} 신규 포스트")

                for item in post_items:
                    if post_count >= daily_limit:
                        break
                    try:
                        session.headers.update({"User-Agent": random.choice(USER_AGENTS)})
                        post_resp = session.get(item["url"], timeout=12)
                        post_resp.raise_for_status()
                        await asyncio.sleep(random.uniform(0.5, 1.2))

                        post_soup = BeautifulSoup(post_resp.text, "html.parser")
                        content = _extract_content(post_soup)
                        if not content:
                            continue

                        if not _is_conflict_related(item["title"] or "", content):
                            continue

                        author = item["author_listing"] or _extract_author_from_detail(post_soup)
                        time_raw = item["time_listing"] or _extract_time_from_detail(post_soup)
                        posted_at = _parse_posted_at(time_raw)

                        results.append({
                            "content": content,
                            "content_type": "POST",
                            "source": "natepan",
                            "category": "talk",
                            "title": item.get("title"),
                            "source_url": item.get("url"),
                            "author_id": author,
                            "posted_at": posted_at,
                        })
                        post_count += 1

                        if comment_count < daily_limit // 5:
                            for txt in _extract_comments(post_soup, limit=3):
                                results.append({
                                    "content": txt,
                                    "content_type": "COMMENT",
                                    "source": "natepan",
                                    "category": "talk",
                                    "source_url": item.get("url"),
                                    "author_id": None,
                                    "posted_at": None,
                                })
                                comment_count += 1

                    except Exception as e:
                        logger.debug(f"Post {item['url']} 오류: {e}")
                        continue

            except Exception as e:
                logger.warning(f"Section '{name}' p{page} 오류: {e}")
                await asyncio.sleep(2)
                continue

        logger.info(f"Section '{name}' 완료 — posts={post_count}, comments={comment_count}")

    logger.info(f"NATEPAN 크롤 완료: posts={post_count}, comments={comment_count}, total={len(results)}")
    return results
