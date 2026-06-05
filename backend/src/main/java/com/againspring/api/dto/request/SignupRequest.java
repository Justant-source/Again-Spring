package com.againspring.api.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Signup request DTO.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SignupRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;

    @NotBlank(message = "Nickname is required")
    private String nickname;

    @NotBlank(message = "Verification code is required")
    @Size(min = 4, max = 4, message = "Verification code must be 4 digits")
    private String verificationCode;

    @AssertTrue(message = "이용약관에 동의해주세요.")
    private boolean termsAgreed;

    @AssertTrue(message = "개인정보 처리방침에 동의해주세요.")
    private boolean privacyAgreed;

    @AssertTrue(message = "면책 고지에 동의해주세요.")
    private boolean disclaimerAgreed;

    private boolean marketingAgreed;

    /**
     * 게스트 → 회원 마이그레이션 ID (선택).
     * FE가 현재 게스트 user.id를 함께 전송하면 BE가 게스트 데이터(온보딩/MBTI/세션)를
     * 신규 회원에 이전. AuthController에서 Authorization 헤더의 게스트 토큰과 일치 검증.
     */
    private String migrateFromGuestId;
}
