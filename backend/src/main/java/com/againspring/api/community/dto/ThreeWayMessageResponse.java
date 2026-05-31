package com.againspring.api.community.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response containing a single 3-way mediation message.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThreeWayMessageResponse {

    private Long id;
    private String twsId;
    private String authorRole;
    private String content;
    private String createdAt;
    private String llmModel;
}
