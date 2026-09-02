# 구조적 AI투 후보 탐지 (로그 전용) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `SelfCritiqueService`의 기존 정규식 체크(어휘 단위)가 못 잡는 구조적 AI투(마지막 문단 요약·"A가 아니라 B다" 대칭 대조 반복) 2종을, 재시도 프롬프트 체크리스트로 무비용 완화하고, 실제 발생 빈도는 점수 미반영 로그로 수집한다.

**Architecture:** (1) `buildRetryPrompt`가 이미 걸리는 모든 재시도 호출에 구조적 체크리스트 한 줄을 항상 덧붙인다 — 신규 LLM 호출 없음. (2) `quickCheck` 안에 static 판별 메서드 2개를 추가해 `log.debug`로만 후보를 남긴다 — score·issues·passed에는 영향 없음.

**Tech Stack:** Java 17 · Spring Boot 3.3 (`ai-user/llm` 모듈, 독립 gradle) · JUnit 5.

**Spec:** 이 문서 자체가 spec을 겸한다 (별도 spec 문서 없음 — 스코프가 단일 파일 쌍 + 문서 1건으로 작아 브레인스토밍 문서를 생략함, `.claude/rules/skill-ops.md` §1 조건부 규정에 따름).

## 배경 — 왜 스코어링 게이트가 아니라 로그 전용인가

애초 가설은 이 두 패턴을 새 정규식 체크로 추가해 점수를 깎는 것이었다. 로컬 블라인드 코퍼스(`.history/.result/ai-user-v2/eval/blind_kit_v*.md` 등 15개 파일)를 실사 조사한 결과:

- **마무리 요약 문단**: "정리하자면"/"요약하자면"/"결론적으로"는 코퍼스에 0건. "결국"은 8건 있지만 전부 **문단 시작이 아니라 서사 중간의 인과 접속사**로 쓰임.
- **대칭 대조("아니라")**: 같은 글 안에서 2회 이상 반복되는 사례가 0건 — 전부 서로 다른 글에 1회씩. 실제 용례도 가설과 다름 — "이건 신뢰가 아니라 그냥..."/"한두 마디가 아니라 계속 욕을 했어"는 "~다"로 끝나는 대칭 종결형이 아니라 반말 구어체의 자연스러운 부정 강조.
- 결정적으로 이 15개 파일은 **AI/사람 라벨이 빈칸인 설문지 원본**이라 애초에 "AI가 이걸 더 많이 쓰는가"를 검증할 수 없었다.

가설을 검증할 데이터가 없는 상태에서 점수를 깎는 체크를 넣으면, 정상적인 반말 구어체("아니라")까지 오탐해 불필요한 LLM 재시도(코스트)를 유발할 위험이 있다. 그래서 이번 작업은: (a) 비용 없는 프롬프트 체크리스트 추가, (b) 점수에 영향 없는 로그 수집으로 범위를 좁힌다. 스코어링 편입은 실제 생성물 로그가 쌓인 뒤 별도 작업으로 재검토한다.

## Global Constraints

