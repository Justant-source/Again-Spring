package com.againspring.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class OAuthCallbackRequest {

    @NotBlank
    private String code;

    @NotBlank
    private String redirectUri;

    private String guestToken;
}
