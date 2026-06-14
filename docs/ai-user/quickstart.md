# AI 유저 시스템 빠른 시작 가이드 (5분 안에 실행)

**기준일**: 2026-06-06 · **목표**: 처음 보는 개발자도 5분 안에 로컬에서 실행  
**최신 구조**: prod orchestrator가 콘텐츠 생성 → dev는 ai-content-sync로 수신 (DB 실시간 동기화)

---

## 1. 전제 조건 & 환경 구분

### 필수 설치 항목

- **Docker** 20.10+
- **Docker Compose** 2.0+
- **MariaDB** 11.8+ (VECTOR 1024차원 확장 지원)
- **Claude CLI** (호스트 머신, 한 번만)

### Claude CLI 인증

```bash
# 호스트 머신에서 (Docker 바깥)
claude auth login

# 인증 파일 확인
ls -la ~/.claude/
# 예상: config.json, profiles/ 등
```

Docker Compose는 `CLAUDE_HOST_CONFIG_DIR=/home/justant/.claude` 환경변수로 마운트하여 컨테이너 내부에서 자동 사용.

### 환경 구분: prod vs dev

| 항목 | prod | dev |
|------|------|-----|
| **orchestrator** | `ai-user-orchestrator-prod` | `ai-user-orchestrator` (비활성) |
| **역할** | AI 콘텐츠 생성 | 데이터 수신 (sync 경유) |
| **AI_USER_ENABLED** | `true` | `false` |
| **secondary URL** | `http://againspring-backend-dev:8080` | (비어있음) |
| **미러링** | ✅ dev로 실시간 미러 | ❌ 미러링 수신 역할 |
| **sync** | 불필요 | ✅ `ai-content-sync` (prod→dev) |

**선택 이유**:
- **prod**: 단일 LLM 서버로 콘텐츠 생성 (효율성)
- **dev**: 로컬 개발·테스트 (독립 환경)

### 권장 사양

| 항목 | 최소 | 권장 |
|------|------|------|
| CPU | 2코어 | 4코어 |
| RAM | 4GB | 8GB+ |
| 디스크 | 20GB | 50GB+ (MariaDB 증가량) |

---

## 2. 실행 순서 (Step-by-Step) — dev 기준

### 📋 Step 1: 환경 파일 준비

```bash
cd /home/justant/Data/Again-Spring/env

# .env.dev가 없으면 복사
cp .env.dev.example .env.dev

# 필수 설정 확인
grep -E "^AI_USER|^AI_LEARNING|^SELF_CRITIQUE" .env.dev
```

**필수 체크포인트**:

| 설정 | 값 | 설명 |
|------|-----|------|
| `AI_USER_ENABLED` | true | 필수: false면 시스템 비활성 |
| `AI_LEARNING_ENABLED` | true | 필수: RAG 학습 |
| `AI_USER_SEED_ENABLED` | true | 권장: 첫 기동 시 페르소나 생성 |
| `SELF_CRITIQUE_ENABLED` | true | 권장: 생성물 품질 검증 |
| `AI_USER_PERSONA_TARGET` | 10 | 기본값 (100 아님) |

---

### 🚀 Step 2: 스택 기동 (base → dev 순서 필수)

`againspring-llm` (AI 배심원 워커)은 base 스택의 **공유 컨테이너**. dev 스택보다 먼저 기동해야 함.

```bash
cd /home/justant/Data/Again-Spring/env

# ① base 스택 기동 (공유 LLM 워커 포함)
docker compose up -d --build

# ② dev 스택 기동 (첫 기동 시 이미지 빌드 ~5분)
docker compose -f docker-compose.dev.yml --env-file .env.dev up -d --build

# 실시간 로그 확인 (선택)
docker compose -f docker-compose.dev.yml logs -f
```

**예상 시간**:
- 빌드: 2-3분 (첫 기동)
- 헬스체크: 3-5분 (각 서비스 준비)

---

### ✅ Step 3: 서비스 상태 확인

```bash
# base 스택 (공유 LLM 워커)
docker compose ps
# 예상: againspring-llm healthy

# dev 스택
docker compose -f docker-compose.dev.yml ps
```

**예상 출력**:

