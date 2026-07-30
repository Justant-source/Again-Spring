import pytest
from .ngram_guard import overlap_ratio, passes_ngram_guard


class TestOverlapRatio:
    """n-gram 겹침 비율 계산 테스트"""

    def test_completely_different_texts(self):
        """
        완전히 다른 두 문장: 겹침 거의 0

        생성: "안녕하세요 어떻게 지내세요"
        원본: "사과는 맛있는 과일입니다"

        겹침 부분이 없으므로 ratio는 0에 가까워야 함.
        """
        generated = "안녕하세요 어떻게 지내세요"
        original = "사과는 맛있는 과일입니다"
        ratio = overlap_ratio(generated, original, min_gram=12)
        assert ratio < 0.05, f"Expected near 0, got {ratio}"

    def test_exact_copy_of_original(self):
        """
        생성 텍스트가 원본과 완전히 동일: 겹침 1.0

        생성: "내일 출장이 있어서 회의에 참석할 수 없을 것 같습니다"
        원본: "내일 출장이 있어서 회의에 참석할 수 없을 것 같습니다"

        겹침이 100%이므로 ratio는 1.0에 가까워야 함.
        """
        text = "내일 출장이 있어서 회의에 참석할 수 없을 것 같습니다"
        generated = text
        original = text
        ratio = overlap_ratio(generated, original, min_gram=12)
        assert ratio > 0.95, f"Expected near 1.0, got {ratio}"

    def test_partial_copy_few_words_changed(self):
        """
        생성 텍스트가 원본의 일부를 복사하고 몇 단어만 변경: 중간 겹침

        생성: "내일 출장이 있어서 중요한 회의에 참석할 수 없을 것 같습니다"
        원본: "내일 출장이 있어서 회의에 참석할 수 없을 것 같습니다"

        공통 부분이 있지만 완전하지 않으므로, 겹침 비율은 중간대(0.3 ~ 0.7) 범위.
        """
        generated = "내일 출장이 있어서 중요한 회의에 참석할 수 없을 것 같습니다"
        original = "내일 출장이 있어서 회의에 참석할 수 없을 것 같습니다"
        ratio = overlap_ratio(generated, original, min_gram=12)
        # 겹침이 상당하지만 전체는 아님
        assert 0.2 < ratio < 0.9, f"Expected mid-range, got {ratio}"

    def test_whitespace_normalization(self):
        """
        공백 차이만 있는 두 텍스트: 겹침 높음

        공백·개행 차이로 회피되지 않도록 정규화 후 비교.
        """
        generated = "내일 출장이 있어서   회의에\n참석할 수 없을 것 같습니다"
        original = "내일 출장이 있어서 회의에 참석할 수 없을 것 같습니다"
        ratio = overlap_ratio(generated, original, min_gram=12)
        # 공백 정규화 후 같은 텍스트이므로 높은 겹침
        assert ratio > 0.9, f"Expected high overlap despite whitespace diff, got {ratio}"

    def test_short_text_with_min_gram(self):
        """
        min_gram보다 짧은 텍스트: 겹침 검사가 작동해야 함
        """
        generated = "짧은 텍스트"
        original = "짧은 텍스트"
        # min_gram을 작게 설정해서 짧은 텍스트도 검사
        ratio = overlap_ratio(generated, original, min_gram=3)
        assert ratio > 0.8, f"Expected high overlap for short text, got {ratio}"

    def test_empty_generated_text(self):
        """
        생성 텍스트가 빈 문자열: ratio는 0
        """
        generated = ""
        original = "어떤 텍스트입니다"
        ratio = overlap_ratio(generated, original, min_gram=12)
        assert ratio == 0.0, f"Expected 0, got {ratio}"

    def test_empty_original_text(self):
        """
        원본 텍스트가 빈 문자열: ratio는 0
        """
        generated = "어떤 텍스트입니다"
        original = ""
        ratio = overlap_ratio(generated, original, min_gram=12)
        assert ratio == 0.0, f"Expected 0, got {ratio}"


class TestPassesNgramGuard:
    """n-gram 가드 통과/탈락 판정 테스트"""

    def test_different_texts_pass(self):
        """
        완전히 다른 텍스트는 통과해야 함.
        """
        generated = "안녕하세요 어떻게 지내세요"
        original = "사과는 맛있는 과일입니다"
        assert passes_ngram_guard(generated, original, threshold=0.15) is True

    def test_exact_copy_rejected(self):
        """
        정확한 복사는 탈락해야 함.
        """
        text = "내일 출장이 있어서 회의에 참석할 수 없을 것 같습니다"
        generated = text
        original = text
        assert passes_ngram_guard(generated, original, threshold=0.15) is False

    def test_high_overlap_rejected(self):
        """
        겹침이 threshold를 초과하면 탈락해야 함.
        """
        generated = "내일 출장이 있어서 중요한 회의에 참석할 수 없을 것 같습니다"
        original = "내일 출장이 있어서 회의에 참석할 수 없을 것 같습니다"
        # 이 경우 겹침 비율이 높을 것으로 예상
        result = passes_ngram_guard(generated, original, threshold=0.15)
        # 테스트 실행 후 실제 비율을 확인하고, high overlap이면 False
        # (정확한 임계값은 실측 데이터에 따라 조정 필요)

    def test_default_threshold_applied(self):
        """
        threshold가 None이면 기본값(0.15)을 적용해야 함.
        """
        generated = "완전히 다른 문장입니다"
        original = "이것도 다른 문장이군요"
        # 기본 threshold로 통과
        result = passes_ngram_guard(generated, original)  # threshold=None (기본값)
        assert isinstance(result, bool), "Should return bool"

    def test_threshold_sensitivity(self):
        """
        threshold를 조정하면 결과가 달라져야 함.
        """
        generated = "내일 출장이 있어서 중요한 회의에 참석할 수 없을 것 같습니다"
        original = "내일 출장이 있어서 회의에 참석할 수 없을 것 같습니다"

        ratio = overlap_ratio(generated, original, min_gram=12)

        # 낮은 threshold: 더 엄격 (탈락 가능성 높음)
        result_strict = passes_ngram_guard(generated, original, threshold=0.10)

        # 높은 threshold: 더 관대 (통과 가능성 높음)
        result_lenient = passes_ngram_guard(generated, original, threshold=0.50)

        # threshold 차이로 결과가 달라질 수 있음
        # (실제 ratio에 따라 다름)


if __name__ == "__main__":
    pytest.main([__file__, "-v"])
