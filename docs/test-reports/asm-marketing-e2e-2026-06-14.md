# ASM 마케팅 E2E 검증 보고서

**날짜**: 2026-06-14  
**범위**: ASM(Again-Spring-Marketing) ↔ AS(Again-Spring) 통합 전체 — dev 환경  
**기준**: `/goal` P0~P6 체크리스트  

---

## 결과 요약

| 단계 | 항목 수 | PASS | FAIL | BUG→FIX |
|------|---------|------|------|---------|
| P0 환경 확인 | 5 | 5 | 0 | 0 |
| P1 ASM 단독 계약 테스트 | 10 | 10 | 0 | 0 |
| P2 자격증명 CRUD | 6 | 6 | 0 | 0 |
| P3 AS→ASM 통합 | 8 | 8 | 0 | 2 |
| P4 아티팩트·드리프트 | 5 | 5 | 0 | 1 |
| P5 오류/엣지케이스 | 6 | 6 | 0 | 0 |
| P6 FE 스모크 | 4 | 4 | 0 | 0 |
| **합계** | **44** | **44** | **0** | **3** |

---

## P0 — 환경 확인

| 항목 | 결과 |
|------|------|
| ASM `GET /health` → 200 | PASS |
| ASM DB 연결 정상 (asm-db 컨테이너) | PASS |
| AS dev BE `/api/health` → 200 | PASS |
| ASM Bearer 토큰 인증 작동 | PASS |
| Callback 경로 ASM→AS 204 수신 | PASS |

---

## P1 — ASM 단독 계약 테스트 (pytest 147/147 PASS)

ASM 컨테이너(`again-spring-marketing-asm-1`) 내 pytest 전체 실행 결과.

| 테스트 모듈 | 항목 | 결과 |
|------------|------|------|
| test_callback_emit | 6 | PASS |
| test_copy_generator | 3 | PASS (pytest-asyncio 누락 → 설치 후 통과) |
| test_credentials | 11 | PASS |
| test_credentials_routes | 5 | PASS |
| test_gpu_lock | 3 | PASS |
| test_health | 1 | PASS |
| test_job_idempotency | 5 | PASS |
| test_job_state_partial | 9 | PASS |
| test_oauth_state | 4 | PASS |
| test_public_url | 8 | PASS |
| test_script | 26 | PASS |
| test_test_publish_routes | 7 | PASS |
| test_threads_inherit | 6 | PASS |
| 기타 | 53 | PASS |
| **합계** | **147** | **147 PASS / 0 FAIL** |

> **환경 이슈(AS 포함 아님)**: 호스트에서 직접 pytest 실행 시 `pydantic_settings`·`fastapi` 없어 수집 오류. 컨테이너 내 실행이 올바른 방법.

---

## P2 — 자격증명 CRUD

`GET/PUT/DELETE /api/admin/marketing/credentials` — AS Admin API 경유, ASM에 프록시.

| 항목 | 기대 | 실제 | 결과 |
|------|------|------|------|
| GET /credentials → 7개 플랫폼 열거 | 200 + 7 platforms | 200 + 7 | PASS |
| PUT /credentials/x → 저장 | 200 + masked | 200 + masked | PASS |
| PUT /credentials/x (빈 secret) → 보존 | 기존 값 유지 | 유지 | PASS |
| GET /credentials/{platform} → 405 | 405 Method Not Allowed | 405 | PASS (의도된 설계) |
| PUT /credentials/unsupported → 400 | 400 | 400 | PASS |
| DELETE /credentials/x → 204 (idempotent) | 204 | 204 | PASS |

---

## P3 — AS→ASM 통합

| 항목 | 기대 | 실제 | 결과 |
|------|------|------|------|
| POST /api/admin/marketing/jobs (정상) | 201 + QUEUED | 201 + QUEUED | PASS |
| GET /api/admin/marketing/jobs → 목록 | 200 + array | 200 + array | PASS |
| GET /api/admin/marketing/jobs/{id} | 200 + job | 200 + job | PASS |
| ASM_ENABLED=false → 잡 생성 | 503 ASM_UNAVAILABLE | 503 ✓ | PASS (BUG-1 수정) |
| ASM 컨테이너 다운 → 잡 생성 | 503 | 503 ✓ | PASS (BUG-1 수정) |
| 중복 활성 잡 → 잡 생성 | 400 INVALID_STATE | 400 ✓ | PASS |
| 터미널 잡 후 신규 잡 생성 허용 | 201 | 201 ✓ | PASS |
| Idempotency-Key 헤더 전달 확인 | 중복 키→기존 잡 반환 | ✓ | PASS |

### BUG-1: ASM 비활성/다운 시 500 반환

- **원인**: `createJob()`에 `isEnabled()` 체크 없음 + `GlobalExceptionHandler`에 `AsmUnavailableException` 핸들러 없음 → generic 500
- **수정**:
  1. `MarketingJobService.createJob()` 첫 줄에 `!asmProperties.isEnabled()` 체크 추가
  2. `GlobalExceptionHandler`에 `@ExceptionHandler(AsmUnavailableException.class)` → 503 핸들러 추가
