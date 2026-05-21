package com.againspring.llm.remote.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class WorkerInvocationResultResponse {
    private String status;  // DONE, STREAMING, PENDING, CANCELED, FAILED
    private String text;
    private String partial;
    private String error;
    private String errorType;
}
