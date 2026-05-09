package com.againspring.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SubmitFeedbackRequest {

    @NotBlank(message = "카테고리는 필수입니다.")
    private String category;

    @NotBlank(message = "내용은 필수입니다.")
    @Size(min = 10, message = "의견은 10자 이상이어야 합니다.")
    private String content;

    private boolean contactConsent;

    private String contactEmail;

    private String sessionId;

    private String pageUrl;

    private String userAgent;
}
