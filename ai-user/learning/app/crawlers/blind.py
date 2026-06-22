"""
블라인드 크롤러 v3 — 3개 채널 타게팅 (결혼생활 / 썸·연애 / 회사생활)
인증 불필요, ?page=N 페이지네이션, 갈등 키워드 필터
"""
import asyncio
import logging
import random
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


def _extract_content(soup: BeautifulSoup) -> str | None:
    # 1. og:description (SSR에서 본문 일부 포함)
    og = soup.find("meta", property="og:description")
    if og and og.get("content"):
        c = og["content"].strip()
        if len(c) >= MIN_CONTENT_LENGTH:
            return c

    # 2. <main> 영역
    main = soup.find("main")
    if main:
        t = main.get_text(separator="\n", strip=True)
        if len(t) >= MIN_CONTENT_LENGTH:
            return t

    # 3. <p> 태그 전체 합산
    paras = [p.get_text(strip=True) for p in soup.find_all("p") if len(p.get_text(strip=True)) > 30]
    if paras:
        t = "\n".join(paras)
        if len(t) >= MIN_CONTENT_LENGTH:
            return t

    # 4. <article>
    article = soup.find("article")
    if article:
        t = article.get_text(separator="\n", strip=True)
        if len(t) >= MIN_CONTENT_LENGTH:
            return t

    # 5. div[class*='content']
    div = soup.find("div", class_=lambda x: x and "content" in x)
    if div:
        t = div.get_text(separator="\n", strip=True)
        if len(t) >= MIN_CONTENT_LENGTH:
            return t

    return None


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

                results.append({
                    "content": content[:1500],
                    "content_type": "POST",
                    "category": category,
                    "title": title or None,
                    "source_url": post_url,
                })
                logger.debug(f"Blind: 저장 [{channel_name}] {post_url}")

        # 채널 간 딜레이 (마지막 채널 제외)
        if idx < len(CHANNELS) - 1:
            await asyncio.sleep(random.uniform(3, 6))

    logger.info(f"Blind: 크롤 완료 — {len(results)}개 수집")
    return results
