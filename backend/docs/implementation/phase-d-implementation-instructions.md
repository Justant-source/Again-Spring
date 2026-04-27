# Phase D 컨텍스트 알고리즘 — Claude Code 구현 지시서

> **이 문서의 위치**: 본 문서는 [`shared/docs/policies/context-algorithm.md`](../policies/context-algorithm.md) 정책의 **구현 지시서**입니다. Claude Code가 백엔드 코드를 작성할 때 이 문서를 *작업 단위*로 사용합니다. 정책 차원의 의문은 항상 권위본([`context-algorithm.md`](../policies/context-algorithm.md))으로 회귀합니다.
>
> **권장 작업 위치**: `backend/docs/implementation/phase-d-context-algorithm.md` 또는 본 문서를 `shared/docs/`에 두는 경우 `shared/docs/implementation/phase-d-context-algorithm.md`. CLAUDE.md의 "4-디렉토리 규칙" 준수.
>
> **작업 단위**: 6개 PR. 각 PR은 독립 머지 가능, 회귀 0 보장.
>
> **현재 기준일**: 2026-04-27

---

## 작업 시작 전 필수 점검

Claude Code가 본 작업을 시작하기 전 다음을 *반드시* 읽고 머리에 넣어야 합니다.

1. **권위본 정책** (모두 읽음 필수)
   - `shared/docs/policies/context-algorithm.md` ← 본 작업의 권위본
   - `shared/docs/policies/psychology-model.md` ← "추적 변수 4개 제한" 절대 준수
   - `shared/docs/policies/categories.md` ← 한국 고유 4종 주의사항
   - `shared/docs/policies/forbidden-words.md` ← 출력 검증
   - `shared/docs/policies/crisis-detection.md` ← 위기 감지 시 알고리즘 비활성
   - `shared/docs/policies/data-retention.md` ← 30일 만료 적용

2. **기존 구현 (동등 레이어 — 패턴 참조용)**
   - `backend/src/main/java/com/againspring/service/prompt/ChatPromptAssembler.java`
   - `backend/src/main/java/com/againspring/service/prompt/PsychologyFeedbackFormatter.java`
   - `backend/src/main/java/com/againspring/service/prompt/DuoBalanceFormatter.java`
   - `backend/src/main/java/com/againspring/service/prompt/UserProfileFragment.java`
   - `backend/src/main/java/com/againspring/service/parser/ChatTurnMetaParser.java`
   - `backend/src/main/java/com/againspring/service/ChatService.java`
   - `backend/src/main/java/com/againspring/domain/Session.java`

3. **프롬프트 (변경 대상)**
   - `shared/docs/prompts/chat/_response_instructions.md`
   - `shared/docs/prompts/chat/duo_chat.md`
   - `shared/docs/prompts/chat/welcome_partner.md` ← 신규 작성

4. **CLAUDE.md 절대 규칙 준수**
   - 모든 LLM 요청은 BE 경유
   - 코드 위치 규칙 (`shared`/`backend`/`frontend`/`env`)
   - 권위본 정책 우선
   - dev 배포 후 수동 확인 → main push → prod 배포

---

## 작업 흐름 — 6개 PR

각 PR은 독립적으로 dev 배포 → 5종 시나리오 검증 → main push 가능합니다. PR 사이에 회귀 0이 보장되도록 설계됐습니다.

### PR 순서와 의존성

```
PR-1 (골격) ─→ PR-2 (UserState) ─→ PR-3 (IssueContext) ─→ PR-4 (QuestionQueue) ─→ PR-5 (B 환영)
                                                                                       │
                                                                                       ↓
                                                                                  PR-6 (운영도구)
```

PR-2 머지 없이 PR-3 시작 가능하지만, 권장은 순서대로.

---

## PR-1: 골격 도입 (1~2일)

**목표**: 모든 신규 클래스·DB 컬럼·프롬프트 슬롯을 만들지만, 모든 fragment가 빈 문자열만 반환. 시스템 동작 변화 0.

### 구현 항목

#### [1.1] Flyway 마이그레이션

**파일**: `backend/src/main/resources/db/migration/V10__phase_d_context_algorithm.sql`

```sql
-- Phase D - 컨텍스트 알고리즘 신규 컬럼
-- 권위본: shared/docs/policies/context-algorithm.md

ALTER TABLE sessions
    ADD COLUMN user_state_history JSON NULL COMMENT 'Phase D - UserState 전이 이력',
    ADD COLUMN issue_context JSON NULL COMMENT 'Phase D - 누적 이슈 컨텍스트',
    ADD COLUMN question_queue_a JSON NULL COMMENT 'Phase D - A에게 물을 질문 PQ',
    ADD COLUMN question_queue_b JSON NULL COMMENT 'Phase D - B에게 물을 질문 PQ';

-- 인덱스 불필요 — 모든 접근은 PK로 세션 조회 후 deserialize.
-- current_focus 컬럼은 PR-3에서 issue_context.headline과 동기화. PR-1에선 변경 없음.
```

#### [1.2] Session 도메인 확장

**파일**: `backend/src/main/java/com/againspring/domain/Session.java`

기존 `HorsemenTurnEntry` / `NvcTurnEntry` 옆에 다음 inner classes 추가:

```java
// === Phase D enums ===

public enum UserState {
    OPENING, VENTING, DEFENSIVE, BLAMING, REFLECTING, NEGOTIATING, RESOLVING
}

public enum Intent {
    SEEK_FACT, SEEK_FEELING, SEEK_NEED, BRIDGE_PERSPECTIVE,
    REFLECT_PATTERN, INVITE_REPAIR, WELCOME_PARTNER
}

public enum RatioElement {
    BOUNDARY, HORSEMEN, REPAIR, PERSPECTIVE, ESCALATION
}

// === Phase D inner data classes (모두 public static, Lombok 미사용 — 기존 패턴과 동일) ===

public static class UserStateEntry {
    public Integer turn;
    public String sender;            // USER_A | USER_B
    public UserState state;
    public String evidenceSnippet;   // 30자
    public Double confidence;
    public String derivedFrom;
}

public static class IssueContext {
    public String headline;          // 50자, currentFocus 대체
    public List<IssueFact> facts = new ArrayList<>();
    public List<NeedSlot> namedNeeds = new ArrayList<>();
    public List<UnresolvedThread> threads = new ArrayList<>();
    public Integer revision = 0;
    public Instant lastUpdatedAt;
}

public static class IssueFact {
    public String text;              // 80자
    public String source;            // "USER_A_T3" 형식
    public Boolean confirmedByOther = false;
    public RatioElement contributesTo;  // nullable
    public String categoryRule;      // nullable — categories.md의 룰 ID
}

public static class NeedSlot {
    public String text;              // 60자
    public String owner;             // USER_A | USER_B
    public Integer firstMentionedTurn;
    public RatioElement contributesTo;
}

public static class UnresolvedThread {
    public String text;              // 60자
    public String origin;
    public Integer mentionedTurn;
    public Boolean addressedByQueue = false;
    public Integer ageInTurns = 0;
}

public static class PendingQuestion {
    public String id;                // UUID
    public Intent intent;
    public String target;            // USER_A | USER_B
    public String text;              // 80자, LLM에게 단서. 그대로 발화 X.
    public String hookFromIssue;
    public RatioElement antidoteFor;
    public Double priority = 0.0;
    public Integer createdTurn;
    public Integer ageInTurns = 0;
    public Boolean asked = false;
    public Integer askedTurn;
    public String categoryRuleApplied;
}
```

기존 필드 옆에 4개 JSON 컬럼 추가 (현재 `horsemen_history` 아래 위치):

```java
@Type(JsonType.class)
@Column(name = "user_state_history", columnDefinition = "JSON")
private List<UserStateEntry> userStateHistory;

@Type(JsonType.class)
@Column(name = "issue_context", columnDefinition = "JSON")
private IssueContext issueContext;

@Type(JsonType.class)
@Column(name = "question_queue_a", columnDefinition = "JSON")
private List<PendingQuestion> questionQueueA;

@Type(JsonType.class)
@Column(name = "question_queue_b", columnDefinition = "JSON")
private List<PendingQuestion> questionQueueB;
```

