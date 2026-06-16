# Step 8 완료 기록 — 수집/리랭킹 분리 + eval 계기판

**날짜**: 2026-06-16  
**세션**: 6  
**상태**: ✅ 완료 (66/66 orchestrator 테스트 + dev/prod 배포 + MAUVE_OK)

---

## 교착 원인 (동료 검토 진단)

`AiUserMlClient.pushNegative()`가 `rerank()`와 **동일한 `enabled` 플래그**로 게이트됨.
`AI_USER_ML_ENABLED=false`(기본)라 게시된 AI 텍스트가 `/corpus/ingest`로 미전송 → `n_ai` 영구 0.

단순히 `enabled=true`로 켜면 안 됨: 현재 AUC 0.2~0.43(synthetic negative 반사 학습)이라
리랭킹이 **가장 AI스러운 초안을 winner로 선택** → 출력 악화. 수집·리랭킹 분리 필요.

## 해결

`AI_USER_ML_COLLECT` 신규 플래그로 수집을 리랭킹에서 독립 제어.

| 플래그 | 역할 | 현재값 |
|---|---|---|
| `AI_USER_ML_COLLECT` | pushNegative() 게이트 (수집) | **true** (dev+prod) |
| `AI_USER_ML_ENABLED` | rerank() 게이트 (리랭킹·Best-of-N) | false (AUC≥0.55 전까지) |

## AS 변경 (commit 9ee6e1d8)

| 파일 | 변경 |
|---|---|
| `AiUserMlClient.java` | `collect` @Value 필드, pushNegative() 게이트 변경, isCollectEnabled() 추가 |
| `application.yml` | `ai-user-ml.collect: ${AI_USER_ML_COLLECT:false}` 추가 |
| `docker-compose.dev.yml` | orchestrator env에 `AI_USER_ML_*` 5종 추가 (기존 미주입) |
| `docker-compose.prod.yml` | 동일 |
| `AiUserMlClientTest.java` | collect 분리 검증 3종 추가, 기존 1종 갱신 → 66/66 |
| `docs/env/environment-variables.md` | `AI_USER_ML_COLLECT` 항목 추가 |

## WSL 변경 (commit 906ebd7)

- `pyproject.toml`: `mauve-text>=0.4` 추가
- Docker 재빌드 → `import mauve` MAUVE_OK

## 배포 확인

```
# prod orchestrator env
AI_USER_ML_COLLECT=true   ✅
AI_USER_ML_ENABLED=false  ✅
AI_USER_ML_BASE_URL=http://100.115.252.61:8201  ✅

# prod health
GET /api/health → { "status": "UP" }  ✅

# prod DB 백업
backups/prod-backup-20260616-090114.sql (71MB)  ✅
```

## 이후 예상 흐름

1. prod 봇 tick 시 글/댓글 게시 → `pushNegative()` 호출 → ML `/corpus/ingest`
2. `GET /metrics/readiness`에서 `n_ai` 상승 시작
3. n_ai ≥ 30/커뮤니티 → `retrain_loop` (6h 주기) 자동 `/train` 트리거
4. 재학습 후 AUC ≥ 0.55 → `🎯 READINESS` 로그 → 수동 `AI_USER_ML_ENABLED=true` 활성화

## MAUVE 계기판

AI 샘플 축적 후 `POST /eval/baseline` 재실행 시 `eval_harness._try_mauve()`가
자동으로 MAUVE 점수 계산 (현재 null → 실제값으로 전환 예정).

## 다음 구체 작업 (동료 조언 2·3순위)

- **2순위 (파이프 검증 후)**: n_ai 상승 확인 → 시딩 가속으로 n_ai≥30 도달 가속
- **3순위 (계기판 켜진 후)**: TSD 프롬프팅 (CAT-LLM §2.2) > Style-RAG 순서로 생성 품질 개선
- **저우선**: Korean Unsmile — human 코퍼스 혼입 금지, 생성시 negative constraint 전용
- **대기**: Phase 2/3 QLoRA+DPO — AUC>0.75 or MAUVE<0.80 정체 시에만 진입
