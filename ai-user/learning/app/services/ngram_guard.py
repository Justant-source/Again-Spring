import re
import logging
from typing import Tuple

logger = logging.getLogger(__name__)

# WO-CRAWL-01 소급 감사(2026-07-30, .request/WO-CRAWL-01_표절_소급감사_결과.md) 결과로 캘리브레이션됨.
# 정상 재가공 50건 표본: 72%는 겹침 0%, 99%(49/50)는 20% 이하. 0.20은 "당장" 단계 임계값 —
# 위양성(정상 재가공을 표절로 오탐) 없이 유일한 이상치(초단문 1건)만 걸러냄.
# 중기 계획: 표본이 쌓이면 0.10으로 단계적 강화(감사 시점 기준 92%가 10% 이하).
DEFAULT_NGRAM_THRESHOLD = 0.20
DEFAULT_MIN_GRAM_SIZE = 12


def _normalize_text_for_comparison(text: str) -> str:
    """
    텍스트를 n-gram 비교용으로 정규화합니다.
    - 연속 공백 축약 (한 칸으로)
    - 개행을 공백으로 변환
    - 트레일 공백 제거

    단순 공백 차이로 표절 검사를 회피하는 것을 방지합니다.
    """
    text = text.replace("\r\n", " ").replace("\r", " ").replace("\n", " ")
    text = re.sub(r"[ \t]+", " ", text)
    text = text.strip()
    return text


def _find_overlaps(generated: str, original: str, min_gram: int) -> Tuple[list[str], int]:
    """
    두 텍스트에서 min_gram 이상의 연속으로 일치하는 부분 문자열들을 찾습니다.

    Args:
        generated: 생성된 텍스트
        original: 원본 텍스트
        min_gram: 최소 겹침 길이

    Returns:
        (겹침 문자열 리스트, 총 겹침 문자수)
    """
    if not generated or not original or min_gram < 1:
        return [], 0

    overlaps = []
    total_overlap_chars = 0

    # Sliding window: generated의 모든 부분문자열을 original과 비교
    gen_len = len(generated)

    for start in range(gen_len):
        for end in range(start + min_gram, gen_len + 1):
            substring = generated[start:end]
            if substring in original:
                # 겹침 발견. 이미 더 긴 겹침이 있으면 중복 계산 피하기.
                # 같은 start에서 가장 긴 겹침만 카운트.
                overlaps.append(substring)
            else:
                # substring이 없으면 더 긴 것도 없을 것 (문자열 특성상)
                # 하지만 정확성을 위해 계속 진행
                pass

    # 중복 제거 후 총 문자수 계산
    unique_overlaps = list(set(overlaps))

    # 더 정밀한 계산: greedy matching으로 비겹침 부분만 카운트
    # 하지만 간단하게 하기 위해, 겹침 문자열의 합집합 길이 계산
    # (같은 부분이 중복 나타나도 1회만 카운트)
    covered = set()
    for overlap_str in sorted(unique_overlaps, key=len, reverse=True):
        # 가장 긴 겹침부터 처리
        for i in range(len(generated) - len(overlap_str) + 1):
            if generated[i:i+len(overlap_str)] == overlap_str:
                for j in range(i, i + len(overlap_str)):
                    covered.add(j)

    total_overlap_chars = len(covered)

    return unique_overlaps, total_overlap_chars


def overlap_ratio(generated: str, original: str, min_gram: int = 12) -> float:
    """
    생성된 텍스트와 원본 텍스트 사이의 n-gram 겹침 비율을 계산합니다.

    두 텍스트를 정규화한 후, min_gram 이상의 연속으로 일치하는
    부분 문자열들을 찾아서, 그 겹침 문자 총합을 생성 텍스트 길이로 나눕니다.

    공백·개행 차이로 회피되는 것을 방지하기 위해 정규화를 먼저 수행합니다.

    Args:
        generated: 생성된 텍스트 (점검 대상)
        original: 원본 텍스트 (참조)
        min_gram: 최소 겹침 길이 (기본값 12)

    Returns:
        0.0 ~ 1.0 범위의 비율. 1.0에 가까울수록 큰 겹침.
    """
    if not generated or not original:
        return 0.0

    # 정규화
    gen_normalized = _normalize_text_for_comparison(generated)
    orig_normalized = _normalize_text_for_comparison(original)

    if not gen_normalized:
        return 0.0

    # 겹침 찾기
    _, total_overlap = _find_overlaps(gen_normalized, orig_normalized, min_gram)

    # 비율 계산
    ratio = total_overlap / len(gen_normalized)

    logger.debug(
        f"overlap_ratio: gen_len={len(gen_normalized)}, "
        f"orig_len={len(orig_normalized)}, "
        f"overlap_chars={total_overlap}, ratio={ratio:.3f}"
    )

    return ratio


def passes_ngram_guard(generated: str, original: str, threshold: float = None) -> bool:
    """
    n-gram 겹침 검사를 통과하는지 판단합니다.

    overlap_ratio(...) < threshold 이면 True (통과, 표절 의심 없음).
    overlap_ratio(...) >= threshold 이면 False (탈락, 표절 의심).

    Args:
        generated: 생성된 텍스트
        original: 원본 텍스트
        threshold: 겹침 비율 임계값 (기본값 0.20)

    Returns:
        True if 표절 가능성 낮음, False if 표절 가능성 높음
    """
    if threshold is None:
        threshold = DEFAULT_NGRAM_THRESHOLD

    ratio = overlap_ratio(generated, original, min_gram=DEFAULT_MIN_GRAM_SIZE)
    passes = ratio < threshold

    if not passes:
        logger.warning(
            f"ngram_guard REJECT: overlap_ratio={ratio:.3f} >= threshold={threshold}"
        )
    else:
        logger.debug(
            f"ngram_guard PASS: overlap_ratio={ratio:.3f} < threshold={threshold}"
        )

    return passes
