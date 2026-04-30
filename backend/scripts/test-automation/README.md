# 다시봄 자동화 테스트

Python 기반 자동화 테스트 스크립트. 10명의 페르소나로 8개 시나리오(일반·취소·예외)를 병렬 실행합니다.

## 필요 조건

- Python 3.11+
- dev BE 실행 중 (https://dev.againspring.net 또는 http://localhost:8090)
- 시드 페르소나 로드됨 (test1~10@again.com)

## 설치

```bash
cd backend/scripts/test-automation
pip install -r requirements.txt
```

## 실행

### 단일 시나리오 (dry run)

```bash
python run.py --scenario SC13 --persona test1@again.com
```

### 취소 시나리오만 (★ 핵심 검증)

```bash
python run.py --scenarios SC13,SC14,SC15
```

### 전체 실행 (5개 병렬)

```bash
python run.py --all --max-concurrent 5
```

### 커스텀 병렬도 지정

```bash
python run.py --all --max-concurrent 3
```

## 결과

`results/{timestamp}/` 디렉토리에 저장됨:

- `summary.json` — 전체 요약 (통과율, 카테고리별 성적)
- `SC*_*.json` — 시나리오별 상세 로그 (이벤트·검증·에러)

## 시나리오 목록

### 일반 (Normal) — 5턴 이상 채팅

- **SC01**: 시댁 부엌일 갈등 (test1, test3, test5)
- **SC02**: 양육관 충돌 (test5)
- **SC03**: 친구 절교 후회 (test3)

### 취소 (Cancellation) — 메시지 폭주 → 통합 응답

- **SC13**: 연속 2개 메시지 1초 간격 (test1, test2, test10) ★ 가장 중요
- **SC14**: 5개 빠른 연속 (test10, test2)
- **SC15**: Duo 양쪽 동시 (test1 USER_A, test2 USER_B)

### 예외 (Exception)

- **SC19**: 위기 키워드 — 즉시 차단 (test9)
- **SC22**: 종료 권유 거부 후 계속 채팅 (test1)

## 검증 규칙

각 시나리오는 다음을 검증합니다:

1. **mediator_response_count** — AI 응답 수 (expected / expected_min)
2. **response_contains_context_from_both** — 양쪽 맥락 통합 검증 (키워드)
3. **cancellation_log_present** — 메시지 취소 발생 여부
4. **no_avoidance_pattern** — 회피 문구 없음 (AI 신뢰성)
5. **response_to_user_b** — Duo 시나리오: B에게 응답
6. **session_status** — 세션 상태 확인

## 문제 해결

### "prod URL 사용 금지" 에러

`config.py`에서 `DEV_URL` 확인. 반드시 `https://dev.againspring.net` 또는 `http://localhost:8090`.

### "Login failed for test*@again.com"

- 시드 페르소나가 DB에 로드되지 않았거나 삭제된 경우
- 해결: `backend/gradlew bootRun`으로 BE 재시작 (SeedDataLoader 자동 실행)

### "No mediator response found"

- LLM 호출 실패 또는 응답 생성 안 됨
- BE 로그 확인: `docker compose logs backend-dev`

## 개발자 가이드

### 새 시나리오 추가

1. `scenarios/{normal|cancellation|exception}/scXX_*.py` 파일 생성
2. `SCENARIO_SCXX` 딕셔너리 정의
3. `run.py`에 임포트 + `ALL_SCENARIOS` 추가
4. `SCENARIO_PERSONA_MAP` 업데이트 (이메일 매핑)

### 검증 규칙 확장

`runner/verifier.py` → `_check_rule()` 메서드에 새 rule type 추가.

## 주의사항

- **prod URL 금지**: DEV_URL이 prod 환경이면 즉시 중단
- **금지어 스캔**: 시나리오 메시지에 금지어 없는지 확인
- **마크다운 금지**: 스크립트 내 마크다운 처리 미지원 (일반 텍스트만)