#### [1.3] 신규 Fragment 골격 (모두 빈 반환)

**파일**: `backend/src/main/java/com/againspring/service/prompt/IssueContextFragment.java`

```java
package com.againspring.service.prompt;

import com.againspring.domain.Session;
import org.springframework.stereotype.Component;

/**
 * Phase D — IssueContext를 프롬프트에 주입할 XML 블록으로 렌더.
 * PR-1 단계: 항상 빈 문자열 반환. PR-3에서 실제 로직 추가.
 *
 * 권위본: shared/docs/policies/context-algorithm.md §4.2
 */
@Component
public class IssueContextFragment {
    public String render(Session session) {
        return ""; // PR-3에서 구현
    }
}
```

**파일**: `backend/src/main/java/com/againspring/service/prompt/UserStateFragment.java`

```java
package com.againspring.service.prompt;

import com.againspring.domain.Session;
import org.springframework.stereotype.Component;

/**
 * Phase D — 가장 최근 UserStateEntry를 프롬프트에 주입.
 * PR-1 단계: 빈 반환. PR-2에서 실제 로직.
 *
 * 권위본: shared/docs/policies/context-algorithm.md §4.3
 */
@Component
public class UserStateFragment {
    public String render(Session session, boolean isDuo) {
        return ""; // PR-2에서 구현
    }
}
```

**파일**: `backend/src/main/java/com/againspring/service/prompt/QuestionQueueFragment.java`

```java
package com.againspring.service.prompt;

import com.againspring.domain.Session;
import com.againspring.domain.enums.MessageSender;
import org.springframework.stereotype.Component;

/**
 * Phase D — 현재 사용자의 PQ 상위 N개를 프롬프트에 주입.
 * PR-1 단계: 빈 반환. PR-4에서 실제 로직.
 *
 * 권위본: shared/docs/policies/context-algorithm.md §4.4
 */
@Component
public class QuestionQueueFragment {
    public String render(Session session, MessageSender currentUserSender) {
        return ""; // PR-4에서 구현
    }
}
```

#### [1.4] ChatPromptAssembler에 fragment 호출 추가

**파일**: `backend/src/main/java/com/againspring/service/prompt/ChatPromptAssembler.java`

기존 코드의 *psychology_feedback 직후*에 3개 fragment 호출. 위치는 `assembleSoloTurn`과 `assembleDuoTurn` 둘 다.

```java
// 의존성 주입 추가 (생성자 옆에)
private final IssueContextFragment issueContextFragment;
private final UserStateFragment userStateFragment;
private final QuestionQueueFragment questionQueueFragment;
```

`assembleSoloTurn` 안 — 기존 `psychologyFeedback.render(session)` 직후:

```java
String feedback = psychologyFeedback.render(session);
if (!feedback.isEmpty()) {
    sb.append(feedback).append("\n");
}

// === Phase D 추가 — 순서: issue → state → queue ===
String issue = issueContextFragment.render(session);
if (!issue.isEmpty()) sb.append(issue).append("\n");

String state = userStateFragment.render(session, false);
if (!state.isEmpty()) sb.append(state).append("\n");

String queue = questionQueueFragment.render(session, userSender);
if (!queue.isEmpty()) sb.append(queue).append("\n");
// === Phase D 끝 ===

sb.append(safeLoad("relations/" + session.getRelationType().getValue() + ".md")).append("\n\n");
```

`assembleDuoTurn` 동일 패턴 — `userSender` 대신 `currentUserSender` 사용. `userStateFragment.render(session, true)` 호출 (Duo 모드).

#### [1.5] 단위 테스트

**파일**: `backend/src/test/java/com/againspring/service/prompt/PhaseDFragmentSkeletonTest.java`

```java
@Test
void issueContextFragment_returnsEmptyString_whenSessionEmpty() {
    Session session = new Session();
    assertEquals("", new IssueContextFragment().render(session));
}

@Test
void userStateFragment_returnsEmptyString_whenSessionEmpty() {
    Session session = new Session();
    assertEquals("", new UserStateFragment().render(session, false));
    assertEquals("", new UserStateFragment().render(session, true));
}

@Test
void questionQueueFragment_returnsEmptyString_whenQueueNull() {
    Session session = new Session();
    assertEquals("", new QuestionQueueFragment().render(session, MessageSender.USER_A));
    assertEquals("", new QuestionQueueFragment().render(session, MessageSender.USER_B));
}
```

### PR-1 머지 검증

- [ ] `./gradlew test` 통과
- [ ] `./gradlew bootRun` 정상 시작
- [ ] Flyway V10이 dev DB에 적용됨 (`information_schema.columns`로 4개 컬럼 확인)
- [ ] 5종 기존 시나리오 LLM 응답이 *Phase D 적용 전과 동일* (회귀 0 검증)
- [ ] 신규 컴포넌트들이 Spring context에 정상 등록 (애플리케이션 로그 확인)

---

## PR-2: UserState 단독 도입 (2~3일)

**목표**: 가장 단순한 메타 1개만 활성화. 7개 enum 분류가 LLM 응답으로 들어오기 시작.

### 구현 항목

#### [2.1] _response_instructions.md 갱신

**파일**: `shared/docs/prompts/chat/_response_instructions.md`

기존 `<turn_meta>` JSON 형식 안내 직후, 다음 단락 추가:

```markdown
## Phase D 메타 필드 — `user_state` (옵션)

`<turn_meta>` 안에 사용자의 현재 대화 상태를 1개 라벨로 분류해 추가합니다.

\`\`\`jsonc
{
  // ... 기존 horsemen, nvc_completion ...
  "user_state": {
    "state": "VENTING",
    "evidence": "며칠 전부터 그런 분위기였거든요",
    "confidence": 0.7,
    "derived_from": "horsemen.criticism=0.5, nvc.feeling=true"
  }
}
\`\`\`

**state**: 다음 7개 중 하나
- `OPENING` — 막 시작, 본 이슈 진입 전 (보통 처음 1~2턴)
- `VENTING` — 감정·상황 풀어내는 중 (가장 흔한 기본값)
- `DEFENSIVE` — 자기 방어 중 (4 Horsemen defensiveness ≥ 0.4 신호)
- `BLAMING` — 상대 비난 중 (criticism 또는 contempt 신호)
- `REFLECTING` — 자기 입장을 거리 두고 보는 중 ("사실 저도", "근데 제가" 같은 자기 인정 단서)
- `NEGOTIATING` — 받기·주기 탐색 중 (NVC request 단계 시도)
- `RESOLVING` — 결심·해결 시그널 ("해볼게", "알겠어")

**evidence**: 메시지에서 30자 이내 발췌. 분류 근거.
**confidence**: 0.0~1.0. 모르겠으면 0.3 이하.
**derived_from**: "horsemen.X=N, nvc.Y=Z" 같은 산출 근거 요약. 디버그용.

확신이 없으면 `VENTING`이 안전한 기본값입니다. 모르겠으면 user_state 필드를 통째로 생략해도 됩니다.

**절대 금지**: 본문에 "당신은 지금 자기 방어 중입니다" 같은 라벨 노출.
```

#### [2.2] ChatTurnMetaParser 확장

**파일**: `backend/src/main/java/com/againspring/service/parser/ChatTurnMetaParser.java`

`Result` record에 `userState` 필드 추가:

```java
public record Result(
    String mediatorMessage,
    Session.HorsemenTurnEntry horsemen,
    Session.NvcTurnEntry nvc,
    Session.UserStateEntry userState   // 신규
) {}
```

`parse()` 메서드의 try 블록에서 `user_state` 노드 파싱:

```java
JsonNode root = objectMapper.readTree(json);
horsemen = readHorsemen(root.get("horsemen"), turn, senderTag);
nvc = readNvc(root.get("nvc_completion"), turn, senderTag);
userState = readUserState(root.get("user_state"), turn, senderTag);  // 신규
```

`readUserState()` 메서드 추가:

