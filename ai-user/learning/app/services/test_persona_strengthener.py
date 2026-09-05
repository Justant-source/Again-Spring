"""Unit tests for persona_strengthener (persona-diversity-v4 / WP1, 2026-09-05).

lexicon/writing_quirks/general_style/*_style은 이제 PersonaProfileRegenerator(오케스트레이터)
전용 쓰기 경로다 — 이 크롤 강화 루프가 같은 필드를 덮어쓰지 않는지 검증한다
(01-wp1-persona-data.md §7).
"""
from unittest.mock import patch

from app.services.persona_strengthener import (
    strengthen_all,
    update_persona_profiles,
)


def test_update_persona_profiles_is_noop_returns_zero():
    """patterns가 있어도 항상 0을 반환하고 DB에 쓰지 않는다."""
    patterns = {
        "signature_phrases": ["아 진짜", "그니까", "레알"],
        "consistent_errors": ["온점 미사용"],
        "hot_topics": ["연애", "직장"],
        "typing_habit": "짧게 끊어 씀",
    }
    with patch("app.services.persona_strengthener.get_db") as mock_get_db:
        result = update_persona_profiles("NATEPAN", patterns)
        assert result == 0
        # DB에 전혀 접근하지 않아야 한다 — lexicon/writing_quirks/general_style을
        # 덮어쓰는 유일한 경로였던 UPDATE 쿼리가 완전히 제거됐는지 확인.
        mock_get_db.assert_not_called()


def test_update_persona_profiles_empty_patterns_returns_zero():
    with patch("app.services.persona_strengthener.get_db") as mock_get_db:
        assert update_persona_profiles("NATEPAN", {}) == 0
        mock_get_db.assert_not_called()


def test_strengthen_all_never_calls_llm_analysis_or_profile_overwrite():
    """strengthen_all은 이제 예시 풀 확장만 하고, LLM 분석·voice_profile 필드 덮어쓰기
    경로(analyze_style_with_llm/update_persona_profiles)는 절대 호출하지 않는다."""
    with patch("app.services.persona_strengthener.expand_persona_example_pools", return_value=5) as mock_pool, \
         patch("app.services.persona_strengthener.analyze_style_with_llm") as mock_analyze, \
         patch("app.services.persona_strengthener.update_persona_profiles") as mock_update, \
         patch("app.services.persona_strengthener.get_examples_by_source") as mock_examples:
        results = strengthen_all(min_examples=10)

        mock_analyze.assert_not_called()
        mock_update.assert_not_called()
        mock_examples.assert_not_called()
        assert mock_pool.call_count == 2  # NATEPAN, BLIND
        assert results["NATEPAN"] == {"status": "ok", "pool_updated": 5}
        assert results["BLIND"] == {"status": "ok", "pool_updated": 5}
