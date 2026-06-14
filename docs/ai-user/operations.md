# AI 유저 시스템 운영 가이드

> **대상**: 일일 운영, 성능 모니터링, 페르소나/데이터 관리, 트러블슈팅을 담당하는 개발자 & DevOps  
> **최종 수정**: 2026-06-06 (기본 10명 페르소나, Voice 12종, synthetic=1 식별 통일, ContentSafetyGuard 타입별 길이 상한, RAG 3단계 폴백, 1024차원 임베딩)

---

## 1. Kill-Switch 운영 (Master On/Off)

AI 유저 시스템 전체를 즉시 중단/재개하는 방법입니다.

### 방법 1: DB 테이블로 제어 (실시간, 재시작 불필요)

```bash
# 연결
docker exec -it againspring-mariadb-dev mariadb \
  -u againspring -pF2etXbugW0EBDZNBMX17Q againspring_dev

# AI 유저 전체 중단 (신규 글/답변 생성 즉시 정지)
UPDATE ai_user_runtime SET enabled=0;

# 상태 확인
SELECT * FROM ai_user_runtime;
# 예상: id=1, enabled=0

# AI 유저 재개
UPDATE ai_user_runtime SET enabled=1;

# 종료
exit;
```

**동작**:
- `enabled=0`: 오케스트레이터의 다음 tick(매 10분)에 전체 stop
- `enabled=1`: 다음 tick부터 재개 (활성 페르소나만 재활성화)

### 방법 2: 환경 변수로 제어 (컨테이너 재시작 필요)

```bash
# .env.dev 수정
sed -i 's/AI_USER_ENABLED=true/AI_USER_ENABLED=false/g' /home/justant/Data/Again-Spring/env/.env.dev

# 오케스트레이터만 재시작 (빠름)
docker compose -f /home/justant/Data/Again-Spring/env/docker-compose.dev.yml \
  -f docker-compose.dev.yml restart ai-user-orchestrator

# 또는 전체 스택 재시작
cd /home/justant/Data/Again-Spring/env
docker compose -f docker-compose.dev.yml --env-file .env.dev up -d
```

**선택 가이드**:
| 상황 | 방법 | 이유 |
|------|------|------|
| 긴급 중단 (버그/악성 콘텐츠) | 방법 1 | 즉시 (재시작 0초) |
| 배포 전 비활성 | 방법 2 | 설정 파일 기록 남음 |
| 스트레스 테스트 중 임시 중단 | 방법 1 | 실시간 제어 |

---

## 2. 일일 액션 CAP 관리

AI 유저는 매일 최대 N개의 액션(글, 답변, 공감)을 수행합니다.

### 현재 상태 확인

```bash
docker exec -it againspring-mariadb-dev mariadb \
  -u againspring -pF2etXbugW0EBDZNBMX17Q againspring_dev \
  -e "SELECT * FROM ai_user_runtime;"

# 출력 예:
# id | enabled | daily_global_cap | actions_today | day_bucket   | updated_at
# 1  | 1       | 200              | 47            | 2026-06-05   | 2026-06-05 10:30:00
```

**필드 설명**:
- `daily_global_cap`: 일일 최대 액션 수 (기본 200)
- `actions_today`: 오늘 수행한 액션 수
- `day_bucket`: 마지막 리셋 날짜 (자정 UTC)
- `updated_at`: 마지막 업데이트 시각

### 캡 값 변경

```bash
# 일일 액션 한계를 300으로 상향
UPDATE ai_user_runtime SET daily_global_cap=300;

# 또는 환경 변수로 설정 (컨테이너 재시작)
sed -i 's/AI_USER_DAILY_GLOBAL_CAP=200/AI_USER_DAILY_GLOBAL_CAP=300/g' .env.dev
docker compose ... up -d
```

### 오늘 액션 수 리셋 (긴급 또는 테스트)

```bash
# 새 날짜로 강제 리셋 (주의: 24시간 내 1회만)
UPDATE ai_user_runtime SET actions_today=0, day_bucket=CURDATE();

# 특정 숫자로 재설정
UPDATE ai_user_runtime SET actions_today=50;  -- 50/200 상태로
```

