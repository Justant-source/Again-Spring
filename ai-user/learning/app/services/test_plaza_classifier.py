"""plaza_classifier — weighted scoring, natal FAMILY, channel_hint as bonus only."""
from app.services.plaza_classifier import (
    classify_plaza,
    confident_plaza,
    score_all_plazas,
)


class TestCoreCases:
    def test_mother_in_law_is_married(self):
        assert classify_plaza("시어머니가 자꾸 참견해", "") == "MARRIED"

    def test_dad_parents_are_family(self):
        assert classify_plaza("아빠가 자꾸 술을 마셔", "부모님 걱정") == "FAMILY"

    def test_parenting_plus_husband_is_married_not_family(self):
        assert classify_plaza("육아 너무 힘든데 남편이 전혀 안 도와줘.", "육아") == "MARRIED"


class TestChannelHint:
    def test_hint_does_not_override_strong_family(self):
        title = "아빠 본가 원가족 갈등"
        content = "아빠랑 친오빠, 친동생이 본가에서 싸운다. 부모님이 너무 힘들다."
        assert classify_plaza(content, title) == "FAMILY"
        assert classify_plaza(content, title, channel_hint="MARRIED") == "FAMILY"

    def test_hint_can_break_a_near_tie(self):
        content = "엄마가 육아를 도와주긴 하는데 마음이 복잡하다."
        scores = score_all_plazas(content, "")
        assert scores["FAMILY"] > scores["MARRIED"]
        assert classify_plaza(content, "") == "FAMILY"
        assert classify_plaza(content, "", channel_hint="MARRIED") == "MARRIED"

    def test_empty_stays_other_even_with_hint(self):
        assert classify_plaza("", "", channel_hint="WORK") == "OTHER"


class TestSpouseBonusUsesTitle:
    def test_spouse_keyword_in_title_only_counts(self):
        """Regression: `content or "" + title` dropped title from spouse bonus."""
        assert classify_plaza("참견이 너무 심해요", "시어머니") == "MARRIED"


class TestConfidentPlaza:
    def test_uncertain_when_scores_are_low(self):
        assert confident_plaza("날씨가 좋네요", "일상") is None

    def test_confident_when_single_plaza_meets_floor(self):
        result = confident_plaza("시어머니가 자꾸 참견해 시댁도 마찬가지", "시어머니 시댁")
        assert result is not None
        plaza, scores = result
        assert plaza == "MARRIED"
        assert scores["MARRIED"] >= 6
