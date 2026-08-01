"""
네이트판 크롤러 v5 — 완전 병렬 async (2026-06-21)
설계: asyncio.Semaphore(8) + asyncio.to_thread()로 순차 대비 5~8× 속도
  1. 정적 랭킹 9섹션 listing → 동시 fetch
  2. post detail → 8병렬 fetch
  3. ID 범위 크롤 → 배치(32개)씩 8병렬
  4. 테마 채널 크롤 → 채널별 plaza 분류 (분류기 precision gate)
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

from app.services.plaza_classifier import classify_plaza

logger = logging.getLogger(__name__)

CONCURRENCY = 8  # 동시 HTTP 연결 수 (NATEPAN 배려 + 속도 균형)

# 채널별 페이지네이션 한도 (광장형 피벗 — WORK 플러스)
MAX_PAGES_WORK = 5    # WORK 채널 (회사생활, 취업과 면접, 알바 경험담) → 페이지 1~5
MAX_PAGES_OTHER = 2   # 나머지 채널 (MARRIED, COUPLE) → 페이지 1~2

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

# 테마 채널 매핑 — 채널 ID + 이름 + 목표 plaza
CHANNELS = [
    {"id": "20019", "name": "회사생활", "plaza": "WORK"},
    {"id": "20054", "name": "취업과 면접", "plaza": "WORK"},
    {"id": "20020", "name": "알바 경험담", "plaza": "WORK"},
    {"id": "20023", "name": "남편 VS 아내", "plaza": "MARRIED"},
    {"id": "20025", "name": "결혼/시집/친정", "plaza": "MARRIED"},
    {"id": "20026", "name": "맞벌이 부부 이야기", "plaza": "MARRIED"},
    {"id": "20006", "name": "사랑과 이별", "plaza": "COUPLE"},
    {"id": "20009", "name": "지금은 연애중", "plaza": "COUPLE"},
    {"id": "20011", "name": "헤어진 다음날", "plaza": "COUPLE"},
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

# 댓글 수집 (c918e6e5 이전 계약 복원 — 문체 앵커용). POST daily_limit과 별도 예산.
COMMENTS_PER_POST = 3
MIN_COMMENT_LEN = 10
MAX_COMMENT_LEN = 500


def _parse_int_with_commas(raw: str) -> Optional[int]:
    """'44,903' / '93' → int. 실패 시 None."""
    if not raw:
        return None
    try:
        return int(raw.replace(",", "").strip())
    except ValueError:
        return None


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


def _extract_view_count(soup) -> Optional[int]:
    """조회수 추출.

    실측 DOM (2026-08):
      <span class="count"><span class="tit">조회</span>44,903</span>
    get_text(strip=True) → '조회44,903' (공백 없음).
    """
    try:
        # 방법 1: span.tit=조회 를 포함한 span.count
        for el in soup.select("span.count"):
            tit = el.select_one("span.tit")
            if tit and "조회" in tit.get_text(strip=True):
                m = re.search(r"([\d,]+)", el.get_text(" ", strip=True))
                if m:
                    return _parse_int_with_commas(m.group(1))

        # 방법 2: 텍스트 '조회' + 숫자 (공백 유무 모두)
        for el in soup.select("span.count"):
            txt = el.get_text(" ", strip=True)
            m = re.search(r"조회\s*([\d,]+)", txt)
            if m:
                return _parse_int_with_commas(m.group(1))

        # 방법 3: 페이지 전역 fallback (info 영역)
        for el in soup.select(".info span.count, .post_info span.count, .info_area span.count"):
            txt = el.get_text(" ", strip=True)
            m = re.search(r"조회\s*([\d,]+)", txt)
            if m:
                return _parse_int_with_commas(m.group(1))
    except Exception:
        pass
    return None


def _extract_like_count(soup) -> Optional[int]:
    """추천수 추출.

    실측 DOM (2026-08):
      <span class="count"><em>추천수</em><span>93</span></span>
    get_text(strip=True) → '추천수93' — '추천\\s*(\\d+)' 단독 패턴은 실패하므로
    '추천수?' 또는 nested span 숫자를 우선한다.
    """
    try:
        # 방법 1: <em>추천수</em><span>N</span> nested
        for el in soup.select("span.count"):
            em = el.select_one("em")
            if not em:
                continue
            em_txt = em.get_text(strip=True)
            if "추천" not in em_txt or "반대" in em_txt:
                continue
            nested = el.select_one("span")
            if nested:
                n = _parse_int_with_commas(nested.get_text(strip=True))
                if n is not None:
                    return n

        # 방법 2: span.count 텍스트 '추천수93' / '추천 343'
        for el in soup.select("span.count"):
            txt = el.get_text(" ", strip=True)
            if "반대" in txt:
                continue
            m = re.search(r"추천수?\s*([\d,]+)", txt)
            if m:
                return _parse_int_with_commas(m.group(1))

        # 방법 3: 추천 버튼 옆 span.count
        for btn_span in soup.select("button[value='R'] ~ span.count, div.btnbox.up span.count"):
            nested = btn_span.select_one("span")
            if nested:
                n = _parse_int_with_commas(nested.get_text(strip=True))
                if n is not None:
                    return n
            m = re.search(r"([\d,]+)", btn_span.get_text(" ", strip=True))
            if m:
                return _parse_int_with_commas(m.group(1))
    except Exception:
        pass
    return None


def _extract_comment_count(soup) -> Optional[int]:
    """댓글 수 추출. 셀렉터: span.num / span.reple-num / cmt_item 개수 fallback."""
    try:
        # 방법 1: "<strong>83</strong>개의 댓글"
        for el in soup.select("span.num, .cmt_tit span.num"):
            txt = el.get_text(" ", strip=True)
            if "댓글" in txt or el.select_one("strong"):
                m = re.search(r"(\d+)", txt)
                if m:
                    return int(m.group(1))

        # 방법 2: 목록 (56) 형식
        el = soup.select_one("span.reple-num")
        if el:
            m = re.search(r"(\d+)", el.get_text(strip=True))
            if m:
                return int(m.group(1))

        # 방법 3: 페이지에 렌더된 댓글 개수 (부분 로드일 수 있어 최후 수단)
        n = len(soup.select("dl.cmt_item"))
        if n > 0:
            return n
    except Exception:
        pass
    return None


def _comment_posted_at(cmt_item) -> Optional[str]:
    """dl.cmt_item 한 건에서 작성 시각 추출."""
    i_el = cmt_item.select_one("dt i")
    if not i_el:
        return None
    time_str = i_el.get_text(strip=True)
    if not time_str or not re.search(r"\d{4}\.\d{2}\.\d{2}", time_str):
        return None
    return _parse_posted_at(time_str[:16])


def _extract_comment_timestamps(soup) -> Optional[List[str]]:
    """댓글 작성 시각 추출. 셀렉터: dl.cmt_item > dt > i (시각)."""
    try:
        timestamps: List[str] = []
        for cmt_item in soup.select("dl.cmt_item"):
            parsed = _comment_posted_at(cmt_item)
            if parsed:
                timestamps.append(parsed)
        return timestamps if timestamps else None
    except Exception:
        pass
    return None


def _extract_comment_rows(soup, url: str, category: str, limit: int) -> List[Dict]:
    """상세 페이지에서 COMMENT 행 생성.

    c918e6e5 이전: `.cmt_list dd.usertxt` 상위 N건.
    현재 DOM도 `dl.cmt_item > dd.usertxt` — 동일 계열.
    source_url에 `#cmtN`을 붙여 ingest(source_url dedup)가 POST와 충돌하지 않게 한다.
    """
    if limit <= 0:
        return []

    rows: List[Dict] = []
    items = soup.select("dl.cmt_item")
    if not items:
        # 구형/축약 마크업 fallback
        for el in soup.select(".cmt_list dd.usertxt, dd.usertxt"):
            if len(rows) >= limit:
                break
            txt = el.get_text(" ", strip=True)
            if not txt or not (MIN_COMMENT_LEN <= len(txt) <= MAX_COMMENT_LEN):
                continue
            idx = len(rows) + 1
            rows.append({
                "content": txt,
                "content_type": "COMMENT",
                "source": "natepan",
                "category": category,
                "source_url": f"{url}#cmt{idx}",
                "author_id": None,
                "posted_at": None,
            })
        return rows

    for cmt_item in items:
        if len(rows) >= limit:
            break
        txt_el = cmt_item.select_one("dd.usertxt")
        if not txt_el:
            continue
        txt = txt_el.get_text(" ", strip=True)
        if not txt or not (MIN_COMMENT_LEN <= len(txt) <= MAX_COMMENT_LEN):
            continue

        author = None
        name_el = cmt_item.select_one(".nameui, a.writer, dt a")
        if name_el:
            name = name_el.get_text(strip=True)
            if name and 1 <= len(name) <= 50:
                author = name

        idx = len(rows) + 1
        rows.append({
            "content": txt,
            "content_type": "COMMENT",
            "source": "natepan",
            "category": category,
            "source_url": f"{url}#cmt{idx}",
            "author_id": author,
            "posted_at": _comment_posted_at(cmt_item),
        })
    return rows


def _is_valid(title: str, content: str) -> bool:
    text = (title or "") + (content or "")
    for kw in _DROP_KEYWORDS:
        if kw in text:
            return False
    return True


def _parse_post_soup(soup, url: str, author_listing: Optional[str] = None,
                     section_name: Optional[str] = None,
                     channel_plaza: Optional[str] = None) -> Optional[Dict]:
    """이미 파싱된 soup → POST dict. None = 무효."""
    content = _extract_content(soup)
    if not content:
        return None

    title_el = soup.select_one("h2.tit, h1.tit, .post_title, dd.tit h2, dt h2")
    title = title_el.get_text(strip=True) if title_el else None

    if not _is_valid(title or "", content):
        return None

    author = _extract_author_from_detail(soup) or author_listing

    # category 결정 로직
    # 1. channel_plaza 지정 → 분류기로 검증 (precision gate)
    # 2. section_name 지정 → 섹션 기반 (정적 섹션용)
    # 3. 기본값 → OTHER
    if channel_plaza:
        classified = classify_plaza(content, title or "")
        category = channel_plaza if classified == channel_plaza else "OTHER"
    elif section_name and "연애" in section_name:
        category = "COUPLE"
    else:
        category = "OTHER"

    return {
        "content": content,
        "content_type": "POST",
        "source": "natepan",
        "category": category,
        "title": title,
        "source_url": url,
        "author_id": author,
        "posted_at": _parse_posted_at(_extract_time_from_detail(soup)),
        "view_count": _extract_view_count(soup),
        "like_count": _extract_like_count(soup),
        "comment_count": _extract_comment_count(soup),
        "comment_timestamps": _extract_comment_timestamps(soup),
    }


def _parse_post_detail(html: str, url: str, author_listing: Optional[str] = None,
                       section_name: Optional[str] = None, channel_plaza: Optional[str] = None) -> Optional[Dict]:
    """HTML → POST 딕셔너리 파싱. None = 무효(비어있거나 필터됨)."""
    soup = BeautifulSoup(html, "html.parser")
    return _parse_post_soup(soup, url, author_listing, section_name, channel_plaza)


def _parse_detail_bundle(html: str, url: str, author_listing: Optional[str] = None,
                         section_name: Optional[str] = None, channel_plaza: Optional[str] = None,
                         max_comments: int = 0) -> List[Dict]:
    """POST(+선택적 COMMENT) 행 리스트. POST가 무효면 빈 리스트."""
    soup = BeautifulSoup(html, "html.parser")
    post = _parse_post_soup(soup, url, author_listing, section_name, channel_plaza)
    if not post:
        return []
    rows: List[Dict] = [post]
    if max_comments > 0:
        rows.extend(_extract_comment_rows(
            soup, url, post["category"],
            limit=min(COMMENTS_PER_POST, max_comments),
        ))
    return rows


async def _fetch_static_sections_parallel(sem: asyncio.Semaphore,
                                           seen_ids: Set[str],
                                           limit: int,
                                           comment_budget: List[int]) -> List[Dict]:
    """
    9개 랭킹 섹션 병렬 처리.
    Step 1: 9개 listing 동시 fetch
    Step 2: 모든 post detail 8병렬 fetch (+ COMMENT)
    comment_budget: 길이 1 리스트 — 남은 COMMENT 예산 (mutable).
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
                "section_name": sec["name"],  # 섹션 이름 전달
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
    comment_count = 0
    for item, html in zip(post_items, detail_results):
        if not html:
            continue
        max_comments = min(COMMENTS_PER_POST, comment_budget[0])
        rows = _parse_detail_bundle(
            html, item["url"], item["author_listing"],
            section_name=item.get("section_name"),
            max_comments=max_comments,
        )
        if not rows:
            continue
        results.extend(rows)
        n_comments = sum(1 for r in rows if r["content_type"] == "COMMENT")
        comment_budget[0] -= n_comments
        comment_count += n_comments

    post_n = sum(1 for r in results if r["content_type"] == "POST")
    logger.info(f"정적 섹션 완료: {post_n} posts + {comment_count} comments")
    return results


