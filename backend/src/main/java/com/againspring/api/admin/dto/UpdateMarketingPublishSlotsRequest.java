package com.againspring.api.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateMarketingPublishSlotsRequest {

    @NotBlank
    @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "must be HH:mm (24h)")
    private String instagramFeed;

    @NotBlank
    @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "must be HH:mm (24h)")
    private String instagramReels;

    @NotBlank
    @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "must be HH:mm (24h)")
    private String youtubeShorts;

    @NotBlank
    @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "must be HH:mm (24h)")
    private String xThread;
}
