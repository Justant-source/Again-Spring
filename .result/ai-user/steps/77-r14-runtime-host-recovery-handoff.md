# Step 77 (R14) — runtime host recovery handoff

## 상태

- R14의 다음 순서는 여전히 `:8092` runtime 복구다.
- 하지만 현재 셸 제약은 Step 68과 동일하다.
  - `docker` 없음
  - `curl` 없음
  - `/usr/bin/ssh`는 있으나 권한 거부
  - `localhost:8092/actuator/health`는 connection refused
- 따라서 이 단계의 목표는 복구 자체가 아니라, **host 접근 주체가 즉시 실행할 helper를 남기는 것**이다.

## 이번 세션 추가

### 1. host-side recovery helper

- 파일: `.result/ai-user/scripts/recover_runtime_host.py`
- 역할:
  1. `docker compose -f env/docker-compose.dev.yml --env-file env/.env.dev up -d llm-ai-user`
  2. `http://localhost:8092/actuator/health` polling
  3. 성공 시 후속 probe 명령 출력

### 2. request snapshot 동기화

- `.requesr/ai-user/NEXT.md`
  - 1번을 `recover_runtime_host.py` 기준으로 교체
- `.requesr/ai-user/STATE.md`
  - 현재 셸 제약을 `docker`/`curl` 부재까지 포함해 갱신

## 실행 예시

```bash
cd /home/justant/Data/Again-Spring
python3 .result/ai-user/scripts/recover_runtime_host.py
```

성공하면 바로 아래 순서로 진행한다.

```bash
python3 .result/ai-user/scripts/probe_runtime_pipeline.py --community THEQOO --strict-runtime
python3 .result/ai-user/scripts/probe_runtime_pipeline.py --community CLIEN --strict-runtime
python3 .result/ai-user/scripts/probe_runtime_pipeline.py --community NATEPAN --strict-runtime
```

## 검증

- 현재 셸에서 helper 자체 문법 검증 가능
- 실제 `status=OK`는 `docker`가 있는 dev host에서만 확인 가능

## 의미

- R14 크리티컬 패스는 변하지 않았다.
- 다만 host 접근 주체가 더 이상 수동으로 명령을 조합할 필요 없이, recovery와 health check를 한 번에 실행할 수 있게 됐다.

## 다음 스텝

1. dev host에서 `python3 .result/ai-user/scripts/recover_runtime_host.py`
2. `probe_runtime_pipeline.py --strict-runtime` 3커뮤니티 실행
3. THEQOO runtime h2h owner+friend
4. NATEPAN fresh cond5 owner(+friend)
