package com.againspring.aiuser.orchestrator.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PersonaDailyQuotaId implements Serializable {
    private String personaId;
    private LocalDate dayBucket;
}
