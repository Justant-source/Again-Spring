"""
네이트판 크롤러 v4 — Phase 1 공격 크롤 (2026-06-21)
설계:
  1. 정적 랭킹 섹션 (오늘/일/주/월/연/연애 × 각 ~30건)
  2. 포스트 ID 범위 크롤 (순차 ID 직접 접근 → 역사 아카이브 대량 수집)
확인된 셀렉터: 작성자=a.writer, 본문=div.talk-content div.text 또는 div#contentArea
"""
import asyncio
import logging
import random
import re
from datetime import datetime, timedelta
from typing import List, Dict, Optional

import requests
from bs4 import BeautifulSoup

logger = logging.getLogger(__name__)

# ── 정적 랭킹 섹션 (페이지네이션 없음, 각 ~30건씩 고유 포스트) ──────────
STATIC_SECTIONS = [
    {"name": "베스트-오늘",  "url": "https://pann.nate.com/talk/ranking"},
    {"name": "베스트-일간",  "url": "https://pann.nate.com/talk/ranking/d"},
    {"name": "베스트-주간",  "url": "https://pann.nate.com/talk/ranking/w"},
    {"name": "베스트-월간",  "url": "https://pann.nate.com/talk/ranking/m"},
    {"name": "베스트-연간",  "url": "https://pann.nate.com/talk/ranking/y"},
    {"name": "연애-오늘",   "url": "https://pann.nate.com/talk/ranking?rankingType=lovetalk"},
    {"name": "연애-주간",   "url": "https://pann.nate.com/talk/ranking/w?rankingType=lovetalk"},
    {"name": "연애-월간",   "url": "https://pann.nate.com/talk/ranking/m?rankingType=lovetalk"},
    {"name": "연애-연간",   "url": "https://pann.nate.com/talk/ranking/y?rankingType=lovetalk"},
]

USER_AGENTS = [
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:124.0) Gecko/20100101 Firefox/124.0",
]

# 포스트 ID 범위
# 실측: 현재 최신 ID ≈ 375_474_000, 1포스트당 ~200 ID 간격
# 최신 3개월치(373M~376M) 밀집 구간 집중 → 순차(최신→과거) 크롤
ID_RANGE_START = 370_000_000
ID_RANGE_END   = 375_480_000

# 비-사연 필터
_DROP_KEYWORDS = ["광고", "공지", "이벤트", "혜택안내", "앱 다운", "무료체험"]
MIN_CONTENT_LEN = 80
MAX_CONTENT_LEN = 8000


def _make_session() -> requests.Session:
    s = requests.Session()
    s.headers.update({
        "User-Agent": random.choice(USER_AGENTS),
        "Accept-Language": "ko-KR,ko;q=0.9",
        "Referer": "https://pann.nate.com/",
    })
    return s


def _extract_author_from_li(li) -> Optional[str]:
    """목록 항목에서 작성자 추출. 확인된 셀렉터: a.writer"""
    el = li.select_one("a.writer")
    if el:
        txt = el.get_text(strip=True)
        if txt and 1 <= len(txt) <= 50:
            return txt
    return None


def _extract_author_from_detail(soup) -> Optional[str]:
    """상세 페이지에서 작성자 추출."""
    for sel in ["a.writer", ".user-info .name", ".writer_name", ".nick", "span.user_name"]:
        el = soup.select_one(sel)
        if el:
            txt = el.get_text(strip=True)
            if txt and 1 <= len(txt) <= 50:
                return txt
    return None


def _extract_time_from_detail(soup) -> Optional[str]:
    """상세 페이지에서 게시시각 추출."""
    for sel in [".post_date", ".write_date", ".writeDate", ".date",
                "span[class*='date']", ".info_area .date", "meta[property='article:published_time']"]:
        el = soup.select_one(sel)
        if el:
            txt = el.get("content") or el.get_text(strip=True)
            if txt and re.search(r"\d{2,4}", txt):
                return txt.strip()[:32]
    return None


