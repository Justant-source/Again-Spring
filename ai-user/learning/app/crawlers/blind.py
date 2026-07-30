"""
블라인드 크롤러 v3 — 3개 채널 타게팅 (결혼생활 / 썸·연애 / 회사생활)
인증 불필요, ?page=N 페이지네이션, 갈등 키워드 필터
"""
import asyncio
import logging
import random
import re
from datetime import datetime, timedelta
from typing import List, Dict
from urllib.parse import quote

import requests
from bs4 import BeautifulSoup

logger = logging.getLogger(__name__)

BASE_URL = "https://www.teamblind.com"
PAGES_PER_CHANNEL = 2   # 채널당 2페이지 × ~40개 = ~80개
MIN_CONTENT_LENGTH = 100

# (채널 한글명, category값, URL-encoded 경로)
CHANNELS = [
    ("결혼생활", "marriage",  quote("결혼생활")),
    ("썸·연애",  "romance",   quote("썸·연애")),
    ("회사생활", "workplace", quote("회사생활")),
]

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
    },
    {
        "User-Agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
        "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7",
        "Accept-Language": "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7",
        "Accept-Encoding": "gzip, deflate, br",
        "Referer": "https://www.google.com/",
        "Sec-Ch-Ua": '"Google Chrome";v="131", "Chromium";v="131", "Not_A Brand";v="24"',
        "Sec-Ch-Ua-Mobile": "?0",
        "Sec-Ch-Ua-Platform": '"macOS"',
        "Sec-Fetch-Dest": "document",
        "Sec-Fetch-Mode": "navigate",
        "Sec-Fetch-Site": "none",
    },
]

CONFLICT_KEYWORDS = {
    "싸웠", "싸움", "화났", "억울", "짜증", "모욕", "갈등", "상처", "배신", "속았",
    "미쳤", "어이없", "황당", "분노", "열받", "화가", "기분나",
    "남편", "아내", "와이프", "남친", "여친", "남자친구", "여자친구",
    "이혼", "별거", "외도", "바람", "헤어", "차였", "연락두절", "차단",
    "시어머니", "시댁", "처가", "장모", "장인",
    "상사", "직장", "부장", "팀장", "대표", "사장", "갑질", "괴롭힘",
    "퇴사", "해고", "잘렸", "욕먹", "혼났", "무시", "따돌림",
    "어떻게", "이럴수가", "말이되", "이게맞", "억울하", "너무했", "선넘",
}


def _has_conflict_keyword(text: str) -> bool:
    return any(kw in text for kw in CONFLICT_KEYWORDS)


def _extract_title(soup: BeautifulSoup) -> str | None:
    og = soup.find("meta", property="og:title")
    if og and og.get("content"):
        return og["content"].strip()[:512]
    for tag in ("h1", "h2"):
        el = soup.find(tag)
        if el:
            t = el.get_text(strip=True)
            if t:
                return t[:512]
    return None


def _clean_soup(soup: BeautifulSoup) -> BeautifulSoup:
    """nav/header/footer/script/style 노이즈 제거"""
    for tag in soup.find_all(['nav', 'header', 'footer', 'script', 'style', 'noscript']):
        tag.decompose()
    return soup


def _filter_lines(text: str, min_line_len: int = 15) -> str:
    """짧은 UI 라벨(메뉴·버튼) 제거 후 실 본문 텍스트만 반환"""
    lines = [ln.strip() for ln in text.split("\n") if len(ln.strip()) >= min_line_len]
    return "\n".join(lines)


def _extract_content(soup: BeautifulSoup) -> str | None:
    # 1. og:description (SSR에서 본문 요약 포함 — 가장 깨끗)
    og = soup.find("meta", property="og:description")
    if og and og.get("content"):
        c = og["content"].strip()
        if len(c) >= MIN_CONTENT_LENGTH:
            return c

    # 2. 노이즈 제거 후 <main> 추출
    clean = _clean_soup(soup)
    main = clean.find("main")
    if main:
        t = _filter_lines(main.get_text(separator="\n", strip=True))
        if len(t) >= MIN_CONTENT_LENGTH:
            return t

    # 3. 노이즈 제거 후 실질적인 <p> 태그 합산
    paras = [p.get_text(strip=True) for p in clean.find_all("p") if len(p.get_text(strip=True)) > 30]
    if paras:
        t = "\n".join(paras)
        if len(t) >= MIN_CONTENT_LENGTH:
            return t

    # 4. <article>
    article = clean.find("article")
    if article:
        t = _filter_lines(article.get_text(separator="\n", strip=True))
        if len(t) >= MIN_CONTENT_LENGTH:
            return t

    return None


