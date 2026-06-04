"""
Naver News Comments Crawler
Fetches comments from Naver news articles about Korean conflicts/relationships.
"""
import asyncio
import httpx
import json
import logging
import random
import re
from typing import List, Dict
from urllib.parse import quote
import time

logger = logging.getLogger(__name__)

KEYWORDS = [
    "남자친구 갈등",
    "시어머니 갈등",
    "직장 상사",
    "친구 배신",
    "이혼",
    "부부 싸움",
    "돈 빌려줬는데",
]

USER_AGENTS = [
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
]


def extract_oid_aid(url: str) -> tuple:
    """Extract OID and AID from Naver news URL."""
    match = re.search(r'/article/(\d+)/(\d+)', url)
    if match:
        return match.group(1), match.group(2)
    return None, None


def parse_jsonp(jsonp_text: str) -> dict:
    """Parse JSONP response by removing callback wrapper."""
    # Remove callback wrapper: callback({...})
    match = re.search(r'\(({.*})\)', jsonp_text, re.DOTALL)
    if match:
        try:
            return json.loads(match.group(1))
        except json.JSONDecodeError:
            logger.warning("Failed to parse JSONP")
            return {}
    return {}


async def crawl(daily_limit: int = 200) -> List[Dict]:
    """
    Crawl Naver news comments.

    Args:
        daily_limit: Maximum number of comments to fetch

    Returns:
        List of comment dicts with keys: content, content_type, source, category
    """
    results = []
    comment_count = 0

    keyword = random.choice(KEYWORDS)
    logger.info(f"Naver crawl started with keyword: {keyword}")

    async with httpx.AsyncClient(timeout=10) as client:
        # Search for articles
        search_url = f"https://search.naver.com/search.naver?where=news&query={quote(keyword)}"

        try:
            headers = {"User-Agent": random.choice(USER_AGENTS)}
            response = await client.get(search_url, headers=headers)
            response.raise_for_status()
        except httpx.HTTPStatusError as e:
            if e.response.status_code in [403, 429]:
                logger.error(f"HTTP {e.response.status_code} - stopping immediately")
                return results
            raise

        # Extract article links
        article_matches = re.findall(
            r'news\.naver\.com/article/(\d+)/(\d+)',
            response.text
        )

        logger.info(f"Found {len(article_matches)} articles")

        for oid, aid in article_matches[:10]:  # Limit articles per search
            if comment_count >= daily_limit:
                break

            # Delay between requests
            await asyncio.sleep(random.uniform(2, 5))

            # Fetch comments
            comment_url = (
                f"https://apis.naver.com/commentBox/cbox5/web_neo_list_jsonp.json"
                f"?ticket=news&templateId=default&pool=cbox5&lang=ko&country=KR"
                f"&objectId=news{oid}%2C{aid}&pageSize=30&sort=NEW"
            )

            try:
                headers = {"User-Agent": random.choice(USER_AGENTS)}
                resp = await client.get(comment_url, headers=headers)
                resp.raise_for_status()
            except httpx.HTTPStatusError as e:
                if e.response.status_code in [403, 429]:
                    logger.error(f"HTTP {e.response.status_code} - stopping immediately")
                    return results
                logger.warning(f"Failed to fetch comments for {oid}/{aid}: {e}")
                continue

            data = parse_jsonp(resp.text)

            if not data or 'result' not in data:
                continue

            result = data.get('result', {})
            comments_list = result.get('commentList', [])

            for comment_item in comments_list:
                if comment_count >= daily_limit:
                    break

                comment_text = comment_item.get('contents', '').strip()
                if not comment_text:
                    continue

                results.append({
                    "content": comment_text,
                    "content_type": "COMMENT",
                    "source": "naver_news",
                    "category": "relationship_conflict",
                })
                comment_count += 1

        logger.info(f"Naver crawl completed: {comment_count} comments collected")

    return results


if __name__ == "__main__":
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s - %(name)s - %(levelname)s - %(message)s"
    )

    result = asyncio.run(crawl(daily_limit=200))
    print(f"Total results: {len(result)}")
    for r in result[:3]:
        print(r)