def _parse_posted_at(raw: Optional[str]) -> Optional[str]:
    if not raw:
        return None
    raw = raw.strip()
    for fmt in ("%Y.%m.%d %H:%M", "%Y-%m-%dT%H:%M:%S", "%Y-%m-%d %H:%M:%S",
                "%Y-%m-%d %H:%M", "%Y.%m.%d", "%Y-%m-%d", "%m.%d %H:%M"):
        try:
            dt = datetime.strptime(raw[:19], fmt[:len(raw[:19])])
            if dt.year == 1900:
                dt = dt.replace(year=datetime.now().year)
            return dt.strftime("%Y-%m-%d %H:%M:%S")
        except ValueError:
            pass
    return raw[:32]


def _extract_content(soup) -> Optional[str]:
    for sel in ["div.talk-content div.text", "div#contentArea",
                "div.content_area", "div.post_content"]:
        el = soup.select_one(sel)
        if el:
            txt = el.get_text("\n", strip=True)
            if txt and len(txt) >= MIN_CONTENT_LEN:
                return txt[:MAX_CONTENT_LEN]
    return None


def _is_valid(title: str, content: str) -> bool:
    text = (title or "") + (content or "")
    for kw in _DROP_KEYWORDS:
        if kw in text:
            return False
    return True


def _extract_comments(soup, limit: int = 3) -> List[str]:
    texts = []
    for el in soup.select(".cmt_list dd.usertxt")[:limit]:
        txt = el.get_text(" ", strip=True)
        if txt and 10 <= len(txt) <= 500:
            texts.append(txt)
    return texts


async def _fetch_static_sections(session: requests.Session, seen_ids: set,
                                  daily_limit: int) -> List[Dict]:
    """정적 랭킹 섹션 크롤 (lovetalk 포함)."""
    results = []
    post_count = 0
    comment_count = 0

    for section in STATIC_SECTIONS:
        if post_count >= daily_limit:
            break
        try:
            session.headers.update({"User-Agent": random.choice(USER_AGENTS)})
            resp = session.get(section["url"], timeout=12)
            resp.raise_for_status()
            await asyncio.sleep(random.uniform(0.8, 1.5))

            soup = BeautifulSoup(resp.text, "html.parser")
            post_items = []

            for li in soup.select("div.cntList ul.post_wrap li"):
                link = li.select_one("dl dt h2 a") or li.select_one("dd.tit h2 a")
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
                author_listing = _extract_author_from_li(li)
                post_items.append({
                    "origin_id": origin_id,
                    "title": title,
                    "url": f"https://pann.nate.com/talk/{origin_id}",
                    "author_listing": author_listing,
                })

            logger.info(f"Section '{section['name']}': {len(post_items)} 신규 포스트 수집")

            for item in post_items:
                if post_count >= daily_limit:
                    break
                try:
                    session.headers.update({"User-Agent": random.choice(USER_AGENTS)})
                    post_resp = session.get(item["url"], timeout=12)
                    post_resp.raise_for_status()
                    await asyncio.sleep(random.uniform(0.5, 1.0))

                    post_soup = BeautifulSoup(post_resp.text, "html.parser")
                    content = _extract_content(post_soup)
                    if not content or not _is_valid(item["title"] or "", content):
                        continue

                    author = item["author_listing"] or _extract_author_from_detail(post_soup)
                    posted_at = _parse_posted_at(_extract_time_from_detail(post_soup))

                    results.append({
                        "content": content,
                        "content_type": "POST",
                        "source": "natepan",
                        "category": "talk",
                        "title": item["title"],
                        "source_url": item["url"],
                        "author_id": author,
                        "posted_at": posted_at,
                    })
                    post_count += 1

                    if comment_count < daily_limit // 10:
                        for txt in _extract_comments(post_soup):
                            results.append({
                                "content": txt, "content_type": "COMMENT",
                                "source": "natepan", "category": "talk",
                                "source_url": item["url"],
                                "author_id": None, "posted_at": None,
                            })
                            comment_count += 1

                except Exception as e:
                    logger.debug(f"Post {item['url']} 오류: {e}")
                    continue

        except Exception as e:
            logger.warning(f"Section '{section['name']}' 오류: {e}")
            continue

    logger.info(f"정적 섹션 완료: posts={post_count}, comments={comment_count}")
    return results


