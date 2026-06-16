# Step 19 (N2) — split_sentences() D-21 준수 검증 (2026-06-16)

## 결론

**배포된 `split_sentences()`(features_katfish.py:32-57)는 D-21 경계 전부 이미 구현.**
Step 10 문서의 `re.split(r'[\.\!\?]+\s+')` 기술은 **문서가 틀린 것** — 코드는 정상.
N2 = 교체 아님. D-21 경계 단위테스트 13개 추가 + DC avg_sl 재확인.

---

## 완료 기준 달성

| 항목 | 결과 |
|---|---|
| D-21 경계 단위테스트 추가 | ✅ 13개 추가 |
| pytest 전체 통과 | ✅ 13/13 PASS |
| DC avg_sl 실측 | ✅ **2.62** (57.40 버그 → 고정 확인) |
| cond3 불리언 입증 | ✅ `SPLITTER_VERIFIED=True` 근거 확보 |

---

## 추가된 단위테스트 (tests/test_features.py — TestSentenceSplitterD21)

| 테스트 | 검증 경계 | 결과 |
|---|---|---|
| test_newline_boundary | `\n` 경계 | ✅ |
| test_ellipsis_boundary | `...` 경계 | ✅ |
| test_unicode_ellipsis_boundary | `…` 경계 | ✅ |
| test_jamo_repetition_boundary | `ㅠㅠ`/`ㅋㅋ` 경계 | ✅ |
| test_emoji_boundary | 이모지 경계 | ✅ |
| test_exclamation_boundary | `!` 경계 | ✅ |
| test_question_boundary | `?` 경계 | ✅ |
| test_korean_morpheme_period_boundary | `다.`/`요.` 경계 | ✅ |
| test_len_filter_removes_single_jamo | len≤2 필터 동작 확인 | ✅ |
| test_short_expression_kept | 3자+ 표현 보존 확인 | ✅ |
| test_dc_style_no_period | 마침표 없는 DC 스타일 개행 분리 | ✅ |
| test_empty_lines_collapsed | 연속 빈 줄 단일 경계 처리 | ✅ |
| test_dc_avg_sl_reasonable | DC avg_sl < 30 (57.40 버그 방지) | ✅ |

---

## DC avg_sl 실측값

```
DC 스타일 샘플: "진짜 ㅋㅋㅋ웃기다\n남친이 어제 약속 펑크냄\n전화도 안 받고!\n..."
분리 결과 avg_sl = 2.62
```

Step 10에서 수정된 버그(57.40→7.02) 확정 재현. cond3 통과 근거 수치.

---

## 함수 스펙 (배포 코드 확인)

`app/ml/features_katfish.py:32-57`:
```python
def split_sentences(text: str) -> list[str]:
    text = re.sub(r'\n+', '\n', text)                    # 1) 개행
    text = re.sub(r'[.]{3,}|…', '\n', text)              # 2) 말줄임
    text = re.sub(r'([ㄱ-ㅎ])\1{1,}', ...)               # 3) 2자+ 자모
    text = re.sub(r'[\U0001F000-...]+ ', ...)             # 4) 이모지
    text = re.sub(r'(?<=[다요여임나죠])\. *', '.\n', text) # 5) 종결어미
    text = re.sub(r'[!?]+', ...)                          # 6) !?
    sents = [s.strip() for s in text.split('\n') if len(s.strip()) > 2]
    return sents if sents else [text.strip()]
```

모든 D-21 경계 구현 확인. Step 10 문서 오기재와 무관.

---

## WSL 커밋

- **commit**: `73f227c`
- **메시지**: `test(splitter): D-21 boundary unit tests — verify split_sentences() (N2/Step19)`
- **변경 파일**: `tests/test_features.py` (+101줄)

---

## cond3 연결 (→ Step 20/N3)

- N2 완료 → `SPLITTER_VERIFIED=True` 근거 확보
- N3에서 `app/config.py`에 `splitter_verified=True` 상수 추가 + `enable_candidates()` cond3 교체
