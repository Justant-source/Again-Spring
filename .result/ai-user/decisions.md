# 결정 로그 (append-only)

> 모든 설계 결정을 시간 순 기록. 삭제 금지.

---

## 2026-06-15 — 사용자 4문항 답변 (계획 수립)

| 질문 | 답 |
|---|---|
| 연구 범위 | **Phase 0–1만** — 판별기+평가+Best-of-N+분포매칭. 생성은 Claude 유지, GPU는 학습/추론만. QLoRA/DPO는 조건부 미래. |
| 추출 범위 | **신규 GPU ML 서비스만** — orchestrator/llm/learning은 AS 그대로. orchestrator가 REST로 ML 서비스 호출. |
| GPU 위치 | **WSL 3090** (`100.115.252.61`). |
| AS 이전 | **불필요** — AS는 이미 Ubuntu(`100.81.189.92`). WSL에 AI-User만 신설. |

## 2026-06-15 — VRAM 권한 (세션 1)

- 2026-06-15 ~ 약 2026-06-22 (1주) WaggleBot VRAM(ComfyUI/LTX + fish-speech) 전부 unload 가능.
- **3090 24GB 전체 사용 가능.** Step 4 판별기 학습 시 VRAM 가드 완화.
- 창 종료 후: 다시 ~11GB 여유 기준 + WaggleBot 유휴창 필요.

## 2026-06-15 — 신규 서비스 설계 결정

| 결정 | 내용 | 근거 |
|---|---|---|
| 포트 | **8201** | ASM=8200, learning=8099, AS 콜백=8090과 구분 |
| DB | **MariaDB:11 (자체 `aiuser-ml-db`)** | ASM 선례, 커뮤니티 DB 비공유, 동시성 |
| API 토큰 (dev) | `aiuser-ml-api-token-dev-2026` (AS→ML 방향) | Step 5 AS 배포 시 orchestrator env 추가 필요 |
| 콜백 토큰 (dev) | `aiuser-ml-callback-dev-token-2026` (ML→AS 방향) | Step 5 AS 수신 엔드포인트 구현 시 필요 |
| Dockerfile 베이스 | **`pytorch/pytorch:2.5.1-cuda12.4-cudnn9-runtime`** | WSL 드라이버 610(CUDA 13.3 지원) → CUDA 12.4 컨테이너 호환 |
| torch 설치 | 베이스 이미지 사전탑재 (pyproject.toml 미포함) | CPU 버전 재설치 방지 |
| POS 태거 | Step 1에서 결정 (mecab-python3 vs kiwipiepy vs konlpy) | Step 0에서는 불필요 |
| Best-of-N 기본값 | N=4, POST 우선 적용, COMMENT는 베이스라인 AUC 확인 후 | 토큰 비용 vs 효과 균형 |
| `/score`·`/rerank` 초기 | Stub (degraded=true, 중립 0.5 반환) | Step 4 판별기 구현 전까지 graceful degradation |

## 2026-06-16 동료 검토 보정 결정

### D-08: 수집·리랭킹 분리
- **결정**: `AI_USER_ML_COLLECT`(수집)과 `AI_USER_ML_ENABLED`(리랭킹)를 독립 플래그로 분리
- **이유**: AUC 미달(0.2~0.43) 시 리랭킹을 켜면 AI스러운 초안이 winner 선택됨 → 출력 악화. 수집만 먼저 켜야 진짜 negative 코퍼스 축적 가능.
- **상태**: 적용 완료 (commit 9ee6e1d8)

### D-09: mauve-text 계기판 선행 설치
- **결정**: MAUVE 없이 생성 품질 최적화 착수 금지 (측정 먼저 원칙)
- **이유**: eval_harness._try_mauve() 이미 graceful fallback 있음, 의존성만 추가하면 자동 활성화
- **상태**: 적용 완료 (WSL commit 906ebd7)

### D-10: TSD 프롬프팅 > Style-RAG 우선순위
- **이유**: TSD는 "애초에 사람처럼 생성"(생성 사전 개입), Style-RAG는 사후 패치. 계기판(MAUVE/AUC) 없이 둘 다 블라인드 최적화 — 1·2순위 완료 후 착수.

### D-11: Korean Unsmile 사용 범위 제한
- **결정**: human 코퍼스 혼입 금지. 생성시 negative constraint/필터 전용.
- **이유**: 혼입 시 판별기가 "혐오 표현=인간"으로 오염됨.

### D-12: Phase 2/3 (QLoRA+DPO) 진입 조건 명시
- **조건**: 실제 AUC(n_ai≥30 후 재학습) 측정값 기준으로 AUC>0.75 OR MAUVE<0.80 정체 시만 진입.
- **현재**: 대기 (실제 AUC 아직 미측정)
