package com.againspring.aiuser.llm.dto;

import lombok.Builder;
import lombok.Value;
import java.util.List;

@Value
@Builder
public class HumanReplyBatchResponse {
    String provider;
    String model;
    String correlationId;
    List<Reply> replies;
    long elapsedMs;

    @Value @Builder
    public static class Reply { Long humanCommentId; String personaId; String body; }
}
