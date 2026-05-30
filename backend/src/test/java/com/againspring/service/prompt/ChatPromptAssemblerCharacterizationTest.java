package com.againspring.service.prompt;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.againspring.domain.Message;
import com.againspring.domain.Session;
import com.againspring.domain.User;
import com.againspring.domain.enums.MessageSender;
import com.againspring.domain.enums.RelationType;
import com.againspring.domain.enums.SessionStatus;
import com.againspring.llm.prompt.CacheTier;
import com.againspring.llm.prompt.PromptLoader;
import com.againspring.llm.prompt.PromptSegment;
import com.againspring.llm.prompt.StructuredPrompt;
import com.againspring.service.category.CategoryCatalog;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.DefaultResourceLoader;

/**
 * Characterization Test: Ensures StructuredPrompt.flatten() produces byte-for-byte equivalence
 * with legacy String-based assembly methods (assemblySoloTurn, assemblyDuoTurn).
 *
 * This test validates that the refactoring to structured prompts does not change the
 * semantic content delivered to the LLM.
 */
@ExtendWith(MockitoExtension.class)
class ChatPromptAssemblerCharacterizationTest {

    @Mock
    private PromptLoader loader;

    @Spy
    private UserProfileFragment profileFragment = new UserProfileFragment();

    @Spy
    private PsychologyFeedbackFormatter psychologyFeedback = new PsychologyFeedbackFormatter();

    @Spy
    private DuoBalanceFormatter duoBalance = new DuoBalanceFormatter();

    @Spy
    private IssueContextFragment issueContextFragment = new IssueContextFragment();

    @Spy
    private UserStateFragment userStateFragment = new UserStateFragment();

    @Spy
    private QuestionQueueFragment questionQueueFragment = new QuestionQueueFragment();

    @Spy
    private CategoryContextFragment categoryContextFragment =
            new CategoryContextFragment(new CategoryCatalog(new DefaultResourceLoader(), "classpath:nonexistent.yml"));

    @InjectMocks
    private ChatPromptAssembler assembler;

    @BeforeEach
    void stubPromptLoader() throws Exception {
        when(loader.get(anyString())).thenAnswer(inv -> "[stub:" + inv.getArgument(0) + "]");
    }

    private Session basicSession() {
        return Session.builder()
            .id("s1")
            .createdByUserId("ua")
            .inviteeUserId("ub")
            .relationType(RelationType.COUPLE)
            .status(SessionStatus.CHATTING_SOLO)
            .mediatorStyleX(50)
            .mediatorStyleY(50)
            .build();
    }

    // ============ Solo Turn Tests ============

