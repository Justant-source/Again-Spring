package com.againspring.aiuser.llm.service;

import com.againspring.aiuser.llm.dto.PostGenRequest;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PromptAssemblerOverrideTest {

    @Test
    void overrideBeatsClasspath() {
        PromptAssembler pa = new PromptAssembler();
        pa.loadGuides();
        String base = pa.guide("voice/post", null);
        assertFalse(base.isBlank(), "classpath voice/post.md must load");
        String over = pa.guide("voice/post", Map.of("voice/post", "오버라이드 가이드 100%"));
        assertEquals("오버라이드 가이드 100%%", over, "% must be escaped for String.formatted");
    }

    @Test
    void unknownKeyWithoutOverrideIsEmpty() {
        PromptAssembler pa = new PromptAssembler();
        pa.loadGuides();
        assertEquals("", pa.guide("voice/reconstruct", null));
    }

    @Test
    void postPromptUsesOverride() {
        PromptAssembler pa = new PromptAssembler();
        pa.loadGuides();
        PostGenRequest req = new PostGenRequest();
        req.setPromptOverrides(Map.of("voice/post", "유일무이한오버라이드문구"));
        String prompt = pa.assemblePostPrompt(req);
        assertTrue(prompt.contains("유일무이한오버라이드문구"));
    }

    @Test
    void assemblerHasNoJdbcField() {
        for (var f : PromptAssembler.class.getDeclaredFields()) {
            assertFalse(f.getType().getName().contains("Jdbc"), "worker must be stateless: " + f.getName());
        }
    }
}
