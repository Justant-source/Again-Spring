"""Unit tests for export_persona_yaml.py (01-wp1-persona-data.md §7-b).

DB/파일시스템 없이 순수 함수만 검증한다 — 3명 픽스처.
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from export_persona_yaml import (  # noqa: E402
    age_band,
    build_identity_block,
    merge_profile_yaml,
    parse_env_file,
    render_specsheet,
    render_voice_yaml,
    resolve_profile_dir_name,
)

FIXTURE_ROWS = [
    {
        "id": "011cd2636dba4b9384b53a2702755cdd",
        "nickname": "야근일상",
        "age_years": 34,
        "gender": "M",
        "marital": "MARRIED",
        "married_years": 6,
        "has_kids": 1,
        "job_type": "CORP_MID",
        "job_title": "중견 제조업 구매팀 대리",
        "tier": "HEAVY",
        "style_axes": {"directness": "BLUNT", "speech": "BANMAL"},
        "voice_profile": {
            "voice_type": "NATEPAN",
            "general_style": "결론부터 던지는 편",
            "lexicon": {"signature_phrases": ["결론부터", "이건 좀"]},
        },
    },
    {
        "id": "01e99095413b401e954378ecc108f44c",
        "nickname": "봄날소녀",
        "age_years": 26,
        "gender": "F",
        "marital": "DATING",
        "married_years": None,
        "has_kids": 0,
        "job_type": "STARTUP",
        "job_title": None,
        "tier": "REGULAR",
        "style_axes": {"directness": "SOFT", "speech": "MIXED"},
        "voice_profile": {"voice_type": "BLIND", "general_style": "감정 표현이 풍부함"},
    },
    {
        "id": "05f8f3042933499ca416599a629c1c66",
        "nickname": "새벽출근",
        "age_years": 45,
        "gender": "M",
        "marital": "SINGLE",
        "married_years": None,
        "has_kids": 0,
        "job_type": "SELF_EMPLOYED",
        "job_title": "자영업 10년차",
        "tier": "LIGHT",
        "style_axes": {"directness": "BLUNT", "speech": "BANMAL"},
        "voice_profile": {"voice_type": "NATEPAN"},
    },
]


def test_age_band_boundaries():
    assert age_band(23) == "20s_late"
    assert age_band(29) == "20s_late"
    assert age_band(30) == "30s_early"
    assert age_band(36) == "30s_early"
    assert age_band(37) == "30s_late"
    assert age_band(39) == "30s_late"
    assert age_band(40) == "40s"
    assert age_band(49) == "40s"


def test_build_identity_block_married_includes_married_years_and_kids():
    identity = build_identity_block(FIXTURE_ROWS[0])
    assert identity["age_years"] == 34
    assert identity["marital"] == "MARRIED"
    assert identity["married_years"] == 6
    assert identity["has_kids"] is True
    assert identity["job_title"] == "중견 제조업 구매팀 대리"


def test_build_identity_block_single_omits_married_years():
    identity = build_identity_block(FIXTURE_ROWS[2])
    assert "married_years" not in identity
    assert identity["has_kids"] is False


def test_merge_profile_yaml_preserves_existing_fields():
    existing = {
        "id": "011cd2636dba4b9384b53a2702755cdd",
        "email": "ai-user-001@againspring.internal",
        "nickname": "야근일상",
        "demographics": {"age_band": "30s_early", "gender": "M", "region": "경기"},
        "archetype_preferences": ["work_toxic"],
    }
    merged = merge_profile_yaml(existing, FIXTURE_ROWS[0])
    assert merged["email"] == "ai-user-001@againspring.internal"
    assert merged["archetype_preferences"] == ["work_toxic"]
    assert merged["demographics"]["region"] == "경기"
    assert merged["identity"]["marital"] == "MARRIED"


def test_merge_profile_yaml_creates_skeleton_when_no_existing_file():
    merged = merge_profile_yaml(None, FIXTURE_ROWS[1])
    assert merged["id"] == FIXTURE_ROWS[1]["id"]
    assert merged["nickname"] == "봄날소녀"
    assert merged["demographics"]["age_band"] == "20s_late"
    assert merged["identity"]["job_type"] == "STARTUP"


def test_render_voice_yaml_includes_persona_id_and_nickname():
    voice = render_voice_yaml(FIXTURE_ROWS[0])
    assert voice["persona_id"] == FIXTURE_ROWS[0]["id"]
    assert voice["nickname"] == "야근일상"
    assert voice["voice_type"] == "NATEPAN"
    assert voice["general_style"] == "결론부터 던지는 편"


def test_resolve_profile_dir_name_reuses_existing_and_assigns_new():
    existing = {"011cd2636dba4b9384b53a2702755cdd": "ai-user-001"}
    next_seq = [1]

    reused = resolve_profile_dir_name(existing, "011cd2636dba4b9384b53a2702755cdd", next_seq)
    assert reused == "ai-user-001"

    new_name_1 = resolve_profile_dir_name(existing, "01e99095413b401e954378ecc108f44c", next_seq)
    existing["01e99095413b401e954378ecc108f44c"] = new_name_1
    new_name_2 = resolve_profile_dir_name(existing, "05f8f3042933499ca416599a629c1c66", next_seq)

    # 신규 배정은 기존 001과 충돌하지 않고 서로 달라야 한다
    assert new_name_1 != "ai-user-001"
    assert new_name_2 != "ai-user-001"
    assert new_name_1 != new_name_2


def test_render_specsheet_contains_all_three_fixture_rows():
    rows_with_dirs = [
        (FIXTURE_ROWS[0], "ai-user-001"),
        (FIXTURE_ROWS[1], "ai-user-116"),
        (FIXTURE_ROWS[2], "ai-user-117"),
    ]
    sheet = render_specsheet(rows_with_dirs)
    assert "ai-user-001" in sheet
    assert "ai-user-116" in sheet
    assert "ai-user-117" in sheet
    assert "야근일상" in sheet
    assert "봄날소녀" in sheet
    assert "새벽출근" in sheet


def test_parse_env_file_reads_key_value_pairs(tmp_path):
    env_path = tmp_path / ".env.dev"
    env_path.write_text("MARIADB_USER=someuser\nMARIADB_DATABASE=againspring_dev\n# comment\n\n",
                         encoding="utf-8")
    env = parse_env_file(env_path)
    assert env["MARIADB_USER"] == "someuser"
    assert env["MARIADB_DATABASE"] == "againspring_dev"


def test_parse_env_file_missing_file_returns_empty():
    assert parse_env_file(Path("/nonexistent/path/.env")) == {}
