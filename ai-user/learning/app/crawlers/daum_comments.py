"""
Daum News Comments Crawler
Fetches comments from Daum news articles about Korean conflicts.
"""
import asyncio
import httpx
import logging
import random
import re
from typing import List, Dict
from urllib.parse import quote
import time

logger = logging.getLogger(__name__)

KEYWORDS = [
    "남자친구",
    "시어머니",
    "직장 갑질",
    "친구 배신",
    "부부 싸움",
    "가족 갈등",
]

USER_AGENTS = [
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
]


def extract_article_id(url: str) -> str:
    """Extract article ID from Daum news URL."""
    # Format: https://v.daum.net/v/{id}
    match = re.search(r'/v/(\d+)', url)
    if match:
        return match.group(1)
    return None


async def crawl(daily_limit: int = 200) -> List[Dict]:
    """
    Crawl Daum news comments.

    Args:
        daily_limit: Maximum number of comments to fetch

    Returns:
        List of comment dicts with keys: content, content_type, source, category
    """
    results = []
    comment_count = 0

    keyword = random.choice(KEYWORDS)
    logger.info(f"Daum crawl started with keyword: {keyword}")

    async with httpx.AsyncClient(timeout=10) as client:
        # Search for articles
        search_url = f"https://search.daum.net/search?w=news&q={quote(keyword)}"

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
            r'v\.daum\.net/v/(\d+)',
            response.text
        )
        article_ids = list(set(article_matches))  # Deduplicate

        logger.info(f"Found {len(article_ids)} articles")

        for article_id in article_ids[:10]:
            if comment_count >= daily_limit:
                break

            # Delay between requests
            await asyncio.sleep(random.uniform(2, 4))

            # Fetch comments
            comment_url = (
                f"https://comment.daum.net/apis/v1/posts/@{article_id}/comments"
                f"?parentId=0&offset=0&limit=30&sort=POPULAR"
            )

            try:
                headers = {"User-Agent": random.choice(USER_AGENTS)}
                resp = await client.get(comment_url, headers=headers)
                resp.raise_for_status()
            except httpx.HTTPStatusError as e:
                if e.response.status_code in [403, 429]:
                    logger.error(f"HTTP {e.response.status_code} - stopping immediately")
                    return results
                logger.warning(f"Failed to fetch comments for {article_id}: {e}")
                continue

            try:
                data = resp.json()
            except Exception as e:
                logger.warning(f"Failed to parse JSON for {article_id}: {e}")
                continue

            # Navigate response structure
            comments_list = data.get('data', {}).get('comments', [])

            for comment_item in comments_list:
                if comment_count >= daily_limit:
                    break

                comment_text = comment_item.get('contents', '').strip()
                if not comment_text:
                    continue

                results.append({
                    "content": comment_text,
                    "content_type": "COMMENT",
                    "source": "daum_news",
                    "category": "relationship_conflict",
                })
                comment_count += 1

        logger.info(f"Daum crawl completed: {comment_count} comments collected")

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
