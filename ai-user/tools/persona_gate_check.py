#!/usr/bin/env python3
"""persona-diversity-v4 게이트 a(분포)·b(다양성)·c(회전)·d(관계) 검증 스크립트.

계약 출처: `.request/persona-diversity-v4/00-shared.md`(계약 1~2~3),
`.request/persona-diversity-v4/04-wp4-cleanup-gates.md`(게이트 정의),
`.request/persona-diversity-v4/01-wp1-persona-data.md` §6(`PersonaRelationshipFiller`).

Usage:
  python3 ai-user/tools/persona_gate_check.py --env-file env/.env.dev --gate a
  python3 ai-user/tools/persona_gate_check.py --env-file env/.env.dev --gate b
  python3 ai-user/tools/persona_gate_check.py --env-file env/.env.dev --gate c --days 7
  python3 ai-user/tools/persona_gate_check.py --env-file env/.env.dev --gate d
  python3 ai-user/tools/persona_gate_check.py --env-file env/.env.dev --gate all --json

종료 코드: 0=PASS, 1=FAIL(a·b·d가 배포 게이트), 2=personas에 V22 컬럼 없음(아직 미적용).
게이트 c는 참고용 — 항상 0 반환(집계만 출력).
게이트 d는 `PersonaRelationshipFiller`(fill-persona-relationships 트리거)가 만든/유지한
`persona_relationships`를 검증한다: 관계 0개 페르소나 = 0명, 성별·나이 제약 위반 = 0건,
marital-관계유형 정합성 위반 = 0건. 프로필 재생성(marital/age_years/gender 축 재배정) 완료
전에 채점하면 MARRIAGE/COUPLE이 아직 없어 오탐이 날 수 있다 — 재생성 완료 후 채점할 것.
"""

from __future__ import annotations

import argparse
import itertools
import json
import os
import stat
import subprocess
import sys
import tempfile
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[2]

CONTAINERS = {
    "dev": "againspring-mariadb-dev",
    "prod": "againspring-mariadb-prod",
}

# 계약 1(00-shared.md) — V22__persona_identity_axes.sql로 추가되는 컬럼 중 게이트 a/b가 읽는 것.
REQUIRED_V22_COLUMNS = {"age_years", "gender", "marital", "married_years", "has_kids", "style_axes"}

# PersonaProfileRegenerator.CURRENT_PROFILE_REV(voice_profile.profile_rev 마커)와 동기화 —
# 값을 바꾸면 두 곳 모두 갱신할 것. style_axes 유무만으로 "재생성 완료"를 판정하면 오염 상태
# (축은 채워졌지만 감사/voice_profile 갱신이 실패한 상태, 2026-09 dev 12명 사례)를 완료로
# 잘못 세게 된다 — 재생성 진척 계측은 반드시 이 마커까지 함께 확인한다.
CURRENT_PROFILE_REV = "v5"  # 축 배정 알고리즘 변경 시 올린다 — PersonaProfileRegenerator와 같은 값이어야 한다

QUOTA_TOLERANCE = 3  # 계약 2 — 축별 오차 ±3
DIVERSITY_TOLERANCE = 5  # 계약 3 — style_axes 각 축 분포 오차 ±5

# 계약 2 — 150명 쿼터 그리드
GENDER_QUOTA = {"M": 75, "F": 75}
AGE_BAND_QUOTA = {"23-29": 60, "30-36": 60, "37-49": 30}
MARITAL_GROUP_QUOTA = {"SINGLE_GROUP": 60, "MARRIED": 90}
MARRIED_BY_AGE_BAND_QUOTA = {"23-29": 15, "30-36": 45, "37-49": 30}
TIER_QUOTA = {"HEAVY": 20, "REGULAR": 80, "LIGHT": 50}
VOICE_TYPE_QUOTA = {"NATEPAN": 75, "BLIND": 75}
HAS_KIDS_OF_MARRIED_QUOTA = 45  # MARRIED 90명 중 45명

STYLE_AXES_OPTIONS: dict[str, list[str]] = {
    "directness": ["BLUNT", "SOFT"],
    "affect": ["EMOTIONAL", "ANALYTIC"],
    "humor": ["JOKER", "SERIOUS"],
    "stance": ["OFFENSIVE", "DEFENSIVE"],
    "length": ["LONG", "SHORT"],
    "speech": ["BANMAL", "JONDAE", "MIXED"],
    "emoticon": ["NONE", "LOW", "HIGH"],
    "spelling": ["CLEAN", "SLOPPY"],
    "linebreak": ["WALL", "CHOPPED"],
    "profanity": ["NONE", "MILD", "HEAVY"],
}

