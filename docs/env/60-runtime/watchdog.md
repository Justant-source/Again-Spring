# 운영 감시 (Watchdog) + 알림

> 로컬 호스트 + WSL 호스트 watchdog 자동 감시 및 복구 (최대 3회 한도).

## 개요

systemd 사용자 타이머가 5분마다 watchdog 스크립트를 실행하여:

1. **Claude 세션 생존** — **10분마다**(2026-08-15 단축, 기존 1시간) canary ping (최소 토큰 호출). ping은 **90초** 제한·`ANTHROPIC_API_KEY` 제거·WSL은 `~/.local/bin/claude`를 nvm보다 우선한다(nvm v22에 claude가 없어 30초 타임아웃으로 정상 oauth를 실패 처리한 전례). 마케팅 LLM 호출 실패 기반 즉시 감지(연속 인증 오류 2회 → 긴급 알림)는 `docs/backend/llm-bridge.md` §인증 오류 감지 참조 — canary는 요청이 없는 시간대(새벽)만 보조로 커버한다.
2. **자격증명 소유권** — `~/.claude/.credentials.json` 소유자 확인 (justant 여부)
3. **컨테이너 헬스** — `docker ps`에서 `unhealthy` 상태인 againspring 컨테이너 자동 재시작. `.State.Health.Status`가 있는 컨테이너는 그 값을, 없으면(`health=NONE`) 기존처럼 `.State.Status`만 판정 (2026-08-15부터 WSL 워치독에 반영)
4. **WSL 재부팅 감지** — 부팅 시각을 상태 파일에 기록해 이전 값과 비교, 변경 시 Telegram 통보 (2026-08-15 신설 — 최근 12일 재부팅 5회 실측, 근본원인 미확정 상태에서 최소한의 가시성 확보)

**자동 복구 한도**: 각 항목당 최대 3회. 초과 시 사용자 수동 조치 필요 메시지 전송.

**알림**: Telegram 채팅방(@WaggleBot_bot) — "발생 → 조치중 → 결과" 3단계.

---

## 설정

### 텔레그램 Credentials

파일: `~/.config/again-spring-watchdog/telegram.env` (git 무시, 권한 600)

```bash
TELEGRAM_BOT_TOKEN=7965451096:AAG...  # 토큰값은 git 커밋 금지
TELEGRAM_CHAT_ID=6873494912
```

**위치**: WSL의 WaggleBot 프로젝트에서 확인 → 로컬에 복사 (자동 배포 불가).

### systemd 타이머

**파일**:
- `~/.config/systemd/user/again-spring-watchdog.service` — oneshot 서비스
- `~/.config/systemd/user/again-spring-watchdog.timer` — 5분 간격

**활성화** (로컬 터미널에서):

```bash
systemctl --user daemon-reload
systemctl --user enable again-spring-watchdog.timer
systemctl --user start again-spring-watchdog.timer
```

**로그아웃 후 타이머 유지** (필요시):

```bash
loginctl enable-linger justant
```

---

## 스크립트

**파일**: `env/scripts/ops-watchdog.sh`

**상태 추적**:
- `watchdog-state/claude-canary.timestamp` — 마지막 canary 실행 시각
- `watchdog-state/retry-state.json` — 각 항목별 재시도 횟수 + 실패 경험
- `watchdog-state/watchdog.log` — 실행 로그

**금지 사항**:
- 브라우저 OAuth 재로그인 자동화 (headless 불가)
- `docker compose up` 스택 재배포
- DB/콘텐츠 조작

**허용**: 양쪽 Claude canary가 실패하면 피어에서 `claudeAiOauth`를 가져와 ping 재시도.
성공한 쪽은 `expiresAt`이 더 큰 oauth를 반대쪽에 push/pull 해서 **같은 유효 세션**을 유지한다
(`scripts/claude-oauth-peer.sh`). 브라우저 로그인이 아니라 이미 로그인된 세션 복사다.

---

## 운영

### 타이머 상태 확인

```bash
# 다음 실행 시각 확인
systemctl --user list-timers again-spring-watchdog.timer

# 최근 실행 로그
journalctl --user -u again-spring-watchdog.service -n 20 --no-pager
```

