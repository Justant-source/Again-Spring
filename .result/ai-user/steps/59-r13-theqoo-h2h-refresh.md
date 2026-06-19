# Step 59 (R13-next2) — THEQOO h2h survey 재생성 + 활성화 HOLD 정리

## 상태

- Step 58에서 `source_filter="theqoo"` real-only snapshot **311/300** 및 `Δ_real=+0.1326` 확보 후 진행
- 목적: D-68 신 cond4의 **조건 B(h2h 비퇴행)** 측정을 위한 THEQOO 설문 재생성
- 최종 상태 (2026-06-19 세션 30): **완료**
  - THEQOO h2h survey **20쌍** 생성
  - 활성화 판정은 **HOLD**로 정리 (`AI_USER_ML_ENABLED=false` 유지)

## 이번 세션 변경

### 1. h2h 설문 생성기 Codex 경로로 정합화

- 파일: `.result/ai-user/scripts/build_h2h_survey.py`
- 변경:
  - `claude` 직접 실행 경로 제거
  - `run_ab_test.py`와 동일하게 `codex exec` 단일 경로 사용
  - 출력 파일은 `--output-last-message` 임시 파일로 회수
  - 설문 지시문을 기존 산출물과 동일하게 `AI가 쓴 것처럼 느껴지는 쪽` 선택 방식으로 통일

### 2. THEQOO h2h survey 재생성

- 명령:

```bash
python3 .result/ai-user/scripts/build_h2h_survey.py \
  --community THEQOO \
  --n-contexts 20 \
  --drafts 4 \
  --workers 8
```

- 결과:
  - 초안 생성: **20 contexts × 4 drafts = 80 calls**
  - 유효 contexts: **20/20**
  - rerank pair: **20쌍**
  - 산출물:
    - `.result/ai-user/blind/r13-h2h-theqoo-survey.md`
    - `.result/ai-user/blind/r13-h2h-theqoo-answers-template.json`

### 3. 생성기 실패 원인 확인

- 최초 시도는 `claude` 경로가 실제로 `claude.exe` 링크인 환경에서 `Permission denied`로 전량 실패
- 판단:
  - h2h만 예전 경로를 쓰고 있었고
  - A-B 측정은 이미 Codex bridge로 전환되어 있었으므로
  - h2h 생성기도 같은 경로로 맞추는 것이 최소 변경

## 현재 판정

- **THEQOO cond4-A**: PASS
  - `Δ_real=+0.1326`
  - snapshot **311**
- **THEQOO cond4-B**: **미측정**
  - survey는 생성됐지만 아직 사람 응답이 없음
- 따라서 전역 활성화는 **GO 아님**
  - 표현상으론 **HOLD**
  - 실질 동작은 `AI_USER_ML_ENABLED=false` 유지

## 다음 스텝

1. 오너가 `.result/ai-user/blind/r13-h2h-theqoo-survey.md` 응답
2. 가능하면 친구 응답도 추가
3. rerank 탐지율 ≤ random 탐지율이면 THEQOO cond4 최종 PASS
4. 그 결과로 `AI_USER_ML_ENABLED` 수동 활성화 go/no-go 확정
