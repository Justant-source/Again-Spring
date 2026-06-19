# R14 runtime probe — CLIEN
> 생성: 2026-06-19 21:06:06
> llm_url: `http://againspring-llm-ai-user:8092`
> strict_runtime: `true`

## Health

- status: **UP**
- raw: `{"status": "UP", "components": {"claudeCli": {"status": "UP", "details": {"claude-version": "2.1.169 (Claude Code)"}}, "db": {"status": "UP", "details": {"database": "MariaDB", "validationQuery": "isValid()"}}, "diskSpace": {"status": "UP", "details": {"total": 1965172678656, "free": 290508013568, "threshold": 10485760, "path": "/app/.", "exists": true}}, "ping": {"status": "UP"}}}`

## Probe summary

- theme: `동료가 내 코드를 허락 없이 수정했을 때`
- drafts requested: **4**
- non-empty drafts: **4**
- unique drafts: **4**
- rerank winnerId: **d1**
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
| 1 | 210 | 0 |
| 2 | 213 | 0 |
| 3 | 187 | 0 |
| 4 | 195 | 0 |

## Drafts (manual review)

### Draft 1

어제 배포 직전에 PR 보다가 제 담당 모듈에 제가 올린 적 없는 커밋이 들어가 있는 거예요
확인해보니 옆 파트 동료가 급하다고 그냥 수정했다고 하더라고요 ㅎㅎ
팀장한테 얘기했더니 기능 이상 없으면 그냥 넘어가래요

근데 이게 사전에 말 한 마디 없이 남 코드를 건드리는 게 맞는 건가요
나중에 버그 나면 책임 소재가 어떻게 되는 건지, 저만 이상하게 생각하는 건지 모르겠네요

### Draft 2

오늘 오전에 제 브랜치 열어봤더니 커밋이 두 개 생겨 있더라고요. 제가 올린 게 아닌데

확인해보니까 옆자리 동료가 제 코드 로직을 통째로 바꿔놨어요. 사전에 아무 말도 없었거든요

리팩토링 해줬다고 하던데... 제가 설계한 구조를 허락도 없이 갈아엎은 거잖아요. 내가 왜 이게 화나는지 설명을 해야 하나 싶고, 근데 또 그냥 넘어가자니 찜찜하고

어떻게 해야 하는 건지 모르겠네요

### Draft 3

어제 팀 레포 들어갔더니 제가 작성한 로직이 싹 바뀌어 있었어요. PR도 없고 리뷰 요청도 없이요. 나중에 물어봤더니 동료가 자기 방식이 더 깔끔하다고 생각했다고 하더라고요. 이번이 벌써 세 번째거든요. 처음엔 제가 너무 예민한 건가 싶었는데, 지난 주에도 똑같이 당했고. 내가 짠 코드인데 협의 한 번 없는 게 당연한 건지 모르겠어요

### Draft 4

어제 깃 로그 확인하다가 황당한 거 발견했는데요

팀원이 제가 작성한 함수를 허락도 없이 통째로 바꿔놨더라고요. PR도 없이 그냥 main에 직접 올렸고요 ㅎㅎ

뭐가 문제인지 여쭤봤더니 그냥 더 낫게 고쳐준 거라고만 하는데...

내가 그 로직 짤 때 다 이유가 있었던 건데, 그게 1도 안 궁금한 거잖아요. 이걸 그냥 넘겨야 하는 건지 모르겠네요

