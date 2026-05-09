package com.againspring.api;

import com.againspring.api.dto.request.AgreeReconfirmRequest;
import com.againspring.api.dto.request.SignupRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Auth Consent Validation Tests")
class AuthConsentTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    // ===== SignupRequest 동의 검증 =====

    @Test
    @DisplayName("필수 동의 3개 모두 false → 검증 실패")
    void signupRequest_allConsentFalse_fails() {
        SignupRequest req = buildSignupRequest(false, false, false, false);
        Set<ConstraintViolation<SignupRequest>> violations = validator.validate(req);

        long consentViolations = violations.stream()
                .filter(v -> v.getPropertyPath().toString().contains("Agreed"))
                .count();
        assertEquals(3, consentViolations, "필수 동의 3개 모두 위반 발생해야 함");
    }

    @Test
    @DisplayName("termsAgreed=false → 이용약관 동의 위반")
    void signupRequest_termsAgreedFalse_fails() {
        SignupRequest req = buildSignupRequest(false, true, true, false);
        Set<ConstraintViolation<SignupRequest>> violations = validator.validate(req);

        boolean hasTermsViolation = violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("termsAgreed"));
        assertTrue(hasTermsViolation);
    }

    @Test
    @DisplayName("privacyAgreed=false → 개인정보 처리방침 동의 위반")
    void signupRequest_privacyAgreedFalse_fails() {
        SignupRequest req = buildSignupRequest(true, false, true, false);
        Set<ConstraintViolation<SignupRequest>> violations = validator.validate(req);

        boolean hasPrivacyViolation = violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("privacyAgreed"));
        assertTrue(hasPrivacyViolation);
    }

    @Test
    @DisplayName("disclaimerAgreed=false → 면책 고지 동의 위반")
    void signupRequest_disclaimerAgreedFalse_fails() {
        SignupRequest req = buildSignupRequest(true, true, false, false);
        Set<ConstraintViolation<SignupRequest>> violations = validator.validate(req);

        boolean hasDisclaimerViolation = violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("disclaimerAgreed"));
        assertTrue(hasDisclaimerViolation);
    }

    @Test
    @DisplayName("필수 동의 3개 true → 동의 검증 통과 (다른 필드 위반은 별도)")
    void signupRequest_allRequiredConsentTrue_noConsentViolations() {
        SignupRequest req = buildSignupRequest(true, true, true, false);
        Set<ConstraintViolation<SignupRequest>> violations = validator.validate(req);

        long consentViolations = violations.stream()
                .filter(v -> v.getPropertyPath().toString().contains("Agreed"))
                .count();
        assertEquals(0, consentViolations, "필수 동의 통과 후 동의 관련 위반 없어야 함");
    }

    @Test
    @DisplayName("마케팅 false도 위반 없음 (선택 항목)")
    void signupRequest_marketingFalse_noViolation() {
        SignupRequest req = buildSignupRequest(true, true, true, false);
        Set<ConstraintViolation<SignupRequest>> violations = validator.validate(req);

        boolean hasMarketingViolation = violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("marketingAgreed"));
        assertFalse(hasMarketingViolation);
    }

    // ===== AgreeReconfirmRequest 동의 검증 =====

    @Test
    @DisplayName("AgreeReconfirmRequest 필수 3개 false → 검증 실패")
    void agreeReconfirmRequest_allFalse_fails() {
        AgreeReconfirmRequest req = buildAgreeReconfirmRequest(false, false, false, false);
        Set<ConstraintViolation<AgreeReconfirmRequest>> violations = validator.validate(req);

        long consentViolations = violations.stream()
                .filter(v -> v.getPropertyPath().toString().contains("Agreed"))
                .count();
        assertEquals(3, consentViolations);
    }

    @Test
    @DisplayName("AgreeReconfirmRequest 필수 3개 true → 검증 통과")
    void agreeReconfirmRequest_allRequiredTrue_passes() {
        AgreeReconfirmRequest req = buildAgreeReconfirmRequest(true, true, true, false);
        Set<ConstraintViolation<AgreeReconfirmRequest>> violations = validator.validate(req);

        long consentViolations = violations.stream()
                .filter(v -> v.getPropertyPath().toString().contains("Agreed"))
                .count();
        assertEquals(0, consentViolations);
    }

    // ===== 헬퍼 =====

    private SignupRequest buildSignupRequest(
            boolean termsAgreed, boolean privacyAgreed, boolean disclaimerAgreed, boolean marketingAgreed) {
        try {
            SignupRequest req = new SignupRequest();
            setField(req, "email", "test@test.com");
            setField(req, "password", "password123");
            setField(req, "nickname", "테스터");
            setField(req, "verificationCode", "123456");
            setField(req, "termsAgreed", termsAgreed);
            setField(req, "privacyAgreed", privacyAgreed);
            setField(req, "disclaimerAgreed", disclaimerAgreed);
            setField(req, "marketingAgreed", marketingAgreed);
            return req;
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private AgreeReconfirmRequest buildAgreeReconfirmRequest(
            boolean termsAgreed, boolean privacyAgreed, boolean disclaimerAgreed, boolean marketingAgreed) {
        try {
            AgreeReconfirmRequest req = new AgreeReconfirmRequest();
            setField(req, "termsAgreed", termsAgreed);
            setField(req, "privacyAgreed", privacyAgreed);
            setField(req, "disclaimerAgreed", disclaimerAgreed);
            setField(req, "marketingAgreed", marketingAgreed);
            return req;
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private void setField(Object obj, String fieldName, Object value) throws Exception {
        var field = obj.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(obj, value);
    }
}
