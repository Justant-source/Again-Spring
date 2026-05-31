package com.againspring.api.community.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response containing 3-way session details.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ThreeWaySessionResponse {

    private String id;
    private String status;
    private String inviteToken;
    private String partyAUserId;
    private String partyBUserId;
    private String category;
    private String createdAt;
    private String updatedAt;
}
