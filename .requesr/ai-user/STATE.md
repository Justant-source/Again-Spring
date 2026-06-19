# STATE — Request Snapshot

**최종 갱신**: 2026-06-19
**기준 원본**: `.result/ai-user/STATE.md` Step 78

## 현재 상태

- R14 기준 상태는 **HOLD**
- `AI_USER_ML_ENABLED=false` 유지
- 기존 `:8092 down` 가정은 철회됨
  - `8092/8096`은 dev compose에서 internal 서비스
  - backend → orchestrator proxy는 live `202` 확인
  - backend → `llm-ai-user:8092/internal/prompts/reload`도 live probe 성공
- 현재 실제 블로커는 external strict runtime generate probe 부재와 direct `/admin/trigger/*` 500

## 최신 핵심 결과

### THEQOO

- real-only A-B latest snapshot: `330`
- live `/corpus/stats`: `human=562`, `ai=116`
- fresh cond5 owner:
  - 유효 응답 `19/20`
  - AI 탐지 정확도 `84.2% (16/19)`
  - **FAIL**
- 주요 탐지 신호:
  - inline `개공감`, `헐`
  - `1도 모르겠음`, `1도 이해가 안 됨`
  - `월·화·수` middle dot
  - 반복 주제, 긴 서술형 전개

### 자동 pre-blind 게이트

- 추가 완료:
  - `auto_tell_scan.py`
  - `ensemble_blind_judge.py`
  - `adversarial_generate_and_filter.py`
- 실측:
  - THEQOO proxy blind judge: `50.0%`
  - NATEPAN proxy blind judge: `45.0%`
- 해석:
  - THEQOO는 proxy `50.0%`였지만 human owner는 `84.2% FAIL`
  - 따라서 자동 게이트는 **pre-screen 용도**이고 수동 cond5 대체물이 아님

## 지금 바로 남은 작업

1. dev host에서 `:8092` runtime 복구
2. `probe_runtime_pipeline.py`로 runtime 배관 확인
3. THEQOO runtime h2h를 owner+friend로 다시 수집
4. `r14-cond5-natepan-survey.md` 수동 응답 수집
5. 필요 시 THEQOO cond5 friend 응답 추가

## 빠른 링크

- THEQOO cond5 결과:
  - `.result/ai-user/blind/r14-cond5-theqoo-results.md`
- THEQOO auto tell scan:
  - `.result/ai-user/blind/r14-cond5-theqoo-survey-auto-tell-scan.md`
- THEQOO proxy judge:
  - `.result/ai-user/blind/r14-cond5-theqoo-survey-ensemble-judge.md`
- THEQOO adversarial shortlist:
  - `.result/ai-user/blind/r14-adversarial-theqoo.md`
