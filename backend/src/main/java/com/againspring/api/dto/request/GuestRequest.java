package com.againspring.api.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class GuestRequest {

    /** 초대 토큰 (같은 URL 재접속 시 동일 Guest ID 반환) */
    private String inviteToken;

    /** 게스트 닉네임 (선택) */
    private String nickname;

    /** 브라우저 기기 식별자 — 동일 기기 재접속 시 동일 Guest ID 재발급 */
    private String deviceId;
}