```java
private Session.UserStateEntry readUserState(JsonNode node, int turn, String sender) {
    if (node == null || !node.isObject()) return null;
    Session.UserStateEntry e = new Session.UserStateEntry();
    e.turn = turn;
    e.sender = sender;
    JsonNode stateNode = node.get("state");
    if (stateNode == null || stateNode.isNull()) return null;
    try {
        e.state = Session.UserState.valueOf(stateNode.asText());
    } catch (IllegalArgumentException ex) {
        log.warn("Unknown UserState: {}", stateNode.asText());
        return null;
    }
    JsonNode evNode = node.get("evidence");
    e.evidenceSnippet = (evNode != null && !evNode.isNull()) ? trim(evNode.asText(), 30) : null;
    JsonNode confNode = node.get("confidence");
    e.confidence = readDouble(confNode);
    JsonNode derNode = node.get("derived_from");
    e.derivedFrom = (derNode != null && !derNode.isNull()) ? derNode.asText() : null;
    return e;
}

private String trim(String s, int max) {
    return s.length() <= max ? s : s.substring(0, max);
}
```

#### [2.3] UserStateAppender 신규

**파일**: `backend/src/main/java/com/againspring/service/context/UserStateAppender.java`

```java
package com.againspring.service.context;

import com.againspring.domain.Session;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

/**
 * Phase D - LLM이 분류한 UserState를 Session.userStateHistory에 누적.
 * 권위본: shared/docs/policies/context-algorithm.md §4.3
 */
@Component
public class UserStateAppender {

    /** PR-2 — 단순 append. PR-3 이후 변화 패턴 감지 추가 검토. */
    public void append(Session session, Session.UserStateEntry entry) {
        if (entry == null || entry.state == null) return;
        List<Session.UserStateEntry> hist = session.getUserStateHistory();
        if (hist == null) hist = new ArrayList<>();
        hist.add(entry);
        session.setUserStateHistory(hist);
    }
}
```

#### [2.4] UserStateFragment 실제 로직

**파일**: `backend/src/main/java/com/againspring/service/prompt/UserStateFragment.java`

```java
package com.againspring.service.prompt;

import com.againspring.domain.Session;
import com.againspring.domain.enums.MessageSender;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class UserStateFragment {

    public String render(Session session, boolean isDuo) {
        List<Session.UserStateEntry> hist = session.getUserStateHistory();
        if (hist == null || hist.isEmpty()) return "";

        Session.UserStateEntry latestA = latestFor(hist, MessageSender.USER_A);
        Session.UserStateEntry latestB = isDuo ? latestFor(hist, MessageSender.USER_B) : null;
        if (latestA == null && latestB == null) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("<user_states note=\"현재 사용자(들)의 대화 상태. 톤 조정에만 사용. 본문 인용 금지.\">\n");
        if (latestA != null) {
            sb.append("- USER_A: ").append(latestA.state.name())
              .append(" (turn ").append(latestA.turn).append(")\n");
        }
        if (latestB != null) {
            sb.append("- USER_B: ").append(latestB.state.name())
              .append(" (turn ").append(latestB.turn).append(")\n");
        }
        sb.append("</user_states>\n");
        return sb.toString();
    }

    private Session.UserStateEntry latestFor(List<Session.UserStateEntry> hist, MessageSender sender) {
        Session.UserStateEntry latest = null;
        for (Session.UserStateEntry e : hist) {
            if (sender.name().equals(e.sender)) latest = e;
        }
        return latest;
    }
}
```

#### [2.5] ChatService 통합

**파일**: `backend/src/main/java/com/againspring/service/ChatService.java`

기존 `appendPsychologyHistory(session, parsed)` 호출 직후에 추가:

```java
appendPsychologyHistory(session, parsed);
userStateAppender.append(session, parsed.userState());  // 신규
```

생성자에 `UserStateAppender userStateAppender` 의존성 주입.

#### [2.6] 단위 테스트

**파일**: `backend/src/test/java/com/againspring/service/context/UserStateAppenderTest.java`

```java
@Test
void append_addsEntry_whenStateValid() {
    Session session = new Session();
    Session.UserStateEntry entry = new Session.UserStateEntry();
    entry.state = Session.UserState.VENTING;
    entry.sender = "USER_A";
    entry.turn = 1;

    new UserStateAppender().append(session, entry);

    assertEquals(1, session.getUserStateHistory().size());
    assertEquals(Session.UserState.VENTING, session.getUserStateHistory().get(0).state);
}

@Test
void append_doesNothing_whenEntryNull() {
    Session session = new Session();
    new UserStateAppender().append(session, null);
    assertNull(session.getUserStateHistory());
}
```

**파일**: `backend/src/test/java/com/againspring/service/parser/ChatTurnMetaParserUserStateTest.java`

```java
@Test
void parse_extractsUserState() {
    String response = "응답 본문\n\n<turn_meta>{\n"
        + "\"horsemen\":{\"criticism\":0.0,\"contempt\":0.0,\"defensiveness\":0.0,\"stonewalling\":0.0},\n"
        + "\"nvc_completion\":{\"observation\":false,\"feeling\":true,\"need\":false,\"request\":false},\n"
        + "\"user_state\":{\"state\":\"VENTING\",\"evidence\":\"무거운 분위기\",\"confidence\":0.7,\"derived_from\":\"nvc.feeling=true\"}\n"
        + "}</turn_meta>";

    ChatTurnMetaParser.Result result = new ChatTurnMetaParser().parse(response, 3, "USER_A");

    assertNotNull(result.userState());
    assertEquals(Session.UserState.VENTING, result.userState().state);
    assertEquals("무거운 분위기", result.userState().evidenceSnippet);
    assertEquals(0.7, result.userState().confidence);
}

@Test
void parse_handlesUnknownState_returnsNull() {
    String response = "본문\n\n<turn_meta>{\"user_state\":{\"state\":\"UNKNOWN_STATE\"}}</turn_meta>";
    ChatTurnMetaParser.Result result = new ChatTurnMetaParser().parse(response, 1, "USER_A");
    assertNull(result.userState());
}
```

### PR-2 머지 검증

- [ ] `./gradlew test` 통과
- [ ] dev 배포 후 5턴 이상 진행하며 `<user_states>` 블록이 LLM 프롬프트에 들어가는지 확인 (BE 로그)
- [ ] `user_state_history` JSON 컬럼이 채워짐 (DB 직접 조회)
- [ ] LLM이 `user_state` 필드를 응답에 포함하는 비율 측정 (메트릭 또는 로그 분석)
- [ ] 회귀: 부적절한 응답이 *늘어나지 않음*

---

## PR-3: IssueContext 도입 (3~5일)

**목표**: 이슈가 누적되기 시작. 카테고리 룰 검증 동작.

### 구현 항목

#### [3.1] _response_instructions.md 갱신

`user_state` 다음에 `issue_delta` 안내 추가:

```markdown
## Phase D 메타 필드 — `issue_delta` (옵션)

이번 턴 발화에서 *새로 확인된* 이슈 컨텍스트만 변경분으로 보고합니다. 기존 컨텍스트는 보존됩니다.

\`\`\`jsonc
{
  "issue_delta": {
    "headline": "최근 며칠간 이어진 무거운 분위기",
    "facts_added": [
      {
        "text": "어제 인사 없이 지나침",
        "source": "USER_A_T1",
        "contributesTo": "BOUNDARY",
        "categoryRule": null
      }
    ],
    "facts_confirmed": ["어제 인사 없이 지나침"],
    "needs_added": [
      {"text": "관심받고 있다는 느낌이 필요", "owner": "USER_A", "contributesTo": "PERSPECTIVE"}
    ],
    "threads_added": [
      {"text": "며칠 전 분위기가 무거웠던 이유", "origin": "USER_A_T2"}
    ],
    "threads_resolved": []
  }
}
\`\`\`

- **headline**: 50자 이내. 이번 턴에 갱신 필요할 때만. 미변경이면 null.
- **facts_added**: 80자 이내. *추측이 아닌 사용자 발화에 명시된 사실만*.
- **facts_confirmed** (Duo 모드만): 양쪽이 인정한 사실의 텍스트 배열.
- **needs_added**: 60자 이내. NVC §욕구 단계의 명시.
- **threads_added**: 60자 이내. 이번 턴에 떠올랐지만 답하지 않은 갈래.
- **threads_resolved**: 이번 턴에 해결됐다고 보는 미해결 갈래 텍스트.
- **contributesTo**: BOUNDARY | HORSEMEN | REPAIR | PERSPECTIVE | ESCALATION 중 하나 (선택).

**카테고리별 절대 금지**:
- `in_law` 카테고리: facts에 *제3자(시어머니/장모) 판단형 표현* 저장 금지. 사실만 가능.
- `lingered` 카테고리: 단일 사건 fact 추가 금지. 누적 패턴만.
- `generation` 카테고리: 가치관 우열 시사 금지.

모르겠으면 issue_delta 필드 통째로 생략.
```

