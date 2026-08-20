"""
블라인드 크롤러 v3 — 3개 채널 타게팅 (결혼생활 / 썸·연애 / 회사생활)
인증 불필요, ?page=N 페이지네이션, 갈등 키워드 필터
"""
import asyncio
import hashlib
import logging
import random
import re
from datetime import datetime, timedelta
from typing import List, Dict
from urllib.parse import quote

import requests
from bs4 import BeautifulSoup

from app.services.plaza_classifier import classify_plaza

logger = logging.getLogger(__name__)

BASE_URL = "https://www.teamblind.com"
PAGES_PER_CHANNEL = 2   # 채널당 2페이지 × ~40개 = ~80개
MIN_CONTENT_LENGTH = 100
MIN_COMMENT_LENGTH = 2  # 댓글은 문체 앵커용 — 짧은 한줄도 수집

# (채널 한글명, category값, URL-encoded 경로)
CHANNELS = [
    ("결혼생활", "marriage",  quote("결혼생활")),
    ("썸·연애",  "romance",   quote("썸·연애")),
    ("회사생활", "workplace", quote("회사생활")),
]

# Board slug → plaza hint only. Stored category is the classifier plaza enum
# (FAMILY/FRIEND can appear from these three boards). PLAZA_BANK_CATEGORIES
# already accepts both enums and romance/marriage/workplace.
BOARD_PLAZA_HINT = {
    "marriage": "MARRIED",
    "romance": "COUPLE",
    "workplace": "WORK",
}


def _resolve_category(content: str, title: str = "", board_category: str = "") -> str:
    hint = BOARD_PLAZA_HINT.get(board_category)
    return classify_plaza(content, title or "", channel_hint=hint)

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


def _visible_text(el) -> str:
    """스크린리더 전용(.blind) 라벨을 제외한 표시 텍스트."""
    if el is None:
        return ""
    parts: list[str] = []
    for child in el.children:
        if getattr(child, "name", None) is not None:
            classes = child.get("class") or []
            if "blind" in classes:
                continue
            parts.append(child.get_text(strip=True))
        else:
            text = str(child).strip()
            if text:
                parts.append(text)
    return "".join(parts).strip() or el.get_text(strip=True)


def _parse_like_element(el) -> int | None:
    """블라인드 좋아요 엘리먼트 파싱.

    SSR에서 좋아요 0은 숫자 없이 '좋아요'만 노출된다 (`좋아요좋아요` = blind라벨+표시).
    예전 파서는 숫자를 못 찾으면 None → like 커버리지 ~18.9%. 0으로 해석하면 커버리지가 올라간다.
    추천 글 목록의 span.like는 호출 측에서 제외해야 한다.
    """
    if el is None:
        return None
    text = _visible_text(el)
    num = _parse_numeric_with_unit(text)
    if num is not None:
        return num
    # 숫자 없는 좋아요 센티널 → 0
    cleaned = re.sub(r"좋아요수?", "", text).strip()
    if cleaned == "" or cleaned == "좋아요":
        return 0
    if text in ("좋아요", "좋아요좋아요", "좋아요수좋아요"):
        return 0
    return None


def _is_recommended_or_list_like(el) -> bool:
    """추천 글·토픽 베스트 등 사이드 목록의 like 엘리먼트인지."""
    for parent in el.parents:
        classes = parent.get("class") or []
        if any(c.startswith("rcmd_") for c in classes):
            return True
        if "article-list" in classes or "article-list-pre" in classes:
            return True
    return False


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

        # 첫 번째 wrap-info에서 조회수/댓글수 추출 (article-view-head)
        first_wrap = main.find("div", class_="wrap-info")
        if first_wrap:
            # 조회수 (span.pv)
            pv_span = first_wrap.find("span", class_="pv")
            if pv_span:
                view_count = _parse_numeric_with_unit(_visible_text(pv_span) or pv_span.get_text(strip=True))

            # 댓글수 (span.cmt)
            cmt_span = first_wrap.find("span", class_="cmt")
            if cmt_span:
                comment_count = _parse_numeric_with_unit(_visible_text(cmt_span) or cmt_span.get_text(strip=True))

        # 좋아요 — 본문 액션바(div.article-view-contents div.info span.like) 우선.
        # 예전: 첫 span.like를 break → 0건은 None, 추천글 like를 잘못 집을 위험.
        like_el = main.select_one("div.article-view-contents div.info span.like")
        if like_el is None:
            for like_span in main.find_all("span", class_="like"):
                if like_span.find_parent("div", class_="wrap-comment"):
                    continue
                if _is_recommended_or_list_like(like_span):
                    continue
                like_el = like_span
                break
        like_count = _parse_like_element(like_el)
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


