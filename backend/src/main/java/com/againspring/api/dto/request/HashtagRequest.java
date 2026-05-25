package com.againspring.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HashtagRequest {
    @NotBlank
    private String platform;

    @NotBlank
    @Size(max = 100)
    private String tag;

    @Size(max = 50)
    private String category;
}
