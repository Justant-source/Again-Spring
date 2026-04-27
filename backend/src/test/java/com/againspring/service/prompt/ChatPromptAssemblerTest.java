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
import com.againspring.llm.prompt.PromptLoader;
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

@ExtendWith(MockitoExtension.class)
class ChatPromptAssemblerTest {

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

    // 빈 catalog (테스트 환경에서는 카테고리 컨텍스트 주입 불필요) → render() returns ""
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
            .build();
    }

    @Test
    void assembleSoloTurn_includesProfile_whenStyleSet() throws Exception {
        Session s = basicSession();
        User user = User.builder()
            .id("ua").nickname("A")
            .communicationStyle("wave")
            .build();

        String out = assembler.assembleSoloTurn(s, user, "테스트 메시지", Collections.emptyList());

        assertTrue(out.contains("<user_profile"), "profile block missing");
        assertTrue(out.contains("파도형"));
        assertTrue(out.contains("[stub:relations/couple.md]"), "relation prompt missing");
        assertTrue(out.contains("<current_user_message>"));
        assertTrue(out.contains("테스트 메시지"));
    }

    @Test
    void assembleSoloTurn_omitsProfileBlock_whenStyleMissing() throws Exception {
        Session s = basicSession();
        User user = User.builder().id("ua").nickname("A").build();

        String out = assembler.assembleSoloTurn(s, user, "msg", Collections.emptyList());

        assertFalse(out.contains("<user_profile"));
    }

    @Test
    void assembleSoloTurn_omitsProfileBlock_whenUserNull() throws Exception {
        Session s = basicSession();
        String out = assembler.assembleSoloTurn(s, null, "msg", Collections.emptyList());
        assertFalse(out.contains("<user_profile"));
    }

    @Test
    void assembleDuoTurn_includesBothProfiles_whenBothStylesSet() throws Exception {
        Session s = basicSession();
        s.setStatus(SessionStatus.CHATTING_DUO);
        User a = User.builder().id("ua").nickname("A").communicationStyle("wave").build();
        User b = User.builder().id("ub").nickname("B").communicationStyle("mountain").build();

        String out = assembler.assembleDuoTurn(s, a, b, MessageSender.USER_A, "msg", Collections.emptyList());

        assertTrue(out.contains("sender=\"USER_A\""));
        assertTrue(out.contains("sender=\"USER_B\""));
        assertTrue(out.contains("파도형"));
        assertTrue(out.contains("산형"));
    }

    @Test
    void assembleDuoTurn_includesOnlyOneProfile_whenOneStyleMissing() throws Exception {
        Session s = basicSession();
        s.setStatus(SessionStatus.CHATTING_DUO);
        User a = User.builder().id("ua").nickname("A").communicationStyle("wave").build();
        User b = User.builder().id("ub").nickname("B").build();

        String out = assembler.assembleDuoTurn(s, a, b, MessageSender.USER_A, "msg", Collections.emptyList());

        assertTrue(out.contains("sender=\"USER_A\""));
        assertFalse(out.contains("sender=\"USER_B\""));
    }

    @Test
    void assembleDuoTurn_keepsDuoSpecificRules() throws Exception {
        Session s = basicSession();
        s.setStatus(SessionStatus.CHATTING_DUO);
        User a = User.builder().id("ua").nickname("A").communicationStyle("flame").build();
        User b = User.builder().id("ub").nickname("B").communicationStyle("leaf").build();

        String out = assembler.assembleDuoTurn(s, a, b, MessageSender.USER_B, "msg", List.of());

        assertTrue(out.contains("duo_specific_rules"));
        assertTrue(out.contains("사용자 B"));
    }
}
