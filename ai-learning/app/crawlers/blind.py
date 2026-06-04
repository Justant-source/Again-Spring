"""
Blind Korea Topics Crawler
Fetches posts and comments from Blind Korea (non-login accessible topics).
Uses Playwright with strict anti-bot measures.
"""
import asyncio
import logging
import random
from typing import List, Dict
from playwright.async_api import async_playwright

logger = logging.getLogger(__name__)

BLIND_URL = "https://www.teamblind.com/kr/topics"

USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

HIDE_WEBDRIVER_SCRIPT = """
    Object.defineProperty(navigator, 'webdriver', {
        get: () => undefined,
    });
"""


async def crawl(daily_limit: int = 50) -> List[Dict]:
    """
    Crawl Blind Korea topics.

    Args:
        daily_limit: Maximum number of posts to fetch (max 8 posts, stricter anti-bot)

    Returns:
        List of post/comment dicts with keys: content, content_type, source, category
    """
    results = []
    logger.info("Blind Korea crawl started")

    async with async_playwright() as p:
        browser = await p.chromium.launch(headless=True)

        context = await browser.new_context(
            user_agent=USER_AGENT,
            viewport={"width": 1280, "height": 720},
        )

        page = await context.new_page()

        # Hide webdriver
        await page.add_init_script(HIDE_WEBDRIVER_SCRIPT)

        try:
            await page.goto(BLIND_URL, wait_until="domcontentloaded", timeout=10000)
            await asyncio.sleep(random.uniform(3, 7))

            # Extract post links with deduplication
            post_link_elements = await page.locator("article a[href*='/kr/post/']").all()
            post_urls_seen = set()
            post_links = []

            for elem in post_link_elements:
                try:
                    url = await elem.get_attribute("href")
                    if url and url not in post_urls_seen:
                        post_urls_seen.add(url)
                        post_links.append(elem)
                except Exception:
                    continue

            logger.info(f"Found {len(post_links)} unique posts")

            for idx, link in enumerate(post_links[:8]):  # max 8 posts
                if idx >= 8:
                    break

                try:
                    post_url = await link.get_attribute("href")
                    if not post_url:
                        continue

                    # Make absolute URL if needed
                    if not post_url.startswith("http"):
                        post_url = "https://www.teamblind.com" + post_url

                    # Stricter delay for anti-bot
                    await asyncio.sleep(random.uniform(3, 7))

                    await page.goto(post_url, wait_until="domcontentloaded", timeout=10000)
                    await asyncio.sleep(random.uniform(3, 7))

                    # Extract post body
                    body_elem = await page.locator("div.article-body").first.text_content()
                    if body_elem and body_elem.strip():
                        results.append({
                            "content": body_elem.strip(),
                            "content_type": "POST",
                            "source": "blind",
                            "category": "topic",
                        })

                    # Extract comments
                    comment_elems = await page.locator("div.comment-body p").all()
                    for comment_elem in comment_elems:
                        comment_text = await comment_elem.text_content()
                        if comment_text and comment_text.strip():
                            results.append({
                                "content": comment_text.strip(),
                                "content_type": "COMMENT",
                                "source": "blind",
                                "category": "topic",
                            })

                    if len(results) >= daily_limit:
                        break

                except Exception as e:
                    logger.warning(f"Failed to process post {idx}: {e}")
                    await asyncio.sleep(random.uniform(3, 7))
                    continue

        except Exception as e:
            logger.error(f"Failed to load Blind page: {e}")

        finally:
            await context.close()
            await browser.close()

        logger.info(f"Blind Korea crawl completed: {len(results)} items collected")

    return results


if __name__ == "__main__":
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s - %(name)s - %(levelname)s - %(message)s"
    )

    result = asyncio.run(crawl(daily_limit=50))
    print(f"Total results: {len(result)}")
    for r in result[:3]:
        print(r)
