package com.againspring.service.oauth;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OAuthUserInfo {
    private final String providerId;
    private final String email;
    private final String nickname;
}
