# ASM 서버 설치·운영 가이드

## 서버 정보

| 항목 | 값 |
|---|---|
| 호스트 | WSL 서버 (`desktop-b6vjvg9-wsl`) |
| Tailscale IP | `100.115.252.61` |
| 포트 | `8200` |
| 프로젝트 경로 | `/home/justant/Data/Again-Spring-Marketing/` |
| GPU | NVIDIA RTX 3090 24GB |

## SSH 접속 (AS → ASM)

AS 호스트(Tailscale `100.81.189.92`)에서 **암호 없이**:

```bash
ssh justant@100.115.252.61
cd ~/Data/Again-Spring-Marketing
```

마케팅 작업 초점(미공개): **X / `x_thread`만**. 상세: ASM `CLAUDE.md` · AS `docs/shared/marketing/x-thread-strategy.md`.

---

## 초기 설치

### 1. MariaDB 준비 (WSL 로컬 MariaDB 사용)

WSL 머신의 MariaDB에 `asm` 데이터베이스와 유저를 생성합니다:

```sql
CREATE DATABASE IF NOT EXISTS asm CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'asm'@'%' IDENTIFIED BY 'asm_dev';
GRANT ALL PRIVILEGES ON asm.* TO 'asm'@'%';
FLUSH PRIVILEGES;
```

### 2. `.env` 파일 설정

`/home/justant/Data/Again-Spring-Marketing/.env`:

```env
ASM_DATABASE_URL=mysql+pymysql://asm:asm_dev@host.docker.internal:3306/asm
ASM_BEARER_TOKEN=asm-dev-token-change-in-prod
FISH_SPEECH_URL=http://127.0.0.1:8080
COMFYUI_URL=http://127.0.0.1:8188
CLAUDE_BIN=claude
CLAUDE_CONFIG_DIR=/home/justant/.claude
MEDIA_DIR=/home/justant/Data/Again-Spring-Marketing/data
GPU_STAGE_CONCURRENCY=1
AUTO_PUBLISH=false
ASM_PORT=8200
ASM_HOST=0.0.0.0
```

> **주의**: Docker 컨테이너에서 호스트 MariaDB에 접근하려면 `127.0.0.1` 대신 `host.docker.internal`을 사용해야 합니다.

### 3. docker-compose.yml

```yaml
services:
  asm:
    build: .
    ports:
      - "8200:8200"
    env_file: .env
    volumes:
      - ./data:/app/data
      - ~/.claude:/root/.claude:ro
    restart: unless-stopped
    extra_hosts:
      - "host.docker.internal:host-gateway"
```

`extra_hosts`의 `host-gateway`가 Docker의 호스트 IP를 `host.docker.internal`로 매핑합니다.

---

## 시작 / 재시작

```bash
cd /home/justant/Data/Again-Spring-Marketing

# 빌드 후 시작
docker compose up -d --build

# 재시작만
docker compose restart

# 로그 확인
docker compose logs -f --tail=50

# 헬스 체크
curl http://localhost:8200/api/v1/health
```

---

## 헬스 체크 응답

```json
{ "status": "ok", "version": "0.1.0" }
```

---

## DB 마이그레이션

ASM은 Alembic을 사용합니다. 최초 기동 시 자동으로 마이그레이션이 실행됩니다.

수동 실행이 필요한 경우:

```bash
cd /home/justant/Data/Again-Spring-Marketing
docker compose exec asm alembic upgrade head
```

---

## 파이프라인 단계 (M0 스텁 → M6 완전 자동화)

| 단계 | 코드명 | 설명 | 상태 |
|---|---|---|---|
| M0 | stub | 가짜 아티팩트로 전체 흐름 검증 | ✅ 구현됨 |
| M1 | copy | Claude CLI 카피라이팅 | 🔜 미구현 |
| M2 | tts | Fish Speech TTS 음성 생성 | 🔜 미구현 |
| M3 | video | LTX-2 영상 생성 | 🔜 미구현 |
| M4 | render | FFmpeg NVENC 렌더링 | 🔜 미구현 |
| M5 | image | 카드뉴스 이미지 생성 | 🔜 미구현 |
| M6 | publish | 소셜 미디어 게시 | 🔜 미구현 |

---

## 트러블슈팅

### 컨테이너가 DB에 연결 못 하는 경우

```bash
# docker-compose.yml에 extra_hosts 확인
grep extra_hosts /home/justant/Data/Again-Spring-Marketing/docker-compose.yml

# .env의 DB URL 확인 (host.docker.internal 사용 여부)
grep ASM_DATABASE_URL /home/justant/Data/Again-Spring-Marketing/.env
```

### 8200 포트 응답 없는 경우

```bash
docker compose ps
docker compose logs --tail=30
```

### 로컬 영상 보존

게시 성공 직후 `data/jobs/<job_id>/*__video.mp4`를 지우지 않는다. `PUBLISHED` 시각 + **30일** 후 시간당 스윕이 mp4 바이트만 삭제한다(`VIDEO_RETENTION_DAYS`, 기본 30 · `VIDEO_RETENTION_POLL_INTERVAL_SECONDS` 기본 3600). 어드민 인라인 미리보기는 그 동안 동작한다.

### Again-Spring 폴링 오류 (STALE 상태)

STALE 상태는 ASM에 5회 연속 폴링 실패 시 발생합니다.

1. ASM 서버 재시작: `docker compose restart`
2. Again-Spring 백엔드 재시작 시 폴링 스케줄러가 자동 재개됩니다
3. STALE 잡은 수동으로 새 잡을 다시 생성해야 합니다 (재시도 미구현)