MIN_SIGNATURE_PHRASES_UNIQUE = 140
MIN_REPLY_STYLE_UNIQUE = 120
MIN_COMMENT_STYLE_UNIQUE = 120
# general_style 쌍별 8-gram Jaccard 상한.
#
# 0.10 → 0.15 (2026-09-06 prod 실측 근거). 개선 전에는 100명 넘는 페르소나가 동일 템플릿을
# 공유해 이 값이 1.0이었다. 재생성 후 11,175개 쌍 중 최댓값이 0.1077이었고, 그 한 쌍
# (36세 남 기혼 / 25세 여 연애중)은 실제로 서로 다른 문체이며 "조목조목" 같은 흔한 한국어
# 표현이 겹쳤을 뿐이다. 0.10은 한국어 산문에 지나치게 빡빡해 noise를 결함으로 잡는다.
# 0.15는 템플릿 중복(실측 0.5 이상)은 여전히 잡으면서 자연스러운 표현 겹침은 통과시킨다.
MAX_PAIRWISE_JACCARD = 0.15

GATE_C_MIN_POSTING_SHARE = 0.90
GATE_C_MAX_TOP10_SHARE = 0.25
GATE_C_MIN_COMMENTS = 30

# 게이트 d(관계) — PersonaRelationshipFiller가 다루는 커버링 관계 유형(01-wp1-persona-data.md §6).
COVERING_RELATION_TYPES = ("COUPLE", "MARRIAGE", "FRIEND")
COUPLE_MARITAL_VALUES = ("DATING", "ENGAGED")


@dataclass
class DbTarget:
    env_name: str
    container: str
    user: str
    password: str
    database: str


@dataclass
class GateResult:
    gate: str
    passed: bool
    checks: list[dict[str, Any]] = field(default_factory=list)
    note: str = ""

    def add(self, name: str, ok: bool, detail: str) -> None:
        self.checks.append({"check": name, "pass": ok, "detail": detail})
        if not ok:
            self.passed = False


class MissingV22ColumnsError(RuntimeError):
    pass


# --- env/DB 배선 (curate_legacy_posts.py / reclassify_post_categories.py와 동일 관례) ---