async def _fetch_by_id_range(session: requests.Session, seen_ids: set,
                               remaining_limit: int, id_step: int = 150) -> List[Dict]:
    """
    포스트 ID 직접 범위 크롤 — 최신→과거 순차 밀집 샘플링.
    실측: 1포스트 ≈ 200 ID 간격 → step=150으로 거의 모든 포스트 커버.
    셔플 없이 최신→과거 순서(높은 ID부터) = 유효 포스트 연속 히트 보장.
    """
    if remaining_limit <= 0:
        return []

    results = []
    post_count = 0
    fail_streak = 0
    MAX_FAIL_STREAK = 80  # 넉넉하게 (비어있는 ID 구간 80개까지 허용)

    # 최신→과거 순서 (높은 ID부터, 유효 포스트 밀집 보장)
    candidate_ids = list(range(ID_RANGE_END, ID_RANGE_START, -id_step))

    logger.info(f"ID 범위 크롤 시작: {len(candidate_ids)}개 ID 후보, step={id_step}, 최신→과거")

    for post_id in candidate_ids:
        if post_count >= remaining_limit:
            break
        if fail_streak >= MAX_FAIL_STREAK:
            logger.info(f"ID 크롤: 연속 {MAX_FAIL_STREAK}회 실패 → 조기 종료")
            break

        origin_id = str(post_id)
        if origin_id in seen_ids:
            continue

        url = f"https://pann.nate.com/talk/{post_id}"
        try:
            session.headers.update({"User-Agent": random.choice(USER_AGENTS)})
            resp = session.get(url, timeout=10)

            if resp.status_code in (404, 403, 410):
                fail_streak += 1
                await asyncio.sleep(0.2)
                continue
            resp.raise_for_status()
            await asyncio.sleep(random.uniform(0.5, 0.9))

            post_soup = BeautifulSoup(resp.text, "html.parser")
            content = _extract_content(post_soup)
            if not content:
                fail_streak += 1
                continue

            fail_streak = 0  # 성공 시 리셋
            seen_ids.add(origin_id)

            title_el = post_soup.select_one("h2.tit, h1.tit, .post_title, dd.tit h2")
            title = title_el.get_text(strip=True) if title_el else None

            if not _is_valid(title or "", content):
                continue

            author = _extract_author_from_detail(post_soup)
            posted_at = _parse_posted_at(_extract_time_from_detail(post_soup))

            results.append({
                "content": content,
                "content_type": "POST",
                "source": "natepan",
                "category": "talk",
                "title": title,
                "source_url": url,
                "author_id": author,
                "posted_at": posted_at,
            })
            post_count += 1

            if post_count % 50 == 0:
                logger.info(f"ID 크롤 진행: {post_count}/{remaining_limit}")

        except Exception as e:
            logger.debug(f"ID {post_id} 오류: {e}")
            fail_streak += 1
            await asyncio.sleep(0.3)
            continue

    logger.info(f"ID 범위 크롤 완료: {post_count}건")
    return results


async def crawl(daily_limit: int = 1500) -> List[Dict]:
    """
    네이트판 공격 크롤 v4.
      Phase 1: 정적 랭킹(9섹션) + ID 범위 크롤(나머지 할당량)
    """
    session = _make_session()
    seen_ids: set = set()

    # ── Phase A: 정적 랭킹 섹션 (큐레이션 + lovetalk) ──────────
    static_limit = min(daily_limit // 5, 200)  # 최대 200건 정적 섹션에 할당
    static_results = await _fetch_static_sections(session, seen_ids, static_limit)

    # ── Phase B: ID 범위 크롤 (나머지 할당량 전부, step=150 밀집) ──────
    remaining = daily_limit - len([r for r in static_results if r["content_type"] == "POST"])
    id_results = await _fetch_by_id_range(session, seen_ids, remaining, id_step=150)

    all_results = static_results + id_results
    posts = [r for r in all_results if r["content_type"] == "POST"]
    comments = [r for r in all_results if r["content_type"] == "COMMENT"]
    logger.info(f"NATEPAN 크롤 v4 완료: posts={len(posts)}, comments={len(comments)}, total={len(all_results)}")
    return all_results