def _parse_numeric_with_unit(text: str) -> int | None:
    """'66K', '1.2M', '375' 등의 텍스트를 정수로 변환

    숫자가 없으면 None 반환 (예: "좋아요좋아요"는 무시)
    """
    import re
    if not text:
        return None
    try:
        match = re.search(r"(\d+(?:\.\d+)?)(K|M)?", text.strip())
        if not match:
            return None
        num = float(match.group(1))
        unit = match.group(2)
        if unit == "K":
            num = int(num * 1000)
        elif unit == "M":
            num = int(num * 1000000)
        else:
            num = int(num)
        return num
    except (ValueError, AttributeError):
        return None


def _extract_post_stats(soup: BeautifulSoup) -> tuple[int | None, int | None, int | None]:
    """메인 게시글의 조회수, 좋아요수, 댓글수 추출

    Returns:
        (view_count, like_count, comment_count) tuple
    """
    view_count = None
    like_count = None
    comment_count = None

    try:
        main = soup.find("main")
        if not main:
            return None, None, None

        # 첫 번째 wrap-info에서 조회수/댓글수 추출
        first_wrap = main.find("div", class_="wrap-info")
        if first_wrap:
            # 조회수 (span.pv)
            pv_span = first_wrap.find("span", class_="pv")
            if pv_span:
                pv_text = pv_span.get_text(strip=True)
                view_count = _parse_numeric_with_unit(pv_text)

            # 댓글수 (span.cmt)
            cmt_span = first_wrap.find("span", class_="cmt")
            if cmt_span:
                cmt_text = cmt_span.get_text(strip=True)
                comment_count = _parse_numeric_with_unit(cmt_text)

        # 좋아요수 (첫 번째 span.like, wrap-comment 부모가 아닌 것)
        for like_span in main.find_all("span", class_="like"):
            # 부모가 wrap-comment가 아닌 경우 = 메인 게시글의 좋아요
            if not like_span.find_parent("div", class_="wrap-comment"):
                like_text = like_span.get_text(strip=True)
                like_count = _parse_numeric_with_unit(like_text)
                break
    except Exception as e:
        logger.debug(f"Blind: stats extraction error: {e}")

    return view_count, like_count, comment_count


def _extract_post_date(soup: BeautifulSoup, reference: datetime) -> str | None:
    """게시글 작성 시각 추출 — 댓글과 같은 span.date 클래스를 본문 wrap-info에서 찾는다.
    posted_at이 없으면 popularity_scorer가 age_hours를 계산할 수 없어 인기도 점수가
    영구히 NULL로 남으므로(블라인드 최우선 강화 요건과 직결) 반드시 채워야 한다.
    """
    try:
        main = soup.find("main")
        if not main:
            return None
        first_wrap = main.find("div", class_="wrap-info")
        if not first_wrap:
            return None
        date_span = first_wrap.find("span", class_="date")
        if not date_span:
            return None
        date_text = date_span.get_text(strip=True).replace("작성일", "").strip()
        if not date_text:
            return None
        return _relative_to_absolute(date_text, reference)
    except Exception as e:
        logger.debug(f"Blind: post date extraction error: {e}")
        return None


def _relative_to_absolute(text: str, reference: datetime) -> str | None:
    """블라인드 상대 시간("어제", "11시간", "방금")을 크롤 시점(reference) 기준 절대 시각으로 변환

    engagement_span_hours(첫~마지막 댓글 시간폭) 계산에 쓰이므로, 다른 크롤러(natepan)와
    같은 "YYYY-MM-DD HH:MM:SS" 포맷으로 반환한다. 상대 시간이라 정밀도는 낮지만
    (예: "11시간"은 반올림된 값), 지속 참여도의 대략적 폭을 재는 데는 충분하다.
    """
    text = text.strip()
    if not text:
        return None
    try:
        if text in ("방금", "방금 전", "방금전"):
            dt = reference
        elif "분" in text:
            n = int(re.sub(r"[^0-9]", "", text) or 0)
            dt = reference - timedelta(minutes=n)
        elif "시간" in text:
            n = int(re.sub(r"[^0-9]", "", text) or 0)
            dt = reference - timedelta(hours=n)
        elif text == "어제":
            dt = reference - timedelta(days=1)
        elif re.fullmatch(r"\d+일", text):
            n = int(re.sub(r"[^0-9]", "", text) or 0)
            dt = reference - timedelta(days=n)
        elif re.fullmatch(r"\d{4}\.\d{2}\.\d{2}", text):
            dt = datetime.strptime(text, "%Y.%m.%d")
        else:
            return None
        return dt.strftime("%Y-%m-%d %H:%M:%S")
    except Exception:
        return None