def _extract_comment_author(comment_el) -> str | None:
    """댓글 작성자 닉네임 (마스킹된 형태 그대로)."""
    name_el = comment_el.find("p", class_="name")
    if not name_el:
        return None
    text = " ".join(name_el.get_text(" ", strip=True).split())
    if not text:
        return None
    # "롯데건설 · U*****" / "비공개 · I******** 작성자" → 뒤쪽 닉네임 우선
    if "·" in text:
        tail = text.split("·")[-1].strip()
        # "작성자" 배지 제거
        tail = re.sub(r"\s*작성자\s*$", "", tail).strip()
        return (tail or text)[:100]
    return text[:100]


def _extract_comments(
    soup: BeautifulSoup,
    reference: datetime,
    *,
    post_url: str,
    category: str,
) -> list[dict]:
    """div.wrap-comment에서 댓글 본문을 추출해 COMMENT 행 dict 목록으로 반환.

    source_url은 `{post_url}#comment-{id}`로 유니크하게 둬 crawl.py URL dedup을 통과시킨다.
    COMMENT는 popularity 대상이 아니라 문체 앵커이므로 view/comment_count는 넣지 않는다.
    """
    rows: list[dict] = []
    try:
        main = soup.find("main")
        if not main:
            return rows

        for comment in main.find_all("div", class_="wrap-comment"):
            body_el = comment.find("p", class_="cmt-txt")
            if not body_el:
                continue
            content = body_el.get_text(" ", strip=True)
            if not content or len(content) < MIN_COMMENT_LENGTH:
                continue

            comment_id = comment.get("id") or ""
            # URL fragment로 댓글 단위 dedup (동일 post_url을 POST와 공유하면 crawl.py가 스킵함)
            if comment_id:
                source_url = f"{post_url}#comment-{comment_id}"
            else:
                # id 없는 댓글: 본문 기반 안정 키
                digest = hashlib.sha1(content.encode("utf-8")).hexdigest()[:12]
                source_url = f"{post_url}#comment-{digest}"

            posted_at = None
            date_span = comment.find("span", class_="date")
            if date_span:
                date_only = date_span.get_text(strip=True).replace("작성일", "").strip()
                posted_at = _relative_to_absolute(date_only, reference) if date_only else None

            like_span = comment.find("span", class_="like")
            like_count = _parse_like_element(like_span) if like_span else None

            rows.append({
                "content": content[:1000],
                "content_type": "COMMENT",
                "category": category,
                "title": None,
                "source_url": source_url,
                "author_id": _extract_comment_author(comment),
                "posted_at": posted_at,
                "like_count": like_count,
            })
    except Exception as e:
        logger.debug(f"Blind: comment extraction error: {e}")

    return rows


async def crawl(daily_limit: int = 240) -> List[Dict]:
    """블라인드 크롤링 v3 — 결혼생활/썸·연애/회사생활 채널"""
    if not daily_limit:
        return []

    results: List[Dict] = []
    post_count = 0
    seen_urls: set = set()
    session = requests.Session()
    try:

        for idx, (channel_name, category, encoded) in enumerate(CHANNELS):
            if post_count >= daily_limit:
                break

            logger.info(f"Blind: 채널 '{channel_name}' 크롤 시작")

            for page in range(1, PAGES_PER_CHANNEL + 1):
                if post_count >= daily_limit:
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
                    if post_count >= daily_limit:
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

                    stored_category = _resolve_category(content, title, board_category=category)

                    results.append({
                        "content": content[:1500],
                        "content_type": "POST",
                        "category": stored_category,
                        "title": title or None,
                        "source_url": post_url,
                        "posted_at": posted_at,
                        "view_count": view_count,
                        "like_count": like_count,
                        "comment_count": comment_count,
                        "comment_timestamps": comment_timestamps,
                    })
                    post_count += 1
                    logger.debug(
                        f"Blind: 저장 [{channel_name}] {post_url} "
                        f"(조회:{view_count}, 좋아요:{like_count}, 댓글:{comment_count})"
                    )

                    # COMMENT — 이미 받은 HTML에서 본문 추출 (추가 HTTP 없음). daily_limit은 POST만 카운트.
                    comment_rows = _extract_comments(
                        post_soup,
                        fetch_reference,
                        post_url=post_url,
                        category=stored_category,
                    )
                    results.extend(comment_rows)
                    if comment_rows:
                        logger.debug(f"Blind: COMMENT {len(comment_rows)}건 [{channel_name}] {post_url}")

            # 채널 간 딜레이 (마지막 채널 제외)
            if idx < len(CHANNELS) - 1:
                await asyncio.sleep(random.uniform(3, 6))

        comment_total = sum(1 for r in results if r.get("content_type") == "COMMENT")
        logger.info(f"Blind: 크롤 완료 — POST {post_count} + COMMENT {comment_total} = {len(results)}개 수집")
        return results
    finally:
        session.close()