#### [3.2] ChatTurnMetaParser에 issue_delta 파싱 추가

[2.2] 패턴 동일하게 `IssueContextDelta` 별도 클래스 + `readIssueDelta` 메서드.

**파일**: `backend/src/main/java/com/againspring/service/context/IssueContextDelta.java`

```java
package com.againspring.service.context;

import com.againspring.domain.Session;
import java.util.List;

/** PR-3 — LLM이 보낸 이슈 컨텍스트 변경분. */
public class IssueContextDelta {
    public String headline;
    public List<Session.IssueFact> factsAdded;
    public List<String> factsConfirmed;
    public List<Session.NeedSlot> needsAdded;
    public List<Session.UnresolvedThread> threadsAdded;
    public List<String> threadsResolved;
}
```

`Result` record에 `IssueContextDelta issueDelta` 필드 추가.

#### [3.3] CategoryRuleEnforcer

**파일**: `backend/src/main/java/com/againspring/service/context/CategoryRuleEnforcer.java`

categories.md §"한국 고유" 룰을 코드화.

```java
package com.againspring.service.context;

import com.againspring.domain.Session;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.Set;

@Slf4j
@Component
public class CategoryRuleEnforcer {

    // categories.md §"한국 고유" 의 카테고리 ID
    private static final String IN_LAW = "in_law";
    private static final String FACE = "face";
    private static final String LINGERED = "lingered";
    private static final String GENERATION = "generation";

    private static final Set<String> THIRD_PARTY_JUDGMENT = Set.of(
        "잘못", "차별", "못된", "괴롭힘", "악의", "이기적");

    private static final Set<String> VALUE_HIERARCHY = Set.of(
        "구식", "낡은", "잘못된 가치관", "이상한 사고방식");

    /**
     * 카테고리 룰을 위반하는 fact는 false 반환 → IssueContextMerger가 거부.
     * 권위본: shared/docs/policies/categories.md §"한국 고유"
     */
    public boolean isFactAllowed(Session.IssueFact fact, String categoryMinorId) {
        if (fact == null || fact.text == null) return false;
        String text = fact.text.toLowerCase();

        if (IN_LAW.equals(categoryMinorId)) {
            for (String word : THIRD_PARTY_JUDGMENT) {
                if (text.contains(word)) {
                    log.info("Rejected fact for {} category (third-party judgment): {}",
                        IN_LAW, fact.text);
                    return false;
                }
            }
        }

        if (LINGERED.equals(categoryMinorId)) {
            // 단일 사건 인터뷰 패턴 감지: "어제", "그날", "오늘", "그때"
            if (text.matches(".*\\b(어제|그날|오늘|그때|방금)\\b.*")) {
                log.info("Rejected single-event fact for {} category: {}", LINGERED, fact.text);
                return false;
            }
        }

        if (GENERATION.equals(categoryMinorId)) {
            for (String word : VALUE_HIERARCHY) {
                if (text.contains(word)) {
                    log.info("Rejected value-hierarchy fact for {} category: {}",
                        GENERATION, fact.text);
                    return false;
                }
            }
        }

        return true;
    }

    /** 카테고리에서 비활성화된 Intent 검사. PR-4에서 사용. */
    public boolean isIntentAllowed(Session.Intent intent, String categoryMinorId) {
        if (LINGERED.equals(categoryMinorId) && intent == Session.Intent.SEEK_FACT) {
            return false; // 단일 사건 인터뷰 금지
        }
        return true;
    }
}
```

#### [3.4] RatioElementTagger

**파일**: `backend/src/main/java/com/againspring/service/context/RatioElementTagger.java`

LLM이 `contributesTo`를 명시 안 한 경우 휴리스틱으로 매핑.

```java
package com.againspring.service.context;

import com.againspring.domain.Session;
import org.springframework.stereotype.Component;

/**
 * Phase D - facts/needs를 ratio-calculation.md §5요소에 매핑.
 * LLM이 contributesTo를 명시했으면 그대로, 아니면 휴리스틱.
 */
@Component
public class RatioElementTagger {

    public Session.RatioElement tagFact(Session.IssueFact fact) {
        if (fact.contributesTo != null) return fact.contributesTo;
        String text = fact.text == null ? "" : fact.text.toLowerCase();
        if (text.matches(".*\\b(약속|거짓말|숨겼|배신)\\b.*")) {
            return Session.RatioElement.BOUNDARY;
        }
        if (text.matches(".*\\b(사과|미안|화해|받아주)\\b.*")) {
            return Session.RatioElement.REPAIR;
        }
        return null; // 분류 불가 — 그대로 두면 ratio 계산 시 무시
    }

    public Session.RatioElement tagNeed(Session.NeedSlot need) {
        if (need.contributesTo != null) return need.contributesTo;
        return Session.RatioElement.PERSPECTIVE; // 기본값 — 욕구는 대개 perspective 보강
    }
}
```

#### [3.5] IssueContextMerger

**파일**: `backend/src/main/java/com/againspring/service/context/IssueContextMerger.java`

```java
package com.againspring.service.context;

import com.againspring.domain.Session;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class IssueContextMerger {

    private static final int MAX_FACTS = 12;
    private static final int MAX_NEEDS = 8;
    private static final int MAX_THREADS = 8;

    private final CategoryRuleEnforcer ruleEnforcer;
    private final RatioElementTagger ratioTagger;

    public void merge(Session session, IssueContextDelta delta, int currentTurn) {
        if (delta == null) return;

        Session.IssueContext ctx = session.getIssueContext();
        if (ctx == null) {
            ctx = new Session.IssueContext();
            ctx.facts = new ArrayList<>();
            ctx.namedNeeds = new ArrayList<>();
            ctx.threads = new ArrayList<>();
            ctx.revision = 0;
        }

        String categoryMinor = session.getCategory() != null ? session.getCategory().getMinorId() : null;

        // headline
        if (delta.headline != null && !delta.headline.isBlank()) {
            ctx.headline = trim(delta.headline, 50);
            session.setCurrentFocus(ctx.headline); // 호환 레이어
        }

        // facts
        if (delta.factsAdded != null) {
            for (Session.IssueFact f : delta.factsAdded) {
                if (!ruleEnforcer.isFactAllowed(f, categoryMinor)) continue;
                if (containsFactText(ctx.facts, f.text)) continue;
                if (ctx.facts.size() >= MAX_FACTS) ctx.facts.remove(0);
                if (f.contributesTo == null) f.contributesTo = ratioTagger.tagFact(f);
                f.categoryRule = categoryMinor;
                ctx.facts.add(f);
            }
        }

        // facts_confirmed
        if (delta.factsConfirmed != null) {
            for (String text : delta.factsConfirmed) {
                ctx.facts.stream()
                    .filter(f -> text.equals(f.text))
                    .forEach(f -> f.confirmedByOther = true);
            }
        }

        // needs
        if (delta.needsAdded != null) {
            for (Session.NeedSlot n : delta.needsAdded) {
                if (containsNeed(ctx.namedNeeds, n.text, n.owner)) continue;
                if (ctx.namedNeeds.size() >= MAX_NEEDS) ctx.namedNeeds.remove(0);
                if (n.contributesTo == null) n.contributesTo = ratioTagger.tagNeed(n);
                n.firstMentionedTurn = currentTurn;
                ctx.namedNeeds.add(n);
            }
        }

        // threads
        if (delta.threadsAdded != null) {
            for (Session.UnresolvedThread t : delta.threadsAdded) {
                if (containsThreadText(ctx.threads, t.text)) continue;
                if (ctx.threads.size() >= MAX_THREADS) ctx.threads.remove(0);
                t.mentionedTurn = currentTurn;
                t.addressedByQueue = false;
                t.ageInTurns = 0;
                ctx.threads.add(t);
            }
        }

        // threads_resolved
        if (delta.threadsResolved != null) {
            ctx.threads.removeIf(t -> delta.threadsResolved.contains(t.text));
        }

        // age existing threads
        for (Session.UnresolvedThread t : ctx.threads) {
            t.ageInTurns = (t.ageInTurns == null ? 0 : t.ageInTurns) + 1;
        }

        ctx.revision = (ctx.revision == null ? 0 : ctx.revision) + 1;
        ctx.lastUpdatedAt = Instant.now();
        session.setIssueContext(ctx);
    }

    private String trim(String s, int max) {
        return s == null ? null : (s.length() <= max ? s : s.substring(0, max));
    }
    private boolean containsFactText(List<Session.IssueFact> list, String text) {
        return list.stream().anyMatch(f -> text.equals(f.text));
    }
    private boolean containsNeed(List<Session.NeedSlot> list, String text, String owner) {
        return list.stream().anyMatch(n -> text.equals(n.text) && owner.equals(n.owner));
    }
    private boolean containsThreadText(List<Session.UnresolvedThread> list, String text) {
        return list.stream().anyMatch(t -> text.equals(t.text));
    }
}
```