def _extract_comment_timestamps(soup: BeautifulSoup, reference: datetime) -> list[str] | None:
    """댓글들의 작성 시각 추출 — 블라인드는 상대 시간("어제", "8시간", "방금")만 노출하므로
    reference(크롤 시점)를 기준으로 절대 시각으로 변환해서 반환한다.
    """
    timestamps = []
    try:
        main = soup.find("main")
        if not main:
            return None

        # 모든 댓글 (div.wrap-comment) 찾기
        comments = main.find_all("div", class_="wrap-comment")
        for comment in comments:
            date_span = comment.find("span", class_="date")
            if date_span:
                # "작성일어제" → "어제" 추출
                date_text = date_span.get_text(strip=True)
                # "작성일" 제거 후 나머지
                date_only = date_text.replace("작성일", "").strip()
                absolute = _relative_to_absolute(date_only, reference) if date_only else None
                if absolute:
                    timestamps.append(absolute)
    except Exception as e:
        logger.debug(f"Blind: comment timestamp extraction error: {e}")

    return timestamps if timestamps else None


async def crawl(daily_limit: int = 240) -> List[Dict]:
    """블라인드 크롤링 v3 — 결혼생활/썸·연애/회사생활 채널"""
    if not daily_limit:
        return []

    results: List[Dict] = []
    seen_urls: set = set()
    session = requests.Session()

    for idx, (channel_name, category, encoded) in enumerate(CHANNELS):
        if len(results) >= daily_limit:
            break

        logger.info(f"Blind: 채널 '{channel_name}' 크롤 시작")

        for page in range(1, PAGES_PER_CHANNEL + 1):
            if len(results) >= daily_limit:
                break

            page_url = f"{BASE_URL}/kr/topics/{encoded}?page={page}"
            session.headers.update(random.choice(BROWSER_PROFILES))

            try:
                resp = session.get(page_url, timeout=15)
                if resp.status_code == 403:
                    logger.warning(f"Blind: 403 on {page_url}")
                    break
                resp.raise_for_status()
            except Exception as e:
                logger.warning(f"Blind: page error ({page_url}): {e}")
                break

            await asyncio.sleep(random.uniform(2, 4))

            soup = BeautifulSoup(resp.text, "html.parser")
            post_urls = []
            for a in soup.find_all("a", href=True):
                href = a["href"]
                if "/kr/post/" in href or "/kr/articles/" in href:
                    full = href if href.startswith("http") else f"{BASE_URL}{href}"
                    if full not in seen_urls:
                        post_urls.append(full)
                        seen_urls.add(full)

            if not post_urls:
                logger.warning(f"Blind: 게시글 없음 ({page_url})")
                break

            for post_url in post_urls:
                if len(results) >= daily_limit:
                    break

                session.headers.update(random.choice(BROWSER_PROFILES))
                try:
                    post_resp = session.get(post_url, timeout=10)
                    if post_resp.status_code == 403:
                        continue
                    post_resp.raise_for_status()
                except Exception as e:
                    logger.debug(f"Blind: post error ({post_url}): {e}")
                    continue

                await asyncio.sleep(random.uniform(0.8, 1.5))

                post_soup = BeautifulSoup(post_resp.text, "html.parser")
                content = _extract_content(post_soup)
                if not content or len(content) < MIN_CONTENT_LENGTH:
                    continue

                title = _extract_title(post_soup) or ""
                if not _has_conflict_keyword(content + " " + title):
                    logger.debug(f"Blind: 갈등 키워드 없음 ({post_url})")
                    continue

                # 통계 정보 추출 — reference는 이 글을 긁는 시점(상대 시간 → 절대 시각 변환 기준)
                fetch_reference = datetime.now()
                view_count, like_count, comment_count = _extract_post_stats(post_soup)
                comment_timestamps = _extract_comment_timestamps(post_soup, fetch_reference)
                posted_at = _extract_post_date(post_soup, fetch_reference)

                results.append({
                    "content": content[:1500],
                    "content_type": "POST",
                    "category": category,
                    "title": title or None,
                    "source_url": post_url,
                    "posted_at": posted_at,
                    "view_count": view_count,
                    "like_count": like_count,
                    "comment_count": comment_count,
                    "comment_timestamps": comment_timestamps,
                })
                logger.debug(f"Blind: 저장 [{channel_name}] {post_url} (조회:{view_count}, 좋아요:{like_count}, 댓글:{comment_count})")

        # 채널 간 딜레이 (마지막 채널 제외)
        if idx < len(CHANNELS) - 1:
            await asyncio.sleep(random.uniform(3, 6))

    logger.info(f"Blind: 크롤 완료 — {len(results)}개 수집")
    return results