- **영향**: AS dev BE Docker 이미지 재빌드 + 재배포 완료
- **테스트 수정**: `MarketingJobServiceTest` 3개 테스트에 `when(asmProperties.isEnabled()).thenReturn(true)` 추가 (mock 기본값 false 문제)

---

## P4 — 아티팩트·드리프트

| 항목 | 기대 | 실제 | 결과 |
|------|------|------|------|
| 잡 완료 후 artifacts DB 저장 형태 | `{"x":{"upload":"...","card_01":"..."}}` | ✓ | PASS |
| GET /jobs/{id} artifacts 필드 | per-platform dict | ✓ | PASS |
| 아티팩트 프록시 다운로드 | 200 + 파일 바이트 | ✓ | PASS |
| 콜백 payload artifacts 구조 | per-platform dict | ✓ (BUG-2 수정) | PASS |
| STALE 전이 검증 (poll_fail_count≥5) | STALE→FAILED | DB jobs id=4,6 확인 ✓ | PASS |

### BUG-2: 콜백 payload `artifacts: {}` 하드코딩

- **원인**: `app/worker/callback.py`가 `"artifacts": {}` 하드코딩 — 실제 아티팩트 미포함
- **수정**: `job.artifacts` 관계에서 `{platform}__{filename}` 명명 규칙으로 per-platform dict 빌드 (`routes_jobs.py`의 `_to_job_view()`와 동일 로직)
- **영향**: ASM Docker 이미지 재빌드(`docker compose up -d --build asm`) 완료
- **검증**: job id=11 콜백 수신 → AS DB `{"x":{"upload":"...","card_01":"..."}}` 확인 ✓

---

## P5 — 오류/엣지케이스

| 항목 | 기대 | 실제 | 결과 |
|------|------|------|------|
| READY 아닌 잡 publish → 400/409 | 400 | 400 ✓ | PASS |
| 존재하지 않는 잡 GET → 404 | 404 | 404 ✓ | PASS |
| 잘못된 Bearer 토큰 → 401 | 401 | 401 ✓ | PASS |
| 콜백 토큰 불일치 → 401 | 401 | 401 ✓ | PASS |
| 없는 플랫폼 credential PUT → 400 | 400 | 400 ✓ | PASS |
| 존재하지 않는 아티팩트 GET → 404 | 404 | 404 ✓ | PASS |

---

## P6 — FE 스모크 (dev.againspring.net/admin/marketing)

| 항목 | 결과 |
|------|------|
| 마케팅 잡 목록 페이지 로드 | PASS |
| 플랫폼 계정 탭 → 7개 플랫폼 표시 | PASS |
| 새 마케팅 잡 다이얼로그 열기 | PASS |
| 게시 버튼 → READY 아닌 잡에 400 처리 | PASS |

---

## 버그·수정 이력

| ID | 발견 단계 | 설명 | 수정 파일 | 상태 |
|----|----------|------|----------|------|
| BUG-1 | P3 | ASM 비활성/다운 시 500 반환 (503 기대) | `MarketingJobService.java`, `GlobalExceptionHandler.java`, `MarketingJobServiceTest.java` | ✅ 수정완료 |
| BUG-2 | P4 | 콜백 payload `artifacts: {}` 하드코딩 | `app/worker/callback.py` (ASM) | ✅ 수정완료 |
| ENV-1 | P1 | ASM pytest가 `pytest-asyncio` 누락으로 3건 수집 실패 | 컨테이너 내 `pip install pytest-asyncio` | ✅ 해결 |

---

## 미구현 항목 (M-series — 범위 외, HALT 아님)

| ID | 항목 | 비고 |
|----|------|------|
| M1 | YouTube OAuth 실 토큰 교환 E2E | Google 인증 화면 필요, 자동화 불가 |
| M2 | 실 소셜 게시 검증 (social-poster) | 목킹만 허용 (guardrail) |
| M3 | TTS/Video/Render 파이프라인 실 출력 검증 | GPU 의존, 목킹 |
| M4 | 공개 서명 URL 만료 TTL 실시간 검증 | 긴 대기 필요 |
| M5 | STALE 24h→FAILED 전이 실시간 대기 | 24h 대기 불가 |
| M6 | 방문 이벤트(visit_events) 트래픽 집계 E2E | 실 트래픽 의존 |

---

## HALT 항목

없음. 모든 구현된 기능이 계약대로 동작함.

---

## 테스트 스위트 최종 상태

| 스위트 | 결과 |
|--------|------|
| AS BE 단위 테스트 (`./gradlew test`) | **BUILD SUCCESSFUL** (5 tasks) |
| ASM pytest (컨테이너 내) | **147 passed / 0 failed** |
| AS BE E2E (dev:8090, e2e-realbe) | 별도 게이트 — prod 배포 전 실행 필요 |

---

**작성**: Claude Code (Agent)  
**환경**: dev (dev.againspring.net / ASM port 8200)  
**prod 배포**: 이번 세션에서 실행되지 않음 (guardrail 준수)
