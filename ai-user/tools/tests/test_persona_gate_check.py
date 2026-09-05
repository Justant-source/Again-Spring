"""persona_gate_check.py 단위 테스트 — DB 없이 픽스처 dict/list만으로 판정 함수 검증."""

from __future__ import annotations

import itertools
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import persona_gate_check as mod  # noqa: E402


# --- fixture 생성기: 계약 2 쿼터를 정확히 만족하는 150명 -----------------------


def _build_compliant_personas() -> list[dict]:
    rows: list[dict] = []
    idx = 0

    # (age_band, band_size, married_count) — 계약 2
    band_plan = [("23-29", 60, 15), ("30-36", 60, 45), ("37-49", 30, 30)]
    age_by_band = {"23-29": list(range(23, 30)), "30-36": list(range(30, 37)), "37-49": list(range(37, 50))}

    married_flags: list[bool] = []
    ages: list[int] = []
    for band, size, married_count in band_plan:
        cyc = itertools.cycle(age_by_band[band])
        for i in range(size):
            ages.append(next(cyc))
            married_flags.append(i < married_count)

    genders = ["M"] * 75 + ["F"] * 75
    tiers = ["HEAVY"] * 20 + ["REGULAR"] * 80 + ["LIGHT"] * 50
    voice_types = ["NATEPAN"] * 75 + ["BLIND"] * 75

    married_indices = [i for i, m in enumerate(married_flags) if m]
    kids_indices = set(married_indices[:45])  # married 90 중 45명 has_kids

    for i in range(150):
        marital = "MARRIED" if married_flags[i] else "SINGLE"
        has_kids = i in kids_indices
        rows.append(
            {
                "id": f"persona_{i:03d}",
                "age_years": ages[i],
                "gender": genders[i],
                "marital": marital,
                "married_years": None,  # 단순화: null은 항상 위반 0건
                "has_kids": has_kids,
                "tier": tiers[i],
                "voice_profile": json.dumps({"voice_type": voice_types[i]}),
                "style_axes": None,
                "idx": idx,
            }
        )
        idx += 1
    return rows


def test_evaluate_gate_a_passes_on_compliant_fixture():
    rows = _build_compliant_personas()
    result = mod.evaluate_gate_a(rows)
    failed = [c for c in result.checks if not c["pass"]]
    assert result.passed, f"unexpected failures: {failed}"


def test_evaluate_gate_a_fails_on_gender_skew():
    rows = _build_compliant_personas()
    # 성별 쏠림: 앞 20명을 전부 M으로 바꿔 오차 ±3 초과 유도
    for row in rows[75:95]:
        row["gender"] = "M"
    result = mod.evaluate_gate_a(rows)
    assert result.passed is False
    names = {c["check"] for c in result.checks if not c["pass"]}
    assert "gender:M" in names


def test_evaluate_gate_a_flags_married_years_violation():
    # 계약1 개정(2026-09-05): married_years ≤ age-23. 30세면 상한 7년차, 10년차는 위반.
    rows = _build_compliant_personas()
    rows[0]["marital"] = "MARRIED"
    rows[0]["age_years"] = 30
    rows[0]["married_years"] = 10  # 30-23=7 < 10 → 위반
    result = mod.evaluate_gate_a(rows)
    detail = next(c for c in result.checks if c["check"] == "married_years_violations")
    assert detail["pass"] is False
    assert "persona_000" in detail["detail"]


def test_evaluate_gate_a_flags_married_years_zero_as_violation():
    # 계약1 개정: married_years=0(결혼 0년차)은 23세든 몇 세든 항상 위반이다.
    rows = _build_compliant_personas()
    rows[0]["marital"] = "MARRIED"
    rows[0]["age_years"] = 23
    rows[0]["married_years"] = 0
    result = mod.evaluate_gate_a(rows)
    detail = next(c for c in result.checks if c["check"] == "married_years_violations")
    assert detail["pass"] is False
    assert "persona_000" in detail["detail"]


