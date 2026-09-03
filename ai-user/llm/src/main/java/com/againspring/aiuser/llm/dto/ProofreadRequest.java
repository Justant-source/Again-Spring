package com.againspring.aiuser.llm.dto;

import lombok.*;
import java.util.Map;

/**
 * 게시 직전 맞춤법 교정 요청 — 의도적으로 persona/voice/category 필드가 없다.
 * 이 호출은 문체 재현이 아니라 오탈자만 고치는 좁은 목적이다 (2026-08-16 shortform-content-quality fix).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProofreadRequest {
    private String body;
    private String correlationId;
    private long timeoutMs;
    /** 생성 백엔드: "CLI" | "API" | null (null→CLI). */
    private String backend;
    /** CLAUDE | CODEX | API | STUB. 비면 backend(구 필드) → CLAUDE 순으로 해석. */
    private String provider;

    public com.againspring.aiuser.llm.service.LlmProvider resolveProvider() {
        return com.againspring.aiuser.llm.service.LlmProvider.parseLegacy(provider, backend);
    }
    /** 요청별 프롬프트 가이드 오버라이드. 이 DTO는 현재 가이드를 주입하지 않지만 필드 시그니처 통일을 위해 보유. 없으면 null. */
    private Map<String, String> promptOverrides;
}
