"""원문 글에 실제로 달린 댓글을 문체·길이 참고용으로 뽑는다 (POST /examples/source-comments).

배경: 생성 댓글이 실제 댓글보다 5배 길었다(실제는 절반이 30자 이하). 원문 글의 진짜 댓글을
LLM에 참고로 넘겨 길이 분포를 맞춘다.

글↔댓글 URL 대응 (prod 실측 2026-11-21, 크롤러 blind.py/natepan.py와 동일):
  - natepan: 댓글 source_url = `<글 URL>#cmt<N>` (구형 일부는 `<글 URL>` 그대로)
  - blind  : 댓글 source_url = `<글 URL>#comment-<id>`
  → 두 소스 모두 `urldefrag(댓글 URL)[0] == 글 URL`. source_url엔 인덱스가 없어 `>= 'URL#' AND < 'URL$'`
    범위로 좁히고(LIKE의 %·_ 이스케이프 회피), 파이썬에서 defrag 일치를 한 번 더 확인한다.

뽑는 방식: 좋아요 순이 아니라 **무작위 표본** — 실제 길이 분포(절반이 30자 이하)가 유지돼야 한다.
"""
from __future__ import annotations

import logging
import random
import re
from typing import Any, Optional
from urllib.parse import urldefrag

from app.db.session import get_db

logger = logging.getLogger(__name__)

MIN_LEN = 2
MAX_LEN = 200
DEFAULT_LIMIT = 12
MAX_LIMIT = 20
# 매칭 댓글이 이보다 적으면 같은 source(가능하면 같은 category)의 최근 실제 댓글로 채운다.
MIN_MATCHED = 6
# 같은 본문이 이 개수 이상 서로 다른 행에 있으면 광고·도배로 본다(블라인드 사이드바 상품명 등).
# 길이 6자 미만(ㅋㅋㅋ, ㅇㅈ 같은 반응)은 정상적으로 반복되므로 대상에서 뺀다.
DUP_AD_MIN_COUNT = 3
DUP_CHECK_MIN_LEN = 6
_FILL_POOL_ROWS = 800

_PLACEHOLDER_RE = re.compile(
    r"삭제된 댓글|삭제한 댓글|삭제되었|숨김 처리|숨겨진 댓글|블라인드 처리|클린봇|관리자에 의해|신고에 의해|\(광고\)|^\[?광고\]?"
)
_PII_RE = re.compile(
    r"https?://|www\.|[A-Za-z0-9-]+\.(?:com|net|kr|co|io|me|ly)\b"
    r"|[\w.+-]+@[\w-]+\.[\w.-]+"
    r"|(?<!\d)0\d{1,2}[-.\s]?\d{3,4}[-.\s]?\d{4}(?!\d)"
)


def clamp_limit(limit: Optional[int]) -> int:
    try:
        n = int(limit) if limit is not None else DEFAULT_LIMIT
    except (TypeError, ValueError):
        n = DEFAULT_LIMIT
    return max(1, min(MAX_LIMIT, n))


def _signatures():
    try:
        from app.services import llm_error_signatures as les
        return les.load()
    except Exception as e:  # SSOT 파일 미탑재 환경에서도 댓글 조회 자체는 살린다
        logger.error("source_comments: llm error signatures unavailable: %s", e)
        return None


def clean_comment(raw: Any, sigs=None) -> Optional[str]:
    """부적합하면 None, 적합하면 공백 정리된 본문."""
    if not isinstance(raw, str):
        return None
    text = raw.strip()
    if len(text) < MIN_LEN or len(text) > MAX_LEN:
        return None
    if _PLACEHOLDER_RE.search(text) and len(text) < 60:
        return None
    if _PII_RE.search(text):
        return None
    if sigs is not None:
        if sigs.contains_signature(text.lower()) or sigs.has_prompt_leak(text):
            return None
    return text