def test_evaluate_gate_a_passes_married_years_one_at_age24():
    # 계약1 개정: 24세 기혼 1년차는 정확히 경계값이며 위반이 아니다.
    rows = _build_compliant_personas()
    rows[0]["marital"] = "MARRIED"
    rows[0]["age_years"] = 24
    rows[0]["married_years"] = 1
    result = mod.evaluate_gate_a(rows)
    detail = next(c for c in result.checks if c["check"] == "married_years_violations")
    assert detail["pass"] is True


def test_evaluate_gate_a_flags_has_kids_without_married():
    rows = _build_compliant_personas()
    single_row = next(r for r in rows if r["marital"] == "SINGLE")
    single_row["has_kids"] = True
    result = mod.evaluate_gate_a(rows)
    detail = next(c for c in result.checks if c["check"] == "has_kids_requires_married")
    assert detail["pass"] is False


def test_evaluate_gate_a_flags_age_out_of_range():
    rows = _build_compliant_personas()
    rows[0]["age_years"] = 55
    result = mod.evaluate_gate_a(rows)
    detail = next(c for c in result.checks if c["check"] == "age_range_violations")
    assert detail["pass"] is False


def test_evaluate_gate_a_empty_rows_fails_persona_count():
    result = mod.evaluate_gate_a([])
    assert result.passed is False


def test_evaluate_gate_a_has_kids_as_bit_int_not_treated_as_violation():
    """실 DB의 has_kids는 BIT(1) — `has_kids + 0` 캐스팅 후 정수 0/1로 들어온다.
    2026-09-05 실측: 캐스팅 없이 JSON_OBJECT에 BIT을 바로 넣으면 원시 바이트(\\u0000)가
    나와 JSON_ARRAYAGG 파싱이 깨졌다(json.JSONDecodeError). 정수 0/1로 들어와도
    bool(has_kids) 판정이 정상 동작해야 한다."""
    rows = _build_compliant_personas()
    for row in rows:
        row["has_kids"] = 1 if row["has_kids"] else 0
    result = mod.evaluate_gate_a(rows)
    detail = next(c for c in result.checks if c["check"] == "has_kids_requires_married")
    assert detail["pass"] is True
    kids_detail = next(c for c in result.checks if c["check"] == "has_kids_of_married")
    assert "actual=45/90" in kids_detail["detail"]


def test_evaluate_gate_a_reports_regeneration_progress_in_note():
    rows = _build_compliant_personas()
    for row in rows[:12]:
        row["style_axes"] = {"directness": "BLUNT"}
        vp = json.loads(row["voice_profile"])
        vp["profile_rev"] = mod.CURRENT_PROFILE_REV
        row["voice_profile"] = json.dumps(vp)
    result = mod.evaluate_gate_a(rows)
    assert "12/150" in result.note
    assert "138" in result.note


def test_evaluate_gate_a_does_not_count_style_axes_without_profile_rev_marker_as_regenerated():
    """오염 상태 재현(2026-09 dev 12명 사례): style_axes만 채워지고 voice_profile에
    profile_rev 마커가 없으면(PersonaProfileRegenerator의 감사 실패 등으로 롤백되지 않은
    부분 갱신) 재생성 진척에 포함시키지 않는다."""
    rows = _build_compliant_personas()
    for row in rows[:12]:
        row["style_axes"] = {"directness": "BLUNT"}  # 마커 없이 축만 채움 = 오염 상태
    result = mod.evaluate_gate_a(rows)
    assert "0/150" in result.note
    assert "150명 미재생성" in result.note


# --- gate b -------------------------------------------------------------------


def _balanced_style_axes(n: int = 150) -> list[dict]:
    axes_cycles = {axis: itertools.cycle(opts) for axis, opts in mod.STYLE_AXES_OPTIONS.items()}
    rows = []
    for _ in range(n):
        rows.append({axis: next(cyc) for axis, cyc in axes_cycles.items()})
    return rows


