# 자동화 테스트 스크립트 — 빠른 시작

LLM 호출 취소 메커니즘을 검증하는 Python 기반 자동화 테스트.  
**V2 재설계**: 24개 → 10개 시나리오, 3개 활성 페르소나, `--reset` 플래그로 ~43% 토큰 절감.

**상태**: V2 (2026-05-01) — mechanism 3 + flow 4 + validation 3 = 10개 시나리오

## 빠른 실행

```bash
cd backend/scripts/test-automation

# 1. 설치
pip install -r requirements.txt

# 2. test 계정 데이터 리셋 후 취소 메커니즘 검증 (★ 핵심)
python3 run.py --reset --scenario SC-CANCEL-FAST

# 3. 전체 10개 실행 (리셋 포함, ~5분)
python3 run.py --all --reset --max-concurrent 3

# 4. 결과 확인
cat results/$(ls -t results/ | head -1)/summary.json | jq
```

## 사전 요구사항

- Python 3.11+
- dev BE 실행: `https://dev.againspring.net` 또는 `http://localhost:8090`
- 테스트 계정 로드: test1~test10@again.com (BE 시작 시 SeedDataLoader 자동 생성)

## 시나리오 구조 (10개)

| 카테고리 | ID | 설명 | 페르소나 | LLM 호출 |
|---|---|---|---|---|
| mechanism | SC-CANCEL-FAST | 1초 간격 2개 → 통합 응답 1개 | test1 | 1 |
| mechanism | SC-CANCEL-BURST | 0.5초 간격 3개 → 2회 취소 | test1 | 1 |
| mechanism | SC-CANCEL-DUO | Duo A→B, 마지막(B)에게 응답 | test1+test2 | 1 |
| flow | SC-FLOW-SOLO | Solo 3턴 정상 대화 | test1 | ≥2 |
| flow | SC-FLOW-DUO-WELCOME | B join 후 환영 메시지 수신 | test1+test2 | 2 |
| flow | SC-FLOW-DUO-CHAT | Duo 양방향 각 2턴 | test1+test2 | ≥2 |
| flow | SC-FLOW-FINALIZE | A 종료→B 합의→COMPLETED | test1+test2 | ≥1+리포트 |
| validation | SC-VALID-EMPTY | 빈 메시지 → 400 | test1 | 0 |
| validation | SC-VALID-CRISIS | 위기 키워드 → 409 | test3 | 0 |
| validation | SC-VALID-LIMIT | 4번째 세션 → 429 | test1 | 0 |

예상 LLM 호출 합산: **약 16~18회** (이전 V1: ~30회 → 43% 절감)

## 활성 페르소나 (3개)

| 계정 | 닉네임 | 역할 |
|---|---|---|
| test1@again.com | 서영 | Solo 메인, Duo A |
| test2@again.com | 지훈 | Duo B |
| test3@again.com | 수민 | 위기 시나리오 (우울 톤) |

test4~test10: dev DB 보존, 수동 검증용 (자동화 미사용)

## --reset 플래그

실행 전 test 계정(test%@again.com)의 sessions/messages/reports를 일괄 삭제.  
SC-VALID-LIMIT처럼 세션 수에 의존하는 시나리오는 반드시 `--reset`과 함께 실행해야 함.

```bash
# reset만 실행
python3 -c "import asyncio; from runner.reset import reset_dev_test_data; asyncio.run(reset_dev_test_data())"

# reset 후 단일 시나리오
python3 run.py --reset --scenario SC-VALID-LIMIT
```

## 핵심 명령

```bash
# 단일 시나리오
python3 run.py --scenario SC-CANCEL-FAST

# 복수 시나리오
python3 run.py --scenarios SC-CANCEL-FAST,SC-CANCEL-BURST,SC-CANCEL-DUO

# 전체 (리셋 포함)
python3 run.py --all --reset --max-concurrent 3

# 특정 페르소나만
python3 run.py --scenario SC-FLOW-SOLO --persona test1@again.com
```

## 결과 저장 위치

`results/{timestamp}/`
- `summary.json` — 전체 통과율, mechanism/flow/validation 카테고리별 성적
- `SC-*_*.json` — 시나리오별 상세 이벤트 로그

## 아카이브 (기존 SC01~SC24)

`scenarios/archive/` 에 보존됨 (normal/, cancellation/, exception/ 서브폴더).  
참조 목적으로 유지하나 `run.py`에는 포함되지 않음.

## 주의사항

- Prod URL 금지 (`config.py` + `runner/reset.py` 이중 보호)
- SC-VALID-LIMIT은 반드시 `--reset` 플래그와 함께 실행
- LLM 응답 자연어 → 완벽 매칭 불가 (WARNING 레벨 사용)
- 상세 가이드: `backend/docs/test-automation.md`
