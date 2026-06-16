# Step 23 (N6) — 댓글 분포매칭 활성 (2026-06-16)

## 상태: 🔄 코드 완료, dev build + e2e 게이트 대기

---

## 목표

COMMENT 경로에 초성체 주입 활성화. 현재 POST는 allowChosung=true이나 COMMENT는 false(하드코딩) → DCINSIDE/THEQOO/FMKOREA/ARCALIVE 댓글에도 커뮤니티 특색 초성체가 삽입되어야 함.

---

## 변경 내용 (AS-side)

### OutputSanitizer.java (ai-user/llm 모듈)

**이전 (line 71-74)**:
```java
public String sanitizeComment(String raw, String voiceType) {
    String base = sanitize(raw, MAX_COMMENT);
    return applyDist(base, voiceType, false);  // allowChosung 항상 false
}
```

**수정 (N6)**:
```java
public String sanitizeComment(String raw, String voiceType) {
    String base = sanitize(raw, MAX_COMMENT);
    // N6: allowChosung=true — VOICE_DIST.chosungInject 값이 voice별 주입 여부를 결정
    // (이전: false 하드코딩 → DCINSIDE/THEQOO/FMKOREA/ARCALIVE 댓글 초성체 완전 차단)
    return applyDist(base, voiceType, true);
}
```

### applyDist 로직 (변경 없음 — 이미 정상)

```java
// allowChosung=true여도 VOICE_DIST.chosungInject=false이면 주입 안 함
if (allowChosung && dist.chosungInject() && dist.chosungPhrases() != null) {
    s = injectChosung(s, dist.chosungPhrases());
}
```

즉: NATEPAN(chosungInject=false), CLIEN(false) 등은 변경 후에도 초성체 주입 없음. DCINSIDE(true), THEQOO(true), FMKOREA(true), ARCALIVE(true)만 주입됨.

---

## VOICE_DIST 초성체 설정 (변경 없음)

| voice_type | chosungInject | chosungPhrases |
|---|---|---|
| NATEPAN | false | — |
| DCINSIDE | **true** | ㄹㅇ, ㅇㅈ, ㄷㄷ, ㅋㅋ |
| BLIND | false | — |
| GENERAL | false | — |
| FMKOREA | **true** | ㄹㅇㅋㅋ, ㄷㄷ, ㅇㅈ, 후추 |
| RULIWEB | false | — |
| THEQOO | **true** | 헐, ㅠㅠ, ㄷㄷ, 개공감 |
| ARCALIVE | **true** | ㄹㅇ, ㄱㄱ, ㅇㅇ, 어쩔 |
| INVEN | false | — |
| MLBPARK | false | — |
| PPOMPPU | false | — |
| CLIEN | false | — |

---

## 범위 결정 (D-26)

| 항목 | 결정 |
|---|---|
| 초성체 주입 (COMMENT) | ✅ 활성화 (N6 변경) |
| 쉼표율 정규화 (COMMENT) | ✅ 이미 동작 (변경 전부터) |
| 길이컷 (COMMENT 300자) | ✅ 이미 동작 |
| Best-of-N (COMMENT) | ⏳ **N1 완료 후 결정** — 역전 판별기로 리랭크 시 품질 악화 위험 (D-26) |
| REPLY 초성체 | ❌ 미적용 유지 (voiceType 없는 overload 경로, N6 스코프 외) |

---

## 테스트 결과

```
LLM 모듈 OutputSanitizer 테스트: 8/8 PASS
```

---

## 완료 기준 (dev e2e 후 업데이트)

- [x] OutputSanitizer.java allowChosung 수정
- [x] LLM 모듈 테스트 8/8 PASS
- [ ] dev rebuild + e2e-realbe 통과
- [ ] COMMENT MAUVE before/after 측정 (N9에서 측정 예정)
- [ ] 댓글 Best-of-N 결정 기록 (D-26 - N1 완료 후)

---

## 함정

- `VOICE_DIST`는 Java 하드코딩 (OutputSanitizer.java:36-55) — `voices.yml post_processing`은 죽은 설정
- REPLY 경로 (`sanitizeComment(raw)` voiceType 없는 overload)는 분포매칭 없음 — 의도된 범위 외