DISTINCT_SENTENCES = [
    "야근 끝나고 집에 오니 새벽 두 시라 그냥 씻지도 않고 뻗었다",
    "주말에 애들 데리고 놀이공원 갔는데 줄이 너무 길어서 두 개밖에 못 탔음",
    "팀장이 또 남 탓하는 거 보고 진짜 정 떨어졌다 이직 알아봐야 하나",
    "여자친구랑 싸운 이유가 결국 카톡 답장 속도 때문이라니 어이없다",
    "부모님이 명절마다 결혼 얘기 꺼내는 거 이제 지친다 그만 좀 하셨으면",
    "친구가 갑자기 돈 빌려달라는데 어떻게 거절해야 할지 모르겠다",
    "회식 자리에서 부장님이 옛날 얘기 또 시작해서 다들 눈치만 봤다",
    "시댁 갔다가 하루종일 설거지만 하고 온 것 같다 진이 다 빠진다",
    "재택근무 첫 주인데 집중이 하나도 안 되고 냉장고만 들락날락한다",
    "동기가 먼저 승진해서 축하는 했지만 솔직히 마음이 복잡하다",
]


def test_evaluate_gate_b_passes_with_balanced_fixture():
    data = {
        "signature_phrases": [f"phrase_{i}" for i in range(150)],
        "reply_style": [f"reply_{i}" for i in range(130)],
        "comment_style": [f"comment_{i}" for i in range(130)],
        "general_style": DISTINCT_SENTENCES,
        "style_axes": _balanced_style_axes(150),
    }
    result = mod.evaluate_gate_b(data)
    failed = [c for c in result.checks if not c["pass"]]
    assert result.passed, f"unexpected failures: {failed}"


def test_evaluate_gate_b_fails_on_low_signature_phrase_diversity():
    data = {
        "signature_phrases": ["결론부터"] * 150,  # 고유값 1개뿐
        "reply_style": [f"reply_{i}" for i in range(130)],
        "comment_style": [f"comment_{i}" for i in range(130)],
        "general_style": [f"문장 {i}" for i in range(10)],
        "style_axes": _balanced_style_axes(150),
    }
    result = mod.evaluate_gate_b(data)
    assert result.passed is False
    detail = next(c for c in result.checks if c["check"] == "signature_phrases_unique")
    assert detail["pass"] is False


def test_evaluate_gate_b_fails_on_near_duplicate_general_style():
    data = {
        "signature_phrases": [f"phrase_{i}" for i in range(150)],
        "reply_style": [f"reply_{i}" for i in range(130)],
        "comment_style": [f"comment_{i}" for i in range(130)],
        "general_style": [
            "야근하고 집에 오면 진짜 아무것도 하기 싫고 그냥 눕고 싶다",
            "야근하고 집에 오면 진짜 아무것도 하기 싫고 그냥 눕고 싶어요",  # 거의 동일
        ],
        "style_axes": _balanced_style_axes(150),
    }
    result = mod.evaluate_gate_b(data)
    assert result.passed is False
    detail = next(c for c in result.checks if c["check"] == "general_style_pairwise_jaccard")
    assert detail["pass"] is False


def test_evaluate_gate_b_reports_regeneration_progress_in_note():
    data = {
        "signature_phrases": [f"phrase_{i}" for i in range(150)],
        "reply_style": [f"reply_{i}" for i in range(130)],
        "comment_style": [f"comment_{i}" for i in range(130)],
        "general_style": DISTINCT_SENTENCES,
        "style_axes": _balanced_style_axes(12),
        "total_personas": 150,
    }
    result = mod.evaluate_gate_b(data)
    assert "12/150" in result.note
    assert "138" in result.note


def test_evaluate_gate_b_uses_regenerated_count_over_raw_style_axes_when_provided():
    """style_axes는 채워졌지만(플랜 산출물이라 오염 상태에서도 항상 채워짐) 실제 완료가 아닌
    건수를 진척에서 빼기 위해, fetch_gate_b_data가 채우는 regenerated_count가 있으면
    style_axes 리스트 길이보다 그것을 우선한다."""
    data = {
        "signature_phrases": [f"phrase_{i}" for i in range(150)],
        "reply_style": [f"reply_{i}" for i in range(130)],
        "comment_style": [f"comment_{i}" for i in range(130)],
        "general_style": DISTINCT_SENTENCES,
        "style_axes": _balanced_style_axes(12),  # 오염 12명 포함 — 축만 있는 상태
        "total_personas": 150,
        "regenerated_count": 0,  # 마커 없는 오염 12명은 완료로 세지 않음
    }
    result = mod.evaluate_gate_b(data)
    assert "0/150" in result.note


