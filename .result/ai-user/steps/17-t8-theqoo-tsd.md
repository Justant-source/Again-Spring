# Step 17 (T8) — THEQOO TSD 프롬프팅 개선 (2026-06-16)

## 목표

오케스트레이터 THEQOO 생성 MAUVE=0.345 → 0.60+ (run_ab_test.py 단순 프롬프트 MAUVE=0.985 대비)

---

## 근본 원인 분석

### 왜 오케스트레이터 MAUVE가 낮았나

1. **`appendWritingQuirks`가 `features` 필드를 무시**: 
   `ActionExecutor.appendWritingQuirks()`는 `writing_quirks.consistent_errors` / `.mobile_typos`만 읽음.
   `voices.yml`에 존재하는 `features` 필드는 **코드에서 전혀 읽지 않았음** (dead field).

2. **`voiceBlockForPost` 출력이 거의 비어 있었음**:
   THEQOO DB 페르소나들 중 일부는:
   - `consistent_errors` 비어 있음 + `mobile_typos: false` → `appendWritingQuirks` = 출력 없음
   - `general_style`은 부정확한 auto-generated 내용 (예: "논리적이고 날카로운 사회 비판")
   - `## 페르소나 특성` 섹션이 personality만 있고 구조적 TSD 제약이 없음

3. **DB 페르소나 세대 불일치**:
   - `ai-user/docs/personas/profiles/ai-user-{N}/voice.yml` persona_ids ≠ DB 페르소나 IDs
   - DB(7개 THEQOO)는 다른 세대의 auto-generated 페르소나
   - `AiUserSeedLoader`는 "already 100 personas, skip" → voice.yml 변경 반영 안 됨

---

## 변경 내용

### 1. `ActionExecutor.java` — `appendWritingQuirks` features 읽기 추가

```java
// features: 커뮤니티별 문체 구조 제약 (TSD)
Object featuresObj = quirks.get("features");
if (featuresObj instanceof String) {
    String featureStr = ((String) featuresObj).trim();
    if (!featureStr.isEmpty()) {
        sb.append("\n[문체 패턴] ").append(featureStr);
    }
}
```

`[문체 패턴]` 섹션이 `[맞춤법·오타 패턴]` 전에 출력됨 — 기존 코드 비파괴.

### 2. THEQOO voice.yml files — features 추가

10개 `profiles/ai-user-{N}/voice.yml`에 `writing_quirks.features` 추가:

```yaml
writing_quirks:
  ...
  features: "짧은 문장 단위 (한 문장 10~20자 이내). 단락 없이 한 덩어리로. 헐/ㅠㅠ/ㄷㄷ 문장 중간 삽입. ~당/~징/~음/ㅎㅎ 종결 위주."
```

파일: ai-user-021/022/035/053/054/055/056/057/058/059

### 3. dev DB — JSON_SET 직접 업데이트

voice.yml의 페르소나 IDs ≠ DB 페르소나 IDs → DB 직접 업데이트 필요:

```sql
UPDATE personas 
SET voice_profile = JSON_SET(voice_profile, '$.writing_quirks.features', 
    '짧은 문장 단위 (한 문장 10~20자 이내). 단락 없이 한 덩어리로. 헐/ㅠㅠ/ㄷㄷ 문장 중간 삽입. ~당/~징/~음/ㅎㅎ 종결 위주.')
WHERE JSON_EXTRACT(voice_profile, '$.voice_type') = 'THEQOO';
-- 7개 업데이트 완료 (dev DB)
```

---

## 배포 & 검증

| 항목 | 상태 |
|---|---|
| orchestrator 컴파일 | ✅ `BUILD SUCCESSFUL` |
| orchestrator 테스트 (4/4) | ✅ |
| dev docker rebuild | ✅ `againspring-ai-user-orchestrator` |
| dev DB JSON_SET 7건 | ✅ features 필드 확인 |
| e2e-realbe 142/147 | ✅ (5 skipped 정상) |
| main push | ✅ commit `88018822` |

---

## TSD 프롬프트 효과 예측

시스템 프롬프트 `## 페르소나 특성` 섹션에 이제 추가됨:

```
[문체 패턴] 짧은 문장 단위 (한 문장 10~20자 이내). 단락 없이 한 덩어리로. 헐/ㅠㅠ/ㄷㄷ 문장 중간 삽입. ~당/~징/~음/ㅎㅎ 종결 위주.
```

- 모델이 명시적 구조 제약 수신 → 긴 구조화 서사 대신 짧은 구어체 흐름 생성 기대
- 기존 `writing_quirks`의 맞춤법·오타 패턴과 분리된 별도 섹션 → 의미 명확

---

## 다음 단계

1. **MAUVE 재측정**: THEQOO 봇이 T8 조건에서 게시글 충분히 생성 후 `/eval/baseline` 재트리거
2. **A-B 재실행**: T5(n_ai≥100) + T8 완료 후 `/eval/ab-test` → cond4 재검증
3. **prod**: 명시적 배포 지시 + dev MAUVE 개선 확인 후 (SQL 업데이트 포함)