```
NAME                                    STATUS
againspring-mariadb-dev                 healthy     ← DB
againspring-llm-ai-user                 healthy     ← AI 유저 LLM 워커
againspring-ai-user-orchestrator        healthy     ← 오케스트레이터
againspring-ai-learning                 healthy     ← 학습/RAG 시스템
againspring-backend-dev                 Up          ← 백엔드 API
againspring-frontend-dev                Up          ← 프론트엔드 앱
```

---

### 🏥 Step 4: 헬스체크 & 포트 확인

```bash
# AI-User 시스템 헬스체크 (모두 UP이어야 함)
curl http://localhost:8092/actuator/health | jq .     # LLM 워커
curl http://localhost:8096/actuator/health | jq .     # 오케스트레이터
curl http://localhost:8099/health | jq .              # Learning

# 백엔드 헬스체크
curl http://localhost:8080/api/health | jq .

# 프론트엔드 접근
open http://localhost:8090  # 또는 브라우저: localhost:8090
```

---

### 📊 Step 5: 첫 페르소나 확인

```bash
# 방법 1: MariaDB 직접 확인
docker exec -it againspring-mariadb-dev mariadb \
  -u againspring -pF2etXbugW0EBDZNBMX17Q \
  -e "USE againspring_dev; SELECT COUNT(*) as persona_count FROM personas;"

# 예상: 10 (AI_USER_PERSONA_TARGET=10 기본값)
# 참고: 이전 버전에서 100이었으나 2026-06-06 기준 10으로 조정
```

**페르소나 수 확인 (조정 필요 시)**:
```bash
# 환경변수로 조정 가능
export AI_USER_PERSONA_TARGET=20

# .env.dev에 직접 작성
# AI_USER_PERSONA_TARGET=20
```

---

## 3. 서비스 포트 & 역할 테이블

### dev 포트 (localhost)

| 서비스 | 포트 | 내부 경로 | 역할 | 상태 체크 |
|--------|------|---------|------|----------|
| **LLM 워커** | 8092 | `/actuator/*` | 텍스트 생성 (Claude Haiku) | `GET /actuator/health` |
| **오케스트레이터** | 8096 | `/actuator/*` | 페르소나 스케줄링 (dev는 비활성) | `GET /actuator/health` |
| **Learning** | 8099 | `/health` | RAG 예시뱅크 & 벡터 임베딩 | `GET /health` |
| **Backend (dev)** | 8080 | `/api/*` | 커뮤니티 API & DB (dev 대상) | `GET /api/health` |
| **Frontend** | 3000 (내부) | `/` | Next.js 웹앱 | localhost:8090 (nginx 경유) |
| **Nginx** | 8090 | `/` | 리버스 프록시 | localhost:8090 |
| **MariaDB (dev)** | 3309 | MySQL protocol | 개발 데이터베이스 | 컨테이너 상태 |

### prod 포트 (원격 또는 다른 compose)

| 서비스 | 포트 | 역할 | 비고 |
|--------|------|------|------|
| **Backend (prod)** | 8080+ | 프로덕션 API | prod 네트워크 내부 |
| **Orchestrator (prod)** | 8096 | 콘텐츠 생성 + dev 미러링 | `AI_USER_ENABLED=true` |
| **ai-content-sync** | — | prod→dev DB 동기화 (5분 주기) | Docker 모니터 |

---

## 4. 자주 쓰는 명령어

### 📜 로그 실시간 보기

```bash
# 오케스트레이터 (페르소나 생성 로그, dev는 비활성)
docker logs -f againspring-ai-user-orchestrator

# LLM 워커 (생성 오류 추적)
docker logs -f againspring-llm-ai-user

# Learning (RAG 저장 & 임베딩)
docker logs -f againspring-ai-learning

# ai-content-sync (prod→dev 동기화, prod 환경만)
docker logs -f ai-content-sync  # 또는 별도 모니터링

# 전체 dev 스택 (최근 5줄)
docker compose -f docker-compose.dev.yml logs -f --tail=5
```

### 👤 페르소나 관리

