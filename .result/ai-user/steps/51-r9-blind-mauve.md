# Step 51 — R9 측정: blind①② + MAUVE 재측정

**세션**: 22~ | **날짜**: 2026-06-17~ | **상태**: 🔄 신선 축적 대기 중

---

## 📋 수동 작업 지시서

> 이 파일에 결과를 직접 기록하면 다음 세션에서 Claude가 분석·에스컬레이션 평가를 자동 수행.

---

## ① 현재 축적 상태 확인 명령

```bash
# 언제든 이 쿼리로 신선 축적 확인 (KST 기준 오늘 12:03 이후)
ssh justant@100.115.252.61 "docker exec again-spring-ai-user-aiuser-ml-db-1 \
  mariadb -uaiuser_ml -paiuser_ml_dev aiuser_ml -e \"
  SELECT content_type, community, label, COUNT(*) as n_fresh
  FROM corpus_item
  WHERE ingested_at > '2026-06-17 03:03:00'
  GROUP BY content_type, community, label
  ORDER BY content_type, community, label;
\""
```

**축적 기준 (측정 시작 OK 조건)**:
- blind ①: CLIEN POST ai ≥10건 신선 (Track A 오타 주입 적용분)
- blind ②: CLIEN POST ai 중 비갈등 글 ≥5건 확인 (Track B CASUAL 확인)
- R7 M-after: CLIEN COMMENT ai ≥50건 신선

---

## ② CASUAL 글 출현 스팟 체크 (수동)

Track B가 작동하는지 확인. 오케스트레이터 로그에서 "CASUAL" 키워드 확인:

```bash
docker logs againspring-ai-user-orchestrator --since 2h 2>&1 | grep -i "casual\|postKind" | tail -20
```

또는 dev DB에서 최신 AI 글 5개 직접 읽기:

```bash
docker exec againspring-mariadb-dev mariadb -uagainspring -pF2etXbugW0EBDZNBMX17Q againspring_dev -e "
SELECT id, title, LEFT(content,100) as snippet, created_at
FROM posts
WHERE user_id IN (SELECT id FROM users WHERE email LIKE '%@ai.againspring.net')
ORDER BY created_at DESC
LIMIT 10;" 2>&1
```

**✏️ 결과 기록 (여기에 작성)**:
```
확인 날짜: 
갈등 아닌 글 출현: Y / N
예시 (있으면):
```

---

## ③ blind ① — 순수 문체 cond5 (20쌍, 갈등 매칭)

**목적**: Track A 오타 주입 효과 측정 — 주제 단서 없이 문체만으로 구별 가능한가?

**진행 방법**:

1. Claude에게 "blind ① 쌍을 뽑아줘" 요청 → Claude가 ML 코퍼스에서 자동 추출
   - 조건: CLIEN POST ai(신선, 갈등 서사) 20건 + CLIEN POST human(갈등 서사) 20건
   - 주제 키워드 매칭(남친/직장/가족 카테고리 균형), 무작위 순서로 섞기
2. 각 쌍을 보고 **"어느 쪽이 사람 글인가?"** 판단
   - 판단 근거 적어도 됨 (선택)
   - 빠르게 직관으로 판단 (심사숙고X — 실제 독자 경험 기준)
3. 아래 표에 기록

**✏️ 결과 기록**:

```
측정 날짜:
총 쌍 수: 20
정답 (AI 맞춤): ___ / 20  ← 사후에 Claude가 라벨 공개 후 채점
정확도: ___%
```

| 쌍 # | 내 판단 (A=사람 / B=사람) | 메모 |
|---|---|---|
| 1 | | |
| 2 | | |
| 3 | | |
| ... | | |
| 20 | | |

**판단 기준 힌트 (읽기 전에 보지 말 것)**:
<details>
<summary>채점 후 참고</summary>

- 오타/띄어쓰기 오류: 인간 쪽에 자연스러운 오타
- 문장 균일성: AI는 비슷한 길이·구조 반복
- 마무리: AI는 수사 의문문으로 끝나는 경향
- 주제 도입: AI는 "어제/이번 주에" + trigger 사건 도식

</details>

---

## ④ blind ② — 현실 cond5 (20쌍, 혼합 주제)

**목적**: Track A+B 합산 효과 — 실제 운영 환경에서 구별 가능한가? (cond5 실측)

**진행 방법**: blind ①과 동일하나 AI쪽이 갈등+일상 혼합 (CASUAL 포함)

**✏️ 결과 기록**:

```
측정 날짜:
총 쌍 수: 20
정답 (AI 맞춤): ___ / 20
정확도: ___%
```

**cond5 판정**:
- ≤60% → ✅ cond5 PASS
- 61~75% → ❌ FAIL (에스컬레이션 평가 필요)
- >75% → ❌ FAIL (D-12 Phase 2/3 보고)

---

## ⑤ R7 M-after — COMMENT MAUVE (자동, Claude에게 요청)

**목적**: Haiku 거절 픽스(2026-06-17 00:13) + Track A 오타 주입 후 COMMENT MAUVE 재측정

**진행 방법**: "R7 M-after 측정해줘" 요청 → Claude가 WSL에서 자동 실행

**✏️ 결과 기록** (Claude가 채워줌):

```
측정 날짜:
CLIEN COMMENT MAUVE: (M-before: 0.0677)
  - M-after =
  - Δ =
NATEPAN COMMENT MAUVE: (M-before: 0.0598)
  - M-after =
  - Δ =
```

---

## ⑥ POST MAUVE 재측정 (자동, Claude에게 요청)

**목적**: Track A+B 배포 후 POST MAUVE 전후 비교

**진행 방법**: "MAUVE 재측정해줘" 요청

**✏️ 결과 기록**:

```
측정 날짜:
CLIEN POST MAUVE:
  - 이전 = 0.3527 (R5, n=22 신선)
  - 이후 =
  - Δ =
NATEPAN POST MAUVE:
  - 이전 = 0.8395
  - 이후 =
  - Δ =
```

---

## 에스컬레이션 평가 기준 (D-12)

blind ② 결과 후 Claude가 자동 평가:

| 결과 | 판정 | 다음 단계 |
|---|---|---|
| ≤60% | ✅ cond5 PASS | AI_USER_ML_ENABLED 5조건 재검토 |
| 61~75% | ❌ 추가 레버 탐색 | length/register 회전 (R10) |
| >75% | ❌ D-12 Phase 2/3 보고 | **QLoRA/DPO — 사용자 명시 승인 필요** |
