import re

class QualityFilter:
    PERIOD_EOL = re.compile(r'(?<![.?!])\.\s*$', re.MULTILINE)
    DOUBLE_QUOTE = re.compile(r'"[^"\n]{1,60}"')
    MIN_LEN = 15
    MAX_LEN = 1800

    def passes(self, text):
        if not text or len(text) < self.MIN_LEN or len(text) > self.MAX_LEN:
            return False
        if re.search(r'\d{3}-\d{3,4}-\d{4}|\d{6}-[1-4]\d{6}', text):
            return False
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