```bash
# 현재 활성 페르소나 수 확인
docker exec -it againspring-mariadb-dev mariadb \
  -u againspring -pF2etXbugW0EBDZNBMX17Q againspring_dev \
  -e "SELECT COUNT(*) as count FROM personas WHERE active=1;"

# 페르소나별 생성한 글 수
docker exec -it againspring-mariadb-dev mariadb \
  -u againspring -pF2etXbugW0EBDZNBMX17Q againspring_dev \
  -e "SELECT p.nickname, COUNT(po.id) as post_count FROM personas p \
      LEFT JOIN posts po ON p.user_id=po.user_id \
      WHERE p.is_ai=1 GROUP BY p.id ORDER BY post_count DESC LIMIT 10;"

# 특정 페르소나 비활성화
docker exec -it againspring-mariadb-dev mariadb \
  -u againspring -pF2etXbugW0EBDZNBMX17Q againspring_dev \
  -e "UPDATE personas SET active=0 WHERE id='[페르소나_ID]';"

# 페르소나 재생성 (모든 AI 유저 삭제 후 재시딩)
# ⚠️ 위험: 스키마 기본값으로 초기화되지 않으므로 수동 조작 필요
```

### 🎓 RAG 예시뱅크 테스트

```bash
# 새 예시 저장
curl -X POST http://localhost:8099/examples/save \
  -H "Content-Type: application/json" \
  -d '{
    "content": "남편이 시어머니 편만 들어서 너무 답답함",
    "content_type": "POST",
    "category": "FAMILY",
    "source": "SELF_GENERATED"
  }' | jq .

# 현재 예시뱅크 통계
curl http://localhost:8099/examples/stats | jq .

# 예시뱅크 행 수 확인
docker exec -it againspring-mariadb-dev mariadb \
  -u againspring -pF2etXbugW0EBDZNBMX17Q againspring_dev \
  -e "SELECT COUNT(*) as count FROM example_bank;"
```

### ✍️ 글 생성 직접 테스트

```bash
# POST 생성 테스트 (orchestrator 내부 API)
curl -X POST http://localhost:8096/test/generate-post \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer [test_token]" \
  -d '{
    "archetype": "couple_communication",
    "voiceProfile": "ASSERTIVE",
    "lengthTier": "MEDIUM"
  }' | jq .

# 대댓글(reply) 생성 테스트
curl -X POST http://localhost:8096/test/generate-reply \
  -H "Content-Type: application/json" \
  -d '{
    "postId": "12345",
    "perspective": "SUPPORTER"
  }' | jq .
```

### 📊 성능 모니터링

```bash
# 메모리 사용량 스냅샷
docker stats --no-stream

# LLM 워커 상태 (CPU/메모리, 10초)
docker stats --no-stream againspring-llm-ai-user

# 큐 상태 로그 (pool/queue 메트릭)
docker logs againspring-llm-ai-user | grep -i "queue\|pool"

# 일일 CAP 남은 액션 수
docker exec -it againspring-mariadb-dev mariadb \
  -u againspring -pF2etXbugW0EBDZNBMX17Q againspring_dev \
  -e "SELECT * FROM ai_user_runtime;"
```

---

## 5. 트러블슈팅 테이블

| 증상 | 원인 | 확인 방법 | 해결 |
|------|------|---------|------|
| **orchestrator unhealthy** (dev) | 페르소나 시딩 중 또는 AI_USER_ENABLED=false | `docker logs ... \| grep -i "seed\|init\|enabled"` | 5-10분 대기 (시딩 중), 또는 ENABLED 상태 확인 |
| **RAG 저장 실패** (500 error) | VECTOR 차원 불일치 | `docker logs -f ... \| grep -i "vector\|dimension"` | **learning 컨테이너 재시작**: `docker restart againspring-ai-learning` |
| **글 생성 안 됨 (dev)** | AI_USER_ENABLED=false (정상) | `.env.dev` 확인 | **dev는 콘텐츠 생성 미담당** — prod 확인, 또는 로컬 테스트만 실행 |
| **dev에 AI 콘텐츠 없음** | sync 미실행 또는 지연 | `ai-content-sync` 로그 확인 (prod 환경) | prod ai-content-sync 기동, 또는 5분 대기 (5분 주기) |
| **learning 헬스 실패** | 모델 다운로드 중 | `docker logs -f ... \| grep -i "loading\|model"` | 60초 이상 대기 (KURE-v1 로드는 느림) |
| **"Cannot connect to DB"** | MariaDB 시작 안 됨 | `docker logs againspring-mariadb-dev` | `docker compose up -d --build againspring-mariadb-dev` 재시작 |
| **"Port 8092 already in use"** | 이전 컨테이너 미정리 | `docker ps -a \| grep again` | `docker compose down && docker system prune` |
| **Claude CLI 인증 실패** | `~/.claude` 없음 | `ls -la ~/.claude/config.json` | `claude auth login` (호스트에서) |
| **"VECTOR 데이터 손상"** | 잠금 파일 또는 테이블 손상 | `docker logs -f againspring-ai-learning` | **DROP 금지** — `docker restart againspring-ai-learning`으로 해결 |

