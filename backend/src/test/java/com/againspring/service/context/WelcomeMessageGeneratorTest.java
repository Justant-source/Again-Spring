package com.againspring.service.context;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.againspring.domain.Session;
import com.againspring.llm.bridge.ClaudeCodeBridge;
import com.againspring.llm.prompt.PromptLoader;
import com.againspring.service.prompt.IssueContextFragment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WelcomeMessageGeneratorTest {

    @Mock private PromptLoader loader;
    @Mock private ClaudeCodeBridge llmBridge;
    @Mock private IssueContextFragment issueFragment;

    private WelcomeMessageGenerator generator;

    @BeforeEach
    void setUp() throws Exception {
        when(loader.get("chat/welcome_partner.md")).thenReturn("# 환영 프롬프트");
        when(issueFragment.render(any())).thenReturn("");
        generator = new WelcomeMessageGenerator(loader, llmBridge, issueFragment);
    }

    private Session.PendingQuestion welcomeQ() {
        Session.PendingQuestion q = new Session.PendingQuestion();
        q.intent = Session.Intent.WELCOME_PARTNER;
        q.text = "최근 두 분 사이에 어떤 마음이 드셨는지";
        return q;
    }

    @Test
    void llmNormalResponse_returned() throws Exception {
        when(llmBridge.invoke(anyString(), anyString())).thenReturn("  함께해주셔서 고마워요.  ");

        String result = generator.generate(new Session(), welcomeQ());

        assertEquals("함께해주셔서 고마워요.", result, "LLM 응답이 strip되어 반환되어야 함");
    }

    @Test
    void llmReturnsNull_fallback() throws Exception {
        when(llmBridge.invoke(anyString(), anyString())).thenReturn(null);

        String result = generator.generate(new Session(), welcomeQ());

        assertFalse(result.isBlank(), "null 반환 시 fallback 메시지가 있어야 함");
        assertTrue(result.contains("함께 정리하러"), "fallback 메시지 내용이 있어야 함");
    }

    @Test
    void llmReturnsBlank_fallback() throws Exception {
        when(llmBridge.invoke(anyString(), anyString())).thenReturn("   ");

        String result = generator.generate(new Session(), welcomeQ());

        assertFalse(result.isBlank(), "공백 반환 시 fallback 메시지가 있어야 함");
    }

    @Test
    void llmThrowsException_fallback() throws Exception {
        when(llmBridge.invoke(anyString(), anyString())).thenThrow(new RuntimeException("LLM down"));

        String result = generator.generate(new Session(), welcomeQ());

        assertFalse(result.isBlank(), "예외 발생 시 fallback 메시지가 있어야 함");
    }

    @Test
    void categoryIncludedInPrompt() throws Exception {
        when(llmBridge.invoke(anyString(), anyString())).thenReturn("환영합니다.");

        Session session = new Session();
        Session.Category cat = new Session.Category();
        cat.majorId = "couple";
        cat.minorId = "in_law";
        session.setCategory(cat);

        generator.generate(session, welcomeQ());

        verify(llmBridge).invoke(argThat(prompt -> prompt.contains("couple")), anyString());
    }
}