async def _fetch_id_range_parallel(sem: asyncio.Semaphore,
                                    seen_ids: Set[str],
                                    remaining: int,
                                    comment_budget: List[int]) -> List[Dict]:
    """
    ID 범위 크롤 — 배치(32개)씩 8병렬 fetch.
    최신→과거 순서(높은 ID부터), 빈 구간 허용폭 80배치.
    remaining / 조기종료 기준은 POST 건수만 센다 (COMMENT는 별도 예산).
    """
    if remaining <= 0:
        return []

    BATCH = CONCURRENCY * 4  # 배치당 32 ID
    candidate_ids = list(range(ID_RANGE_END, ID_RANGE_START, -ID_STEP))
    total_candidates = len(candidate_ids)
    logger.info(f"ID 범위 크롤: {total_candidates}개 후보, 목표={remaining}, 배치={BATCH}")

    results: List[Dict] = []
    post_found = 0
    empty_batches = 0
    MAX_EMPTY_BATCHES = 80

    for i in range(0, total_candidates, BATCH):
        if post_found >= remaining:
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
            if post_found >= remaining:
                break
            seen_ids.add(str(pid))
            if not html:
                continue
            max_comments = min(COMMENTS_PER_POST, comment_budget[0])
            rows = _parse_detail_bundle(
                html, f"https://pann.nate.com/talk/{pid}",
                section_name=None,
                max_comments=max_comments,
            )
            if not rows:
                continue
            results.extend(rows)
            n_posts = sum(1 for r in rows if r["content_type"] == "POST")
            n_comments = sum(1 for r in rows if r["content_type"] == "COMMENT")
            post_found += n_posts
            comment_budget[0] -= n_comments
            batch_found += n_posts

        if batch_found == 0:
            empty_batches += 1
        else:
            empty_batches = 0

        if post_found % 200 == 0 and post_found > 0:
            logger.info(f"ID 크롤 진행: {post_found}/{remaining}")

    logger.info(f"ID 범위 크롤 완료: posts={post_found}, total_rows={len(results)}")
    return results


