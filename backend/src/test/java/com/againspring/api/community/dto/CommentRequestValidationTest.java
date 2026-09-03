package com.againspring.api.community.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CommentRequestValidationTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void rejectsBodyOver1000Chars() {
        CommentRequest req = CommentRequest.builder().body("가".repeat(1001)).build();
        assertThat(validator.validate(req)).isNotEmpty();
    }

    @Test
    void accepts1000Chars() {
        CommentRequest req = CommentRequest.builder().body("가".repeat(1000)).build();
        assertThat(validator.validate(req)).isEmpty();
    }
}
