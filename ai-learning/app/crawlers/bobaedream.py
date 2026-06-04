"""
Bobaedream Free Board Crawler
Fetches posts and comments from Bobaedream free board.
Uses Playwright with anti-bot measures.
"""
import asyncio
import logging
import random
from typing import List, Dict
from playwright.async_api import async_playwright

logger = logging.getLogger(__name__)

BOARD_URL = "https://www.bobaedream.co.kr/board/list/freeb?sort=recommend"

USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

HIDE_WEBDRIVER_SCRIPT = """
    Object.defineProperty(navigator, 'webdriver', {
        get: () => undefined,
    });
"""


async def crawl(daily_limit: int = 100) -> List[Dict]:
    """
    Crawl Bobaedream free board.

    Args:
        daily_limit: Maximum number of posts to fetch (max 10 posts)

    Returns:
        List of post/comment dicts with keys: content, content_type, source, category
    """
    results = []
    logger.info("Bobaedream crawl started")

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
            await page.goto(BOARD_URL, wait_until="domcontentloaded", timeout=10000)
            await asyncio.sleep(random.uniform(2, 5))

            # Extract post links
            post_links = await page.locator("ul.basicList li a.bsubject").all()
            logger.info(f"Found {len(post_links)} posts")

            for idx, link in enumerate(post_links[:10]):  # max 10 posts
                if idx >= 10:
                    break

                try:
                    post_url = await link.get_attribute("href")
                    if not post_url:
                        continue

                    # Make absolute URL if needed
                    if not post_url.startswith("http"):
                        post_url = "https://www.bobaedream.co.kr" + post_url

                    await page.goto(post_url, wait_until="domcontentloaded", timeout=10000)
                    await asyncio.sleep(random.uniform(2, 5))

                    # Extract post body
                    body_elem = await page.locator("div.bodyCont").first.text_content()
                    if body_elem and body_elem.strip():
                        results.append({
                            "content": body_elem.strip(),
                            "content_type": "POST",
                            "source": "bobaedream",
                            "category": "freeboard",
                        })

                    # Extract comments
                    comment_elems = await page.locator("ul#repList li p.con").all()
                    for comment_elem in comment_elems:
                        comment_text = await comment_elem.text_content()
                        if comment_text and comment_text.strip():
                            results.append({
                                "content": comment_text.strip(),
                                "content_type": "COMMENT",
                                "source": "bobaedream",
                                "category": "freeboard",
                            })

                    if len(results) >= daily_limit:
                        break

                except Exception as e:
                    logger.warning(f"Failed to process post {idx}: {e}")
                    continue

        except Exception as e:
            logger.error(f"Failed to load Bobaedream page: {e}")

        finally:
            await context.close()
            await browser.close()

        logger.info(f"Bobaedream crawl completed: {len(results)} items collected")

    return results


if __name__ == "__main__":
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s - %(name)s - %(levelname)s - %(message)s"
    )

    result = asyncio.run(crawl(daily_limit=100))
    print(f"Total results: {len(result)}")
    for r in result[:3]:
        print(r)
