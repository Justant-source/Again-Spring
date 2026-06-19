# Step 74 (R14) — blind fingerprint registry

## 목적

- `/corpus/export/blind`가 source id 메타를 비워 내려주는 상태에서도, 이미 쓴 blind 텍스트의 exact 재사용을 막는다.

## 추가 파일

- `.result/ai-user/scripts/survey_fingerprints.py`
- `.result/ai-user/scripts/reserve_blind_set.py`

## 변경

1. `survey_fingerprints.py`
   - survey markdown의 `## N번 / [A] / [B]` 블록을 파싱
   - A/B 본문을 정규화 후 SHA-256 fingerprint 생성
   - registry load/save를 atomic write + file lock으로 처리

2. `reserve_blind_set.py`
   - 기존 survey/answers를 읽어 `used-corpus-ids.json`에 backfill
   - `text_fingerprints`, `pair_fingerprints`를 test entry로 저장

3. `build_cond5_blind.py`
   - `used-corpus-ids.json`의 `all_used_text_fingerprints`를 읽어 exact 동일 본문을 필터
   - `--reserve-used` 옵션 추가
   - 생성 직후 registry 예약 가능

## 검증

1. 문법
   - `python3 -m py_compile .result/ai-user/scripts/survey_fingerprints.py .result/ai-user/scripts/reserve_blind_set.py .result/ai-user/scripts/build_cond5_blind.py`

2. registry backfill
   - `r14-cond5-clien-smoke`, `r14-cond5-natepan`, `r14-cond5-theqoo`, `r9-blind1-fresh`, `r9-blind2-mixed`, `r9-blind1` entry 반영
   - 결과:
     - `tests=6`
     - `all_used_text_fingerprints=163`
   - 참고:
     - `r9-blind1`은 기존 survey 포맷 차이로 fingerprint 추출 0건

3. exact reuse 차단
   - 같은 seed로 THEQOO cond5를 다시 fetch + filter
   - 결과:
     - `Not enough items after filtering: humans=0 ais=0 need=20`
   - 해석:
     - 현재 export 결과와 같은 blind 본문은 재사용되지 않는다.

## 제한

- text fingerprint는 exact 또는 정규화 후 동일한 본문 재사용을 막는 장치다.
- source id 메타가 없는 이상, 의미상 유사하지만 텍스트가 다른 항목까지 완전 차단하는 것은 아니다.
- `r9-blind1`처럼 survey 포맷이 표준 A/B 블록과 다르면 fingerprint 추출이 0건일 수 있어, 이후 포맷 보강 대상이다.

## 다음

1. host 접근이 열리면 runtime probe 수행
2. runtime h2h 생성 시 같은 registry 체계로 예약
3. 준비된 cond5 survey에 owner/friend가 수동 응답
