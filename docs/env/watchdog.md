# 운영 감시 (Watchdog) + 알림

> 로컬 호스트 + WSL 호스트 watchdog 자동 감시 및 복구 (최대 3회 한도).

## 개요

systemd 사용자 타이머가 5분마다 watchdog 스크립트를 실행하여:

1. **Claude 세션 생존** — 1시간마다 canary ping (최소 토큰 호출)
2. **자격증명 소유권** — `~/.claude/.credentials.json` 소유자 확인 (justant 여부)
3. **컨테이너 헬스** — `docker ps`에서 `unhealthy` 상태인 againspring 컨테이너 자동 재시작

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
- 재로그인 자동화 (사용자 수동 필요)
- `docker compose up` 스택 재배포
- DB/콘텐츠 조작

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
| Claude 세션 | `timeout 30 claude -p 'ping'` | 사용자 수동 (재로그인) | 3회 후 중단 |
| 자격증명 소유권 | `stat -c '%U' ~/.claude/.credentials.json` | `chown justant:justant` | 3회 후 중단 |
| Unhealthy 컨테이너 | `docker ps --filter health=unhealthy` | `docker restart <container>` | 컨테이너당 3회 |

**예외 — `againspring-ai-learning` 크롤 보호 (2026-08-11)**:
- 컨테이너 안 `/tmp/ai_learning_crawl_in_progress` 마커가 있거나
- KST 시각이 **02시 또는 03시**(일일 크롤·강화 윈도우)
이면 unhealthy여도 **재시작하지 않고 Telegram 알림만** 보낸다.
(이전: 크롤 중 restart → `crawl_log` SUCCESS 유실 → admin 신선도 stale)

---

## 알림 프로토콜

**3단계 흐름**:

1. **⚠️ 발생**: `[Again-Spring] Claude 세션 만료 감지`
2. **🔧 조치중**: `자동 복구 시도 중... (1/3)`
3. **✅ 결과**: `복구됨` 또는 `❌ 3회 초과, 수동 조치: claude login`

**스팸 방지**:
- 이미 알린 동일 문제는 반복 발송 안 함
- 상태 파일로 추적: `retry_count`, `last_alert_id`
- 성공 시 count 리셋 + 1줄 알림만

---

## 참고: WSL 호스트 watchdog

WSL(`100.115.252.61`)의 watchdog은 동일 구조로 별도 배포.
- 타이머: `~/.config/systemd/user/again-spring-watchdog-wsl.timer`
- 스크립트: WSL 내 별도 경로
- Telegram: 동일 채널 (호스트 구분 prefix 추가)

**양방향 감시**: 로컬 ↔ WSL 상호 확인 가능 (ssh reverse tunnel 성공 가정).

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
# 수동 핑
claude -p 'ping'

# 자격증명 확인
ls -la ~/.claude/
stat ~/.claude/.credentials.json
```

---

**마지막 업데이트**: 2026-08-11 | **형태**: systemd user timer + bash script
