# Step 76 (R14) — automatic pre-blind gates

## 목적

- manual cond5 전에 자동 신호 3종으로 위험 표현과 빨간 샘플을 먼저 걸러낸다.

## 추가 파일

- `.result/ai-user/scripts/blind_gate_common.py`
- `.result/ai-user/scripts/auto_tell_scan.py`
- `.result/ai-user/scripts/ensemble_blind_judge.py`
- `.result/ai-user/scripts/adversarial_generate_and_filter.py`

## 구성

1. `auto_tell_scan.py`
   - survey + answers를 읽어 AI 라벨 텍스트만 스캔
   - `헐/개공감`, `1도 ~`, `월·화·수`, `...`, topic-first opener 같은 tell hit 집계
   - 길이/줄수/반복 토큰 기반 risk score 산출

2. `ensemble_blind_judge.py`
   - Codex judge 3종(`style_tells`, `community_fit`, `narrative_flow`) 앙상블
   - pair별 A/B 중 AI로 보이는 쪽을 proxy blind로 판정
   - 정확도는 "judge가 AI side를 맞춘 비율"

3. `adversarial_generate_and_filter.py`
   - community theme별 초안을 여러 개 생성
   - tell scan + proxy single-judge를 합쳐 빨간 샘플 shortlist 생성

## 실행

```bash
python3 .result/ai-user/scripts/auto_tell_scan.py \
  --survey .result/ai-user/blind/r14-cond5-theqoo-survey.md \
  --answers .result/ai-user/blind/r14-cond5-theqoo-answers-template.json

python3 .result/ai-user/scripts/auto_tell_scan.py \
  --survey .result/ai-user/blind/r14-cond5-natepan-survey.md \
  --answers .result/ai-user/blind/r14-cond5-natepan-answers-template.json

python3 .result/ai-user/scripts/ensemble_blind_judge.py \
  --survey .result/ai-user/blind/r14-cond5-theqoo-survey.md \
  --answers .result/ai-user/blind/r14-cond5-theqoo-answers-template.json \
  --workers 8

python3 .result/ai-user/scripts/ensemble_blind_judge.py \
  --survey .result/ai-user/blind/r14-cond5-natepan-survey.md \
  --answers .result/ai-user/blind/r14-cond5-natepan-answers-template.json \
  --workers 8

python3 .result/ai-user/scripts/adversarial_generate_and_filter.py \
  --community THEQOO --themes 6 --samples-per-theme 2 --workers 8 --generator cli
```

## 결과

### 1. THEQOO tell scan

- report:
  - `.result/ai-user/blind/r14-cond5-theqoo-survey-auto-tell-scan.md`
- 상위 hit:
  - `one_do_pattern=7`
  - `reaction_word=7`
  - `many_dots=6`
- highest risk:
  - pair `9A`, `2A`, `12A`, `8B`, `5B`

### 2. NATEPAN tell scan

- report:
  - `.result/ai-user/blind/r14-cond5-natepan-survey-auto-tell-scan.md`
- 상위 hit:
  - `many_dots=15`
  - `one_do_pattern=7`

### 3. proxy blind judge

- THEQOO:
  - `.result/ai-user/blind/r14-cond5-theqoo-survey-ensemble-judge.md`
  - accuracy `50.0%`
- NATEPAN:
  - `.result/ai-user/blind/r14-cond5-natepan-survey-ensemble-judge.md`
  - accuracy `45.0%`

### 4. adversarial shortlist

- THEQOO:
  - `.result/ai-user/blind/r14-adversarial-theqoo.md`
  - CLI 생성 `12`개
  - top combined score `0`
  - proxy judge는 전부 `human`

## 해석

- 자동 proxy는 THEQOO를 `50.0%`로 봤지만, 실제 owner cond5는 `84.2% FAIL`이었다.
- 따라서 이 3도구는 **manual cond5 대체가 아니라 pre-screen** 용도다.
- 그래도 수동 전에 어떤 표현이 위험한지, 어떤 샘플이 빨간지 먼저 좁히는 데는 유용하다.

## 다음

1. `r14-cond5-natepan-survey.md` 수동 응답
2. runtime host 복구 후 THEQOO runtime h2h 재측정
3. 새 survey 생성 때마다 auto tell scan + proxy judge 먼저 실행
