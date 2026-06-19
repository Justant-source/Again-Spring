# Step 70 (R14) — activation gate correction

## 왜 수정했나

- 기존 다음 단계 요약은 큰 방향은 맞았지만, 선행 블로커와 판정 기준 두 가지가 느슨했다.
- 특히:
  1. `:8092` 복구는 측정 이전의 **host 접근 문제**
  2. cond5는 `rerank vs random`인 h2h와 별개로 **community별 AI-vs-human 게이트**

## 이번 세션에서 정정한 내용

### 1. 진짜 크리티컬 패스

- 현재 셸:
  - `ssh` 실행 권한 거부
  - `docker` 바이너리 없음
- 따라서 `docker compose ... up -d llm-ai-user`는 여기서 실행할 수 없다.
- R14의 1번은 "THEQOO 측정"이 아니라 **runtime host에 닿는 것**이다.

### 2. 공식 runtime 측정 조건

- 앞으로 공식 cond4-B/AB는 아래를 모두 만족해야 한다.
  - `:8092` health `UP`
  - `--generator runtime --strict-runtime`
  - 생성 메타 `cli_fallbacks=0`
- 이를 위해 `build_h2h_survey.py`와 `run_ab_test.py`에:
  - `--strict-runtime`
  - draft source accounting (`runtime` / `cli` / `failed`)
  - 결과 메타 기록
  를 추가했다.

### 3. runtime은 먼저 "배관 검증"

- 현재 코드 진실:
  - `ai-user/llm/.../InvokerRouter.java`는 `backend=API`도 무시하고 Codex CLI bridge만 사용한다.
- 따라서 역사적 Sonnet/API 메모는 현재 runtime 동작을 보장하지 않는다.
- runtime h2h 전에 아래를 확인해야 한다.
  1. 실제 backend/model 로그
  2. 4 draft 생성 여부
  3. `/rerank` winner/random 분기 여부

### 4. THEQOO는 owner-only 얇은 근거

- v2 PASS는 owner 단독, 유효 12/20이었다.
- runtime 재측정은 owner + friend를 기본으로 하고, 무효 응답률도 같이 본다.
- `summarize_h2h_results.py`에 invalid count/rate를 추가했다.

### 5. cond5 정정

- `CLIEN blind② 40%`는 CLIEN 전용 PASS 근거다.
- NATEPAN/THEQOO는 fresh community-specific cond5 PASS가 없다.
- 따라서 현재 전역 상태는 `GO candidate`가 아니라 **HOLD**다.

### 6. selective gate 기본 규칙

- `benefit_pp = random 탐지율 - rerank 탐지율`
- 기본 규칙:
  - `benefit_pp >= 5%p` → selective gate(B) 후보
  - 그 미만 → 기본값 C(계속 OFF)
  - A(전역 ON)는 예외적 선택

## 다음 스텝

1. dev host 접근 확보
2. `:8092` 복구 + health 확인
3. runtime strict 배관 검증
4. THEQOO runtime h2h owner+friend
5. CLIEN/NATEPAN runtime h2h 재확인
6. NATEPAN/THEQOO fresh cond5
7. B/C 중심 최종 권고