- 새 env var·설정 플래그를 추가하지 않는다 (YAGNI — 로그 전용이라 게이트가 불필요).
- 신규 LLM 호출을 추가하지 않는다 — `buildRetryPrompt` 체크리스트는 이미 일어나는 재시도 호출에만 얹는다.
- `quickCheck`의 반환값(`score`/`issues`/`passed`)은 이 두 패턴으로 인해 절대 바뀌지 않는다.
- 모든 응답 문자열·로그 메시지·주석은 한국어(AGENTS.md 프로젝트 관례).
- `main` 브랜치에 직접 커밋한다 — worktree/topic 브랜치 생성 금지 (AGENTS.md 절대 규칙 #9).
- 커밋 전 Doc-Sync 게이트: `git diff --staged --name-only` → 코드 변경과 같은 커밋에 문서 갱신 → `python3 scripts/lint_docs.py` 통과.
- 테스트 명령: `cd ai-user/llm && ./gradlew test --tests "*SelfCritiqueServiceTest*"`.

---

### Task 1: SelfCritiqueService — 구조적 후보 탐지 + 재시도 체크리스트

**Files:**
- Modify: `ai-user/llm/src/main/java/com/againspring/aiuser/llm/service/SelfCritiqueService.java:96-243` (`quickCheck`에 로그 전용 호출 추가), `:361-386` (`buildRetryPrompt`에 체크리스트 추가)
- Test: `ai-user/llm/src/test/java/com/againspring/aiuser/llm/service/SelfCritiqueServiceTest.java`

**Interfaces:**
- Produces: `static boolean SelfCritiqueService.hasClosingSummaryParagraph(String text)`, `static int SelfCritiqueService.countSymmetricContrast(String text)` — 둘 다 package-private static, `tokenizeForRareVocab`(같은 파일 L403)과 동일한 테스트 가능 컨벤션.
- Consumes: 없음 (신규 독립 메서드).

- [ ] **Step 1: 실패하는 테스트부터 작성 — static 탐지 메서드**

`ai-user/llm/src/test/java/com/againspring/aiuser/llm/service/SelfCritiqueServiceTest.java`의 108번째 줄(파일 끝, 마지막 `}` 직전) 뒤에 추가:

```java

    @Test
    void detectsClosingSummaryParagraphCandidate() {
        // 마지막 문단이 요약 표지로 시작 — 코퍼스에 실증 사례는 없어 가설 단계, 로그 전용
        String withSummary = "어제 회사에서 있었던 일임\n\n정리하자면, 팀장이 문제였다는 거임";
        assertTrue(SelfCritiqueService.hasClosingSummaryParagraph(withSummary));

        // "결국"이 문단 중간 인과 접속사로만 쓰이는 실제 코퍼스 패턴 — 오탐 아니어야 함
        String midNarrative = "어제 회사에서 있었던 일임\n\n결국 내가 다시 만들어서 제출했는데 팀장이 동료를 칭찬했음";
        assertFalse(SelfCritiqueService.hasClosingSummaryParagraph(midNarrative));
    }

    @Test
    void countsSymmetricContrastOccurrences() {
        // blind_kit_v1_20260621125413.md:129, :189 실제 문장 기반
        String twice = "내가 원하는 건 신뢰가 아니라 그냥 인정임. 한두 마디가 아니라 계속 욕을 했어";
        assertEquals(2, SelfCritiqueService.countSymmetricContrast(twice));

        String once = "내가 원하는 건 신뢰가 아니라 그냥 인정임";
        assertEquals(1, SelfCritiqueService.countSymmetricContrast(once));
    }

    @Test
    void structuralTellCandidatesDoNotAffectScore() {
        // 로그만 남기고 score/passed/issues는 절대 안 바뀌어야 함
        String text = "내가 원하는 건 신뢰가 아니라 그냥 인정임\n\n정리하자면, 그게 다임";
        SelfCritiqueService.CritiqueResult withoutOtherIssues = service.quickCheck(text, "comment", "casual");
        assertTrue(withoutOtherIssues.passed(), "구조적 후보만으로는 감점되면 안 됨: " + withoutOtherIssues.issues());
        assertEquals(7, withoutOtherIssues.score());
    }
```

- [ ] **Step 2: 테스트 실행 — 실패 확인**

Run: `cd ai-user/llm && ./gradlew test --tests "*SelfCritiqueServiceTest*"`
Expected: FAIL — `hasClosingSummaryParagraph`/`countSymmetricContrast` cannot find symbol.

- [ ] **Step 3: static 탐지 메서드 구현**

`SelfCritiqueService.java`에서 `EMPHASIS_WORD`/`SOB_RUN` 패턴 선언부(L79-82) 바로 다음에 추가:

```java
    // ── 구조적 AI투 후보 (2026-09-02, 로그 전용 — 점수 미반영) ──────────────
    // 로컬 블라인드 코퍼스(.history/.result)에 AI/사람 라벨이 없어 사전 캘리브레이션 불가.
    // score/issues에 반영하지 않고 log.debug만 남긴다. 실제 생성물 로그가 쌓이면 재검토.
    private static final Pattern CLOSING_SUMMARY_MARKER =
        Pattern.compile("^(?:결국|정리하자면|요약하자면|결론적으로|결론은)[,\\s]");
    private static final Pattern SYMMETRIC_CONTRAST = Pattern.compile("아니라");

    /** 마지막 문단(빈 줄 기준 분리)이 요약 표지로 시작하는지. */
    static boolean hasClosingSummaryParagraph(String text) {
        if (text == null || text.isBlank()) return false;
        String[] paragraphs = text.strip().split("\\n\\s*\\n");
        String last = paragraphs[paragraphs.length - 1].trim();
        return CLOSING_SUMMARY_MARKER.matcher(last).find();
    }

    /** "아니라" 등장 횟수 — 2회 이상이면 대칭 대조 남용 후보. */
    static int countSymmetricContrast(String text) {
        if (text == null) return 0;
        java.util.regex.Matcher m = SYMMETRIC_CONTRAST.matcher(text);
        int n = 0;
        while (m.find()) n++;
        return n;
    }
```

`quickCheck` 메서드(L240 `boolean passed = score >= passThreshold;` 바로 위)에 추가:

```java
        // 구조적 AI투 후보 — 점수 미반영, 로그만 (캘리브레이션 데이터 수집용)
        if (hasClosingSummaryParagraph(text)) {
            log.debug("[STRUCTURAL_TELL_CANDIDATE] closing-summary-paragraph type={}", contentType);
        }
        int contrastCount = countSymmetricContrast(text);
        if (contrastCount >= 2) {
            log.debug("[STRUCTURAL_TELL_CANDIDATE] symmetric-contrast count={} type={}", contrastCount, contentType);
        }
```

- [ ] **Step 4: 테스트 실행 — 통과 확인**

Run: `cd ai-user/llm && ./gradlew test --tests "*SelfCritiqueServiceTest*"`
Expected: PASS (신규 3건 포함).

- [ ] **Step 5: 재시도 프롬프트 체크리스트 테스트 작성**

같은 테스트 파일, `retryPromptOmitsOriginalGenerationBlobAndKeepsFullDraft`(L74-87) 테스트 바로 뒤에 추가:

```java

    @Test
    void retryPromptIncludesStructuralTellChecklist() {
        String prompt = service.buildRetryPrompt(
                "다들 어떻게 생각해요? 저만 이상한가요 진짜",
                java.util.List.of("반말 위반(~요/~어요 사용) — ~음/~임/~더라 류 반말로 고쳐라"),
                "post",
                "casual");
        assertTrue(prompt.contains("마지막 문단"));
        assertTrue(prompt.contains("아니라"));
    }
```

- [ ] **Step 6: 테스트 실행 — 실패 확인**

Run: `cd ai-user/llm && ./gradlew test --tests "*SelfCritiqueServiceTest*"`
Expected: FAIL — `retryPromptIncludesStructuralTellChecklist` assertion false (체크리스트 문구 없음).

- [ ] **Step 7: buildRetryPrompt에 체크리스트 삽입**

`SelfCritiqueService.java:377-385`의 반환 블록을 교체:

```java
        return """
                아래 %s만 다시 써라. JSON·스키마·페르소나 목록·원본 생성 프롬프트를 출력하지 마라.
                %s
                고칠 문제: %s
                다음도 피하라: 마지막 문단에서 전체를 요약해 마무리하지 마라. "A가 아니라 B다" 식 대조 구문을 반복하지 마라. 비슷한 구조의 문장을 세 개 나란히 나열하지 마라. 별것 아닌 일을 거창한 의미로 포장하지 마라.
                의미·사실·줄바꿈 구조를 유지하고 문제만 고쳐라. 본문만 출력하라.

                [원문]
                %s
                """.formatted(kind, register, issueDetail, draft == null ? "" : draft);
```

- [ ] **Step 8: 전체 테스트 실행 — 통과 확인**

Run: `cd ai-user/llm && ./gradlew test --tests "*SelfCritiqueServiceTest*"`
Expected: PASS — 전체 (기존 8건 + 신규 4건 = 12건).

- [ ] **Step 9: Commit**

```bash
git add ai-user/llm/src/main/java/com/againspring/aiuser/llm/service/SelfCritiqueService.java \
        ai-user/llm/src/test/java/com/againspring/aiuser/llm/service/SelfCritiqueServiceTest.java
git commit -m "$(cat <<'EOF'
feat(ai-user): 구조적 AI투 후보를 재시도 체크리스트+로그로 무비용 완화한다

마지막 문단 요약·"아니라" 대칭 대조 반복은 로컬 블라인드 코퍼스에 AI/사람
라벨이 없어 스코어링 임계값을 캘리브레이션할 수 없었다. 대신 이미 걸리는
재시도 프롬프트에 체크리스트를 얹고(신규 호출 없음), 발생 빈도는
score/issues에 영향 없이 log.debug만 남겨 향후 캘리브레이션 데이터를 모은다.

Doc-Sync: docs/ai-user/70-policy/llm-call-budget.md §1.3 갱신 (별도 커밋 병합)
EOF
)"
```

---

### Task 2: Doc-Sync — `llm-call-budget.md` 갱신

**Files:**
- Modify: `docs/ai-user/70-policy/llm-call-budget.md:49-51` (§1.3 뒤에 문단 추가)

**Interfaces:**
- Consumes: Task 1에서 확정된 동작 설명 (신규 LLM 호출 없음, 점수 미반영, `hasClosingSummaryParagraph`/`countSymmetricContrast` 메서드명, `buildRetryPrompt` 체크리스트).
- Produces: 없음 (문서만).

이 태스크는 Task 1과 **다른 파일**만 건드리므로 독립적으로 병렬 진행 가능하다. Task 1의 정확한 메서드명·동작은 이미 이 계획서에 확정돼 있으므로 Task 1의 코드 완료를 기다릴 필요 없다.

- [ ] **Step 1: 현재 §1.3 확인**

`docs/ai-user/70-policy/llm-call-budget.md:49-51`:
```markdown
### 1.3 PLAN structured 내부 SelfCritique

`/v2/generate/thread-plan` 파싱 후 본문·댓글 각각 `critiqueAndRefine`을 탄다. `quickCheck` PASS면 **추가 CLI 호출 없음**. FAIL이면 항목당 짧은 rewrite 1회. 생성 JSON 스키마 호출과는 별개다.
```

- [ ] **Step 2: 문단 추가**

L51("생성 JSON 스키마 호출과는 별개다.") 바로 뒤에 새 문단 삽입:

```markdown

**구조적 AI투 후보 로깅 (2026-09-02, 점수 미반영)**: `quickCheck`가 마지막 문단 요약·"아니라" 대칭 대조 반복(`SelfCritiqueService.hasClosingSummaryParagraph`/`countSymmetricContrast`)을 감지하면 `log.debug`로만 남긴다. score·passed·LLM 호출 횟수에는 영향 없음 — 로컬 블라인드 코퍼스에 AI/사람 라벨이 없어 사전 캘리브레이션이 불가능해 실제 생성물 로그로 데이터를 모으는 단계다. `buildRetryPrompt`의 재시도 프롬프트에는 이 두 패턴을 포함한 구조적 체크리스트를 항상 덧붙인다(신규 호출 없음, 기존 재시도 지시문에 한 줄 추가).
```

- [ ] **Step 3: lint_docs.py 통과 확인**

Run: `python3 scripts/lint_docs.py`
Expected: PASS (code-ref 경로 `SelfCritiqueService.java`가 실존하므로 통과).

- [ ] **Step 4: Commit**

Task 1 커밋과 **분리하지 않는다** — AGENTS.md 절대 규칙 #8("대응 문서 + README를 코드에 맞춰 같은 커밋에서 갱신")에 따라 Task 1의 커밋에 이 문서 변경을 합쳐 하나의 커밋으로 만든다. 두 태스크를 병렬 에이전트로 실행했더라도 최종 커밋은 조율자(오케스트레이터)가 파일 변경을 모아 한 번에 수행한다.

```bash
git add docs/ai-user/70-policy/llm-call-budget.md
git commit -m "$(cat <<'EOF'
docs(ai-user): 구조적 AI투 후보 로깅을 llm-call-budget SSOT에 반영한다

Doc-Sync: SelfCritiqueService.java 변경(구조적 후보 탐지+재시도 체크리스트)에
대응. 신규 호출 없음·점수 미반영임을 명시해 향후 참조 시 오해를 막는다.
EOF
)"
```

(오케스트레이터가 두 파일을 한 커밋으로 합칠 경우 이 Step은 Task 1 Step 9에 파일만 추가하고 커밋 메시지 본문에 Doc-Sync 대상을 함께 명시하는 것으로 대체한다.)

---

## Self-Review 체크

- **Spec coverage**: 배경 섹션의 두 요구사항(체크리스트 무비용 추가, 로그 전용 데이터 수집) 모두 Task 1이 구현. Doc-Sync 요구(AGENTS.md #8)는 Task 2가 커버.
- **Placeholder scan**: 모든 코드·테스트·정규식·커밋 메시지가 실제 값. TBD/TODO 없음.
- **Type consistency**: `hasClosingSummaryParagraph(String)→boolean`, `countSymmetricContrast(String)→int` — Task 1 Step 3 선언과 Step 1 테스트 호출부가 일치.