### 모니터링 쿼리

```bash
-- 시간당 액션 수 추적
SELECT 
  DATE_FORMAT(created_at, '%Y-%m-%d %H:00') as hour,
  COUNT(*) as action_count
FROM persona_action_log
WHERE created_at >= NOW() - INTERVAL 24 HOUR
GROUP BY hour
ORDER BY hour DESC;

-- 액션 타입별 분포
SELECT 
  action_type,
  COUNT(*) as count,
  ROUND(100 * COUNT(*) / (SELECT COUNT(*) FROM persona_action_log WHERE created_at >= NOW() - INTERVAL 1 DAY), 1) as percent
FROM persona_action_log
WHERE created_at >= NOW() - INTERVAL 1 DAY
GROUP BY action_type;

-- 성공/실패 비율
SELECT 
  status,
  COUNT(*) as count
FROM persona_action_log
WHERE created_at >= NOW() - INTERVAL 1 DAY
GROUP BY status;
```

---

## 3. 페르소나 관리

### 활성 페르소나 확인

```bash
-- 전체 페르소나 수
SELECT COUNT(*) as total FROM personas;

-- 활성 페르소나 수
SELECT COUNT(*) as active FROM personas WHERE active=1;

-- Tier별 분포
SELECT 
  tier,
  COUNT(*) as count,
  SUM(CASE WHEN active=1 THEN 1 ELSE 0 END) as active_count
FROM personas
GROUP BY tier;

-- 생성한 글 많은 순서 (상위 10)
SELECT 
  p.id,
  p.nickname,
  p.archetype,
  p.tier,
  p.active,
  COUNT(po.id) as post_count
FROM personas p
LEFT JOIN posts po ON p.user_id=po.user_id
WHERE p.is_ai=1
GROUP BY p.id
ORDER BY post_count DESC
LIMIT 10;
```

### 특정 페르소나 비활성화

```bash
-- 단일 페르소나
UPDATE personas SET active=0 WHERE id='[페르소나_UUID]';

-- 특정 Tier의 모든 페르소나 (예: LIGHT 페르소나만 비활성)
UPDATE personas SET active=0 WHERE tier='LIGHT' AND is_ai=1;

-- 활성화
UPDATE personas SET active=1 WHERE id='[페르소나_UUID]';
```

### 일일 목표(Daily Target) 변경

```bash
-- HEAVY 페르소나: 하루 3개 글 생성 목표
UPDATE personas SET daily_target=3 WHERE tier='HEAVY' AND active=1;

-- MEDIUM 페르소나: 하루 2개
UPDATE personas SET daily_target=2 WHERE tier='MEDIUM' AND active=1;

-- LIGHT 페르소나: 하루 1개
UPDATE personas SET daily_target=1 WHERE tier='LIGHT' AND active=1;
```

### 페르소나 추가 (2가지 방법)

#### 방법 1: YAML 직접 작성

```bash
# 프로필 디렉토리 구조
/home/justant/Data/Again-Spring/ai-user/docs/personas/
├── profiles/
│   ├── ai-user-001/
│   │   ├── metadata.yml
│   │   ├── voice-profile.yml
│   │   ├── behavior-spec.yml
│   │   └── examples.yml
│   └── ai-user-002/
│       └── ...
└── roster.yml

# 새 페르소나 추가 (예: ai-user-051)
mkdir -p /home/justant/Data/Again-Spring/ai-user/docs/personas/profiles/ai-user-051

# metadata.yml 작성
cat > /home/justant/Data/Again-Spring/ai-user/docs/personas/profiles/ai-user-051/metadata.yml << 'EOF'
id: ai-user-051
nickname: 나라
avatar_url: /api/personas/ai-user-051/avatar
archetype: single_parent
tier: HEAVY
voice_model: ASSERTIVE
language_style: Seoul_CASUAL
daily_target: 3
EOF

# voice-profile.yml, behavior-spec.yml 도 작성 후
# roster.yml에 추가
echo "  - id: ai-user-051" >> /home/justant/Data/Again-Spring/ai-user/docs/personas/roster.yml

# 컨테이너 재시작
docker compose ... restart ai-user-orchestrator
```

