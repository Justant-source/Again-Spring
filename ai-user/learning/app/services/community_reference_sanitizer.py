"""Deterministic normalization for community names found in crawled text.

Source names are useful as internal provenance (``source`` and ``source_url``),
but must never be carried into a story that readers see or into an LLM prompt.
Keep this deliberately separate from user-input moderation: it is only for text
obtained from an external crawler.
"""
from __future__ import annotations

import re
from typing import Any, Mapping


# Longer / unambiguous expressions must be replaced before their abbreviations.
# The replacement is intentionally generic: Again Spring is not presented as the
# place where the original conversation happened.
_REPLACEMENTS: tuple[tuple[re.Pattern[str], str], ...] = (
    (re.compile(r"네이트\s*판|nate\s*pann?", re.IGNORECASE), "인터넷 커뮤니티"),
    (re.compile(r"블라인드|(?<![가-힣])블라(?=(?:랑|와|는|가|에|에서|도|의|에요|임|야|하고|같은|글|댓글|유저|사람|분들|반응|문화|$))|\bblind\b", re.IGNORECASE), "온라인 커뮤니티"),
    (re.compile(r"디시인사이드|디시|dcinside|dc\s*inside", re.IGNORECASE), "온라인 커뮤니티"),
    (re.compile(r"에펨코리아|펨코|fm\s*korea", re.IGNORECASE), "온라인 커뮤니티"),
    (re.compile(r"더쿠|인스티즈|보배드림|클리앙|루리웹|웃긴대학|웃대|오늘의유머|오유|여시|개드립"), "온라인 커뮤니티"),
    (re.compile(r"(?<![가-힣])판녀(?:들)?"), "커뮤니티 이용자들"),
    # "판" is a normal Korean noun, so only replace it in clear community
    # contexts ("판에 올렸다", "판글", "판 유저"), never in words such as 판사.
    (re.compile(r"(?<![가-힣])판\s*(?=(?:에|에서|으로|은|는|도|만|글|댓글|유저|사람|분들|반응|문화))"), "커뮤니티"),
    (re.compile(r"(?<![가-힣])판\s+(?=(?:이럴|보면|에서는|문화|분위기|반응))"), "커뮤니티 "),
)


def sanitize_crawled_text(text: str | None) -> str | None:
    """Replace specific-community references with reader-neutral language."""
    if text is None:
        return None
    sanitized = text
    for pattern, replacement in _REPLACEMENTS:
        sanitized = pattern.sub(replacement, sanitized)
    # The generic noun ends in a vowel, unlike several source names.  Repair
    # particles after replacement so the public sentence remains natural.
    sanitized = sanitized.replace("커뮤니티을", "커뮤니티를").replace("커뮤니티은", "커뮤니티는")
    sanitized = sanitized.replace("커뮤니티이", "커뮤니티가")
    return sanitized


def sanitize_crawled_item(item: Mapping[str, Any]) -> dict[str, Any]:
    """Return a copy safe for ingest; provenance fields remain untouched."""
    sanitized = dict(item)
    sanitized["content"] = sanitize_crawled_text(item.get("content")) or ""
    if "title" in item:
        sanitized["title"] = sanitize_crawled_text(item.get("title"))
    return sanitized