def test_fetch_gate_b_data_regenerated_count_requires_profile_rev_marker():
    """fetch_gate_b_data가 만드는 regenerated_count는 style_axes 존재가 아니라
    voice_profile.profile_rev 마커 기준이어야 한다 — 오염 상태를 완료로 잘못 세지 않기 위함."""
    done_row = {
        "id": "p1",
        "style_axes": {"directness": "BLUNT"},
        "voice_profile": json.dumps({"profile_rev": mod.CURRENT_PROFILE_REV, "general_style": "x"}),
    }
    contaminated_row = {
        "id": "p2",
        "style_axes": {"directness": "SOFT"},  # 축만 있고 마커 없음 = 오염
        "voice_profile": json.dumps({"general_style": "옛 템플릿"}),
    }
    pending_row = {"id": "p3", "style_axes": None, "voice_profile": None}

    data = mod.fetch_gate_b_data([done_row, contaminated_row, pending_row])

    assert data["regenerated_count"] == 1
    assert data["total_personas"] == 3
    # 다양성 분포용 style_axes 리스트는 여전히 축이 채워진 행 전부(done + contaminated)를 포함한다 —
    # 진척 계측과 쿼터 분포 계측은 서로 다른 질문이다.
    assert len(data["style_axes"]) == 2


def test_is_profile_regenerated():
    assert mod.is_profile_regenerated(
        {"style_axes": {"a": 1}, "voice_profile": json.dumps({"profile_rev": mod.CURRENT_PROFILE_REV})}
    )
    assert not mod.is_profile_regenerated(
        {"style_axes": {"a": 1}, "voice_profile": json.dumps({"general_style": "옛 템플릿"})}
    )
    assert not mod.is_profile_regenerated({"style_axes": None, "voice_profile": "{}"})
    assert not mod.is_profile_regenerated(
        {"style_axes": {"a": 1}, "voice_profile": json.dumps({"profile_rev": "v3"})}
    )


def test_extract_style_axes_handles_null_dict_and_json_string():
    assert mod.extract_style_axes({"style_axes": None}) is None
    assert mod.extract_style_axes({"style_axes": {}}) is None
    assert mod.extract_style_axes({"style_axes": {"directness": "BLUNT"}}) == {"directness": "BLUNT"}
    assert mod.extract_style_axes({"style_axes": '{"directness": "SOFT"}'}) == {"directness": "SOFT"}
    assert mod.extract_style_axes({"style_axes": "not json"}) is None


def test_char_ngrams_and_jaccard_basic():
    a = mod.char_ngrams("abcdefgh")
    b = mod.char_ngrams("abcdefgh")
    assert mod.jaccard(a, b) == 1.0
    c = mod.char_ngrams("zzzzzzzz")
    assert mod.jaccard(a, c) == 0.0


# --- gate c: 참고용, 항상 PASS 취급 --------------------------------------------


def test_evaluate_gate_c_always_passes_but_reports_raw_numbers():
    stats = {
        "total_active_personas": 150,
        "posting_personas": 100,  # 66.7% — 90% 미만이라도 gate c는 배포 게이트가 아님
        "top10_post_share": 0.40,  # 25% 초과
        "comment_counts_by_persona": {"p1": 5, "p2": 40},
    }
    result = mod.evaluate_gate_c(stats)
    assert result.passed is True  # 참고용, 항상 PASS
    posting = next(c for c in result.checks if c["check"] == "posting_persona_share")
    assert "raw_pass=False" in posting["detail"]
    top10 = next(c for c in result.checks if c["check"] == "top10_post_share")
    assert "raw_pass=False" in top10["detail"]


def test_evaluate_gate_c_handles_zero_totals():
    stats = {"total_active_personas": 0, "posting_personas": 0, "top10_post_share": 0.0, "comment_counts_by_persona": {}}
    result = mod.evaluate_gate_c(stats)
    assert result.passed is True