async def _fetch_channels(sem: asyncio.Semaphore,
                          seen_ids: Set[str],
                          limit: int,
                          comment_budget: List[int]) -> List[Dict]:
    """
    테마 채널 크롤 — 채널별 plaza 분류 + 페이지네이션 (분류기 precision gate).

    페이지네이션 규칙:
    - WORK 채널 (plaza="WORK"): 페이지 1~MAX_PAGES_WORK
    - 기타 채널 (MARRIED, COUPLE): 페이지 1~MAX_PAGES_OTHER
    - 페이지 limit에 도달하거나, 신규 post id 0개면 조기 종료

    Step 1: 각 채널의 listing 페이지 fetch (페이지 1..MAX_PAGES)
    Step 2: 고유한 post URL 추출 (dedup via seen_ids)
    Step 3: 채널 target 도달 또는 페이지 소진 시 post detail 병렬 fetch
    Step 4: 분류기로 검증 후 channel_plaza와 일치할 때만 결과 포함 (+ COMMENT)
    """
    if not CHANNELS or limit <= 0:
        return []

    channel_limit = max(1, limit // len(CHANNELS))  # 채널당 평균 한도
    logger.info(f"채널 크롤: {len(CHANNELS)}개 채널, 채널당 ~{channel_limit}개, 총 한도={limit}")

    results: List[Dict] = []
    post_found = 0

    for channel in CHANNELS:
        if post_found >= limit:
            break

        ch_id = channel["id"]
        ch_name = channel["name"]
        ch_plaza = channel["plaza"]

        # 채널의 WORK-ness에 따라 max page 결정
        max_pages = MAX_PAGES_WORK if ch_plaza == "WORK" else MAX_PAGES_OTHER

        post_items: List[Dict] = []
        page_new_count = 0  # 페이지별 신규 post 추적

        # Step 1~2: 페이지 루프 — 신규 post 누적
        for page in range(1, max_pages + 1):
            if len(post_items) >= channel_limit or post_found >= limit:
                break

            # URL 구성: page=1 → ?order=rank, page>=2 → ?order=rank&page=N
            if page == 1:
                listing_url = f"https://pann.nate.com/talk/c{ch_id}?order=rank"
            else:
                listing_url = f"https://pann.nate.com/talk/c{ch_id}?order=rank&page={page}"

            # fetch with semaphore + rate limiting
            html = await _fetch(sem, listing_url)
            if not html:
                logger.warning(f"Channel '{ch_name}' page {page} fetch 실패")
                break

            # post URL 추출 & dedup
            soup = BeautifulSoup(html, "html.parser")
            page_new_count = 0

            # title 매핑
            title_by_id: Dict[str, str] = {}
            for a in soup.find_all("a", href=True):
                mm = re.search(r"/talk/(\d{6,})", a["href"])
                if mm:
                    t = a.get("title") or a.get_text(strip=True)
                    if t and mm.group(1) not in title_by_id:
                        title_by_id[mm.group(1)] = t[:200]

            # post id 추출 (정규식)
            for origin_id in re.findall(r"/talk/(\d{6,})", html):
                if origin_id in seen_ids:
                    continue
                if len(post_items) >= channel_limit or post_found >= limit:
                    break
                seen_ids.add(origin_id)
                post_items.append({
                    "origin_id": origin_id,
                    "title": title_by_id.get(origin_id),
                    "url": f"https://pann.nate.com/talk/{origin_id}",
                    "author_listing": None,
                })
                page_new_count += 1

            logger.info(f"Channel '{ch_name}' page {page}: {page_new_count}개 신규")

            # 페이지에서 신규 post 0개 → 이 채널 조기 종료
            if page_new_count == 0:
                logger.info(f"Channel '{ch_name}': 페이지 {page}에서 신규 0개 → 조기 종료")
                break

        if not post_items:
            logger.info(f"Channel '{ch_name}': 0개 신규 (모든 페이지)")
            continue

        logger.info(f"Channel '{ch_name}': {len(post_items)}개 신규 포스트 → detail 병렬 fetch 시작")

        # Step 3: post detail 병렬 fetch
        detail_results = await asyncio.gather(*[
            _fetch(sem, item["url"]) for item in post_items
        ])

        # Step 4: 분류기 검증 후 저장 (+ COMMENT)
        channel_found = 0
        for item, detail_html in zip(post_items, detail_results):
            if post_found >= limit:
                break
            if not detail_html:
                continue
            max_comments = min(COMMENTS_PER_POST, comment_budget[0])
            rows = _parse_detail_bundle(
                detail_html, item["url"],
                author_listing=item["author_listing"],
                channel_plaza=ch_plaza,
                max_comments=max_comments,
            )
            if not rows:
                continue
            results.extend(rows)
            n_posts = sum(1 for r in rows if r["content_type"] == "POST")
            n_comments = sum(1 for r in rows if r["content_type"] == "COMMENT")
            post_found += n_posts
            comment_budget[0] -= n_comments
            channel_found += n_posts

        logger.info(f"Channel '{ch_name}': {channel_found}/{len(post_items)} 분류기 통과 (plaza={ch_plaza})")

    logger.info(f"채널 크롤 완료: posts={post_found}, total_rows={len(results)}")
    return results


async def crawl(daily_limit: int = 1500) -> List[Dict]:
    """
    네이트판 공격 크롤 v5+ — 완전 병렬 with 테마 채널 + COMMENT.
    정적 섹션(9개 동시) + 테마 채널(9개 channels × classifier) + ID 범위(8병렬 배치)
    = 순차 대비 5~8× 속도.

    예산 배분:
    - 정적 섹션: 250개 POST (상위 9개 섹션에서 고품질 COUPLE)
    - 테마 채널: 250개 POST (WORK, MARRIED, COUPLE 정밀 분류)
    - ID 범위: 나머지 POST (배경 채우기)
    - COMMENT: daily_limit // 10 (c918e6e5 이전 계약), POST 한도와 독립
    """
    sem = asyncio.Semaphore(CONCURRENCY)
    seen_ids: Set[str] = set()
    comment_budget = [max(0, daily_limit // 10)]

    # Step 1: 정적 섹션
    static_limit = 250
    static_results = await _fetch_static_sections_parallel(
        sem, seen_ids, static_limit, comment_budget)
    static_count = len([r for r in static_results if r["content_type"] == "POST"])

    # Step 2: 테마 채널 (분류기 precision gate)
    channel_limit = 250
    channel_results = await _fetch_channels(
        sem, seen_ids, channel_limit, comment_budget)
    channel_count = len([r for r in channel_results if r["content_type"] == "POST"])

    # Step 3: ID 범위 크롤 (나머지 POST 한도)
    post_count = static_count + channel_count
    remaining = daily_limit - post_count
    id_results = await _fetch_id_range_parallel(
        sem, seen_ids, remaining, comment_budget)

    all_results = static_results + channel_results + id_results
    posts = [r for r in all_results if r["content_type"] == "POST"]
    comments = [r for r in all_results if r["content_type"] == "COMMENT"]
    id_posts = len([r for r in id_results if r["content_type"] == "POST"])
    logger.info(
        f"NATEPAN v5+ 완료: static={static_count} + channel={channel_count} "
        f"+ id_range={id_posts} = {len(posts)} posts, comments={len(comments)}, "
        f"total={len(all_results)}"
    )
    return all_results
