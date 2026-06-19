# Step 63 (R13-next6) — h2h/ab 하네스 런타임 정합화

## 상태

- Step 62에서 THEQOO 후처리 1차 축소 패치 적용
- 그런데 기존 `build_h2h_survey.py` / `run_ab_test.py`는 `codex exec` 직출력만 사용해서
  `PromptAssembler + OutputSanitizer` 경로를 타지 않았음
- 최종 상태 (2026-06-19 세션 34): **완료**
  - 하네스는 runtime 우선으로 수정 완료
  - 실제 재측정은 `:8092` 서비스 복구가 선결

## 이번 세션 변경

### 1. h2h survey 생성기 런타임 우선화

- 파일: `.result/ai-user/scripts/build_h2h_survey.py`
- 변경:
  - `LLM_AI_USER_URL` 기반 `/generate/post` 호출 추가
  - `--generator runtime|cli` 옵션 추가
  - 기본값은 `runtime`
  - runtime 실패 시 기존 `codex exec` fallback 유지

### 2. A-B 테스트 드라이버 런타임 우선화

- 파일: `.result/ai-user/scripts/run_ab_test.py`
- 변경:
  - 동일하게 `/generate/post` 우선 경로 추가
  - `--generator runtime|cli` 옵션 추가
  - direct CLI는 fallback 유지

### 3. 생성 힌트 정리

- 두 스크립트 모두 `THEQOO` trait를
  - 기존: `감탄사(ㅋㅋ/헐/와 등), 이모지 가끔`
  - 변경: `짧고 구어체, 반말 위주, 공감형`
- 목적:
  - runtime fallback이 direct CLI로 내려가더라도 `헐`/이모지 유도를 덜 세게 만든다

### 4. 검증 결과

- `python3 -m py_compile`:
  - `build_h2h_survey.py` ✅
  - `run_ab_test.py` ✅
- `LLM_AI_USER_URL` 헬스체크:
  - `http://localhost:8092/actuator/health` → connection refused
  - `http://127.0.0.1:8092/actuator/health` → connection refused
  - `http://100.115.252.61:8092/actuator/health` → connection refused
- Java 테스트:
  - `./gradlew test --tests ...` 실행 시 `JAVA_HOME is not set`로 로컬 검증 불가

## 해석

- 이제 측정 하네스 자체는 THEQOO 후처리 패치를 반영할 수 있는 구조가 됐다.
- 다만 실제 LLM 서비스가 내려가 있어서 새 survey/ab를 지금 바로 재생성할 수는 없다.
- 따라서 현재 남은 실질적 블로커는 **코드가 아니라 실행 환경**이다.

## 다음 스텝

1. `LLM_AI_USER_URL(:8092)` 복구
2. `build_h2h_survey.py --generator runtime`로 THEQOO survey 재생성
3. owner 재응답 반영 후 cond4-B 재측정
