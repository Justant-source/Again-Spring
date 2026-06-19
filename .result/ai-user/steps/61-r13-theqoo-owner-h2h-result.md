# Step 61 (R13-next4) — THEQOO owner h2h 집계 + 전역 NO GO 확정

## 상태

- Step 59에서 THEQOO survey 생성 완료
- Step 60에서 answers -> results 자동화 완료
- 최종 상태 (2026-06-19 세션 32): **완료**
  - owner 응답을 반영해 THEQOO h2h 판정 완료
  - 전역 활성화는 **NO GO**

## 이번 세션 변경

### 1. owner 응답 JSON 반영

- 파일: `.result/ai-user/blind/r13-h2h-theqoo-answers-template.json`
- 반영:
  - owner 응답 20건 입력
  - 유효 응답 18건
  - `정답 선택 불가` 1건
  - `구별 불가` 1건

### 2. h2h 집계기 1-based key 해석 버그 수정

- 파일: `.result/ai-user/scripts/summarize_h2h_results.py`
- 버그:
  - 사람 입력 키 `1..N`이 `0..N-1`보다 늦게 해석되어 한 칸씩 밀리는 오류
- 수정:
  - 숫자 키는 **1-based 입력을 우선**
  - 그다음 0-based fallback

### 3. THEQOO h2h 최종 결과

- 파일: `.result/ai-user/blind/r13-h2h-theqoo-results.md`
- 결과:
  - owner 유효 응답: **18/20**
  - rerank 탐지: **11/18 = 61.1%**
  - random 탐지: **7/18 = 38.9%**
  - **D-68 판정: FAIL**

## 해석

- THEQOO는 cond4-A는 통과했지만 cond4-B에서 실패했다.
- 즉, real corpus 기준으로도 **리랭커가 random보다 더 AI스럽게 보이는 초안을 고르고 있음**.
- 따라서 `AI_USER_ML_ENABLED`는 계속 `false`.

## owner 피드백에서 드러난 주된 탐지 신호

1. 문장 끝 또는 중간의 뜬금없는 `헐`
2. 장난스러운 이모지 사용
3. 지나치게 추상적인/모호한 서술
4. 반대로 random draft 쪽의 자연스러운 구체 표현
   - 예: `돌려깐다`, `꼽주듯`, `공무원 시험`, `길드톡`

## 다음 스텝

1. THEQOO post-processing에서 `헐` 위치/빈도 제한
2. 이모지 사용 조건 축소 또는 제거
3. 구체 명사/상황 밀도를 높이는 prompt/rerank 기준 검토
4. 개선 후 THEQOO h2h 재생성 및 재측정