    @Test
    void soloTurnStructuredFlattenEqualsLegacy_noProfile() throws Exception {
        Session s = basicSession();
        User user = User.builder().id("ua").nickname("A").build();

        String legacy = assembler.assembleSoloTurn(s, user, "테스트", Collections.emptyList());
        StructuredPrompt structured = assembler.assembleSoloTurnStructured(s, user, "테스트", Collections.emptyList());
        String flattened = structured.flatten();

        try {
            java.nio.file.Files.write(java.nio.file.Paths.get("/tmp/legacy.txt"), legacy.getBytes());
            java.nio.file.Files.write(java.nio.file.Paths.get("/tmp/structured.txt"), flattened.getBytes());
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (!legacy.equals(flattened)) {
            // Find first difference
            int minLen = Math.min(legacy.length(), flattened.length());
            int firstDiff = -1;
            for (int i = 0; i < minLen; i++) {
                if (legacy.charAt(i) != flattened.charAt(i)) {
                    firstDiff = i;
                    break;
                }
            }
            String msg = String.format("Length diff: legacy=%d, structured=%d", legacy.length(), flattened.length());
            if (firstDiff >= 0) {
                int start = Math.max(0, firstDiff - 30);
                int end = Math.min(minLen, firstDiff + 30);
                msg += String.format(". First diff at %d. Legacy around: '%s...' Structured around: '%s...'",
                    firstDiff,
                    legacy.substring(start, end).replace("\n", "\\n"),
                    flattened.substring(start, end).replace("\n", "\\n"));
            }
            fail(msg);
        }
    }

    @Test
    void soloTurnStructuredFlattenEqualsLegacy_withProfile() throws Exception {
        Session s = basicSession();
        User user = User.builder()
            .id("ua")
            .nickname("A")
            .communicationStyle("wave")
            .mbtiType("ENFP")
            .build();

        String legacy = assembler.assembleSoloTurn(s, user, "현재 메시지", Collections.emptyList());
        StructuredPrompt structured = assembler.assembleSoloTurnStructured(s, user, "현재 메시지", Collections.emptyList());
        String flattened = structured.flatten();

        assertEquals(legacy, flattened, "Solo turn with profile should match exactly");
    }

    @Test
    void soloTurnStructuredFlattenEqualsLegacy_withMessages() throws Exception {
        Session s = basicSession();
        User user = User.builder().id("ua").nickname("A").communicationStyle("mountain").build();

        List<Message> messages = List.of(
            Message.builder()
                .id(1L)
                .sessionId("s1")
                .sender(MessageSender.USER_A)
                .content("A의 첫 메시지")
                .build(),
            Message.builder()
                .id(2L)
                .sessionId("s1")
                .sender(MessageSender.MEDIATOR_TO_A)
                .content("중재자→A의 응답")
                .build(),
            Message.builder()
                .id(3L)
                .sessionId("s1")
                .sender(MessageSender.USER_A)
                .content("A의 두번째 메시지")
                .build()
        );

        String legacy = assembler.assembleSoloTurn(s, user, "최종 메시지", messages);
        StructuredPrompt structured = assembler.assembleSoloTurnStructured(s, user, "최종 메시지", messages);
        String flattened = structured.flatten();

        assertEquals(legacy, flattened, "Solo turn with messages should be byte-for-byte equivalent");
    }

    // ============ Duo Turn Tests ============

    @Test
    void duoTurnStructuredFlattenEqualsLegacy_basicDuo() throws Exception {
        Session s = basicSession();
        s.setStatus(SessionStatus.CHATTING_DUO);
        s.setUserAMessageCount(5);
        s.setUserBMessageCount(5);

        User a = User.builder().id("ua").nickname("A").communicationStyle("wave").build();
        User b = User.builder().id("ub").nickname("B").communicationStyle("mountain").build();

        String legacy = assembler.assembleDuoTurn(s, a, b, MessageSender.USER_A, "A의 메시지", Collections.emptyList());
        StructuredPrompt structured = assembler.assembleDuoTurnStructured(s, a, b, MessageSender.USER_A, "A의 메시지", Collections.emptyList());
        String flattened = structured.flatten();

        assertEquals(legacy, flattened, "Duo turn basic should match");
    }

    @Test
    void duoTurnStructuredFlattenEqualsLegacy_userBEarlyEntry() throws Exception {
        Session s = basicSession();
        s.setStatus(SessionStatus.CHATTING_DUO);
        s.setUserAMessageCount(5);
        s.setUserBMessageCount(1);  // B이 방금 진입 — partner_onboarding 주입 기대

        User a = User.builder().id("ua").nickname("A").communicationStyle("flame").mbtiType("ENFP").build();
        User b = User.builder().id("ub").nickname("B").communicationStyle("leaf").build();

        String legacy = assembler.assembleDuoTurn(s, a, b, MessageSender.USER_B, "B의 메시지", Collections.emptyList());
        StructuredPrompt structured = assembler.assembleDuoTurnStructured(s, a, b, MessageSender.USER_B, "B의 메시지", Collections.emptyList());
        String flattened = structured.flatten();

        assertEquals(legacy, flattened, "Duo turn with early B entry should include partner_onboarding");
    }

    @Test
    void duoTurnStructuredFlattenEqualsLegacy_withConversationHistory() throws Exception {
        Session s = basicSession();
        s.setStatus(SessionStatus.CHATTING_DUO);
        s.setUserAMessageCount(3);
        s.setUserBMessageCount(3);

        User a = User.builder().id("ua").nickname("A").communicationStyle("wave").build();
        User b = User.builder().id("ub").nickname("B").communicationStyle("mountain").build();

        List<Message> allMessages = List.of(
            Message.builder().id(1L).sessionId("s1").sender(MessageSender.USER_A).content("A: 안녕").build(),
            Message.builder().id(2L).sessionId("s1").sender(MessageSender.MEDIATOR_TO_A).content("중재자→A").build(),
            Message.builder().id(3L).sessionId("s1").sender(MessageSender.USER_A).content("A: 감사해").build(),
            Message.builder().id(4L).sessionId("s1").sender(MessageSender.USER_B).content("B: 나도 말하고싶어").build(),
            Message.builder().id(5L).sessionId("s1").sender(MessageSender.MEDIATOR_TO_B).content("중재자→B").build()
        );

        String legacy = assembler.assembleDuoTurn(s, a, b, MessageSender.USER_A, "최종 요청", allMessages);
        StructuredPrompt structured = assembler.assembleDuoTurnStructured(s, a, b, MessageSender.USER_A, "최종 요청", allMessages);
        String flattened = structured.flatten();

        assertEquals(legacy, flattened, "Duo turn with full history should match exactly");
    }

    // ============ Cache Tier Ordering Tests ============

    @Test
    void soloTurnStructured_cacheTierOrdering() throws Exception {
        Session s = basicSession();
        User user = User.builder().id("ua").nickname("A").communicationStyle("wave").build();

        StructuredPrompt prompt = assembler.assembleSoloTurnStructured(s, user, "msg", Collections.emptyList());
        List<PromptSegment> segments = prompt.getSegmentsReadOnly();

        assertFalse(segments.isEmpty(), "Segments should not be empty");

        // Find first segment of each tier
        int firstGlobal = -1, firstSession = -1, firstHistory = -1, firstDynamic = -1;
        for (int i = 0; i < segments.size(); i++) {
            CacheTier tier = segments.get(i).getTier();
            if (tier == CacheTier.GLOBAL_STATIC && firstGlobal == -1) firstGlobal = i;
            if (tier == CacheTier.SESSION_STATIC && firstSession == -1) firstSession = i;
            if (tier == CacheTier.HISTORY && firstHistory == -1) firstHistory = i;
            if (tier == CacheTier.DYNAMIC && firstDynamic == -1) firstDynamic = i;
        }

        // Verify ordering: GLOBAL_STATIC < SESSION_STATIC < HISTORY < DYNAMIC
        assertTrue(firstGlobal >= 0, "Should have GLOBAL_STATIC segments");
        assertTrue(firstSession >= 0, "Should have SESSION_STATIC segments");
        assertTrue(firstHistory >= 0, "Should have HISTORY segments");
        assertTrue(firstDynamic >= 0, "Should have DYNAMIC segments");

        assertTrue(firstGlobal < firstSession, "GLOBAL_STATIC should come before SESSION_STATIC");
        assertTrue(firstSession < firstHistory, "SESSION_STATIC should come before HISTORY");
        assertTrue(firstHistory < firstDynamic, "HISTORY should come before DYNAMIC");
    }

    @Test
    void duoTurnStructured_cacheTierOrdering() throws Exception {
        Session s = basicSession();
        s.setStatus(SessionStatus.CHATTING_DUO);
        s.setUserBMessageCount(1);  // B 초기 진입

        User a = User.builder().id("ua").nickname("A").communicationStyle("wave").build();
        User b = User.builder().id("ub").nickname("B").communicationStyle("mountain").build();

        StructuredPrompt prompt = assembler.assembleDuoTurnStructured(s, a, b, MessageSender.USER_B, "msg", Collections.emptyList());
        List<PromptSegment> segments = prompt.getSegmentsReadOnly();

        assertFalse(segments.isEmpty(), "Duo segments should not be empty");

        // Find first segment of each tier
        int firstGlobal = -1, firstSession = -1, firstHistory = -1, firstDynamic = -1;
        for (int i = 0; i < segments.size(); i++) {
            CacheTier tier = segments.get(i).getTier();
            if (tier == CacheTier.GLOBAL_STATIC && firstGlobal == -1) firstGlobal = i;
            if (tier == CacheTier.SESSION_STATIC && firstSession == -1) firstSession = i;
            if (tier == CacheTier.HISTORY && firstHistory == -1) firstHistory = i;
            if (tier == CacheTier.DYNAMIC && firstDynamic == -1) firstDynamic = i;
        }

        assertTrue(firstGlobal >= 0, "Should have GLOBAL_STATIC segments");
        assertTrue(firstSession >= 0, "Should have SESSION_STATIC segments");
        assertTrue(firstHistory >= 0, "Should have HISTORY segments");
        assertTrue(firstDynamic >= 0, "Should have DYNAMIC segments");

        assertTrue(firstGlobal < firstSession, "GLOBAL_STATIC should come before SESSION_STATIC");
        assertTrue(firstSession < firstHistory, "SESSION_STATIC should come before HISTORY");
        assertTrue(firstHistory < firstDynamic, "HISTORY should come before DYNAMIC");
    }

    // ============ Content Preservation Tests ============

    @Test
    void soloTurnStructured_preservesMediatorStyle() throws Exception {
        Session s = basicSession();
        s.setMediatorStyleX(75);
        s.setMediatorStyleY(25);
        User user = User.builder().id("ua").nickname("A").build();

        StructuredPrompt prompt = assembler.assembleSoloTurnStructured(s, user, "msg", Collections.emptyList());
        String flattened = prompt.flatten();

        assertTrue(flattened.contains("<mediator_style>"), "Should include mediator_style block");
        assertTrue(flattened.contains("75"), "Should preserve styleX value");
        assertTrue(flattened.contains("25"), "Should preserve styleY value");
    }

    @Test
    void duoTurnStructured_preservesDuoSpecificRules() throws Exception {
        Session s = basicSession();
        s.setStatus(SessionStatus.CHATTING_DUO);
        s.setUserAMessageCount(10);
        s.setUserBMessageCount(10);
        User a = User.builder().id("ua").nickname("A").build();
        User b = User.builder().id("ub").nickname("B").build();

        StructuredPrompt prompt = assembler.assembleDuoTurnStructured(s, a, b, MessageSender.USER_A, "msg", Collections.emptyList());
        String flattened = prompt.flatten();

        assertTrue(flattened.contains("duo_specific_rules"), "Should include duo_specific_rules");
        assertTrue(flattened.contains("사용자 A"), "Should mention current sender");
    }

    @Test
    void duoTurnStructured_partnerOnboardingIncluded_whenBEarly() throws Exception {
        Session s = basicSession();
        s.setStatus(SessionStatus.CHATTING_DUO);
        s.setUserAMessageCount(10);
        s.setUserBMessageCount(2);  // B이 초기 진입

        User a = User.builder().id("ua").nickname("A").build();
        User b = User.builder().id("ub").nickname("B").build();

        StructuredPrompt prompt = assembler.assembleDuoTurnStructured(s, a, b, MessageSender.USER_B, "msg", Collections.emptyList());
        String flattened = prompt.flatten();

        assertTrue(flattened.contains("partner_onboarding"), "Should include partner_onboarding for early B entry");
    }

    @Test
    void duoTurnStructured_partnerOnboardingOmitted_whenBEstablished() throws Exception {
        Session s = basicSession();
        s.setStatus(SessionStatus.CHATTING_DUO);
        s.setUserAMessageCount(10);
        s.setUserBMessageCount(5);  // B이 이미 충분히 진행 (> 2 messages)

        User a = User.builder().id("ua").nickname("A").build();
        User b = User.builder().id("ub").nickname("B").build();

        StructuredPrompt prompt = assembler.assembleDuoTurnStructured(s, a, b, MessageSender.USER_B, "msg", Collections.emptyList());
        String flattened = prompt.flatten();

        assertFalse(flattened.contains("partner_onboarding"), "Should omit partner_onboarding when B is established");
    }

    // ============ Segment Count & Composition Tests ============

    @Test
    void soloTurnStructured_segmentCountReasonal() throws Exception {
        Session s = basicSession();
        User user = User.builder().id("ua").nickname("A").communicationStyle("wave").build();

        StructuredPrompt prompt = assembler.assembleSoloTurnStructured(s, user, "msg", Collections.emptyList());

        int count = prompt.segmentCount();
        // Basic structure: system + mediator_style + gottman + nvc (global+session) +
        // profile + relations + solo_chat (session+global) +
        // conversation_history parts + current_message + response_instructions
        // With profile rendered: expect ~13+ segments (actual: system=1 + mstyle=1 + gottman=1 + nvc=1 + profile=1 +
        // relations=1 + solo_chat=1 + hist_open=1 + hist_close=1 + current=1 + resp_instr=1 = 11)
        assertTrue(count >= 10, "Should have reasonable number of segments, got " + count);
    }

    @Test
    void duoTurnStructured_segmentCountReasonable() throws Exception {
        Session s = basicSession();
        s.setStatus(SessionStatus.CHATTING_DUO);
        s.setUserAMessageCount(5);
        s.setUserBMessageCount(5);

        User a = User.builder().id("ua").nickname("A").communicationStyle("wave").build();
        User b = User.builder().id("ub").nickname("B").communicationStyle("mountain").build();

        StructuredPrompt prompt = assembler.assembleDuoTurnStructured(s, a, b, MessageSender.USER_A, "msg", Collections.emptyList());

        int count = prompt.segmentCount();
        // Duo should have more: system + mediator_style + gottman + nvc + duo_chat (global+session) +
        // profileA + profileB + relations (session) +
        // history parts + duo_rules + balance + current_message + instructions
        // Actual: system=1, mstyle=1, gottman=1, nvc=1, profileA=1, profileB=1, relations=1, duo_chat=1 +
        // hist_open=1, hist_close=1, duo_rules=1, balance=1, current=1, resp_instr=1 = 14
        assertTrue(count >= 12, "Duo should have reasonable segments, got " + count);
    }
}