#### [3.6] IssueContextFragment 실제 로직

**파일**: `backend/src/main/java/com/againspring/service/prompt/IssueContextFragment.java`

```java
@Component
public class IssueContextFragment {

    private static final int FACTS_LIMIT = 5;
    private static final int NEEDS_LIMIT = 4;
    private static final int THREADS_LIMIT = 4;

    public String render(Session session) {
        Session.IssueContext ctx = session.getIssueContext();
        if (ctx == null || isEffectivelyEmpty(ctx)) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("<issue_context note=\"누적된 이슈 컨텍스트. ")
          .append("USER_A/USER_B 라벨을 본문에 인용 금지. 양쪽 데이터 격리 유지.\">\n");

        if (ctx.headline != null && !ctx.headline.isBlank()) {
            sb.append("- 핵심: ").append(ctx.headline).append("\n");
        }
        if (ctx.facts != null && !ctx.facts.isEmpty()) {
            sb.append("- 확인된 사실:\n");
            ctx.facts.stream().limit(FACTS_LIMIT).forEach(f ->
                sb.append("  • ").append(f.text)
                  .append(Boolean.TRUE.equals(f.confirmedByOther) ? " [양쪽 인정]" : "")
                  .append("\n"));
        }
        if (ctx.namedNeeds != null && !ctx.namedNeeds.isEmpty()) {
            sb.append("- 명시된 욕구:\n");
            ctx.namedNeeds.stream().limit(NEEDS_LIMIT).forEach(n ->
                sb.append("  • ").append(n.text).append(" (").append(n.owner).append(")\n"));
        }
        if (ctx.threads != null && !ctx.threads.isEmpty()) {
            sb.append("- 미해결 갈래:\n");
            ctx.threads.stream()
                .filter(t -> !Boolean.TRUE.equals(t.addressedByQueue))
                .limit(THREADS_LIMIT)
                .forEach(t -> sb.append("  • ").append(t.text).append("\n"));
        }
        sb.append("</issue_context>\n");
        return sb.toString();
    }

    private boolean isEffectivelyEmpty(Session.IssueContext ctx) {
        return (ctx.headline == null || ctx.headline.isBlank())
            && (ctx.facts == null || ctx.facts.isEmpty())
            && (ctx.namedNeeds == null || ctx.namedNeeds.isEmpty())
            && (ctx.threads == null || ctx.threads.isEmpty());
    }
}
```

#### [3.7] ChatService 통합

```java
appendPsychologyHistory(session, parsed);
userStateAppender.append(session, parsed.userState());
issueContextMerger.merge(session, parsed.issueDelta(), turnIndex);  // 신규
```

생성자에 `IssueContextMerger` 의존성 주입.

#### [3.8] RetentionScheduler 수정

**파일**: `backend/src/main/java/com/againspring/service/retention/RetentionScheduler.java`

`purgeExpiredContent()`의 update 쿼리에 다음 컬럼 추가:

```java
// 기존 turns.content 등 NULL 처리 옆에 추가:
em.createNativeQuery(
    "UPDATE sessions SET " +
    "user_state_history = NULL, " +
    "question_queue_a = NULL, " +
    "question_queue_b = NULL, " +
    "issue_context = JSON_OBJECT('headline', JSON_EXTRACT(issue_context, '$.headline')) " +
    "WHERE status IN ('COMPLETED', 'TERMINATED') " +
    "AND content_expires_at < :threshold")
    .setParameter("threshold", threshold)
    .executeUpdate();
```

`issue_context.headline`만 보존하는 이유: data-retention.md §"리포트는 영구 보존" — 헤드라인은 리포트에 가까운 *요약*이므로.

#### [3.9] 단위 테스트

`IssueContextMergerTest`:
- dedup (같은 fact 두 번 push 시 한 번만 들어감)
- FIFO drop (12개 초과 시 가장 오래된 것 제거)
- threadsResolved (해당 thread 제거 확인)
- categoryRule 적용 (in_law 카테고리에서 "차별" 단어 fact 거부 검증)
- lingered 카테고리에서 "어제" 포함 fact 거부 검증

`CategoryRuleEnforcerTest`:
- in_law: "시어머니가 차별했다" → false
- in_law: "시어머니 댁에 갔다" → true
- lingered: "어제 무슨 일이 있었다" → false
- lingered: "오랫동안 비슷한 일이 반복됐다" → true
- generation: "구식 가치관" → false

### PR-3 머지 검증

- [ ] `./gradlew test` 통과
- [ ] dev에서 5턴 대화 후 `issue_context` JSON이 채워짐
- [ ] in_law 카테고리 시나리오에서 `CategoryRuleEnforcer` 거부 로그 발생
- [ ] `Session.currentFocus`와 `issue_context.headline`이 동기화됨
- [ ] RetentionScheduler가 정상 동작 (수동 트리거 또는 다음 새벽 03:00 확인)

---

## PR-4: QuestionQueue 도입 (3~5일)

**목표**: 우선순위 큐가 동작. LLM이 PQ 최상단을 다루기 시작.

### 구현 항목

#### [4.1] _response_instructions.md 갱신

`question_queue_delta` 안내 추가:

```markdown
## Phase D 메타 필드 — `question_queue_delta` (옵션)

`<pending_questions>` 블록을 받았다면 *가장 위 한 개*만 자연스럽게 다루고, 그 ID를 `asked`에 적어주세요. 다음 턴 이후 물을 새 질문 후보는 `new`에 넣어주세요.

\`\`\`jsonc
{
  "question_queue_delta": {
    "asked": ["q-uuid-1"],
    "new": [
      {
        "intent": "SEEK_NEED",
        "target": "USER_A",
        "text": "분위기가 무거워졌을 때 가장 원하셨던 건",
        "hookFromIssue": "며칠 전 분위기가 무거웠던 이유",
        "antidoteFor": "PERSPECTIVE"
      }
    ]
  }
}
\`\`\`

- **asked**: 이번 턴에 발화한 `<pending_questions>` 항목의 ID 배열
- **new[].intent**: SEEK_FACT | SEEK_FEELING | SEEK_NEED | BRIDGE_PERSPECTIVE | REFLECT_PATTERN | INVITE_REPAIR | WELCOME_PARTNER
- **new[].target**: USER_A | USER_B (B는 합류 전이라도 미리 쌓아둘 수 있음)
- **new[].text**: 80자 이내. *발화 그대로가 아닌 의도 단서*
- **new[].hookFromIssue**: 어느 issue context 항목에서 나왔는지 (text 그대로)
- **new[].antidoteFor**: BOUNDARY | HORSEMEN | REPAIR | PERSPECTIVE | ESCALATION (선택)

**절대 금지**:
- `<pending_questions>` 의 text를 그대로 옮겨 적기
- 한 응답에 두세 개 질문 몰아 묻기
- `INVITE_REPAIR` Intent를 한 세션에 두 번 이상 발화
```

#### [4.2] ChatTurnMetaParser에 queue_delta 파싱 추가

`QuestionQueueDelta` 클래스 + `Result` record 확장.

