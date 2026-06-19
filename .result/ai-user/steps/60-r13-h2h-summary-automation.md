# Step 60 (R13-next3) — h2h 집계 자동화 + THEQOO pending 결과 파일 생성

## 상태

- Step 59에서 THEQOO h2h survey / answers template 생성까지 완료된 상태에서 진행
- 목적: 사람 응답이 들어오는 즉시 손집계 없이 THEQOO cond4-B 판정을 뽑을 수 있게 준비
- 최종 상태 (2026-06-19 세션 31): **완료**
  - h2h answers -> markdown 집계 스크립트 추가
  - THEQOO pending 결과 파일 생성

## 이번 세션 변경

### 1. h2h 집계 스크립트 추가

- 파일: `.result/ai-user/scripts/summarize_h2h_results.py`
- 역할:
  - `r13-h2h-*-answers-template.json` 읽기
  - `responses.owner` / `responses.friend` 집계
  - rerank 탐지율 vs random 탐지율 계산
  - D-68 기준 PASS / FAIL / PENDING 자동 판정
  - markdown 결과 파일 출력

### 2. answers template 입력 형식 명시

- 파일:
  - `.result/ai-user/scripts/build_h2h_survey.py`
  - `.result/ai-user/blind/r13-h2h-theqoo-answers-template.json`
- 변경:
  - `response_instructions` 필드 추가
  - 허용 choice:
    - `A`, `B`
    - `답변불가`, `판단불가`, `미응답`
  - pair key는 `1..N` 또는 `0..N-1` 둘 다 허용

### 3. THEQOO pending 결과 파일 생성

- 명령:

```bash
python3 .result/ai-user/scripts/summarize_h2h_results.py \
  --answers .result/ai-user/blind/r13-h2h-theqoo-answers-template.json
```

- 생성 파일:
  - `.result/ai-user/blind/r13-h2h-theqoo-results.md`

- 현재 상태:
  - owner: `0/20` → `PENDING`
  - friend: `0/20` → `PENDING`
  - combined: `0/40` → `PENDING`

## 의미

- 이제 다음 세션에서는 사람 응답만 JSON에 채우고 위 명령을 다시 실행하면
  THEQOO cond4-B 결과 markdown이 즉시 갱신된다.
- 즉, 남은 병목은 **응답 수집 자체**뿐이고,
  해석/계산/표 작성은 자동화된 상태다.

## 다음 스텝

1. `r13-h2h-theqoo-answers-template.json`에 오너 응답 입력
2. 가능하면 friend 응답도 입력
3. `summarize_h2h_results.py` 재실행
4. 결과가 `rerank 탐지율 ≤ random 탐지율`이면 THEQOO cond4-B PASS로 go/no-go 갱신
