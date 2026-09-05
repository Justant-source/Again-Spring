# WP1B operator runbook — register 단일화 + voice 정화

## 목적

활성 150명 `voice_type`을 **NATEPAN | BLIND**로 맞추고, `example_*` / `lexicon` / `general_style` 등
프롬프트 voice 자산을 natepan·blind 코퍼스 앵커로 정화한다. LLM 없음(결정적 샘플링).

## 산출물

| 경로 | 설명 |
|---|---|
| `ai-user/tools/snapshots/wp1b-voice_profiles-YYYYMMDD-HHMMSS.json` | **정화 전** 스냅샷 (복원용). 여기가 유일본 — `/home/justant/backups/` 사본은 30일 보관 정책으로 삭제됐다 |
| `ai-user/tools/snapshots/wp1b-voice_profiles-latest.json` | 최신 스냅샷 복사 |
| `ai-user/tools/snapshots/wp1b-corpus-anchors.tsv` | natepan\|blind COMMENT/POST 앵커 export |
| `ai-user/tools/snapshots/wp1b-purified-latest.json` | 정화 결과 dump |
| `ai-user/tools/snapshots/wp1b-plan.json` | before/after·페르소나별 메타 |
| `ai-user/tools/wp1b_purify_voices.py` | 스냅샷→재배정→정화→적용·복원 |

## 재배정 규칙 (soft 3:1)

- 이미 NATEPAN/BLIND → 유지, 정화만
- 그 외 → `job`(직장인→BLIND) · `interests.WORK` · `age` 점수 내림차순으로
  상위 ≈22%를 BLIND, 나머지를 NATEPAN
- 보존: age/gender/region/job/interests/bias_profile/circadian

## 명령

```bash
# 0) (필요 시) 코퍼스 재export
PASS=$(docker exec againspring-mariadb-prod printenv MARIADB_ROOT_PASSWORD)
docker exec againspring-mariadb-prod mariadb -uroot -p"$PASS" againspring_prod --batch --raw -N -e "
SELECT CONCAT(id, '\t', LOWER(source), '\t', content_type, '\t', IFNULL(quality_score,0), '\t',
              REPLACE(REPLACE(content, CHAR(10), ' '), '\t', ' '))
FROM example_bank
WHERE LOWER(source) IN ('natepan','blind') AND source != 'SELF_GENERATED'
  AND content_type IN ('COMMENT','POST') AND CHAR_LENGTH(content) BETWEEN 15 AND 400;
" > ai-user/tools/snapshots/wp1b-corpus-anchors.tsv

# 1) dry-run
python3 ai-user/tools/wp1b_purify_voices.py \
  --snapshot ai-user/tools/snapshots/wp1b-voice_profiles-latest.json \
  --corpus ai-user/tools/snapshots/wp1b-corpus-anchors.tsv \
  --plan-out ai-user/tools/snapshots/wp1b-plan.json \
  --force-regen-examples

# 2) 배치 적용 (예: 20명)
python3 ai-user/tools/wp1b_purify_voices.py ... --apply --limit 20 --offset 0 --batch-size 20 --sync-yaml

# 3) 전체 적용
python3 ai-user/tools/wp1b_purify_voices.py ... --apply --batch-size 20 --sync-yaml --force-regen-examples

# 4) 복원
python3 ai-user/tools/wp1b_purify_voices.py \
  --restore ai-user/tools/snapshots/wp1b-voice_profiles-YYYYMMDD-HHMMSS.json
```

검증:

```bash
python3 ai-user/tools/scan_voice_type_reassign.py --json ai-user/tools/snapshots/wp1b-purified-latest.json
python3 ai-user/tools/scan_voice_contamination.py --json ai-user/tools/snapshots/wp1b-purified-latest.json
```

## 안전

- `example_bank` 행 삭제 금지
- 스냅샷 **없이** `--apply` 금지
- learning `persona_strengthener`는 NATEPAN/BLIND + natepan|blind 소스만 허용하도록 수정됨.
  **컨테이너에 반영하려면 learning 이미지 재빌드가 필요** (이 슬라이스는 배포하지 않음).
- YAML은 `profiles/*/voice.yml`이 있는 페르소나만 동기화 (현재 100/150)

## 금지

push / prod 앱 스택 재배포 / feature 브랜치 — 부모 agent·오너 지시 전 금지.