```java
package com.againspring.service.context;

import com.againspring.domain.Session;
import java.util.List;

public class QuestionQueueDelta {
    public List<String> asked;
    public List<Session.PendingQuestion> newQuestions;  // "new"는 Java 예약어
}
```

#### [4.3] QuestionPrioritizer

**파일**: `backend/src/main/java/com/againspring/service/context/QuestionPrioritizer.java`

context-algorithm.md §5.3 식을 그대로 구현.

```java
package com.againspring.service.context;

import com.againspring.domain.Session;
import com.againspring.domain.enums.MessageSender;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class QuestionPrioritizer {

    public void rescore(List<Session.PendingQuestion> queue, Session session,
                        MessageSender target) {
        if (queue == null || queue.isEmpty()) return;

        Session.UserState currentState = currentStateFor(session, target);
        Set<String> unresolvedThreadTexts = collectUnresolvedThreadTexts(session);
        String categoryMinor = session.getCategory() != null ? session.getCategory().getMinorId() : null;

        for (Session.PendingQuestion q : queue) {
            if (Boolean.TRUE.equals(q.asked)) {
                q.priority = 0.0;
                continue;
            }
            double recency = 1.0 / (1 + Math.max(0, q.ageInTurns == null ? 0 : q.ageInTurns));
            double urgency = urgencyOf(q.intent);
            double coverageGap = (q.hookFromIssue != null
                && unresolvedThreadTexts.contains(q.hookFromIssue)) ? 1.0 : 0.3;
            double base = 0.5 * recency + 0.3 * urgency + 0.2 * coverageGap;
            double stateMult = stateMultiplier(currentState, q.intent);
            double catMult = categoryMultiplier(q.intent, categoryMinor);
            q.priority = clamp01(base * stateMult * catMult);
        }
    }

    private double urgencyOf(Session.Intent i) {
        return switch (i) {
            case WELCOME_PARTNER -> 1.0;
            case SEEK_NEED -> 0.7;
            case SEEK_FEELING, BRIDGE_PERSPECTIVE -> 0.5;
            case SEEK_FACT -> 0.4;
            case INVITE_REPAIR, REFLECT_PATTERN -> 0.0;
        };
    }

    /** context-algorithm.md §5.3 state multiplier 매트릭스 (7×7 일부). */
    private double stateMultiplier(Session.UserState state, Session.Intent intent) {
        if (state == null) return 1.0;
        return switch (state) {
            case OPENING -> switch (intent) {
                case SEEK_FEELING, WELCOME_PARTNER -> 1.0;
                case SEEK_FACT -> 1.0;
                case SEEK_NEED -> 0.8;
                case BRIDGE_PERSPECTIVE, REFLECT_PATTERN -> 0.5;
                case INVITE_REPAIR -> 0.3;
            };
            case VENTING -> switch (intent) {
                case SEEK_FEELING -> 1.3;
                case SEEK_NEED, WELCOME_PARTNER -> 1.0;
                case SEEK_FACT, BRIDGE_PERSPECTIVE -> 0.7;
                case REFLECT_PATTERN -> 0.5;
                case INVITE_REPAIR -> 0.3;
            };
            case DEFENSIVE -> switch (intent) {
                case REFLECT_PATTERN -> 1.2;
                case SEEK_FEELING, WELCOME_PARTNER -> 1.0;
                case SEEK_FACT, SEEK_NEED -> 0.7;
                case BRIDGE_PERSPECTIVE -> 0.5;
                case INVITE_REPAIR -> 0.3;
            };
            case BLAMING -> switch (intent) {
                case REFLECT_PATTERN -> 1.3;
                case SEEK_FEELING -> 1.2;
                case SEEK_NEED, WELCOME_PARTNER -> 1.0;
                case SEEK_FACT, BRIDGE_PERSPECTIVE -> 0.5;
                case INVITE_REPAIR -> 0.3;
            };
            case REFLECTING -> switch (intent) {
                case SEEK_NEED, BRIDGE_PERSPECTIVE -> 1.3;
                case WELCOME_PARTNER, SEEK_FACT, SEEK_FEELING, REFLECT_PATTERN -> 1.0;
                case INVITE_REPAIR -> 0.7;
            };
            case NEGOTIATING -> switch (intent) {
                case BRIDGE_PERSPECTIVE -> 1.2;
                case INVITE_REPAIR, SEEK_FACT, SEEK_NEED, WELCOME_PARTNER -> 1.0;
                case SEEK_FEELING, REFLECT_PATTERN -> 0.7;
            };
            case RESOLVING -> switch (intent) {
                case INVITE_REPAIR, WELCOME_PARTNER -> 1.0;
                default -> 0.5;
            };
        };
    }

    /** context-algorithm.md §5.3 categoryMultiplier 표 (한국 고유 4종). */
    private double categoryMultiplier(Session.Intent intent, String categoryMinor) {
        if (categoryMinor == null) return 1.0;
        return switch (categoryMinor) {
            case "in_law" -> switch (intent) {
                case BRIDGE_PERSPECTIVE, SEEK_NEED -> 1.2;
                default -> 1.0;
            };
            case "face" -> intent == Session.Intent.SEEK_FEELING ? 1.3 : 1.0;
            case "lingered" -> switch (intent) {
                case SEEK_NEED, REFLECT_PATTERN -> 1.3;
                case SEEK_FACT -> 0.0; // 단일 사건 인터뷰 금지
                default -> 1.0;
            };
            case "generation" -> switch (intent) {
                case BRIDGE_PERSPECTIVE, SEEK_NEED -> 1.2;
                default -> 1.0;
            };
            default -> 1.0;
        };
    }

    private Session.UserState currentStateFor(Session session, MessageSender target) {
        List<Session.UserStateEntry> hist = session.getUserStateHistory();
        if (hist == null || hist.isEmpty()) return null;
        Session.UserState latest = null;
        for (Session.UserStateEntry e : hist) {
            if (target.name().equals(e.sender)) latest = e.state;
        }
        return latest;
    }

    private Set<String> collectUnresolvedThreadTexts(Session session) {
        Session.IssueContext ctx = session.getIssueContext();
        if (ctx == null || ctx.threads == null) return Collections.emptySet();
        Set<String> texts = new HashSet<>();
        for (Session.UnresolvedThread t : ctx.threads) {
            if (!Boolean.TRUE.equals(t.addressedByQueue)) texts.add(t.text);
        }
        return texts;
    }

    private double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}
```

#### [4.4] QuestionQueueUpdater

**파일**: `backend/src/main/java/com/againspring/service/context/QuestionQueueUpdater.java`

