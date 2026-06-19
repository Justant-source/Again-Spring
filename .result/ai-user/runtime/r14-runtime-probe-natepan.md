# R14 runtime probe — NATEPAN
> 생성: 2026-06-19 21:08:22
> llm_url: `http://againspring-llm-ai-user:8092`
> strict_runtime: `true`

## Health

- status: **UP**
- raw: `{"status": "UP", "components": {"claudeCli": {"status": "UP", "details": {"claude-version": "2.1.169 (Claude Code)"}}, "db": {"status": "UP", "details": {"database": "MariaDB", "validationQuery": "isValid()"}}, "diskSpace": {"status": "UP", "details": {"total": 1965172678656, "free": 290507345920, "threshold": 10485760, "path": "/app/.", "exists": true}}, "ping": {"status": "UP"}}}`

## Probe summary

- theme: `남편이 육아를 전혀 도와주지 않을 때`
- drafts requested: **4**
- non-empty drafts: **4**
- unique drafts: **4**
- rerank winnerId: **d0**
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
| 1 | 176 | 0 |
| 2 | 162 | 0 |
| 3 | 171 | 0 |
| 4 | 148 | 0 |

## Drafts (manual review)

### Draft 1

어젯밤에 아이 열이 39도 넘었는데 남편은 소파에서 폰만 보고 있었어요

제가 혼자 해열제 챙기고 물수건 갈고 밤새 달랬거든요 ㅋㅋㅋ
한 번을 안 일어나더라고요, 진짜

아침에 저 힘들었다고 했더니 자기도 회사 일 있다고만 하는데...
이게 맨날 이런데 내가 너무 기대하는 건지 아니면 이건 아닌 건지 모르겠어요 ㅠ

### Draft 2

어젯밤 애 새벽 두 시에 울었는데 남편은 옆에서 그냥 자고 있더라고요

흔들어 깨웠더니 내일 일 있다고 돌아눕는 거예요 ㅠ 이번 주만 벌써 세 번째거든요

도움 좀 달라고 했더니 자기도 피곤하다는 말만 하잖아요... 그 말 들으니까 아무 말도 하기 싫어지더라고요

저만 이런 건지 모르겠어요

### Draft 3

어제 애가 38도 넘게 열이 났는데 내가 혼자 병원 달려갔어요. 남편은 소파에서 폰 보고 있었거든요. 내가 가방 챙기는 것도 신경 1도 안 쓰더라고요. 집에 돌아와서 약 먹이고 옆에 있었는데 남편은 그냥 자러 갔어요. 나는 뭘 기대한 건지... 이번 주만 세 번째로 내가 혼자 다 했는데 이게 맞는 건가요ㅠ

### Draft 4

어제 애가 열이 났거든요 38.5도나 됐는데 저 혼자 병원 데려가고 약 먹이고 밤새 옆에 붙어 있었는데 남편은 거실 소파에서 핸드폰만 보고 있더라고요 도와달라고 하니까 피곤하다고... 아이 아플 때도 이러면 진짜 혼자인 느낌이잖아요 이게 맞는 건지 저도 모르겠어요ㅠ

