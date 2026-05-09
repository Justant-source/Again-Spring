package com.againspring.api.dto.request;

import jakarta.validation.constraints.AssertTrue;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AgreeReconfirmRequest {

    @AssertTrue(message = "이용약관에 동의해주세요.")
    private boolean termsAgreed;

    @AssertTrue(message = "개인정보 처리방침에 동의해주세요.")
    private boolean privacyAgreed;

    @AssertTrue(message = "면책 고지에 동의해주세요.")
    private boolean disclaimerAgreed;

    private boolean marketingAgreed;
}
