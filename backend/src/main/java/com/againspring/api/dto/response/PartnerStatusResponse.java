package com.againspring.api.dto.response;

import com.againspring.service.ChatService;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * PartnerStatusResponse (V1.5 카톡식)
 * 상대방 상태 정보
 */
@Getter @Builder
@NoArgsConstructor @AllArgsConstructor
public class PartnerStatusResponse {
    private boolean joined;             // Solo→Duo 전이됐는지
    private boolean isActive;           // 최근 60초 안에 활동
    private boolean inviteSent;         // 초대 보냈지만 미합류
    private int messageCount;
    private Instant lastActivityAt;

    public static PartnerStatusResponse from(ChatService.PartnerStatus status) {
        return PartnerStatusResponse.builder()
            .joined(status.joined())
            .isActive(status.isActive())
            .inviteSent(status.inviteSent())
            .messageCount(status.messageCount())
            .lastActivityAt(status.lastActivityAt())
            .build();
    }
}
