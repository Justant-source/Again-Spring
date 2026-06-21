"""
네이트판 크롤러 v5 — 완전 병렬 async (2026-06-21)
설계: asyncio.Semaphore(8) + asyncio.to_thread()로 순차 대비 5~8× 속도
  1. 정적 랭킹 9섹션 listing → 동시 fetch
  2. post detail → 8병렬 fetch
  3. ID 범위 크롤 → 배치(32개)씩 8병렬
확인된 셀렉터: 작성자=a.writer, 본문=div.talk-content div.text 또는 div#contentArea
"""
import asyncio
import logging
import random
import re
from datetime import datetime
from typing import List, Dict, Optional, Set

import requests
from bs4 import BeautifulSoup

logger = logging.getLogger(__name__)

CONCURRENCY = 8  # 동시 HTTP 연결 수 (NATEPAN 배려 + 속도 균형)

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
    "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Mobile/15E148 Safari/604.1",
]

ID_RANGE_START = 370_000_000
ID_RANGE_END   = 375_480_000
ID_STEP        = 150  # 실측: 1포스트당 ~200 ID 간격 → step=150으로 전수 커버

_DROP_KEYWORDS = ["광고", "공지", "이벤트", "혜택안내", "앱 다운", "무료체험"]
MIN_CONTENT_LEN = 80
MAX_CONTENT_LEN = 8000


def _sync_fetch(url: str, timeout: int = 10) -> Optional[str]:
    """동기 HTTP GET — asyncio.to_thread()로 비동기 전환됨."""
    try:
        resp = requests.get(
            url,
            headers={
                "User-Agent": random.choice(USER_AGENTS),
                "Accept-Language": "ko-KR,ko;q=0.9",
                "Referer": "https://pann.nate.com/",
            },
            timeout=timeout,
        )
        if resp.status_code in (404, 403, 410):
            return None
        resp.raise_for_status()
        return resp.text
    except Exception:
        return None


async def _fetch(sem: asyncio.Semaphore, url: str) -> Optional[str]:
    """세마포어 제어 비동기 fetch."""
    async with sem:
        html = await asyncio.to_thread(_sync_fetch, url)
        if html:
            await asyncio.sleep(random.uniform(0.05, 0.15))  # 최소 courtesy
        return html


def _extract_author_from_li(li) -> Optional[str]:
    """목록 항목 작성자 추출. 확인된 셀렉터: a.writer"""
    el = li.select_one("a.writer")
    if el:
        txt = el.get_text(strip=True)
        if txt and 1 <= len(txt) <= 50:
            return txt
    return None


def _extract_author_from_detail(soup) -> Optional[str]:
    for sel in ["a.writer", ".user-info .name", ".writer_name", "span.user_name"]:
        el = soup.select_one(sel)
        if el:
            txt = el.get_text(strip=True)
            if txt and 1 <= len(txt) <= 50:
                return txt
    return None


def _extract_time_from_detail(soup) -> Optional[str]:
    for sel in [".post_date", ".write_date", ".writeDate", ".date",
                "span[class*='date']", ".info_area .date",
                "meta[property='article:published_time']"]:
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


def _parse_post_detail(html: str, url: str, author_listing: Optional[str] = None) -> Optional[Dict]:
    """HTML → 결과 딕셔너리 파싱. None = 무효(비어있거나 필터됨)."""
    soup = BeautifulSoup(html, "html.parser")
    content = _extract_content(soup)
    if not content:
        return None

    title_el = soup.select_one("h2.tit, h1.tit, .post_title, dd.tit h2, dt h2")
    title = title_el.get_text(strip=True) if title_el else None

    if not _is_valid(title or "", content):
        return None

    author = _extract_author_from_detail(soup) or author_listing
    return {
        "content": content,
        "content_type": "POST",
        "source": "natepan",
        "category": "talk",
        "title": title,
        "source_url": url,
        "author_id": author,
        "posted_at": _parse_posted_at(_extract_time_from_detail(soup)),
    }