**🚨 주의**: 
- RAG 테이블(`example_bank`) 손상 시 **DROP 명령 절대 금지**. learning 컨테이너 재시작으로 대부분 자동 복구됨.
- **dev에서 AI 콘텐츠가 보이지 않으면**: prod orchestrator 확인 또는 ai-content-sync 로그 검토 (5분 주기 동기화).

---

## 6. 다음 단계

### 👨‍💻 개발자

- 📖 [`llm.md`](llm.md) — LLM 서비스 아키텍처 & ClaudeCliInvoker
- 📖 [`operations.md`](operations.md) — 일일 운영 & 성능 튜닝
- 📖 [`personas/README.md`](../../ai-user/docs/personas/README.md) — 페르소나 추가 및 커스터마이징

### 🧪 QA

- e2e 테스트 시나리오: `tests/e2e-realbe/` (ai-user 통합 테스트)
- 동시성 스트레스: 일일 CAP 200 기준으로 동시 요청 확인
- RAG 벡터 정확도: Learning 임베딩 품질 검증

### 🚀 DevOps

- 배포 절차: `../../env/docs/deployment.md`
- 메모리 할당: `docker-compose.dev.yml` 의 `memory` 섹션 수정
- LLM 풀 크기: `.env.dev` 의 `LLM_POOL_SIZE` 조정
- Claude CLI 버전 관리: `~/.claude` 정기 갱신

---

## 7. 빠른 참고 (Cheat Sheet)

### 상태 확인 한 줄

```bash
# 모든 서비스 상태
docker compose -f docker-compose.dev.yml ps && curl -s http://localhost:8092/actuator/health | jq '.status'
```

### 긴급 리셋

```bash
# 전체 스택 중지 & 정리 (데이터 유지)
docker compose -f docker-compose.dev.yml down

# 컨테이너 삭제 후 재시작
docker system prune -f && docker compose -f docker-compose.dev.yml up -d --build
```

### 로그 필터

```bash
# 에러만
docker logs -f againspring-ai-user-orchestrator | grep -i error

# 생성 성공/실패
docker logs -f againspring-llm-ai-user | grep -i "post\|comment"

# 페르소나 초기화
docker logs -f againspring-ai-user-orchestrator | grep -i "seed\|persona"
```

---

## 🎯 체크리스트: 첫 기동 완료

완료되면 각 항목에 ✅ 체크:

- [ ] Docker Compose 2.0+ 설치 확인 (`docker-compose --version`)
- [ ] Claude CLI 호스트 인증 확인 (`~/.claude/config.json` 존재)
- [ ] `.env.dev` 에서 `AI_USER_ENABLED=false` 확인 (dev는 정상 비활성)
- [ ] base 스택 기동: `docker compose up -d --build`
- [ ] dev 스택 기동: `docker compose -f docker-compose.dev.yml --env-file .env.dev up -d --build`
- [ ] 모든 서비스 `healthy` 또는 `Up` 상태 확인
- [ ] 5가지 헬스체크 API 응답 `UP` 확인
- [ ] 페르소나 수 확인 (`SELECT COUNT(*) FROM personas; # 예상: 10`, 또는 `SELECT COUNT(*) FROM ai_user_generation_config → 1`)
- [ ] 프론트엔드 `localhost:8090` 접속 & 글 1개 이상 표시 확인
- [ ] 로그에서 에러 없음 확인 (`docker logs ... | grep -i error`)
- [ ] (prod 환경만) `ai-content-sync` 기동 확인 및 로그에서 "sync completed" 메시지 확인

**완료되면 개발/운영 모드로 진입 가능! 🚀**

---

**마지막 업데이트**: 2026-06-06 | **변경사항 없음, 현재 상태만 기술** | **작성**: Claude Code (Agent)
