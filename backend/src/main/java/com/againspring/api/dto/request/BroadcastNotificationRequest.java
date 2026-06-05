package com.againspring.api.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 알림 방송 요청 DTO
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BroadcastNotificationRequest {

    /** 알림 제목 */
    private String title;

    /** 알림 부제목 */
    private String subtitle;

    /** 발송 대상 (ALL, MEMBERS, CUSTOM) */
    private String target;

    /** target=CUSTOM일 때 사용자 ID 목록 */
    private List<String> userIds;

    /** 알림 메시지 (미사용: subtitle이 메시지 역할) */
    private String message;
}