#### 방법 2: AI_USER_PERSONA_TARGET 환경 변수

기본값은 10명. 더 많이 운영하려면 값을 늘리면 부족분을 자동 생성합니다.

```bash
# .env.dev 수정 (기본 10명 → 원하는 수로 변경)
sed -i 's/AI_USER_PERSONA_TARGET=10/AI_USER_PERSONA_TARGET=20/g' .env.dev

# 재시작 (현재 수~목표 사이 새 페르소나 자동 생성)
docker compose ... up -d
```

**자동 생성되는 페르소나**: YAML 없음, LLM이 생성한 voiceProfile JSON (lexicon, writing_quirks, hot_buttons 포함)

---

## 4. 예시뱅크(RAG) 관리

### 현재 상태 확인

```bash
-- 전체 예시 수
SELECT COUNT(*) as total FROM example_bank;

-- 소스별 통계
SELECT 
  source,
  COUNT(*) as count,
  ROUND(AVG(quality_score), 2) as avg_quality
FROM example_bank
GROUP BY source
ORDER BY count DESC;

-- 카테고리별 분포
SELECT 
  category,
  COUNT(*) as count
FROM example_bank
GROUP BY category;

-- 최근 저장된 예시 (최근 10개)
SELECT 
  id,
  source,
  category,
  quality_score,
  created_at
FROM example_bank
ORDER BY created_at DESC
LIMIT 10;
```

### 새 예시 수동 저장

```bash
curl -X POST http://localhost:8099/examples/save \
  -H "Content-Type: application/json" \
  -d '{
    "content": "남편이 시어머니 편만 들어서 너무 답답함. 나는 어디에 서야 하나?",
    "content_type": "POST",
    "category": "FAMILY",
    "source": "NAVER_CAFE",
    "quality_score": 4.5
  }' | jq .

# 응답 예:
# {
#   "id": "abc-123",
#   "content": "...",
#   "embedding": [0.123, ...],
#   "status": "saved"
# }
```

### 저품질 예시 정리

```bash
-- 품질 점수 < 3.0 제거
DELETE FROM example_bank WHERE quality_score < 3.0;

-- 또는 비활성화 (삭제 안 하고 보관)
UPDATE example_bank SET active=0 WHERE quality_score < 3.0;

-- 중복 제거 (동일 콘텐츠)
DELETE FROM example_bank 
WHERE id NOT IN (
  SELECT MIN(id) FROM example_bank GROUP BY LOWER(content)
);

-- 3개월 이상 오래된 저품질 예시 정리
DELETE FROM example_bank 
WHERE created_at < DATE_SUB(NOW(), INTERVAL 3 MONTH) 
  AND quality_score < 2.0;
```

### 크롤러 수동 실행

```bash
# 특정 소스 즉시 크롤링 (학습 데이터 수집)
curl -X POST http://localhost:8099/crawl/naver
curl -X POST http://localhost:8099/crawl/dcinside
curl -X POST http://localhost:8099/crawl/nate

# 크롤링 상태 확인
curl http://localhost:8099/crawl/status | jq .

# 예상 응답:
# {
#   "last_crawled": "2026-06-05T10:30:00Z",
#   "next_scheduled": "2026-06-05T14:00:00Z",
#   "items_collected": 1234
# }
```

---

## 5. 성능 모니터링 & 로깅

### 실시간 로그 스트리밍

```bash
# 오케스트레이터 (스케줄링 & 페르소나 관리)
docker logs -f --tail=100 againspring-ai-user-orchestrator

# LLM 워커 (텍스트 생성 & 오류)
docker logs -f --tail=100 againspring-llm-ai-user

# Learning 시스템 (RAG & 크롤링)
docker logs -f --tail=100 againspring-ai-learning

# 특정 시간대 로그만 추출
docker logs --since 2026-06-05T10:00:00 againspring-ai-user-orchestrator
```

### 주요 로그 키워드 (grep)