def _drop_ads(cur, texts: list[str]) -> list[str]:
    """전역 중복(DUP_AD_MIN_COUNT회 이상)인 긴 본문 제거 — 광고성 상품명 댓글."""
    check = [t for t in texts if len(t) >= DUP_CHECK_MIN_LEN]
    if not check:
        return texts
    ph = ",".join(["%s"] * len(check))
    cur.execute(
        f"""SELECT content, COUNT(*) AS c FROM example_bank
            WHERE content_type = 'COMMENT' AND content IN ({ph})
            GROUP BY content HAVING COUNT(*) >= %s""",
        [*check, DUP_AD_MIN_COUNT],
    )
    bad = {r["content"] for r in cur.fetchall()}
    return [t for t in texts if t not in bad]


def _dedupe(rows_text: list[str]) -> list[str]:
    seen: set[str] = set()
    out: list[str] = []
    for t in rows_text:
        key = re.sub(r"\s+", " ", t)
        if key in seen:
            continue
        seen.add(key)
        out.append(t)
    return out


def _matched_comments(cur, source: str, post_url: str, sigs) -> list[str]:
    cur.execute(
        """SELECT source_url, content FROM example_bank
           WHERE content_type = 'COMMENT' AND source = %s
             AND source_url >= %s AND source_url < %s""",
        (source, post_url + "#", post_url + "$"),
    )
    texts: list[str] = []
    for r in cur.fetchall():
        url = r.get("source_url") or ""
        if urldefrag(url)[0] != post_url:  # 콜레이션(대소문자 무시) 오매칭 방어
            continue
        c = clean_comment(r.get("content"), sigs)
        if c:
            texts.append(c)
    return _dedupe(texts)


def _fill_comments(cur, source: str, category: Optional[str], sigs, exclude: set[str], need: int) -> list[str]:
    """같은 source(가능하면 같은 category)의 최근 실제 댓글 무작위 표본."""
    def pull(with_category: bool) -> list[str]:
        sql = "SELECT content FROM example_bank WHERE content_type = 'COMMENT' AND source = %s"
        params: list[Any] = [source]
        if with_category and category:
            sql += " AND category = %s"
            params.append(category)
        sql += " ORDER BY id DESC LIMIT %s"
        params.append(_FILL_POOL_ROWS)
        cur.execute(sql, params)
        out = []
        for r in cur.fetchall():
            c = clean_comment(r.get("content"), sigs)
            if c and c not in exclude:
                out.append(c)
        return _dedupe(out)

    pool = pull(True) if category else []
    if len(pool) < need:
        extra = [c for c in pull(False) if c not in set(pool)]
        pool = pool + extra
    random.shuffle(pool)
    # 광고 판정 쿼리가 무거우니 필요량의 3배까지만 검사
    pool = _drop_ads(cur, pool[: need * 3])
    return pool[:need]


def get_source_comments(example_id: int, limit: Optional[int] = DEFAULT_LIMIT) -> dict[str, Any]:
    lim = clamp_limit(limit)
    sigs = _signatures()
    with get_db() as conn:
        with conn.cursor() as cur:
            cur.execute(
                "SELECT id, source, source_url, category, content_type FROM example_bank WHERE id = %s",
                (int(example_id),),
            )
            post = cur.fetchone()
            if not post or post.get("content_type") != "POST" or not post.get("source_url"):
                return {"comments": [], "matched": False}
            source = post["source"]
            matched = _matched_comments(cur, source, post["source_url"], sigs)
            matched = _drop_ads(cur, matched)
            random.shuffle(matched)
            picked = matched[:lim]
            if len(matched) < MIN_MATCHED:
                filler = _fill_comments(cur, source, post.get("category"), sigs,
                                        set(picked), lim - len(picked))
                picked = picked + filler
            random.shuffle(picked)  # 매칭/보충이 순서로 구분되지 않게
    return {"comments": picked, "matched": bool(matched)}