```java
package com.againspring.service.context;

import com.againspring.domain.Session;
import com.againspring.domain.enums.MessageSender;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
@RequiredArgsConstructor
public class QuestionQueueUpdater {

    private static final int MAX_QUEUE_SIZE = 5;
    private static final int MAX_AGE_BEFORE_EVICT = 8;
    private static final double MIN_PRIORITY_KEEP = 0.2;

    private final QuestionPrioritizer prioritizer;
    private final CategoryRuleEnforcer ruleEnforcer;

    public void update(Session session, QuestionQueueDelta delta, int currentTurn) {
        // null-safe init
        if (session.getQuestionQueueA() == null) session.setQuestionQueueA(new ArrayList<>());
        if (session.getQuestionQueueB() == null) session.setQuestionQueueB(new ArrayList<>());

        // 1. asked 처리
        if (delta != null && delta.asked != null) {
            markAsked(session.getQuestionQueueA(), delta.asked, currentTurn);
            markAsked(session.getQuestionQueueB(), delta.asked, currentTurn);
            updateThreadAddressedFlag(session, delta.asked);
        }

        // 2. ageing — 모든 미발화 항목
        ageNonAsked(session.getQuestionQueueA());
        ageNonAsked(session.getQuestionQueueB());

        // 3. new 추가
        String categoryMinor = session.getCategory() != null
            ? session.getCategory().getMinorId() : null;
        if (delta != null && delta.newQuestions != null) {
            for (Session.PendingQuestion nq : delta.newQuestions) {
                if (!ruleEnforcer.isIntentAllowed(nq.intent, categoryMinor)) continue;

                List<Session.PendingQuestion> queue = "USER_A".equals(nq.target)
                    ? session.getQuestionQueueA() : session.getQuestionQueueB();

                if (containsDuplicate(queue, nq)) continue;

                Session.PendingQuestion q = new Session.PendingQuestion();
                q.id = UUID.randomUUID().toString();
                q.intent = nq.intent;
                q.target = nq.target;
                q.text = trim(nq.text, 80);
                q.hookFromIssue = nq.hookFromIssue;
                q.antidoteFor = nq.antidoteFor;
                q.createdTurn = currentTurn;
                q.ageInTurns = 0;
                q.asked = false;
                q.categoryRuleApplied = categoryMinor;
                q.priority = 0.0; // 곧 rescore
                queue.add(q);
            }
        }

        // 4. priority 재계산
        prioritizer.rescore(session.getQuestionQueueA(), session, MessageSender.USER_A);
        prioritizer.rescore(session.getQuestionQueueB(), session, MessageSender.USER_B);

        // 5. evict
        evict(session.getQuestionQueueA());
        evict(session.getQuestionQueueB());
    }

    private void markAsked(List<Session.PendingQuestion> queue, List<String> ids, int turn) {
        for (Session.PendingQuestion q : queue) {
            if (ids.contains(q.id)) {
                q.asked = true;
                q.askedTurn = turn;
            }
        }
    }

    private void updateThreadAddressedFlag(Session session, List<String> askedIds) {
        Session.IssueContext ctx = session.getIssueContext();
        if (ctx == null || ctx.threads == null) return;
        Set<String> hooks = new HashSet<>();
        for (Session.PendingQuestion q : session.getQuestionQueueA()) {
            if (askedIds.contains(q.id) && q.hookFromIssue != null) hooks.add(q.hookFromIssue);
        }
        for (Session.PendingQuestion q : session.getQuestionQueueB()) {
            if (askedIds.contains(q.id) && q.hookFromIssue != null) hooks.add(q.hookFromIssue);
        }
        for (Session.UnresolvedThread t : ctx.threads) {
            if (hooks.contains(t.text)) t.addressedByQueue = true;
        }
    }

    private void ageNonAsked(List<Session.PendingQuestion> queue) {
        for (Session.PendingQuestion q : queue) {
            if (!Boolean.TRUE.equals(q.asked)) {
                q.ageInTurns = (q.ageInTurns == null ? 0 : q.ageInTurns) + 1;
            }
        }
    }

    private boolean containsDuplicate(List<Session.PendingQuestion> queue,
                                     Session.PendingQuestion nq) {
        return queue.stream().anyMatch(q ->
            q.intent == nq.intent
            && Objects.equals(q.target, nq.target)
            && Objects.equals(q.hookFromIssue, nq.hookFromIssue)
            && !Boolean.TRUE.equals(q.asked));
    }

    private void evict(List<Session.PendingQuestion> queue) {
        // 1단계: stale (오래되고 priority 낮은 것) 제거
        queue.removeIf(q ->
            !Boolean.TRUE.equals(q.asked)
            && q.intent != Session.Intent.WELCOME_PARTNER  // WELCOME_PARTNER는 절대 evict 금지
            && q.ageInTurns != null && q.ageInTurns >= MAX_AGE_BEFORE_EVICT
            && q.priority < MIN_PRIORITY_KEEP);

        // 2단계: 큐 사이즈 5 초과 시 — asked=true 오래된 것부터
        while (queue.size() > MAX_QUEUE_SIZE) {
            Optional<Session.PendingQuestion> toRemove = queue.stream()
                .filter(q -> Boolean.TRUE.equals(q.asked))
                .min(Comparator.comparingInt(q -> q.askedTurn == null ? 0 : q.askedTurn));
            if (toRemove.isPresent()) {
                queue.remove(toRemove.get());
                continue;
            }
            // asked가 없으면 priority 가장 낮은 것 (단 WELCOME_PARTNER 제외)
            queue.stream()
                .filter(q -> q.intent != Session.Intent.WELCOME_PARTNER)
                .min(Comparator.comparingDouble(q -> q.priority))
                .ifPresent(queue::remove);
            if (queue.size() == MAX_QUEUE_SIZE + 1) break; // safety
        }
    }

    private String trim(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
```

#### [4.5] QuestionQueueFragment 실제 로직

**파일**: `backend/src/main/java/com/againspring/service/prompt/QuestionQueueFragment.java`

```java
@Component
public class QuestionQueueFragment {

    private static final int TOP_K = 3;

    public String render(Session session, MessageSender currentUserSender) {
        List<Session.PendingQuestion> queue = currentUserSender == MessageSender.USER_A
            ? session.getQuestionQueueA() : session.getQuestionQueueB();
        if (queue == null || queue.isEmpty()) return "";

        List<Session.PendingQuestion> top = queue.stream()
            .filter(q -> !Boolean.TRUE.equals(q.asked))
            .sorted(Comparator.comparingDouble((Session.PendingQuestion q) -> q.priority).reversed())
            .limit(TOP_K)
            .toList();
        if (top.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("<pending_questions for=\"").append(currentUserSender.name()).append("\" ")
          .append("note=\"누적된 미발화 질문. 가장 priority 높은 것을 자연스럽게 한 번 다뤄주세요. ")
          .append("그대로 읽지 말고 사용자 발화 흐름에 맞게 재구성해 주세요. ")
          .append("발화 시 question_queue_delta.asked 에 ID를 반드시 적어주세요.\">\n");
        for (Session.PendingQuestion q : top) {
            sb.append("- id=").append(q.id)
              .append(" intent=").append(q.intent.name())
              .append(" priority=").append(String.format("%.2f", q.priority))
              .append("\n  hint: ").append(q.text).append("\n");
        }
        sb.append("</pending_questions>\n");
        return sb.toString();
    }
}
```

#### [4.6] IsolationLintFilter

**파일**: `backend/src/main/java/com/againspring/safety/IsolationLintFilter.java`

```java
package com.againspring.safety;

import org.springframework.stereotype.Component;
import java.util.regex.Pattern;

/**
 * Phase D - LLM 응답 본문에 sender 라벨이 노출됐는지 검증.
 * 권위본: shared/docs/policies/context-algorithm.md §7 방어 3
 */
@Component
public class IsolationLintFilter {
    private static final Pattern SENDER_LABEL = Pattern.compile(
        "\\b(USER_A|USER_B|MEDIATOR_TO_A|MEDIATOR_TO_B)\\b");

    public boolean violatesIsolation(String mediatorMessage) {
        return mediatorMessage != null && SENDER_LABEL.matcher(mediatorMessage).find();
    }
}
```

#### [4.7] ChatService 통합 — 컨텍스트 갱신 + IsolationLint

```java
// LLM 응답 직후 (현재 mediatorResponse 변수 할당 직후)
if (isolationLint.violatesIsolation(mediatorResponse)) {
    log.error("Isolation violation in session {}", sessionId);
    mediatorResponse = "잠깐 정리할 시간이 필요해요. 다시 들려주실 수 있을까요?";
}

// 기존 appendPsychologyHistory 옆에:
appendPsychologyHistory(session, parsed);
userStateAppender.append(session, parsed.userState());
issueContextMerger.merge(session, parsed.issueDelta(), turnIndex);
questionQueueUpdater.update(session, parsed.queueDelta(), turnIndex);  // 신규
sessionRepo.save(session);
```

#### [4.8] duo_chat.md 갱신

**파일**: `shared/docs/prompts/chat/duo_chat.md`

기존 `<duo_specific_rules>` 단락에 한 줄 추가:

```
- <issue_context> / <pending_questions> 안의 USER_A, USER_B 라벨을 본문에 인용 금지.
```

#### [4.9] 단위 테스트

`QuestionPrioritizerTest`:
- 7×7 stateMultiplier 매트릭스의 핵심 셀 검증 (DEFENSIVE/SEEK_FACT=0.7, REFLECTING/SEEK_NEED=1.3, RESOLVING/INVITE_REPAIR=1.0)
- 4종 카테고리 categoryMultiplier 검증 (lingered + SEEK_FACT = 0.0)
- recency: ageInTurns=0 → 1.0, ageInTurns=4 → 0.2
- coverageGap: hookFromIssue가 unresolved threads에 있으면 1.0