async def _fetch_static_sections_parallel(sem: asyncio.Semaphore,
                                           seen_ids: Set[str],
                                           limit: int) -> List[Dict]:
    """
    9개 랭킹 섹션 병렬 처리.
    Step 1: 9개 listing 동시 fetch
    Step 2: 모든 post detail 8병렬 fetch
    """
    # Step 1: 9섹션 동시 fetch
    listing_htmls = await asyncio.gather(*[
        _fetch(sem, s["url"]) for s in STATIC_SECTIONS
    ])

    # Step 2: 모든 listing에서 post URL 수집 (dedup)
    post_items: List[Dict] = []
    for sec, html in zip(STATIC_SECTIONS, listing_htmls):
        if not html:
            logger.warning(f"Section '{sec['name']}' fetch 실패")
            continue
        soup = BeautifulSoup(html, "html.parser")
        count = 0
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
            post_items.append({
                "origin_id": origin_id,
                "title": link.get("title") or link.get_text(strip=True),
                "url": f"https://pann.nate.com/talk/{origin_id}",
                "author_listing": _extract_author_from_li(li),
            })
            count += 1
        logger.info(f"Section '{sec['name']}': {count}개 신규")

    logger.info(f"정적 섹션 listing 완료: {len(post_items)}개 고유 포스트 → detail 병렬 fetch 시작")
    post_items = post_items[:limit]

    # Step 3: post detail 병렬 fetch
    detail_results = await asyncio.gather(*[
        _fetch(sem, item["url"]) for item in post_items
    ])

    results: List[Dict] = []
    for item, html in zip(post_items, detail_results):
        if not html:
            continue
        result = _parse_post_detail(html, item["url"], item["author_listing"])
        if result:
            results.append(result)

    logger.info(f"정적 섹션 완료: {len(results)} posts")
    return results


async def _fetch_id_range_parallel(sem: asyncio.Semaphore,
                                    seen_ids: Set[str],
                                    remaining: int) -> List[Dict]:
    """
    ID 범위 크롤 — 배치(32개)씩 8병렬 fetch.
    최신→과거 순서(높은 ID부터), 빈 구간 허용폭 80배치.
    """
    if remaining <= 0:
        return []

    BATCH = CONCURRENCY * 4  # 배치당 32 ID
    candidate_ids = list(range(ID_RANGE_END, ID_RANGE_START, -ID_STEP))
    total_candidates = len(candidate_ids)
    logger.info(f"ID 범위 크롤: {total_candidates}개 후보, 목표={remaining}, 배치={BATCH}")

    results: List[Dict] = []
    empty_batches = 0
    MAX_EMPTY_BATCHES = 80

    for i in range(0, total_candidates, BATCH):
        if len(results) >= remaining:
            break
        if empty_batches >= MAX_EMPTY_BATCHES:
            logger.info(f"ID 크롤: 빈 배치 {MAX_EMPTY_BATCHES}회 연속 → 조기 종료")
            break

        batch_ids = [pid for pid in candidate_ids[i:i + BATCH]
                     if str(pid) not in seen_ids]
        if not batch_ids:
            continue

        urls = [f"https://pann.nate.com/talk/{pid}" for pid in batch_ids]
        htmls = await asyncio.gather(*[_fetch(sem, url) for url in urls])

        batch_found = 0
        for pid, html in zip(batch_ids, htmls):
            if len(results) >= remaining:
                break
            seen_ids.add(str(pid))
            if not html:
                continue
            result = _parse_post_detail(html, f"https://pann.nate.com/talk/{pid}")
            if result:
                results.append(result)
                batch_found += 1

        if batch_found == 0:
            empty_batches += 1
        else:
            empty_batches = 0

        if len(results) % 200 == 0 and len(results) > 0:
            logger.info(f"ID 크롤 진행: {len(results)}/{remaining}")

    logger.info(f"ID 범위 크롤 완료: {len(results)}건")
    return results


async def crawl(daily_limit: int = 1500) -> List[Dict]:
    """
    네이트판 공격 크롤 v5 — 완전 병렬.
    정적 섹션(9개 동시) + ID 범위(8병렬 배치) = 순차 대비 5~8× 속도.
    """
    sem = asyncio.Semaphore(CONCURRENCY)
    seen_ids: Set[str] = set()

    static_limit = min(daily_limit // 5, 250)
    static_results = await _fetch_static_sections_parallel(sem, seen_ids, static_limit)

    post_count = len([r for r in static_results if r["content_type"] == "POST"])
    remaining = daily_limit - post_count
    id_results = await _fetch_id_range_parallel(sem, seen_ids, remaining)

    all_results = static_results + id_results
    posts = [r for r in all_results if r["content_type"] == "POST"]
    logger.info(f"NATEPAN v5 완료: posts={len(posts)}, total={len(all_results)}")
    return all_results