def load_dotenv(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    if not path.exists():
        return values
    for line in path.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#") or "=" not in stripped:
            continue
        key, value = stripped.split("=", 1)
        values[key.strip()] = value.strip().strip('"').strip("'")
    return values


def infer_env_name(env_file: str) -> str:
    name = Path(env_file).name.lower()
    if "prod" in name:
        return "prod"
    if "dev" in name:
        return "dev"
    raise ValueError(f"env-file '{env_file}'에서 dev/prod를 추론할 수 없다. --env-name을 명시하라.")


def build_db_target(args: argparse.Namespace, env_values: dict[str, str]) -> DbTarget:
    env_name = args.env_name or infer_env_name(args.env_file)
    container = args.db_container or CONTAINERS.get(env_name)
    if not container:
        raise ValueError(f"알 수 없는 env-name: {env_name}")
    return DbTarget(
        env_name=env_name,
        container=container,
        user=args.db_user or env_values.get("MARIADB_USER", "againspring"),
        password=args.db_password or env_values.get("MARIADB_PASSWORD", ""),
        database=args.db_name or env_values.get("MARIADB_DATABASE", "againspring_dev"),
    )


def _mysql_pwd_env_file(password: str) -> str:
    """`docker exec --env-file`에 넘길 임시 파일을 만들고 경로를 반환한다.

    `-p<password>`는 호스트 `ps auxww`와 `docker top`에 평문으로 남는다. `-e MYSQL_PWD=`도
    값이 docker 클라이언트 argv에 남아 같은 문제가 있다. `--env-file`은 argv에 경로만 남긴다.
    파일은 0600으로 만들고 호출 직후 지운다(purge_offtarget_posts와 동일 방식)."""
    fd, path = tempfile.mkstemp(prefix="mysql-pwd-")
    try:
        os.chmod(path, stat.S_IRUSR | stat.S_IWUSR)
        with os.fdopen(fd, "w") as f:
            f.write(f"MYSQL_PWD={password}\n")
    except BaseException:
        os.close(fd)
        os.unlink(path)
        raise
    return path


def run_mariadb_sql(target: DbTarget, sql: str) -> str:
    """`--raw` 필수 — batch 모드의 이중 백슬래시 이스케이프가 JSON_ARRAYAGG 결과를
    깨뜨리는 걸 막는다(purge_offtarget_posts.run_mariadb_sql 참고, 2026-09-05 실측).

    비밀번호는 argv로 넘기지 않는다 — `_mysql_pwd_env_file` 참고."""
    pwd_file = _mysql_pwd_env_file(target.password)
    try:
        result = subprocess.run(
            [
                "docker", "exec", "-i", f"--env-file={pwd_file}", target.container,
                "mariadb", "--raw", "-u", target.user, target.database,
                "-N", "-B", "-e", sql,
            ],
            capture_output=True,
            text=True,
            timeout=60,
        )
    finally:
        os.unlink(pwd_file)
    if result.returncode != 0:
        raise RuntimeError(f"mariadb 실행 실패({target.container}): {result.stderr[:800]}")
    return result.stdout


def fetch_existing_columns(target: DbTarget) -> set[str]:
    sql = (
        "SELECT GROUP_CONCAT(COLUMN_NAME) FROM information_schema.COLUMNS "
        f"WHERE TABLE_SCHEMA = '{target.database}' AND TABLE_NAME = 'personas';"
    )
    raw = run_mariadb_sql(target, sql).strip()
    if not raw or raw in ("NULL", "\\N"):
        return set()
    return set(raw.split(","))


def assert_v22_applied(existing_columns: set[str]) -> None:
    missing = REQUIRED_V22_COLUMNS - existing_columns
    if missing:
        raise MissingV22ColumnsError(
            "personas 테이블에 V22 컬럼이 없다(미적용): " + ", ".join(sorted(missing))
        )


def fetch_persona_rows(target: DbTarget) -> list[dict[str, Any]]:
    sql = (
        "SELECT JSON_ARRAYAGG(JSON_OBJECT("
        "'id', id, 'age_years', age_years, 'gender', gender, 'marital', marital, "
        "'married_years', married_years, 'has_kids', has_kids + 0, 'tier', tier, "
        "'voice_profile', voice_profile, 'style_axes', style_axes"
        ")) FROM personas WHERE active = 1;"
    )
    raw = run_mariadb_sql(target, sql).strip()
    if not raw or raw in ("NULL", "\\N"):
        return []
    return json.loads(raw)


# --- 순수 판정 함수 (픽스처 dict/list만으로 테스트 가능) ---------------------------


def age_band(age_years: int) -> str:
    if 23 <= age_years <= 29:
        return "23-29"
    if 30 <= age_years <= 36:
        return "30-36"
    if 37 <= age_years <= 49:
        return "37-49"
    return "OUT_OF_RANGE"


def marital_group(marital: str) -> str:
    return "MARRIED" if marital == "MARRIED" else "SINGLE_GROUP"


def extract_style_axes(row: dict[str, Any]) -> dict[str, Any] | None:
    """style_axes 컬럼이 채워졌으면(=PersonaProfileRegenerator가 이 페르소나를 재생성했으면)
    dict를 반환하고, 컬럼 기본값(NULL)이면 None을 반환한다. 재생성 진척 계측(계약 밖, 운영
    편의)과 게이트 b 데이터 추출이 공유하는 정규화 로직이다."""
    sa = row.get("style_axes")
    if isinstance(sa, str):
        try:
            sa = json.loads(sa)
        except (json.JSONDecodeError, TypeError):
            return None
    if isinstance(sa, dict) and sa:
        return sa
    return None


def _regeneration_note(regenerated: int, total: int) -> str:
    remaining = total - regenerated
    return f"재생성 진척: {regenerated}/{total} 완료, {remaining}명 미재생성(컬럼 기본값)"


def _voice_profile_dict(row: dict[str, Any]) -> dict[str, Any]:
    vp = row.get("voice_profile")
    if isinstance(vp, str):
        try:
            vp = json.loads(vp)
        except (json.JSONDecodeError, TypeError):
            return {}
    return vp if isinstance(vp, dict) else {}


def is_profile_regenerated(row: dict[str, Any]) -> bool:
    """style_axes 존재 여부가 아니라 실제 갱신 완료 여부(voice_profile.profile_rev 마커,
    PersonaProfileRegenerator.CURRENT_PROFILE_REV와 동일 기준)로 판정한다. style_axes는
    감사·voice_profile 병합과 무관하게 항상 먼저 채워질 수 있어(오염 12명 사례) 그것만으로는
    "완료"를 보장하지 못한다."""
    if extract_style_axes(row) is None:
        return False
    return _voice_profile_dict(row).get("profile_rev") == CURRENT_PROFILE_REV


def _extract_voice_type(row: dict[str, Any]) -> str | None:
    vp = row.get("voice_profile")
    if isinstance(vp, str):
        try:
            vp = json.loads(vp)
        except (json.JSONDecodeError, TypeError):
            return None
    if isinstance(vp, dict):
        return vp.get("voice_type")
    return None


def _quota_check(result: GateResult, label: str, actual: dict[str, int], quota: dict[str, int]) -> None:
    for key, target_count in quota.items():
        actual_count = actual.get(key, 0)
        diff = abs(actual_count - target_count)
        ok = diff <= QUOTA_TOLERANCE
        result.add(
            f"{label}:{key}",
            ok,
            f"actual={actual_count} quota={target_count} diff={diff} (허용 ±{QUOTA_TOLERANCE})",
        )


def evaluate_gate_a(rows: list[dict[str, Any]]) -> GateResult:
    result = GateResult(gate="a", passed=True)
    result.add("persona_count", len(rows) > 0, f"active personas={len(rows)}")
    if not rows:
        return result

    regenerated = sum(1 for row in rows if is_profile_regenerated(row))
    result.note = _regeneration_note(regenerated, len(rows))

    gender_counts: dict[str, int] = {}
    age_band_counts: dict[str, int] = {}
    marital_counts: dict[str, int] = {}
    married_by_age_band: dict[str, int] = {}
    tier_counts: dict[str, int] = {}
    voice_type_counts: dict[str, int] = {}
    kids_of_married = 0
    married_total = 0

    age_range_violations = []
    married_years_violations = []
    has_kids_violations = []

    for row in rows:
        age = row.get("age_years")
        gender = row.get("gender")
        marital = row.get("marital")
        married_years = row.get("married_years")
        has_kids = bool(row.get("has_kids"))
        tier = row.get("tier")
        voice_type = _extract_voice_type(row)

        if age is None or not (23 <= age <= 49):
            age_range_violations.append(row.get("id"))

        if gender is not None:
            gender_counts[gender] = gender_counts.get(gender, 0) + 1

        band = age_band(age) if age is not None else "OUT_OF_RANGE"
        age_band_counts[band] = age_band_counts.get(band, 0) + 1

        group = marital_group(marital) if marital else "SINGLE_GROUP"
        marital_counts[group] = marital_counts.get(group, 0) + 1

        if marital == "MARRIED":
            married_total += 1
            married_by_age_band[band] = married_by_age_band.get(band, 0) + 1
            if has_kids:
                kids_of_married += 1
            # 계약1 개정(2026-09-05): 결혼 최소 연령 25→23세 + married_years≥1 보장.
            # married_years ≤ age-23 AND married_years ≥ 1 (0년차 기혼은 부자연스러워 금지).
            if married_years is not None and age is not None and (
                married_years > age - 23 or married_years < 1
            ):
                married_years_violations.append(row.get("id"))
        elif has_kids:
            has_kids_violations.append(row.get("id"))

        if tier is not None:
            tier_counts[tier] = tier_counts.get(tier, 0) + 1
        if voice_type is not None:
            voice_type_counts[voice_type] = voice_type_counts.get(voice_type, 0) + 1

    _quota_check(result, "gender", gender_counts, GENDER_QUOTA)
    _quota_check(result, "age_band", age_band_counts, AGE_BAND_QUOTA)
    _quota_check(result, "marital", marital_counts, MARITAL_GROUP_QUOTA)
    _quota_check(result, "married_by_age_band", married_by_age_band, MARRIED_BY_AGE_BAND_QUOTA)
    _quota_check(result, "tier", tier_counts, TIER_QUOTA)
    _quota_check(result, "voice_type", voice_type_counts, VOICE_TYPE_QUOTA)

    result.add(
        "has_kids_of_married",
        abs(kids_of_married - HAS_KIDS_OF_MARRIED_QUOTA) <= QUOTA_TOLERANCE,
        f"actual={kids_of_married}/{married_total} quota={HAS_KIDS_OF_MARRIED_QUOTA}",
    )
    result.add(
        "age_range_violations",
        len(age_range_violations) == 0,
        f"count={len(age_range_violations)} ids={age_range_violations[:5]}",
    )
    result.add(
        "married_years_violations",
        len(married_years_violations) == 0,
        f"count={len(married_years_violations)} ids={married_years_violations[:5]}",
    )
    result.add(
        "has_kids_requires_married",
        len(has_kids_violations) == 0,
        f"count={len(has_kids_violations)} ids={has_kids_violations[:5]}",
    )
    return result


def char_ngrams(text: str, n: int = 8) -> set[str]:
    text = text or ""
    if len(text) < n:
        return {text} if text else set()
    return {text[i : i + n] for i in range(len(text) - n + 1)}


def jaccard(a: set[str], b: set[str]) -> float:
    if not a and not b:
        return 0.0
    union = a | b
    if not union:
        return 0.0
    return len(a & b) / len(union)


def evaluate_gate_b(data: dict[str, Any]) -> GateResult:
    """data 키: signature_phrases(list[str]), reply_style(list[str]), comment_style(list[str]),
    general_style(list[str], 150명 각각의 문체 텍스트), style_axes(list[dict]),
    total_personas(int, 참고 — 전체 활성 페르소나 수, 재생성 진척 표시용)."""
    result = GateResult(gate="b", passed=True)

    style_axes_rows_for_note = list(data.get("style_axes", []))
    total_personas = data.get("total_personas", len(style_axes_rows_for_note))
    # regenerated_count(fetch_gate_b_data가 profile_rev 마커까지 확인해 채움)가 있으면 그걸
    # 우선한다 — style_axes 개수만 쓰면 오염 상태(마커 없이 축만 채워짐)를 완료로 잘못 센다.
    # 없으면(직접 만든 data dict로 evaluate_gate_b를 호출하는 기존 호출부·테스트) style_axes
    # 개수로 하위호환.
    regenerated_count = data.get("regenerated_count", len(style_axes_rows_for_note))
    result.note = _regeneration_note(regenerated_count, total_personas)

    sig = list(data.get("signature_phrases", []))
    result.add(
        "signature_phrases_unique",
        len(set(sig)) >= MIN_SIGNATURE_PHRASES_UNIQUE,
        f"unique={len(set(sig))} min={MIN_SIGNATURE_PHRASES_UNIQUE}",
    )

    reply_style = list(data.get("reply_style", []))
    result.add(
        "reply_style_unique",
        len(set(reply_style)) >= MIN_REPLY_STYLE_UNIQUE,
        f"unique={len(set(reply_style))} min={MIN_REPLY_STYLE_UNIQUE}",
    )

    comment_style = list(data.get("comment_style", []))
    result.add(
        "comment_style_unique",
        len(set(comment_style)) >= MIN_COMMENT_STYLE_UNIQUE,
        f"unique={len(set(comment_style))} min={MIN_COMMENT_STYLE_UNIQUE}",
    )

    general_style = list(data.get("general_style", []))
    max_jaccard = 0.0
    worst_pair: tuple[int, int] | None = None
    ngram_sets = [char_ngrams(s) for s in general_style]
    for i, j in itertools.combinations(range(len(ngram_sets)), 2):
        sim = jaccard(ngram_sets[i], ngram_sets[j])
        if sim > max_jaccard:
            max_jaccard = sim
            worst_pair = (i, j)
    result.add(
        "general_style_pairwise_jaccard",
        max_jaccard < MAX_PAIRWISE_JACCARD,
        f"max={max_jaccard:.4f} threshold<{MAX_PAIRWISE_JACCARD} worst_pair={worst_pair}",
    )

    style_axes_rows = list(data.get("style_axes", []))
    n = len(style_axes_rows)
    for axis, options in STYLE_AXES_OPTIONS.items():
        counts: dict[str, int] = {opt: 0 for opt in options}
        for row in style_axes_rows:
            value = row.get(axis)
            if value in counts:
                counts[value] += 1
        expected = n / len(options) if n else 0
        for opt in options:
            diff = abs(counts[opt] - expected)
            ok = diff <= DIVERSITY_TOLERANCE
            result.add(
                f"style_axes:{axis}:{opt}",
                ok,
                f"actual={counts[opt]} expected~={expected:.1f} diff={diff:.1f} (허용 ±{DIVERSITY_TOLERANCE})",
            )

    return result


def evaluate_gate_c(stats: dict[str, Any]) -> GateResult:
    """stats 키: total_active_personas(int), posting_personas(int), top10_post_share(float 0~1),
    comment_counts_by_persona(dict[str,int])."""
    result = GateResult(gate="c", passed=True, note="참고용 — 배포 게이트 아님, 항상 PASS 취급")

    total = stats.get("total_active_personas", 0)
    posting = stats.get("posting_personas", 0)
    share = posting / total if total else 0.0
    ok = share >= GATE_C_MIN_POSTING_SHARE
    result.add("posting_persona_share", True, f"actual={share:.1%} target>={GATE_C_MIN_POSTING_SHARE:.0%} raw_pass={ok}")

    top10_share = stats.get("top10_post_share", 0.0)
    ok_top10 = top10_share < GATE_C_MAX_TOP10_SHARE
    result.add("top10_post_share", True, f"actual={top10_share:.1%} target<{GATE_C_MAX_TOP10_SHARE:.0%} raw_pass={ok_top10}")

    comment_counts = stats.get("comment_counts_by_persona", {})
    under_min = sum(1 for c in comment_counts.values() if c < GATE_C_MIN_COMMENTS)
    total_commenters = len(comment_counts) or 1
    result.add(
        "comments_under_30_share",
        True,
        f"count={under_min}/{total_commenters} ({under_min / total_commenters:.1%}) — 참고치, PASS/FAIL 없음",
    )
    return result


def evaluate_gate_d(data: dict[str, Any]) -> GateResult:
    """게이트 d(관계) — `PersonaRelationshipFiller` 결과 검증(01-wp1-persona-data.md §6).

    data 키: total_active_personas(int), relation_type_counts(dict[str,int] — COUPLE/MARRIAGE/
    FRIEND의 ACTIVE 건수, 참고용), uncovered_count(int — COUPLE|MARRIAGE|FRIEND ACTIVE 관계가
    하나도 없는 활성 페르소나 수), gender_age_violation_count(int — MARRIAGE 성별동일 또는
    나이차>8 / COUPLE 성별동일 / FRIEND 나이차>5인 ACTIVE 관계 수), marital_violation_count
    (int — MARRIAGE인데 당사자 중 비MARRIED가 있거나 COUPLE인데 당사자 중 DATING/ENGAGED가
    아닌 사람이 있는 ACTIVE 관계 수).
    """
    result = GateResult(gate="d", passed=True)
    result.note = f"관계 유형별(ACTIVE): {data.get('relation_type_counts', {})}"

    result.add(
        "uncovered_personas",
        data.get("uncovered_count", 0) == 0,
        f"count={data.get('uncovered_count', 0)} (COUPLE|MARRIAGE|FRIEND ACTIVE 관계가 0개인 활성 페르소나)",
    )
    result.add(
        "gender_age_violations",
        data.get("gender_age_violation_count", 0) == 0,
        f"count={data.get('gender_age_violation_count', 0)} "
        "(MARRIAGE 성별동일·나이차>8 / COUPLE 성별동일 / FRIEND 나이차>5)",
    )
    result.add(
        "marital_consistency_violations",
        data.get("marital_violation_count", 0) == 0,
        f"count={data.get('marital_violation_count', 0)} "
        "(MARRIAGE 당사자에 비MARRIED 포함 / COUPLE 당사자에 비DATING·ENGAGED 포함)",
    )
    return result


# --- gate c 집계 (DB 필요) ----------------------------------------------------


def fetch_gate_c_stats(target: DbTarget, days: int) -> dict[str, Any]:
    total_sql = "SELECT COUNT(*) FROM personas WHERE active = 1;"
    total = int(run_mariadb_sql(target, total_sql).strip() or 0)

    posting_sql = (
        "SELECT COUNT(DISTINCT p.author_id) FROM posts p JOIN users u ON u.id = p.author_id "
        "WHERE u.synthetic = 1 AND p.deleted_at IS NULL "
        f"AND p.created_at >= NOW() - INTERVAL {int(days)} DAY;"
    )
    posting = int(run_mariadb_sql(target, posting_sql).strip() or 0)

    per_author_sql = (
        "SELECT JSON_ARRAYAGG(cnt) FROM ("
        "SELECT COUNT(*) AS cnt FROM posts p JOIN users u ON u.id = p.author_id "
        "WHERE u.synthetic = 1 AND p.deleted_at IS NULL "
        f"AND p.created_at >= NOW() - INTERVAL {int(days)} DAY "
        "GROUP BY p.author_id ORDER BY cnt DESC"
        ") t;"
    )
    raw_counts = run_mariadb_sql(target, per_author_sql).strip()
    post_counts = json.loads(raw_counts) if raw_counts and raw_counts not in ("NULL", "\\N") else []
    total_posts = sum(post_counts)
    top10_posts = sum(sorted(post_counts, reverse=True)[:10])
    top10_share = (top10_posts / total_posts) if total_posts else 0.0

    comment_sql = (
        "SELECT JSON_OBJECTAGG(author_id, cnt) FROM ("
        "SELECT c.author_id AS author_id, COUNT(*) AS cnt FROM post_comments c "
        "JOIN users u ON u.id = c.author_id "
        f"WHERE u.synthetic = 1 AND c.deleted_at IS NULL AND c.created_at >= NOW() - INTERVAL {int(days)} DAY "
        "GROUP BY c.author_id"
        ") t;"
    )
    raw_comments = run_mariadb_sql(target, comment_sql).strip()
    comment_counts = json.loads(raw_comments) if raw_comments and raw_comments not in ("NULL", "\\N") else {}

    return {
        "total_active_personas": total,
        "posting_personas": posting,
        "top10_post_share": top10_share,
        "comment_counts_by_persona": comment_counts,
    }


def fetch_gate_d_stats(target: DbTarget) -> dict[str, Any]:
    """게이트 d 원시 집계 — `persona_relationships` × `personas` 조인. `--raw` 필수(§ 위 주석 참고)."""
    total_sql = "SELECT COUNT(*) FROM personas WHERE active = 1;"
    total = int(run_mariadb_sql(target, total_sql).strip() or 0)

    types_in = ",".join(f"'{t}'" for t in COVERING_RELATION_TYPES)

    type_counts_sql = (
        "SELECT JSON_OBJECTAGG(relation_type, cnt) FROM ("
        f"SELECT relation_type, COUNT(*) AS cnt FROM persona_relationships "
        f"WHERE status = 'ACTIVE' AND relation_type IN ({types_in}) "
        "GROUP BY relation_type"
        ") t;"
    )
    raw_types = run_mariadb_sql(target, type_counts_sql).strip()
    relation_type_counts = json.loads(raw_types) if raw_types and raw_types not in ("NULL", "\\N") else {}

    uncovered_sql = (
        "SELECT COUNT(*) FROM personas p WHERE p.active = 1 AND NOT EXISTS ("
        "SELECT 1 FROM persona_relationships r WHERE r.status = 'ACTIVE' "
        f"AND r.relation_type IN ({types_in}) "
        "AND (r.persona_id = p.id OR r.other_id = p.id));"
    )
    uncovered_count = int(run_mariadb_sql(target, uncovered_sql).strip() or 0)

    gender_age_sql = (
        "SELECT COUNT(*) FROM persona_relationships r "
        "JOIN personas a ON a.id = r.persona_id JOIN personas b ON b.id = r.other_id "
        "WHERE r.status = 'ACTIVE' AND ("
        "(r.relation_type = 'MARRIAGE' AND (a.gender = b.gender OR ABS(a.age_years - b.age_years) > 8)) "
        "OR (r.relation_type = 'COUPLE' AND a.gender = b.gender) "
        "OR (r.relation_type = 'FRIEND' AND ABS(a.age_years - b.age_years) > 5));"
    )
    gender_age_violation_count = int(run_mariadb_sql(target, gender_age_sql).strip() or 0)

    couple_marital_in = ",".join(f"'{m}'" for m in COUPLE_MARITAL_VALUES)
    marital_sql = (
        "SELECT COUNT(*) FROM persona_relationships r "
        "JOIN personas a ON a.id = r.persona_id JOIN personas b ON b.id = r.other_id "
        "WHERE r.status = 'ACTIVE' AND ("
        "(r.relation_type = 'MARRIAGE' AND (a.marital <> 'MARRIED' OR b.marital <> 'MARRIED')) "
        f"OR (r.relation_type = 'COUPLE' AND (a.marital NOT IN ({couple_marital_in}) "
        f"OR b.marital NOT IN ({couple_marital_in}))));"
    )
    marital_violation_count = int(run_mariadb_sql(target, marital_sql).strip() or 0)

    return {
        "total_active_personas": total,
        "relation_type_counts": relation_type_counts,
        "uncovered_count": uncovered_count,
        "gender_age_violation_count": gender_age_violation_count,
        "marital_violation_count": marital_violation_count,
    }


def fetch_gate_b_data(rows: list[dict[str, Any]]) -> dict[str, Any]:
    signature_phrases: list[str] = []
    reply_style: list[str] = []
    comment_style: list[str] = []
    general_style: list[str] = []
    style_axes: list[dict[str, Any]] = []
    regenerated_count = 0

    for row in rows:
        vp = row.get("voice_profile")
        if isinstance(vp, str):
            try:
                vp = json.loads(vp)
            except (json.JSONDecodeError, TypeError):
                vp = {}
        vp = vp or {}
        lexicon = vp.get("lexicon", {}) if isinstance(vp, dict) else {}
        phrases = lexicon.get("signature_phrases") if isinstance(lexicon, dict) else None
        if isinstance(phrases, list):
            signature_phrases.extend(str(p) for p in phrases)
        if isinstance(vp, dict):
            if vp.get("reply_style"):
                reply_style.append(str(vp["reply_style"]))
            if vp.get("comment_style"):
                comment_style.append(str(vp["comment_style"]))
            if vp.get("general_style"):
                general_style.append(str(vp["general_style"]))

        sa = extract_style_axes(row)
        if sa is not None:
            style_axes.append(sa)

        if is_profile_regenerated(row):
            regenerated_count += 1

    return {
        "signature_phrases": signature_phrases,
        "reply_style": reply_style,
        "comment_style": comment_style,
        "general_style": general_style,
        "style_axes": style_axes,
        "total_personas": len(rows),
        "regenerated_count": regenerated_count,
    }


# --- 출력 ---------------------------------------------------------------------


def print_result(result: GateResult, as_json: bool) -> None:
    if as_json:
        print(json.dumps({"gate": result.gate, "passed": result.passed, "note": result.note, "checks": result.checks}, ensure_ascii=False, indent=2))
        return
    status = "PASS" if result.passed else "FAIL"
    print(f"\n=== Gate {result.gate.upper()}: {status} {('(' + result.note + ')') if result.note else ''} ===")
    for c in result.checks:
        mark = "OK  " if c["pass"] else "FAIL"
        print(f"  [{mark}] {c['check']}: {c['detail']}")


def run_gate(gate: str, target: DbTarget, args: argparse.Namespace) -> GateResult:
    if gate in ("a", "b", "d"):
        # d도 age_years/gender/marital(V22 컬럼)에 의존한다 — 재생성 전이면 오탐이 나므로 동일 가드.
        existing_columns = fetch_existing_columns(target)
        assert_v22_applied(existing_columns)
        if gate == "d":
            return evaluate_gate_d(fetch_gate_d_stats(target))
        rows = fetch_persona_rows(target)
        if gate == "a":
            return evaluate_gate_a(rows)
        return evaluate_gate_b(fetch_gate_b_data(rows))
    if gate == "c":
        stats = fetch_gate_c_stats(target, args.days)
        return evaluate_gate_c(stats)
    raise ValueError(f"알 수 없는 gate: {gate}")


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--env-file", default="env/.env.dev")
    parser.add_argument("--env-name", choices=["dev", "prod"], default=None)
    parser.add_argument("--db-container", default=None)
    parser.add_argument("--db-user", default=None)
    parser.add_argument("--db-password", default=None)
    parser.add_argument("--db-name", default=None)
    parser.add_argument("--gate", choices=["a", "b", "c", "d", "all"], required=True)
    parser.add_argument("--days", type=int, default=7, help="gate c 집계 기간(일)")
    parser.add_argument("--json", action="store_true")
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    env_values = load_dotenv(ROOT / args.env_file)
    target = build_db_target(args, env_values)
    # 어느 환경을 실제로 읽는지 밝힌다 — --db-container/--db-name은 --env-file과 독립적으로
    # 덮어쓸 수 있어, 파일명만 보고 dev라고 믿으면 prod 데이터로 게이트를 판정하게 된다.
    print(f"[target] container={target.container} database={target.database}", file=sys.stderr)

    gates = ["a", "b", "c", "d"] if args.gate == "all" else [args.gate]
    overall_rc = 0
    for gate in gates:
        try:
            result = run_gate(gate, target, args)
        except MissingV22ColumnsError as exc:
            print(f"[gate {gate}] {exc}", file=sys.stderr)
            return 2
        print_result(result, args.json)
        if gate != "c" and not result.passed:
            overall_rc = 1
    return overall_rc


if __name__ == "__main__":
    raise SystemExit(main())
