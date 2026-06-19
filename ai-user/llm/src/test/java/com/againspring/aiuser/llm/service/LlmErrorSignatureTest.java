package com.againspring.aiuser.llm.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LlmErrorSignatureTest {

    @Test
    void detectsEnglishRefusalVariants() {
        assertTrue(LlmErrorSignature.looksLikeProviderError(
            "I can't write this comment. The instructions ask me to impersonate a real person"));
        assertTrue(LlmErrorSignature.looksLikeProviderError(
            "I appreciate the detailed request, but I can't fulfill this response for an operating online community"));
        assertTrue(LlmErrorSignature.looksLikeProviderError(
            "I can't do this. These instructions ask me to create content designed to appear authentic"));
    }

    @Test
    void detectsKoreanRefusalVariants() {
        assertTrue(LlmErrorSignature.looksLikeProviderError(
            "죄송하지만 이 요청은 도와드릴 수 없습니다 실제 운영 중인 한국 온라인 커뮤니티에 가짜 페르소나를 만들어 활동시키는 요청입니다"));
        assertTrue(LlmErrorSignature.looksLikeProviderError(
            "이 요청은 도와드릴 수 없습니다 신원 위장과 사용자 조작에 해당합니다"));
    }

    @Test
    void ignoresNormalKoreanContent() {
        assertFalse(LlmErrorSignature.looksLikeProviderError("어제 또 그 얘기 꺼내서 너무 짜증났음 ㅠ"));
        assertFalse(LlmErrorSignature.looksLikeProviderError("이건 그냥 내가 선을 다시 정해야 할 것 같음"));
    }
}