# --- gate d: 관계(PersonaRelationshipFiller) ------------------------------------


def test_evaluate_gate_d_passes_when_everyone_covered_and_no_violations():
    stats = {
        "total_active_personas": 150,
        "relation_type_counts": {"COUPLE": 12, "MARRIAGE": 17, "FRIEND": 113},
        "uncovered_count": 0,
        "gender_age_violation_count": 0,
        "marital_violation_count": 0,
    }
    result = mod.evaluate_gate_d(stats)
    failed = [c for c in result.checks if not c["pass"]]
    assert result.passed, f"unexpected failures: {failed}"
    assert "COUPLE" in result.note


def test_evaluate_gate_d_fails_on_uncovered_personas():
    stats = {
        "total_active_personas": 150,
        "relation_type_counts": {"FRIEND": 10},
        "uncovered_count": 3,
        "gender_age_violation_count": 0,
        "marital_violation_count": 0,
    }
    result = mod.evaluate_gate_d(stats)
    assert result.passed is False
    detail = next(c for c in result.checks if c["check"] == "uncovered_personas")
    assert detail["pass"] is False
    assert "count=3" in detail["detail"]


def test_evaluate_gate_d_fails_on_gender_age_violations():
    """2026-09-05 dev 실측: 재생성 전 60건 시드 관계에 이미 성별동일 COUPLE/MARRIAGE·
    나이차>5 FRIEND가 섞여 있었다(fill 트리거는 기존 관계를 손대지 않아 남는다)."""
    stats = {
        "total_active_personas": 150,
        "relation_type_counts": {"COUPLE": 12, "MARRIAGE": 17, "FRIEND": 113},
        "uncovered_count": 0,
        "gender_age_violation_count": 13,
        "marital_violation_count": 0,
    }
    result = mod.evaluate_gate_d(stats)
    assert result.passed is False
    detail = next(c for c in result.checks if c["check"] == "gender_age_violations")
    assert detail["pass"] is False
    assert "count=13" in detail["detail"]


def test_evaluate_gate_d_fails_on_marital_consistency_violations():
    stats = {
        "total_active_personas": 150,
        "relation_type_counts": {"COUPLE": 12, "MARRIAGE": 17, "FRIEND": 113},
        "uncovered_count": 0,
        "gender_age_violation_count": 0,
        "marital_violation_count": 7,
    }
    result = mod.evaluate_gate_d(stats)
    assert result.passed is False
    detail = next(c for c in result.checks if c["check"] == "marital_consistency_violations")
    assert detail["pass"] is False
    assert "count=7" in detail["detail"]


def test_evaluate_gate_d_defaults_missing_keys_to_zero():
    result = mod.evaluate_gate_d({})
    assert result.passed is True


# --- V22 컬럼 가드 ---------------------------------------------------------------


def test_assert_v22_applied_raises_when_missing():
    try:
        mod.assert_v22_applied({"id", "tier", "voice_profile"})
        assert False, "should have raised"
    except mod.MissingV22ColumnsError as exc:
        assert "age_years" in str(exc)


def test_assert_v22_applied_passes_when_present():
    mod.assert_v22_applied(mod.REQUIRED_V22_COLUMNS | {"id", "tier"})  # no raise


# --- helpers --------------------------------------------------------------------


def test_age_band_boundaries():
    assert mod.age_band(23) == "23-29"
    assert mod.age_band(29) == "23-29"
    assert mod.age_band(30) == "30-36"
    assert mod.age_band(36) == "30-36"
    assert mod.age_band(37) == "37-49"
    assert mod.age_band(49) == "37-49"
    assert mod.age_band(50) == "OUT_OF_RANGE"
    assert mod.age_band(22) == "OUT_OF_RANGE"


def test_marital_group():
    assert mod.marital_group("MARRIED") == "MARRIED"
    for m in ("SINGLE", "DATING", "ENGAGED"):
        assert mod.marital_group(m) == "SINGLE_GROUP"


def test_infer_env_name():
    assert mod.infer_env_name("env/.env.dev") == "dev"
    assert mod.infer_env_name("env/.env.prod") == "prod"