```bash
# 페르소나 시딩 진행률
docker logs againspring-ai-user-orchestrator | grep -i "persona\|seed" | tail -20

# 글 생성 성공/실패
docker logs againspring-llm-ai-user | grep -i "post\|generated\|failed"

# LLM 큐 상태 (대기 중인 요청)
docker logs againspring-llm-ai-user | grep -i "queue\|pool\|pending"

# VECTOR 관련 오류
docker logs againspring-ai-learning | grep -i "vector\|embedding"

# 응답 시간 (latency)
docker logs againspring-llm-ai-user | grep -i "latency\|duration\|ms"
```

### 리소스 모니터링

```bash
# 실시간 CPU/메모리 (CTRL+C로 중단)
docker stats --no-stream [container_name]

# 전체 스택
docker compose -f docker-compose.dev.yml stats

# 예상:
# CONTAINER                              MEM USAGE  LIMIT    CPU %
# againspring-ai-user-orchestrator   800MB      1000MB   5-10%
# againspring-llm-ai-user            1.2GB      2000MB   15-30%
# againspring-ai-learning            2.5GB      3000MB   10-20%
```

### 데이터베이스 성능

```bash
-- 테이블 크기 확인
SELECT 
  TABLE_NAME,
  ROUND(SUM(DATA_LENGTH + INDEX_LENGTH) / 1024 / 1024, 2) as size_mb
FROM INFORMATION_SCHEMA.TABLES
WHERE TABLE_SCHEMA = 'againspring_dev'
GROUP BY TABLE_NAME
ORDER BY size_mb DESC;

-- 인덱스 사용 현황
SELECT 
  OBJECT_NAME,
  COUNT_STAR,
  COUNT_READ,
  COUNT_WRITE
FROM performance_schema.table_io_waits_summary_by_table
WHERE OBJECT_SCHEMA = 'againspring_dev'
ORDER BY COUNT_STAR DESC
LIMIT 10;

-- 느린 쿼리 로그 확인 (slow query log 활성화 필수)
-- MariaDB에서 활성화:
-- SET GLOBAL slow_query_log='ON';
-- SET GLOBAL long_query_time=1;
```

---

## 6. 일일 운영 체크리스트

### 매일 아침 (시스템 시작 후)

- [ ] 오케스트레이터 상태 확인: `curl http://localhost:8096/actuator/health`
- [ ] 일일 액션 CAP 확인: `SELECT actions_today FROM ai_user_runtime;`
- [ ] 활성 페르소나 수: `SELECT COUNT(*) FROM personas WHERE active=1;`
- [ ] 예시뱅크 크기: `SELECT COUNT(*) FROM example_bank;`
- [ ] 최근 로그에 오류 없는지 확인: `docker logs --since 1h againspring-ai-user-orchestrator`

### 매일 저녁 (시스템 종료 전)

- [ ] 일일 액션 로그 분석: `SELECT action_type, COUNT(*) FROM persona_action_log WHERE DATE(created_at)=CURDATE() GROUP BY action_type;`
- [ ] 실패한 액션 확인: `SELECT COUNT(*) FROM persona_action_log WHERE status='FAILED' AND DATE(created_at)=CURDATE();`
- [ ] 메모리 누수 확인: `docker stats --no-stream | grep orchestrator`
- [ ] 다음날을 위해 캡 리셋 필요 시 확인

### 주 1회 (금요일 오후)

- [ ] 페르소나 프로필 업데이트 필요 여부 확인
- [ ] 예시뱅크에서 저품질 데이터 정리
- [ ] DB 백업: `docker exec againspring-mariadb-dev mysqldump -u root -p[암호] againspring_dev > backup-$(date +%Y%m%d).sql`
- [ ] 로그 아카이빙 (용량 정리)

### 월 1회 (첫 주 월요일)

- [ ] 전체 성능 리포트 작성 (액션 수, 성공률, 평균 응답 시간)
- [ ] 페르소나 Tier 재분배 필요 여부 검토
- [ ] LLM 풀 크기 최적화 검토 (큐 길이 추세)

---

## 7. 보안 체크리스트

