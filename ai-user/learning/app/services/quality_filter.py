import re
import logging

logger = logging.getLogger(__name__)

class QualityFilter:
    PERIOD_EOL = re.compile(r'(?<![.?!])\.\s*$', re.MULTILINE)
    DOUBLE_QUOTE = re.compile(r'"[^"\n]{1,60}"')
    MIN_LEN = 10  # 완화: 15 → 10
    MAX_LEN = 5000  # 증가: 1800 → 5000

    def passes(self, text):
        if not text:
            logger.debug("QualityFilter: Empty text")
            return False

        text_len = len(text)
        if text_len < self.MIN_LEN or text_len > self.MAX_LEN:
            logger.debug(f"QualityFilter: Length {text_len} not in [{self.MIN_LEN}, {self.MAX_LEN}]")
            return False

        # PII 검사 (강화)
        if re.search(r'\d{3}-\d{3,4}-\d{4}', text):  # 전화번호
            logger.debug("QualityFilter: Phone number detected")
            return False

        if re.search(r'\d{6}-[1-4]\d{6}', text):  # 주민번호
            logger.debug("QualityFilter: ID number detected")
            return False

        logger.debug(f"QualityFilter: PASS (len={text_len})")
        return True

    def score(self, text):
        score = 1.0
        if self.PERIOD_EOL.search(text):
            score -= 0.3
        if self.DOUBLE_QUOTE.search(text):
            score -= 0.2
        if re.search(r'(?:임|함|됨|거든|거임|더라|잖아|었음|했음)', text):
            score = min(1.0, score + 0.1)
        return round(max(0.0, score), 2)
