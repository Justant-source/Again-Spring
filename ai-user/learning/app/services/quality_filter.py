import re
import logging

logger = logging.getLogger(__name__)

class QualityFilter:
    PERIOD_EOL = re.compile(r'(?<![.?!])\.\s*$', re.MULTILINE)
    DOUBLE_QUOTE = re.compile(r'"[^"\n]{1,60}"')
    MIN_LEN = 10  # 완화: 15 → 10
    MAX_LEN = 5000  # 증가: 1800 → 5000

    # UI/Crawling noise tokens - filter out pure junk
    UI_NOISE_TOKENS = re.compile(
        r'\[\s*(?:원본\s*보기|원문\s*보기|더보기|본문\s*바로가기|'
        r'이\s*글\s*보기|전체\s*보기|숨기기|펼치기)\s*\]',
        re.IGNORECASE
    )

    # Recipe/cooking domain signals - weak conflict likelihood
    RECIPE_SIGNALS = re.compile(
        r'(?:레시피|재료|굽기|한\s*스푼|양념|불\s*조절|'
        r'우유|계란|가루|도우|반죽|구워|삶아|볶아|끓여)',
        re.IGNORECASE
    )

    # Photo gallery metadata noise
    GALLERY_NOISE = re.compile(
        r'(?:흑백|컬러|사진|촬영|카메라|필름|노출|렌즈)',
        re.IGNORECASE
    )

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

        # UI noise filtering (reject if contains structural markers)
        if self.UI_NOISE_TOKENS.search(text):
            logger.debug("QualityFilter: UI noise tokens detected")
            return False

        # Reject obvious non-conflict domains (recipe-heavy + short = cooking noise)
        recipe_matches = len(self.RECIPE_SIGNALS.findall(text))
        if recipe_matches >= 3:  # 3+ recipe signals = likely recipe
            logger.debug(f"QualityFilter: Recipe/cooking domain (signals={recipe_matches})")
            return False

        # Reject gallery metadata noise (photo comments with metadata)
        gallery_matches = len(self.GALLERY_NOISE.findall(text))
        if gallery_matches >= 2 and text_len < 200:  # Short + gallery signals
            logger.debug(f"QualityFilter: Gallery noise (signals={gallery_matches}, len={text_len})")
            return False

        logger.debug(f"QualityFilter: PASS (len={text_len})")
        return True

    def score(self, text):
        """
        Score text 0.0-1.0 for quality.
        Scale: 1.0 (perfect conflict narrative) → 0.5 (borderline) → 0.0 (pure noise).
        Used as filter threshold in search (min 0.5 recommended).
        """
        score = 1.0

        # Deduct for poor punctuation
        if self.PERIOD_EOL.search(text):
            score -= 0.3
        if self.DOUBLE_QUOTE.search(text):
            score -= 0.2

        # Bonus for casual speech patterns (conflict narratives often use these)
        if re.search(r'(?:임|함|됨|거든|거임|더라|잖아|었음|했음)', text):
            score = min(1.0, score + 0.1)

        # Deduct if contains UI noise (fallthrough from passes should catch this,
        # but score it lower just in case)
        if self.UI_NOISE_TOKENS.search(text):
            score -= 0.25

        # Deduct for recipe/cooking signals (non-conflict domain)
        recipe_matches = len(self.RECIPE_SIGNALS.findall(text))
        if recipe_matches >= 2:
            score -= min(0.3, recipe_matches * 0.1)

        # Deduct for gallery noise
        gallery_matches = len(self.GALLERY_NOISE.findall(text))
        if gallery_matches >= 2:
            score -= min(0.2, gallery_matches * 0.08)

        return round(max(0.0, score), 2)
