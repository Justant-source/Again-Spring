# R14 runtime probe — THEQOO
> 생성: 2026-06-19 21:04:13
> llm_url: `http://againspring-llm-ai-user:8092`
> strict_runtime: `true`

## Health

- status: **UP**
- raw: `{"status": "UP", "components": {"claudeCli": {"status": "UP", "details": {"claude-version": "2.1.169 (Claude Code)"}}, "db": {"status": "UP", "details": {"database": "MariaDB", "validationQuery": "isValid()"}}, "diskSpace": {"status": "UP", "details": {"total": 1965172678656, "free": 290508460032, "threshold": 10485760, "path": "/app/.", "exists": true}}, "ping": {"status": "UP"}}}`

## Probe summary

- theme: `친구가 자꾸 비교하며 기죽일 때`
- drafts requested: **4**
- non-empty drafts: **4**
- unique drafts: **4**
- rerank winnerId: **d3**
- rerank degraded: **False**

## What this verifies

1. `:8092` is reachable from the current host
2. `/generate/post` returns text 4 times
3. drafts are not all identical
4. `/rerank` returns a winner

## Still must verify on host logs

1. actual backend/model selected
2. `InvokerRouter` path
3. whether any silent proxy/fallback exists outside this script

## Known tell scan

| draft | length | known tell hits |
|---|---:|---|
| 1 | 202 | 0 |
| 2 | 129 | 0 |
| 3 | 141 | 0 |
| 4 | 153 | 0 |

## Drafts (manual review)

### Draft 1

친구가 어제 모임에서 취직도 아직이냐고 했는데, 다들 있는 데서 대놓고 자기 연봉 얘기 꺼내면서 나랑 비교한 거임

이번만도 아니고 지난달에도 내가 살 좀 쪘다고 했더니 자기는 요즘 오히려 빠진다고 바로 튀어나왔음

걍 내가 뭔 얘기를 해도 자기가 더 낫다는 걸 끼워넣는 느낌

근데 평소엔 되게 잘해줘서 걘 의도적인 건지 내가 너무 예민한 건지 진짜 모르겠음ㅠ

### Draft 2

어제 친구 만났는데 내 직장 얘기 나오니까 자기 친구는 대기업 다닌다고 바로 꺼내는거임
할 말 잃었음... 이게 처음도 아니고 지난달에도 내 옷 보면서 자기 주변은 다 명품 입는다고 했거든 ㅠㅠ
걔가 의도가 있는건지 1도 모르겠는데

### Draft 3

어제 카페에서 만났는데 걔가 또 시작함

내 옷 보더니 그거 세일 한 거냐고, 자긴 요즘 브랜드만 입는다고
이번달만 세 번째인거야

나는 그때마다 웃고 넘겼는데 집에 오면 그냥 기분이 이상해짐

걔가 나를 무시하는 건지 그냥 원래 그런 앤지 걍 모르겠음

### Draft 4

지난주에 나 새 직장 얘기했더니 걔가 바로 자기 연봉이 얼마라며 자랑을 늘어놓음

오늘도 내가 운동 시작했다고 하니까 자기가 몇 달 만에 살 몇 킬로 뺐는지 신나서 떠들고

축하는커녕 내 얘기가 걔 자랑으로 넘어가는 게 이번달만 세 번째임

친구가 맞는 건지 걍 모르겠음 ㅠ

