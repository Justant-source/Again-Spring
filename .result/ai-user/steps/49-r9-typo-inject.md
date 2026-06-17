# Step 49 — R9 Track A: 결정론적 오타 주입 (injectTypos)

**세션**: 22 | **날짜**: 2026-06-17 | **상태**: ✅ 구현·테스트·dev 배포 완료

---

## 배경 / 피벗 근거

CLIEN 5/5 활성 페르소나가 DB에 `mobile_typos=true` + `consistent_errors` 완비임에도
최근 AI POST 오타 = 0 → **Haiku가 오타 지시를 조용히 무시** (R5 블라인드 100% FAIL의 직접 원인).

프롬프트 레벨 오타 주입은 **Haiku 모델 특성상 무효 확정** → `injectChosung()` 선례를 따라
`OutputSanitizer` 후처리에서 결정론적으로 주입 (LLM 준수 비의존).

---

## 구현 위치

| 파일 | 변경 내용 |
|---|---|
| `ai-user/llm/.../service/OutputSanitizer.java` | `VoiceDistribution` record 확장 + `VOICE_DIST` 갱신 + `injectTypos()` 신설 |
| `ai-user/orchestrator/.../task/ActionExecutor.java` | `appendWritingQuirks Math.min(1→2)` cap 상향 |
| `ai-user/llm/.../service/OutputSanitizerTypoTest.java` | 신규 — 7개 통계적 불변식 테스트 |

---

## 핵심 구현 — injectTypos

```
injectTypos(text, fireProb):
  1. len < 40 → skip (단문 보호)
  2. DIST_RNG.nextDouble() > fireProb → skip (≈45% 클린 유지, 인간 이봉분포 모사)
  3. 첫 줄 분리 (hook 보호)
  4. budget = 1 + nextInt(2) (1~2개)
  5. TYPO_TRANSFORMS 셔플 후 budget만큼 순차 적용
  6. firstLine + modified rest 반환
```

### T1~T8 Transform table
| ID | 변형 | 예시 |
|---|---|---|
| T1 | 됐/됬, 웬/왠 confusion | 됐어→됬어 |
| T2 | 종결 '요' 탈락 (마지막 15자 보호) | 인데요→인데 |
| T3 | 띄어쓰기 붙이기 | 조금 더→조금더 |
| T4 | 후치 조사 분리 | 진짜로→진짜 로 |
| T5 | 조사 '의'→'에' confusion | 나의→나에 |
| T6 | ㅋㅋ/ㅎㅎ 중간 행 끝 삽입 (초성체 중복 회피) | — |
| T7 | 받침 단순화 | 갔어→갓어 |
| T8 | 이중자음 오타 | 있었→있엇 |

### VOICE_DIST typoProb 설정
| voice | typoInject | typoProb | 비고 |
|---|---|---|---|
| CLIEN | true | 0.55 | chosungInject=false라 messiness 0이었음 → 반드시 켬 |
| THEQOO | true | 0.30 | 이미지 중심 → 낮게 |
| NATEPAN | true | 0.45 | |
| (기타) | true | 0.40~0.50 | |

---

## 완료 기준 ✅

- [x] 단위테스트 7개 통과 (`OutputSanitizerTypoTest`)
  - MAX_POST 불변, 첫 줄 보호, 단문 skip, UNKNOWN voice 무변
  - 100회 distinct>5 (다양성), 일부 클린 (fireProb gate)
  - CASUAL 프롬프트 키워드, CONFLICT trigger 의무
- [x] `./gradlew :ai-user:llm:test` BUILD SUCCESSFUL (35 tests)
- [x] `./gradlew :ai-user:orchestrator:test` BUILD SUCCESSFUL
- [x] e2e dev:8090 147 passed, 5 skipped
- [x] dev 배포: `againspring-llm-ai-user` healthy

---

## 다음 단계

- 자연 틱으로 신선 CONFLICT ai 축적 확인
- **blind ①**: 갈등 주제 매칭 20쌍 (인간 갈등 vs AI 갈등) → Track A 순수 문체 cond5 측정
- MAUVE 재측정 (신선분 충분 후)
