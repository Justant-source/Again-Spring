"""
DCInside Gallery Crawler
Fetches posts and comments from DCInside galleries (Life, Love, Marriage).
Uses Playwright with anti-bot measures.
"""
import asyncio
import logging
import random
import re
from typing import List, Dict
from playwright.async_api import async_playwright

logger = logging.getLogger(__name__)

GALLERIES = [
    ("life_incident", "https://gall.dcinside.com/mgallery/board/lists/?id=life_incident&sort_type=recomm"),
    ("love", "https://gall.dcinside.com/mgallery/board/lists/?id=love&sort_type=recomm"),
    ("marriage", "https://gall.dcinside.com/board/lists/?id=marriage&sort_type=recomm"),
]

USER_AGENTS = [
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
]

HIDE_WEBDRIVER_SCRIPT = """
    Object.defineProperty(navigator, 'webdriver', {
        get: () => undefined,
    });
"""


async def crawl(daily_limit: int = 100) -> List[Dict]:
    """
    Crawl DCInside galleries.

    Args:
        daily_limit: Maximum number of posts to fetch (max 10 posts per gallery)

    Returns:
        List of post/comment dicts with keys: content, content_type, source, category
    """
    results = []
    gallery_name, gallery_url = random.choice(GALLERIES)
    logger.info(f"DCInside crawl started - gallery: {gallery_name}")

    async with async_playwright() as p:
        browser = await p.chromium.launch(headless=True)

        context = await browser.new_context(
            user_agent=random.choice(USER_AGENTS),
            viewport={"width": 1280, "height": 720},
        )

        page = await context.new_page()

        # Hide webdriver
        await page.add_init_script(HIDE_WEBDRIVER_SCRIPT)

        try:
            await page.goto(gallery_url, wait_until="domcontentloaded", timeout=10000)
            await asyncio.sleep(random.uniform(2.5, 4))

            # Extract post links
            post_links = await page.locator("tr.ub-content td.gall_tit a[href*='/board/view/']").all()
            logger.info(f"Found {len(post_links)} posts")

            for idx, link in enumerate(post_links[:10]):
                if idx >= 10:  # max 10 posts per gallery
                    break

                try:
                    post_url = await link.get_attribute("href")
                    if not post_url:
                        continue

                    # Make absolute URL if needed
                    if not post_url.startswith("http"):
                        post_url = "https://gall.dcinside.com" + post_url

                    await page.goto(post_url, wait_until="domcontentloaded", timeout=10000)
                    await asyncio.sleep(random.uniform(2.5, 6))

                    # Extract post body
                    body_elem = await page.locator(".write_div").first.text_content()
                    if body_elem and body_elem.strip():
                        results.append({
                            "content": body_elem.strip(),
                            "content_type": "POST",
                            "source": "dcinside",
                            "category": gallery_name,
                        })

                    # Extract comments
                    comment_elems = await page.locator(".cmt_txtbox p.usertxt").all()
                    for comment_elem in comment_elems:
                        comment_text = await comment_elem.text_content()
                        if comment_text and comment_text.strip():
                            results.append({
                                "content": comment_text.strip(),
                                "content_type": "COMMENT",
                                "source": "dcinside",
                                "category": gallery_name,
                            })

                    if len(results) >= daily_limit:
                        break

                except Exception as e:
                    logger.warning(f"Failed to process post {idx}: {e}")
                    continue

        except Exception as e:
            logger.error(f"Failed to load gallery page: {e}")

        finally:
            await context.close()
            await browser.close()

        logger.info(f"DCInside crawl completed: {len(results)} items collected")

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
