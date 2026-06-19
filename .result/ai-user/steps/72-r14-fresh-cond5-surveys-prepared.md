# Step 72 (R14) — fresh cond5 surveys prepared

## 무엇을 했나

- community별 fresh cond5 설문을 미리 생성했다.
  - `NATEPAN`
    - `.result/ai-user/blind/r14-cond5-natepan-survey.md`
    - `.result/ai-user/blind/r14-cond5-natepan-answers-template.json`
    - `.result/ai-user/blind/r14-cond5-natepan-results.md`
  - `THEQOO`
    - `.result/ai-user/blind/r14-cond5-theqoo-survey.md`
    - `.result/ai-user/blind/r14-cond5-theqoo-answers-template.json`
    - `.result/ai-user/blind/r14-cond5-theqoo-results.md`

## 검증

- `build_cond5_blind.py --fetch-export` 실제 endpoint로 생성 성공
- `summarize_cond5_results.py` empty-response 상태에서 `PENDING` results 생성 성공

## 중요 제약

- `/corpus/export/blind` 응답에는 현재 source id 메타가 없다.
  - `human_with_meta=0`
  - `ai_with_meta=0`
- 따라서 `used-corpus-ids.json`을 넘겨도 이번 fresh cond5 세트에서는
  **완전한 중복 방지**가 불가능하다.

## 의미

- 사람 응답을 받는 준비는 끝났다.
- 하지만 최종 cond5 해석에는 "export metadata gap"을 꼭 붙여야 한다.

## 다음 스텝

1. owner 응답
2. friend 응답
3. `summarize_cond5_results.py` 재실행
4. cond5 PASS/FAIL을 community별 표에 반영
