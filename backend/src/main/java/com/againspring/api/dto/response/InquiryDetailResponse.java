package com.againspring.api.dto.response;

import com.againspring.domain.inquiry.Inquiry;
import com.againspring.domain.inquiry.InquiryMessage;
import lombok.*;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InquiryDetailResponse {

    private String id;
    private String userId;
    private String subject;
    private String category;
    private String status;
    private String assigneeUserId;
    private Instant createdAt;
    private Instant updatedAt;
    private List<InquiryMessageDto> messages;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InquiryMessageDto {
        private Long id;
        private String senderRole;
        private String senderUserId;
        private String body;
        private Instant createdAt;

        public static InquiryMessageDto from(InquiryMessage msg) {
            return InquiryMessageDto.builder()
                    .id(msg.getId())
                    .senderRole(msg.getSenderRole())
                    .senderUserId(msg.getSenderUserId())
                    .body(msg.getBody())
                    .createdAt(msg.getCreatedAt())
                    .build();
        }
    }

    public static InquiryDetailResponse from(Inquiry inquiry, List<InquiryMessage> messages) {
        return InquiryDetailResponse.builder()
                .id(inquiry.getId())
                .userId(inquiry.getUserId())
                .subject(inquiry.getSubject())
                .category(inquiry.getCategory())
                .status(inquiry.getStatus())
                .assigneeUserId(inquiry.getAssigneeUserId())
                .createdAt(inquiry.getCreatedAt())
                .updatedAt(inquiry.getUpdatedAt())
                .messages(messages.stream()
                        .map(InquiryMessageDto::from)
                        .toList())
                .build();
    }
}