### 수동 실행

```bash
systemctl --user start again-spring-watchdog.service
```

### 장애 주입 테스트 (자격증명 소유권)

```bash
# 테스트 — 소유권 깨뜨리기
sudo chown root:root ~/.claude/.credentials.json

# 다음 watchdog 실행 시 자동 복구 시도 (1회)
# Telegram 알림: ⚠️ 감지 → 🔧 조치중 → ✅ 복구됨

# 검증 후 정상 상태로 복원
sudo chown justant:justant ~/.claude/.credentials.json
```

### 3회 한도 초과 시나리오

동일한 항목이 3회 연속 실패 시:
- Telegram: `❌ 수동 조치 필요: <복구 명령>`
- 상태 파일: 해당 항목의 `retry_count` ≥ 3
- 자동 조치는 **중단** (사람이 직접 실행할 때까지)

**복구 방법**:
```bash
# 예: 자격증명 복구
sudo chown justant:justant ~/.claude/.credentials.json

# 상태 파일 수동 리셋 (선택)
rm -f watchdog-state/retry-state.json
```

---

## 감시 대상 상세

| 대상 | 감지 방법 | 복구 | 복구 한도 |
|---|---|---|---|
| Claude 세션 (AS) | `timeout 30 claude -p 'ping'` (10분 주기) | **WSL oauth pull 후 ping 재시도**. 성공 시 WSL과 expiresAt reconcile | 3회 |
| Claude 세션 (WSL) | 동일 canary | **AS oauth pull 후 ping 재시도**. 성공 시 AS와 reconcile | 3회 |
| 자격증명 소유권 | `stat -c '%U' ~/.claude/.credentials.json` | `chown justant:justant` | 3회 후 중단 |
| Unhealthy 컨테이너 | `docker ps --filter health=unhealthy` (건강체크 없는 컨테이너는 `.State.Status`) | `docker restart <container>` | 컨테이너당 3회 |
| WSL 재부팅 | `/proc/sys/kernel/random/boot_id` 비교(2026-08-16, `uptime -s` 초 단위 흔들림으로 인한 중복 알림 수정) | 자동 복구 없음, Telegram 통보만 | — |

**WSL 워치독(`wsl-ops-watchdog-script.sh`) 감시 컨테이너 목록** (2026-08-15 comfyui 추가):
`llm-worker`, `again-spring-marketing-asm-1`, `again-spring-marketing-llm-bridge-1`,
`again-spring-marketing-social-poster-1`, `env-ai_worker-1`, `comfyui`
— comfyui는 5일간 `Exited(137)`(OOM)로 죽어 있었는데 감시 목록에 없어 아무도 몰랐던 사고 이후 추가됨.
발행 경로 자체는 comfyui에 의존하지 않으나(WaggleBot 렌더는 별도), 죽은 채 방치되는 걸 막기 위해 감시만 한다.

**예외 — `againspring-ai-learning` 크롤 보호 (2026-08-11)**:
- 컨테이너 안 `/tmp/ai_learning_crawl_in_progress` 마커가 있거나
- KST 시각이 **02시 또는 03시**(일일 크롤·강화 윈도우)
이면 unhealthy여도 **재시작하지 않고 Telegram 알림만** 보낸다.
(이전: 크롤 중 restart → `crawl_log` SUCCESS 유실 → admin 신선도 stale)

---

## 알림 프로토콜

**3단계 흐름**:

1. **⚠️ 발생**: 피어 복사 후에도 canary가 실패할 때만. 먼저 반대쪽 세션 pull을 시도한다
2. **🔧 조치중**: AS는 WSL에서, WSL은 AS에서 oauth를 가져와 재시도
3. **✅ 결과**: `Claude 세션 복구 (WSL|AS 복사)` 또는 3회 후 수동. canary가 살아 있으면 텔레그램 없이 expiresAt reconcile만

스크립트:
- 공통: `scripts/claude-oauth-peer.sh pull|push|reconcile user@host`
- AS → WSL 수동+검증: `scripts/sync-claude-creds-to-wsl.sh`
- 래퍼: `scripts/pull-claude-creds-from-as.sh` · `scripts/pull-claude-creds-from-wsl.sh`
- WSL 워치독 SSOT: `env/scripts/wsl-ops-watchdog-script.sh`

