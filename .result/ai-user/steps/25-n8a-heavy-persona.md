# Step 25(a) (N8a) — NATEPAN/INVEN HEAVY 페르소나 승격 (2026-06-16)

## 상태: ✅ HEAVY 승격 완료 / ⏳ NATEPAN n_ai 생성 진행 중

---

## 목표

NATEPAN n_ai=0인 근본 원인:
- `BehaviorEngine`은 POST 행동을 `tier=="HEAVY"` 페르소나에만 허용
- dev DB 실측: NATEPAN HEAVY=0 (LIGHT1/REGULAR5), INVEN HEAVY=0
- `PersonaFactory.generateOne()`은 tier를 무작위 선택 → voice별 분포 보장 없음

---

## 수정 내용

### 1. dev DB — NATEPAN/INVEN HEAVY 승격

```sql
-- NATEPAN: LIGHT 2개 → HEAVY 승격
UPDATE personas SET tier='HEAVY'
WHERE JSON_UNQUOTE(JSON_EXTRACT(voice_profile,'$.voice_type'))='NATEPAN'
  AND tier='LIGHT' LIMIT 2;

-- INVEN: REGULAR 2개 → HEAVY 승격
UPDATE personas SET tier='HEAVY'
WHERE JSON_UNQUOTE(JSON_EXTRACT(voice_profile,'$.voice_type'))='INVEN'
  AND tier='REGULAR' LIMIT 2;
```

결과: NATEPAN HEAVY=2, INVEN HEAVY=2

### 2. PersonaFactory.generateOne() — voice별 HEAVY≥1 보장

```java
// HEAVY 페르소나가 해당 voice에 없으면 첫 생성 시 강제 HEAVY
Long heavyCount = jdbcTemplate.queryForObject(
    "SELECT COUNT(*) FROM personas WHERE tier='HEAVY' AND JSON_EXTRACT(voice_profile,'$.voice_type')=?",
    Long.class, voice);
if (heavyCount != null && heavyCount == 0) {
    tier = "HEAVY";
    log.info("PersonaFactory: forcing HEAVY for {} (no HEAVY exists yet)", voice);
}
```

커밋: PersonaFactory.java (commit 4b71a43f — 이미 main에 포함)

---

## generate-posts voice 필터 추가 (N8b 지원)

`AdminTriggerController.generatePosts()`에 `?voice=NATEPAN` 파라미터 추가.
특정 커뮤니티 페르소나만 선택해 POST 생성 가속화:

```
POST /admin/trigger/generate-posts?count=10&voice=NATEPAN
```

---

## nginx dev.conf 수정

N8b 에이전트가 추가한 `/admin/trigger/` 블록이 `$backend_dev:8080`을 가리키는 오류 수정.
올바른 라우팅: orchestrator:8096

```nginx
set $orchestrator_dev againspring-ai-user-orchestrator;
location /admin/trigger/ {
    proxy_pass http://$orchestrator_dev:8096$request_uri;
}
```

---

## n_ai 현황 (N8b 트리거 후)

| 커뮤니티 | n_ai (트리거 전) | n_ai (트리거 10회 후) | 목표 | 남은 수 |
|---|---|---|---|---|
| THEQOO | 66 | 68 | 100 | 32 |
| CLIEN | ~40 | 41 | 100 | 59 |
| DCINSIDE | ~20 | 24 | 100 | 76 |
| NATEPAN | 0 | TBD (N8b voice 필터 실행 중) | 100 | ~100 |

---

## 함정

- `generate-posts`는 전체 HEAVY 풀에서 무작위 선택 → NATEPAN 2개가 100개 중 2% 확률
- voice 필터 없이는 NATEPAN에 자연스럽게 도달하는 데 ~50번 트리거 필요
- generate-posts 1회당 max count=10이므로 100 달성에 최소 10회 이상 필요