`QuestionQueueUpdaterTest`:
- push: 새 질문이 큐에 추가됨
- dedup: 같은 intent + target + hookFromIssue 중복 거부
- ageing: 미발화 항목 ageInTurns +1
- evict: 5 초과 시 asked 가장 오래된 것 먼저 제거
- WELCOME_PARTNER는 절대 evict 안 됨
- lingered + SEEK_FACT 시도 → CategoryRuleEnforcer가 거부

`IsolationLintFilterTest`:
- "USER_A님은 이렇게 말씀하셨어요" → true (위반)
- "상대분이 이렇게 말씀하셨어요" → false (정상)

### PR-4 머지 검증

- [ ] `./gradlew test` 통과
- [ ] dev에서 5턴 이상 진행 시 `question_queue_a` JSON이 채워짐
- [ ] LLM이 PQ hint를 그대로 인용하지 않는지 5종 시나리오로 수동 검증
- [ ] `IsolationLintFilter`가 발동되지 않는지 (또는 발동 시 폴백 동작) 모니터링
- [ ] DEFENSIVE 상태에서 SEEK_FACT의 priority가 다른 상태보다 낮게 산출됨

---

## PR-5: B 진입 환영 + PQ 통합 (2~3일)

**목표**: B 합류 시 정적 메시지 → 동적 환영 + 첫 질문.

### 구현 항목

#### [5.1] 신규 프롬프트

**파일**: `shared/docs/prompts/chat/welcome_partner.md`

context-algorithm.md §6.4 본문을 그대로 작성.

#### [5.2] WelcomeQuestionResolver

**파일**: `backend/src/main/java/com/againspring/service/context/WelcomeQuestionResolver.java`

context-algorithm.md §6.3 코드 그대로.

#### [5.3] WelcomeMessageGenerator

**파일**: `backend/src/main/java/com/againspring/service/context/WelcomeMessageGenerator.java`

context-algorithm.md §6.5 코드 그대로. `MODEL_HAIKU` 상수는 `ChatService`에서 가져오기.

#### [5.4] ChatService.onPartnerJoined() 수정

기존 `bNotice` 정적 텍스트 부분을 다음으로 교체:

```java
// === Phase D 신규 ===
PendingQuestion welcomeQ = welcomeQuestionResolver.resolveOrCreate(session);
String bNotice = welcomeMessageGenerator.generate(session, welcomeQ);
welcomeQ.asked = true;
welcomeQ.askedTurn = 0;
sessionRepo.save(session);

messageRepo.save(Message.builder()
    .sessionId(sessionId)
    .sender(MessageSender.MEDIATOR_TO_B)
    .content(bNotice)
    .charCount(bNotice.length())
    .isPartnerJoinNotice(true)
    .llmModel(MODEL_HAIKU)
    .build());
```

생성자에 `WelcomeQuestionResolver`, `WelcomeMessageGenerator` 의존성 주입.

#### [5.5] 단위 테스트

`WelcomeQuestionResolverTest`:
- 빈 큐 → fallback PendingQuestion 생성 (intent=WELCOME_PARTNER, priority=1.0)
- 미발화 항목이 큐에 있음 → 최상단을 WELCOME_PARTNER로 격상
- 모두 asked=true 상태 → fallback 생성

`WelcomeMessageGeneratorTest`:
- LLM 정상 응답 → 그 응답 strip
- LLM null 반환 → fallback 메시지
- LLM 예외 → fallback 메시지

### PR-5 머지 검증

- [ ] `./gradlew test` 통과
- [ ] dev에서 A가 5턴 진행 → B 초대 토큰 생성 → B 진입 → B가 받는 메시지가 환영 + 상황 + 질문 3요소 포함 (수동 시나리오)
- [ ] LLM 실패 시 fallback이 동작하는지 (LLM_BIN 일시 중단 후 시나리오)
- [ ] `welcomeQ.asked = true` 마킹 확인 (DB 직접 조회)

---

## PR-6: 운영 도구 (1~2일)

**목표**: 운영자가 시스템 상태를 관찰하고 가중치를 조정.

### 구현 항목

#### [6.1] 디버그 엔드포인트

**파일**: `backend/src/main/java/com/againspring/api/admin/SessionContextDebugController.java`

```java
@RestController
@RequestMapping("/admin/sessions")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class SessionContextDebugController {

    private final SessionRepository sessionRepo;

    @GetMapping("/{id}/context")
    public ResponseEntity<?> debug(@PathVariable String id) {
        Session s = sessionRepo.findById(id).orElseThrow();
        return ResponseEntity.ok(Map.of(
            "sessionId", id,
            "status", s.getStatus(),
            "issueContext", s.getIssueContext(),
            "userStateHistory", s.getUserStateHistory(),
            "questionQueueA", s.getQuestionQueueA(),
            "questionQueueB", s.getQuestionQueueB(),
            "horsemenHistory", s.getHorsemenHistory(),
            "currentFocus", s.getCurrentFocus()
        ));
    }
}
```

#### [6.2] 메트릭 (Micrometer)

기존 메트릭 패턴(`backend/src/main/java/com/againspring/llm/monitoring/`)에 맞춰 추가:

- `phase_d.queue.depth.a` (Gauge) — A 큐 평균 크기
- `phase_d.queue.depth.b` (Gauge)
- `phase_d.queue.ask_rate` (Counter ratio) — 큐 항목이 발화되는 비율
- `phase_d.state.{state_name}` (Counter) — 7개 상태 분포
- `phase_d.isolation.violations` (Counter) — IsolationLintFilter 트리거 횟수
- `phase_d.meta.populated_rate` — `<turn_meta>` 신규 필드가 채워진 응답 비율

#### [6.3] QuestionPrioritizer 가중치 외부화

**파일**: `backend/src/main/java/com/againspring/config/PhaseDProperties.java`

```java
@ConfigurationProperties(prefix = "app.phase-d.priority")
@Data
public class PhaseDProperties {
    private double recencyWeight = 0.5;
    private double urgencyWeight = 0.3;
    private double coverageGapWeight = 0.2;
    // state/category multiplier는 코드에 둠 (변경 시 정책 회의 필요)
}
```

`QuestionPrioritizer`가 이 properties를 주입받아 사용. 재배포 없이 `application.yml`로 튜닝 가능.

### PR-6 머지 검증

- [ ] `./gradlew test` 통과
- [ ] `/admin/sessions/{id}/context` 응답 확인 (실 세션 ID로)
- [ ] 메트릭 대시보드(Grafana 또는 actuator/prometheus)에서 `phase_d.*` 메트릭 노출 확인
- [ ] `application.yml`에서 `app.phase-d.priority.recency-weight: 0.6` 로 변경 후 재배포 없이 적용 확인 (Spring Boot Actuator refresh)

---

## 작업 완료 후 정리

### 본 문서 보존

PR-6 머지 후 본 문서를 `backend/docs/implementation/phase-d-context-algorithm.md`로 이동하여 *과거 구현 기록*으로 보존. 향후 신규 기능 PR 시 참고 가능.

### CLAUDE.md 갱신

CLAUDE.md "현재 진행 상황" 섹션에 추가:

```markdown
- ✅ **중재 컨텍스트 강화 Phase D**: UserState 7종 + IssueContext 4슬롯 + QuestionQueue (A·B 분리 PQ) + B 진입 시 환영+PQ top1 통합 메시지. 권위본: `shared/docs/policies/context-algorithm.md`
```

### 5종 시나리오 전체 회고

운영 데이터 1~2주 누적 후:
- `phase_d.meta.populated_rate` 가 80% 미만이면 `_response_instructions.md` 보강
- `phase_d.isolation.violations` 가 0이 아니면 응답 라인트 강화
- `phase_d.queue.ask_rate` 가 50% 미만이면 PQ 노출 우선순위 조정
- `QuestionPrioritizer` 가중치를 시나리오별 결과로 1차 튜닝

---

## 변경 이력

| 일자 | 변경 |
|---|---|
| 2026-04-27 | v1.0 초안. 6개 PR 단위 작업 분할. 각 PR 독립 머지·회귀 0 보장. |
