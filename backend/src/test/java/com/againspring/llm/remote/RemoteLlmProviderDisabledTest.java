package com.againspring.llm.remote;

import com.againspring.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RemoteLlmProviderDisabledTest {

    @Test
    void invokeThrowsWhenDisabled() {
        RemoteLlmProvider provider = new RemoteLlmProvider(
                "http://127.0.0.1:1",
                1000L,
                "claude-haiku-4-5-20251001",
                false
        );
        BusinessException ex = assertThrows(BusinessException.class, () -> provider.invoke("hi", null));
        assertEquals("LLM_DISABLED", ex.getCode());
        BusinessException withImage = assertThrows(BusinessException.class,
                () -> provider.invoke("hi", null, java.util.List.of(
                        new com.againspring.llm.LlmImage("image/jpeg", "AA=="))));
        assertEquals("LLM_DISABLED", withImage.getCode());
        assertEquals(501, ex.getHttpStatus());
        assertFalse(provider.isHealthy());
        assertEquals("disabled", provider.getProviderName());
    }
}
