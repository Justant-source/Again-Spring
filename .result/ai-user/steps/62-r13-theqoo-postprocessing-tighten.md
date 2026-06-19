# Step 62 (R13-next5) — THEQOO post-processing 축소 패치

## 상태

- Step 61에서 THEQOO owner h2h가 **FAIL**로 확정됨
- owner 이유 다수가 `헐`/유니코드 이모지/과한 감탄사 주입을 직접 지목
- 최종 상태 (2026-06-19 세션 33): **완료**
  - THEQOO 후처리 1차 축소 패치 적용
  - 다음 단계는 재생성 + 재측정

## 이번 세션 변경

### 1. THEQOO 전용 cleanup 추가

- 파일: `ai-user/llm/src/main/java/com/againspring/aiuser/llm/service/OutputSanitizer.java`
- 변경:
  - trailing standalone `헐` 제거
  - 문장 부호 뒤에 뜬 standalone `헐` 제거
  - 문장 중간의 어색한 `헐 제가/헐 내가/...` 패턴 정리
  - 유니코드 이모지 제거 (`😥`, `🥲` 등)
- 특징:
  - 분포 샘플링이 발동하지 않아도 cleanup은 항상 실행

### 2. THEQOO 주입 후보 축소

- 파일: `ai-user/llm/src/main/java/com/againspring/aiuser/llm/service/OutputSanitizer.java`
- 변경 전:
  - `["헐", "ㅠㅠ", "ㄷㄷ", "개공감"]`
  - `sample_prob=0.75`
- 변경 후:
  - `["ㅠㅠ", "ㄷㄷ", "그니까", "ㅇㅇ"]`
  - `sample_prob=0.60`

### 3. 문서/테스트 동기화

- 파일: `ai-user/docs/personas/voices.yml`
  - THEQOO `post_processing` 블록을 런타임 값과 동기화
- 파일: `ai-user/llm/src/test/java/com/againspring/aiuser/llm/service/OutputSanitizerHrTest.java`
  - trailing `헐` 제거 테스트 추가
  - standalone `헐` + 유니코드 이모지 제거 테스트 추가

## 해석

- 이번 수정은 rerank나 prompt보다 더 좁은 레버다.
- owner가 실제로 집어낸 신호를 먼저 없애서 다음 h2h에서 원인을 분리하려는 목적이다.
- 따라서 아직 전역 판정은 바뀌지 않는다. 현재 상태는 여전히 **NO GO**다.

## 다음 스텝

1. THEQOO draft 재생성
2. h2h survey 재생성
3. owner 재응답 반영 후 cond4-B 재측정
4. 여전히 FAIL이면 rerank/prompt 쪽으로 확대
