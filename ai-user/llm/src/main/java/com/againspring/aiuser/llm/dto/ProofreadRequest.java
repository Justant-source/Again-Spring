package com.againspring.aiuser.llm.dto;

import lombok.*;

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
}
