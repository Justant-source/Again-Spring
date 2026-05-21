package com.againspring.llmworker.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InvocationResultResponse {
    private final InvocationStatus status;
    private final String text;
    private final String partial;
    private final String error;
    private final String errorType;

    public static InvocationResultResponse done(String text) {
        return InvocationResultResponse.builder().status(InvocationStatus.DONE).text(text).build();
    }

    public static InvocationResultResponse streaming(String partial) {
        return InvocationResultResponse.builder().status(InvocationStatus.STREAMING).partial(partial).build();
    }

    public static InvocationResultResponse pending() {
        return InvocationResultResponse.builder().status(InvocationStatus.PENDING).build();
    }

    public static InvocationResultResponse canceled() {
        return InvocationResultResponse.builder().status(InvocationStatus.CANCELED).build();
    }

    public static InvocationResultResponse failed(String error, String errorType) {
        return InvocationResultResponse.builder()
                .status(InvocationStatus.FAILED).error(error).errorType(errorType).build();
    }
}