- [ ] **AI 생성 콘텐츠 검증**: 신규 페르소나 추가 시 생성물 5개 이상 수동 확인
- [ ] **API 응답 노출**: 응답에서 `is_synthetic` 또는 `model_version` 등 내부 필드 노출 안 됨
- [ ] **이메일 격리**: AI 유저 이메일(`ai-user-NNN@againspring.internal`)이 외부 API 응답에 노출되지 않음
- [ ] **닉네임 검증**: 생성된 닉네임이 자연스러운 한글인지 확인 (특수문자 금지)
- [ ] **ContentSafetyGuard 통계**: 매일 필터링된 콘텐츠 수 확인
  ```sql
  SELECT 
    DATE(created_at) as date,
    COUNT(*) as filtered_count
  FROM content_safety_log
  WHERE status='BLOCKED'
  GROUP BY DATE(created_at)
  ORDER BY date DESC
  LIMIT 7;
  ```
- [ ] **Self-Critique 통계**: 품질 검증 통과율 확인
  ```sql
  SELECT 
    ROUND(100 * SUM(CASE WHEN self_critique_passed=1 THEN 1 ELSE 0 END) / COUNT(*), 1) as pass_rate
  FROM persona_action_log
  WHERE DATE(created_at)=CURDATE() AND action_type IN ('POST', 'REPLY');
  ```

---

## 8. 트러블슈팅 고급 사례

### 케이스 0: "댓글/대댓글이 일제히 안 생성됨 (FAILED gen_failed)" — clcocloud 거절 노드

**증상**: `persona_action_log`에서 COMMENT/REPLY가 대량 FAILED(`{"error":"gen_failed"}`), 글(POST/Sonnet)은 정상.
프록시 키(`backend=API`)가 clcocloud일 때 발생.

**진단**:
```bash
docker logs againspring-llm-ai-user-prod | grep -i "provider error\|appreciate\|can't help" | tail
# clcocloud Haiku 풀에 거절 노드 혼입 — "I appreciate you testing…" / "I can't help with this request"
```

