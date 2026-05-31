package com.againspring.api.community.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response containing the invite URL for a 3-way session.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InviteUrlResponse {

    private String url;
}
