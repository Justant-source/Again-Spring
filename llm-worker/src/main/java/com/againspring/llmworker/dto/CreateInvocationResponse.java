package com.againspring.llmworker.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CreateInvocationResponse {
    private final String invocationId;
}
