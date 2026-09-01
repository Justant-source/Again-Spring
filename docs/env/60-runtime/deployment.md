# 배포 절차

> ⚠️ **PROD 배포 절대 규칙**: 사용자가 명시적으로 "prod에 배포해줘"라고 요청한 경우에만 prod를 배포한다.
>
> **dev/prod 완전 격리**: 일상 배포·수동 검증·e2e는 **dev(:8090)만**. prod(:8091)에서 e2e·직접 반영 금지.
> `prod-dev-sync` = **5분 콘텐츠** + **24h full**. **dev LLM 금지(L3)**.

## 배포는 `scripts/deploy.sh`로만 한다 — compose 직접 기동 금지

`curl /api/health` 한 줄만으로 "배포 후 검증"을 삼던 과거 절차는 실패했다: 이 엔드포인트는
liveness only(상수 `status=UP`, DB조차 안 봄)라 **DB가 죽어도, 계측이 죽어도 200을 반환**한다
(사고 경위: `docs/_active/deploy-verification.md`). 그리고 배포와 검증이 별개 명령이라 검증
단계를 통째로 건너뛸 수 있는 구조였다.

`scripts/deploy.sh`는 이 두 문제를 함께 없앤다 — compose 기동과 검증을 **한 프로세스로 묶어
분리 불가능**하게 만든다:

```bash
scripts/deploy.sh dev                 # base+dev 기동 → /api/health/deep 대기 → verify-deploy.sh dev
scripts/deploy.sh prod --i-mean-it    # (명시 지시 시만) mariadb-prod 백업 → base+prod 기동 → 헬스대기 → verify-deploy.sh prod
```

- **`/api/health`** = liveness probe. DB 등 어떤 컴포넌트도 확인하지 않는다 — 배포 검증에 쓰지 마라.
- **`/api/health/deep`** = readiness probe. DB `SELECT 1`을 실행하고 실패 시 **503**을 반환한다.
  `scripts/deploy.sh`는 compose 기동 후 이 엔드포인트가 200을 반환할 때까지 타임아웃을 걸고 대기한다.
- 대기 후 **`scripts/verify-deploy.sh <env>`를 자동 실행**한다 — 방문 계측 필드 매핑, 세션 키 채움,
  UTM 귀속, 빌드 주입값, 백그라운드 파이프라인 등 실물 데이터 검증 항목 전체(상세: 동 문서 §4).
  이 스크립트가 실패하면 `deploy.sh`도 exit 1로 실패한다 — "compose는 떴는데 검증은 안 했다"는
  상태를 만들 수 없다.
- prod 경로는 기본 거부다. `--i-mean-it` 플래그(또는 대화형 `yes` 확인) 없이는 진행하지 않고,
  진행해도 **compose 기동 전에 mariadb-prod 백업을 먼저** 수행한다. e2e는 절대 실행하지 않는다
  (`RUN_E2E`가 켜져 있으면 prod 경로에서 조기 차단).

## 표준 흐름
1. base 스택 확인 (`scripts/deploy.sh`가 자동으로 `docker compose up`)
2. local unit / build
3. **`scripts/deploy.sh dev`** — dev(:8090) 기동 + 헬스대기 + 실물 검증 자동 실행
4. e2e-realbe (`E2E_BASE_URL=http://localhost:8090`)
5. 명시적 prod 지시가 없으면 여기서 종료
6. (지시 시) **`scripts/deploy.sh prod --i-mean-it`** — DB 백업 + prod(:8091) 기동 + 헬스대기 + 실물 검증 자동 실행
7. ai-user 관련이면 shared ai-user 재배포
8. commit & push (`main`)


prod는 반드시 `main` 기준으로만 배포한다. **prod 배포 전 dev e2e 전체 통과가 전제**다.

## 1단계: dev 배포 (기본 작업면)

```bash
scripts/deploy.sh dev
```

server-dev는 compose에서 `SPRING_FLYWAY_ENABLED=true` · `SPRING_JPA_HIBERNATE_DDL_AUTO=none` ·
`LLM_ENABLED=false` · **againspring(LLM) 네트워크 미연결(L3)** 로 올린다.
(local `bootRun`의 `application-dev.yml`은 편집용으로 별도.)

## 2단계: e2e 검증 (dev:8090)

`scripts/deploy.sh dev`가 이미 `/api/health/deep` + `verify-deploy.sh dev`로 실물 검증을 마친
상태다. 그 위에 e2e를 추가로 통과시킨다:

```bash
cd frontend
E2E_BASE_URL=http://localhost:8090 npm run test:e2e:realbe
```

## 3단계: prod 배포 (명시 지시 시만)

```bash
scripts/deploy.sh prod --i-mean-it
```

DB 백업(`/home/justant/backups/prod-<timestamp>.sql`) → base+prod 스택 기동 →
`/api/health/deep` 대기 → `scripts/verify-deploy.sh prod` 순서로 한 번에 실행되며,
어느 단계든 실패하면 exit 1로 중단한다. 백업만 별도로 다시 뜨고 싶다면:

```bash
BACKUP_DIR=/home/justant/backups
mkdir -p "$BACKUP_DIR"
docker exec againspring-mariadb-prod sh -c \
  'mariadb-dump -uroot -p"$MARIADB_ROOT_PASSWORD" --single-transaction --routines "$MARIADB_DATABASE"' \
  > "$BACKUP_DIR/prod-$(date +%Y%m%d-%H%M%S).sql"
```

## 4단계: shared ai-user / prod-dev-sync

```bash
cd env
docker compose -f docker-compose.ai-user.yml --env-file .env.ai-user up -d --build
docker logs -f againspring-prod-dev-sync
```

- `prod-dev-sync`: 기동 시 full+content → **5분 콘텐츠** (`SYNC_CONTENT_CRON`) + **매일 full** (`SYNC_CRON`)
- `ai-user-orchestrator-dev`: 기본 미기동 (`profiles: [ai-user-dev]`, `AI_USER_DEV_ENABLED=false`)
- 네트워크: sync만 `againspring-prod` + `againspring-dev` (유일한 교차 쓰기 경로)
- backend-dev는 `againspring`(LLM) 네트워크에 **연결하지 않음** (L3)

## prod 사전 체크리스트

- [ ] local unit / lint / build 통과
- [ ] **`scripts/deploy.sh dev` 성공** (기동 + `/api/health/deep` + `verify-deploy.sh dev` 전부 통과)
- [ ] **e2e-realbe (`localhost:8090`) 전체 통과**
- [ ] 명시적 prod 배포 지시 확인
- [ ] **`scripts/deploy.sh prod --i-mean-it` 성공** (백업 + 기동 + `/api/health/deep` + `verify-deploy.sh prod` 전부 통과)
- [ ] `main` push 완료

## nginx 접근 로그 — 위치·보존·조회

**배경**: 로그가 docker stdout에만 남아 로그 드라이버 순환으로 18일치만 존재했다. 유입 개선 효과를
주 단위로 비교하려면 최소 분기 단위 보존이 필요해 파일로 영속화했다.

- 경로(호스트): `env/logs/nginx/prod/{access,error}.log` · `env/logs/nginx/dev/{access,error}.log`
- 컨테이너 내부: `/var/log/nginx/` (nginx-prod/-dev 서비스에 bind mount)
- 포맷: nginx 기본 `main` — `$remote_addr`(Cloudflare 뒤에서도 실 IP, `real_ip_header CF-Connecting-IP`) ·
  `$http_referer` · `$http_user_agent` · `$http_x_forwarded_for` 포함
- 보존: **90일**, 매일 회전 + gzip 압축 (`rotate 90` = daily × 90일)
- 회전 방식: **호스트 cron + logrotate** (사이드카 컨테이너 아님). 이유:
  이 프로젝트는 이미 justant 사용자 crontab으로 주기 작업을 운영 중이고(`nightly-ai-user-batch.sh` 등),
  호스트에 logrotate가 이미 설치돼 있어 새 이미지/서비스 없이 설정 파일 하나로 끝난다.
  회전(rename)·압축·삭제는 디렉토리 쓰기 권한만 있으면 되므로(파일 소유권 불필요) 컨테이너가
  root로 로그 파일을 만들어도 문제없다. nginx에 "새 로그 파일 열기"만 `docker kill -s USR1`로 알리면
  되므로 sudo/root cron이 필요 없다. 설정·근거 상세 주석: `env/nginx/logrotate.conf`.
- 적용에 필요한 단계 (이 저장소 밖 작업 — 사용자가 직접 실행):
  1. `docker compose -f docker-compose.dev.yml --env-file .env.dev up -d` / prod 동일 — compose에
     볼륨 항목이 추가됐으므로 설정 diff가 감지되어 자동으로 nginx 컨테이너가 재생성된다.
     (⚠️ 참고: `nginx/*.conf` **내용만** 고친 경우는 다르다 — 단일 파일 mount는 compose가 변경을
     감지하지 못해 `--force-recreate`가 필요하다. 이번 볼륨 추가 건은 해당 없음.)
  2. `crontab -e`로 justant 크론에 `env/nginx/logrotate.conf` 상단 주석의 한 줄 등록
  3. `mkdir -p ~/.local/state` (logrotate 상태 파일용, root 불필요)
- 조회: `tail -f env/logs/nginx/prod/access.log` 또는 `docker exec againspring-nginx-prod tail -f /var/log/nginx/access.log`
- gitignore: `env/logs/`가 재귀적으로 이미 커버 (과거 17MB DB 덤프 커밋 사고 재발 방지 목적으로 `.gitignore`에 주석 명시)

## 환경 격리 원칙

- **검증·e2e면** = dev (`:8090`, `mariadb-dev`) — **LLM 토큰 0 (L3)**
- **운영면** = prod (`:8091`, `mariadb-prod`) — 명시 배포만 · AI 생성 SoT
- 서로의 DB에 직접 쓰지 않는다. 예외는 `prod-dev-sync`뿐
- e2e는 `:8090`만 (E3 — prod URL이면 스크립트/설정/DB 가드 실패)
