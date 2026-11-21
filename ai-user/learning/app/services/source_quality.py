"""Source-quality judge for crawled POSTs used as reconstruction sources (pure, no I/O).

배경 (2026-11 prod 최근 30일 example_bank POST 실측, 블라인드 1605 / 네이트판 1363):
  * 블라인드 25%: og:description 미리보기(250자 + "...")가 본문으로 저장됨. 길이 253이 405건 몰림.
    미리보기는 줄바꿈(<br>)도 없다.
  * 블라인드 52%: 본문 대신 <main> 전체 텍스트가 저장돼 사이드바(광고·다른 글 제목·댓글)가 섞임.
  * 네이트판은 본문 전체를 수집하므로 "..." 끝은 작성자가 쓴 말줄임표다(잘림 아님).

판정은 **과하게 버리지 않는 쪽**이 아니라 **확실한 오염만** 버리는 쪽으로 설계했다.
오염 꼬리만 잘라 살리는 정리는 하지 않는다: 사이드바가 본문 앞/중간에도 끼어 있어
(실측: 첫 줄부터 광고인 행 존재) 본문 경계를 신뢰할 수 없다.
"""
from __future__ import annotations

import re
from collections import Counter
from typing import Optional

# 블라인드 og:description 미리보기 = 250자 + "..." (실측 253 405건, 246~259 산포)
BLIND_PREVIEW_MIN_LEN = 245
BLIND_PREVIEW_MAX_LEN = 262
# 사연 재구성 근거 하한. 실측: 150자 미만은 한 줄 푸념·시·가사·낚시가 대부분
# (blind 100~149, natepan 80~149 표본 전수 확인).
MIN_USABLE_CHARS = 150

_ELLIPSIS_END = re.compile(r"(\.\.\.|…)\s*$")

# 광고 표지: "(광고)" 접두 (블라인드 광고 카드, 실측 445종)
_AD_LINE = re.compile(r"^\(?광고\)")

# 사이드바/배너/UI 상용구 (실측 다행 반복 라인에서 추출). 일반 본문에서 줄 전체가
# 이것일 가능성은 사실상 없다.
_BOILERPLATE_LINE = re.compile(
    "|".join([
        r"소개팅하러 가기",
        r"가연\S*\s*(?:에서\s*)?(?:솔로 탈출|미팅파티)",
        r"무료 가연 미팅파티",
        r"쿠팡 특가",
        r"로켓프레시",
        r"쿠팡판매1위",
        r"단 하루 특가",
        r"브랜드관에서",
        r"지금\s*예매하기",
        r"직장인 맞춤 DB",
        r"셀소 주간베스트",
        r"^작성자가 삭제한 댓글입니다",
    ])
)
# 사이드바의 회사명 라벨 줄: "유나이티드파슬서비스코리아 ·"
_COMPANY_LABEL_LINE = re.compile(r"^\S.{0,30}\s·\s*$")


def _lines(text: str) -> list[str]:
    return [ln.strip() for ln in (text or "").split("\n") if ln.strip()]


def _dup_lines(lines: list[str]) -> list[str]:
    cnt = Counter(ln for ln in lines if len(ln) >= 8)
    return [ln for ln, n in cnt.items() if n > 1]


def is_truncated_preview(content: str, source: str) -> bool:
    """블라인드 og:description 미리보기(길이 대역 + 말줄임 + 줄바꿈 없음).

    네이트판은 상세 본문 전체라 이 판정을 적용하지 않는다(말줄임표로 끝난 정상 글 보호).
    블라인드도 줄바꿈이 있으면(<br> 본문 추출본) 미리보기가 아니다.
    """
    if (source or "").strip().lower() != "blind":
        return False
    c = (content or "").rstrip()
    if "\n" in c:
        return False
    return (
        bool(_ELLIPSIS_END.search(c))
        and BLIND_PREVIEW_MIN_LEN <= len(c) <= BLIND_PREVIEW_MAX_LEN
    )


def assess_source(
    title: Optional[str],
    content: Optional[str],
    source: str,
) -> tuple[bool, list[str]]:
    """원문이 사연 재구성 근거로 쓸 만한가. (usable, reasons). reasons 비면 usable."""
    reasons: list[str] = []
    text = (content or "").strip()
    if not text:
        return False, ["empty"]

    if is_truncated_preview(text, source):
        reasons.append("truncated_preview")

    lines = _lines(text)
    if any(_AD_LINE.match(ln) for ln in lines):
        reasons.append("ad_marker")
    if any(
        _BOILERPLATE_LINE.search(ln) or (_COMPANY_LABEL_LINE.match(ln) and len(lines) > 1)
        for ln in lines
    ):
        reasons.append("sidebar_boilerplate")

    dups = _dup_lines(lines)
    if dups:
        dup_chars = sum(len(ln) for ln in dups)
        if len(dups) >= 2 or dup_chars >= 0.25 * max(1, len(text)):
            reasons.append("duplicate_lines")

    if len(text) < MIN_USABLE_CHARS:
        reasons.append("too_short")

    return (not reasons), reasons