**스팸 방지**:
- 이미 알린 동일 문제는 반복 발송 안 함
- 상태 파일로 추적: `retry_count`, `last_alert_id`
- 성공 시 count 리셋 + 1줄 알림만

---

## 참고: WSL 호스트 watchdog

WSL(`100.115.252.61`)의 watchdog은 동일 구조로 별도 배포.
- 타이머: `~/.config/systemd/user/wsl-ops-watchdog.timer` (5분 간격)
- 서비스: `~/.config/systemd/user/wsl-ops-watchdog.service` (`TimeoutStartSec=240s` — pull+재ping 여유. SSOT `env/scripts/wsl-ops-watchdog.service`)
- 스크립트: `~/.config/systemd/user/wsl-ops-watchdog-script.sh` (SSOT `env/scripts/wsl-ops-watchdog-script.sh`)
- Claude canary 실패 → 피어 oauth pull → ping 재시도. 성공 시 `expiresAt` reconcile로 양쪽을 맞춤. bind-mount라 llm-bridge 재시작 없음.
- 상태 파일: `~/.wsl-watchdog/` (canary 타임스탬프, 재시도 카운터, 부팅 식별자, 로그)
  - `boot.ts`: `{"boot_id": "...", "boot_epoch": ...}` — boot_id는 부팅마다 커널이 새로 발급하는
    고정값이라 같은 부팅 중 재확인해도 값이 흔들리지 않는다(2026-08-16, 기존 `uptime -s` 초 단위
    파싱 오차로 인한 중복 재부팅 오탐·중복 알림 수정). 구 형식(숫자 epoch만)은 첫 실행 시 알림 없이
    신 형식으로 자동 마이그레이션된다.
  - 재부팅 알림의 중복방지 키는 메시지 본문이 아닌 `wsl-reboot:{boot_id}`로 고정 — 같은 부팅에서는
    시각 문구가 달라져도 두 번 발송되지 않는다.
- Telegram: 동일 채널 (`[감지]`/`[조치중]`/`[성공]`/`[실패]` prefix)

**양방향 감시**: 로컬 ↔ WSL 상호 확인 가능 (ssh reverse tunnel 성공 가정).

### ASM 컨테이너 healthcheck (2026-08-15 추가)

`~/Data/Again-Spring-Marketing/docker-compose.yml`에 `asm`·`renderer`·`social-poster` 서비스의
healthcheck를 추가했다 (기존 `health=NONE` → WSL 워치독이 `.State.Health.Status`로 unhealthy를 감지 가능):

| 서비스 | 방식 |
|---|---|
| `asm` | `curl -f http://localhost:8200/api/v1/health` |
| `renderer` | TCP 포트 오픈 확인 (HTTP 엔드포인트 미확인) |
| `social-poster` | TCP 포트 오픈 확인 (HTTP 엔드포인트 미확인) |

renderer·social-poster는 HTTP 헬스 엔드포인트가 코드에 명시적으로 없어 TCP 체크로 대체했다 —
추후 각 서비스에 `/health`가 추가되면 HTTP 방식으로 승격할 것.

---

## 트러블슈팅

### 타이머 실행 안 됨

```bash
# 사용자 서비스 활성 확인
systemctl --user is-system-running
systemctl --user status

# linger 확인 (로그아웃 후 실행)
loginctl show-user justant | grep Linger
# 아니면: loginctl enable-linger justant
```

### 텔레그램 전송 실패

```bash
# 수동 테스트
curl -s "https://api.telegram.org/bot<TOKEN>/sendMessage" \
  -d "chat_id=<ID>" \
  -d "text=test"
```

### Claude 세션 만료 진단

```bash
# 수동 핑 (API 키가 oauth를 가리지 않게)
env -u ANTHROPIC_API_KEY timeout 90 claude -p 'ping'

# 자격증명 확인
ls -la ~/.claude/
stat ~/.claude/.credentials.json
```

---