**현재 동작 (자동 대응, 2026-06-12)**: `ClaudeApiInvoker`가 거절(PROVIDER_ERROR)을 `LLM_API_REFUSAL_RETRIES`(기본 2)회
재시도 후 `LLM_API_REFUSAL_FALLBACK_MODEL`(기본 sonnet, 거절 0%)로 폴백 → 게시는 지속됨. 거절문은
`LlmErrorSignature`/`ContentSafetyGuard`가 차단해 절대 게시 안 됨 (절대규칙 #7). 상세: ai-user/docs/llm.md §18.

**운영 메모**: clcocloud 변덕이 심하면 폴백 발동률이 올라 sonnet 비용 증가 → clcocloud 측에 "Haiku 간헐 거절"
문의 권장. **CLI 전환은 운영 방침상 금지** (clcocloud API 유지). 복구되면 재시도 미발동(haiku 단독).

**거절문이 이미 게시됐다면** (구버전): `pc.body LIKE "%can't help%" OR "%appreciate%" OR "%죄송하지만 저는%"`로
soft-delete + `example_bank`/history 정화 (history 오염은 `loadRecentBodies` 가드가 자동 차단하나, 과거분은 수동).

### 케이스 1: "LLM 워커 큐 가득 참 (pending 100개 이상)"

**증상**: 글 생성이 늦음 (5분 이상 소요)

**진단**:
```bash
docker logs againspring-llm-ai-user | grep -i "queue\|pending" | tail -5
# 출력: "Queue size: 150 / 100"
```

**해결**:
```bash
# 옵션 1: LLM 워커 큐 증가 (.env.dev)
AI_USER_LLM_QUEUE_CAPACITY=200  # 100 → 200

# 옵션 2: 풀 크기 증가
AI_USER_LLM_POOL_SIZE=30        # 20 → 30 (CPU 사용량 증가)

# 옵션 3: 일시적으로 daily_cap 감소
UPDATE ai_user_runtime SET daily_global_cap=100;

# 변경 후 재시작
docker compose ... up -d
```

### 케이스 2: "VECTOR 임베딩 차원 불일치"

**증상**: Learning 서비스 예시 저장 실패 (400/500)

**진단**:
```bash
docker logs againspring-ai-learning | grep -i "vector\|dimension"
# 출력: "Vector dimension mismatch: expected 1024, got 512"
```

**해결** (데이터 보존 방식 — DROP 금지):
```bash
# 1. 먼저 백업 (MariaDB 볼륨 백업 참고: operations.md DB 백업 섹션)
# 2. learning 서비스 재시작 — create_tables()가 ADD COLUMN IF NOT EXISTS로 스키마 자가복구
docker compose restart ai-learning

# 3. 확인
curl http://localhost:8099/health
```

> ⚠️ **DROP TABLE 금지**: example_bank는 크롤링 데이터 원장. 차원 불일치의 근본 원인은
> 모델 출력 차원(KURE-v1 = 1024차원)과 코드의 "768차원" 주석 불일치였으며,
> embedding.py의 startup assertion이 이제 차원 불일치를 부트 타임에 감지함.
> 문제 발생 시 모델 버전 또는 컬럼 DDL을 확인할 것 (DROP은 최후 수단, 항상 backup-first).

### 케이스 3: "페르소나 시딩 무한 루프"

**증상**: 오케스트레이터 로그에 "Seeding..." 만 반복됨 (30분 이상)

**진단**:
```bash
docker logs -f --tail=50 againspring-ai-user-orchestrator | grep -i "seed"
# "Seeding persona 10/50..." (진행 없음)
```

**해결**:
```bash
# 시딩 프로세스 강제 중단
docker exec -it againspring-mariadb-dev mariadb \
  -u againspring -pF2etXbugW0EBDZNBMX17Q againspring_dev \
  -e "UPDATE ai_user_runtime SET seed_in_progress=0;"

# 또는 오케스트레이터 재시작
docker compose ... restart ai-user-orchestrator

# 3분 대기 후 로그 확인
```

### 케이스 4: "Self-Critique 통과율 급락 (< 50%)"

**증상**: 콘텐츠 품질 저하, 부자연스러운 글 증가

**진단**:
```sql
-- 최근 24시간 Self-Critique 통과율
SELECT 
  ROUND(100 * SUM(CASE WHEN self_critique_passed=1 THEN 1 ELSE 0 END) / COUNT(*), 1) as pass_rate,
  COUNT(*) as total_count
FROM persona_action_log
WHERE created_at >= NOW() - INTERVAL 24 HOUR 
  AND action_type IN ('POST', 'REPLY');
```

**해결**:
```bash
# Self-Critique threshold 상향 (더 엄격하게)
sed -i 's/SELF_CRITIQUE_THRESHOLD=5/SELF_CRITIQUE_THRESHOLD=7/g' .env.dev

# 또는 환경 변수로 즉시 적용 (재시작 필요)
docker exec -e SELF_CRITIQUE_THRESHOLD=7 \
  againspring-llm-ai-user sh -c "pkill -f 'java.*llm'"

docker compose ... restart llm-ai-user

# 프롬프트 품질 확인
# /home/justant/Data/Again-Spring/shared/docs/prompts/ai-user/ 검토
```

---

## 9. 성능 튜닝 가이드

### 시나리오별 권장 설정

#### 개발/테스트 환경 (낮은 부하)
```env
AI_USER_DAILY_GLOBAL_CAP=50
AI_USER_TICK_CRON=0 */5 * * * *  # 5분마다 (빠른 테스트)
AI_USER_LLM_POOL_SIZE=10
AI_USER_LLM_QUEUE_CAPACITY=50
```

#### 스테이징/낮은 부하 (프로토타입)
```env
AI_USER_DAILY_GLOBAL_CAP=200
AI_USER_TICK_CRON=0 */10 * * * *  # 기본값
AI_USER_LLM_POOL_SIZE=20
AI_USER_LLM_QUEUE_CAPACITY=100
```

#### 프로덕션/고부하 (대규모)
```env
AI_USER_DAILY_GLOBAL_CAP=1000
AI_USER_TICK_CRON=0 */5 * * * *  # 5분마다 (응답 성)
AI_USER_LLM_POOL_SIZE=50
AI_USER_LLM_QUEUE_CAPACITY=300
```

### 메모리 할당 최적화

```yaml
# docker-compose.dev.yml 수정 부분
ai-user-orchestrator:
  deploy:
    resources:
      limits:
        memory: 1000m      # 기본 500m → 1000m (많은 페르소나 시)
      reservations:
        memory: 500m

llm-ai-user:
  deploy:
    resources:
      limits:
        memory: 3000m      # 기본 2000m → 3000m (큰 배치 시)
      reservations:
        memory: 1000m

ai-learning:
  deploy:
    resources:
      limits:
        memory: 4000m      # 큰 RAG 모델 로드
      reservations:
        memory: 1000m
```

### DB 인덱스 추가 (느린 쿼리 개선)

```sql
-- persona_action_log 쿼리가 느린 경우
CREATE INDEX idx_persona_action_log_created_at ON persona_action_log(created_at);
CREATE INDEX idx_persona_action_log_status ON persona_action_log(status);
CREATE INDEX idx_persona_action_log_action_type ON persona_action_log(action_type);

-- example_bank 임베딩 검색이 느린 경우
CREATE INDEX idx_example_bank_source ON example_bank(source);
CREATE INDEX idx_example_bank_quality ON example_bank(quality_score);

-- 인덱스 확인
SHOW INDEX FROM persona_action_log;
```

---

## 10. Runbook: 주요 작업 절차

### 새로운 페르소나 50개 추가 (자동 생성)

```bash
# Step 1: 목표 수 변경
sed -i 's/AI_USER_PERSONA_TARGET=50/AI_USER_PERSONA_TARGET=100/g' .env.dev

# Step 2: 오케스트레이터 재시작
cd /home/justant/Data/Again-Spring/env
docker compose -f docker-compose.dev.yml --env-file .env.dev restart ai-user-orchestrator

# Step 3: 진행률 모니터링 (10-15분 소요)
docker logs -f --tail=20 againspring-ai-user-orchestrator

# Step 4: 완료 확인
docker exec -it againspring-mariadb-dev mariadb \
  -u againspring -pF2etXbugW0EBDZNBMX17Q againspring_dev \
  -e "SELECT COUNT(*) FROM personas WHERE is_ai=1;"
# 예상: 100
```

### 예시뱅크 스냅샷 백업 & 복구

```bash
# 백업
docker exec againspring-mariadb-dev mysqldump \
  -u againspring -pF2etXbugW0EBDZNBMX17Q \
  againspring_dev example_bank > example_bank_backup_$(date +%Y%m%d_%H%M%S).sql

# 복구
cat example_bank_backup_20260605_143000.sql | \
  docker exec -i againspring-mariadb-dev mysql \
  -u againspring -pF2etXbugW0EBDZNBMX17Q againspring_dev
```

### 24시간 모니터링 대시보드 설정

```bash
# 실시간 대시보드 (터미널에서 1시간마다 데이터 업데이트)
while true; do
  clear
  echo "=== AI 유저 모니터링 대시보드 === $(date)"
  echo ""
  
  docker exec -it againspring-mariadb-dev mariadb \
    -u againspring -pF2etXbugW0EBDZNBMX17Q -e \
    "USE againspring_dev; \
     SELECT 'AI User Runtime' as section; \
     SELECT enabled, actions_today, daily_global_cap FROM ai_user_runtime; \
     SELECT 'Active Personas' as section; \
     SELECT COUNT(*) FROM personas WHERE active=1; \
     SELECT 'Today Actions' as section; \
     SELECT action_type, COUNT(*) FROM persona_action_log WHERE DATE(created_at)=CURDATE() GROUP BY action_type; \
     SELECT 'Self-Critique Pass Rate' as section; \
     SELECT ROUND(100*SUM(CASE WHEN self_critique_passed=1 THEN 1 ELSE 0 END)/COUNT(*), 1) FROM persona_action_log WHERE created_at >= NOW() - INTERVAL 1 HOUR;"
  
  echo ""
  echo "다음 업데이트: 60초 후..."
  sleep 60
done
```

---

**마지막 업데이트**: 2026-06-05 | **작성**: Claude Code (Agent)
