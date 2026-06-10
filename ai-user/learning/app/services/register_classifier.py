import re

# 행 끝 종결어미 기준 분류
_POLITE = re.compile(
    r'(?:(?:했|했었|었|이었|이|아|어)어요|에요|예요|해요|했요|습니다|니다|세요)'
    r'\s*$',
    re.MULTILINE
)
_CASUAL = re.compile(
    r'(?:임|함|됨|거든|거임|더라|했음|었음|있음|없음|잖아|냐|노|지|나|구나|네|지뭐)\s*$',
    re.MULTILINE
)


def classify(text: str) -> str:
    """
    텍스트의 종결어미 분포를 보고 register 분류.
    반환: 'casual' | 'polite' | 'mixed'
    """
    if not text:
        return 'mixed'

    lines = [l.strip() for l in text.splitlines() if len(l.strip()) > 5]
    if not lines:
        return 'mixed'

    polite_count = sum(1 for l in lines if _POLITE.search(l))
    casual_count = sum(1 for l in lines if _CASUAL.search(l))
    total = polite_count + casual_count

    if total == 0:
        return 'mixed'

    polite_ratio = polite_count / total
    casual_ratio = casual_count / total

    if polite_ratio >= 0.70:
        return 'polite'
    if casual_ratio >= 0.70:
        return 'casual'
    return 'mixed'
