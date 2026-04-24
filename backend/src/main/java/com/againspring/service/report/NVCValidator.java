package com.againspring.service.report;

import com.againspring.domain.Report;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Validates NVC script structure.
 * Each script must have 4 steps: observation, feeling, need, request.
 */
@Slf4j
@Component
public class NVCValidator {

    /**
     * Validates a single NVC script.
     *
     * @param script Script to validate
     * @return true if valid, false otherwise
     */
    public boolean validate(Report.NVCScripts.NVCScript script) {
        if (script == null) {
            return false;
        }

        boolean hasObservation = script.getObservation() != null && !script.getObservation().isBlank();
        boolean hasFeeling = script.getFeeling() != null && !script.getFeeling().isBlank();
        boolean hasNeed = script.getNeed() != null && !script.getNeed().isBlank();
        boolean hasRequest = script.getRequest() != null && !script.getRequest().isBlank();

        return hasObservation && hasFeeling && hasNeed && hasRequest;
    }

    /**
     * Creates a fallback NVC script with placeholder text.
     */
    public Report.NVCScripts.NVCScript createFallback() {
        return Report.NVCScripts.NVCScript.builder()
                .observation("상대방의 행동을 이해했습니다.")
                .feeling("여러 감정을 느꼈습니다.")
                .need("상호 이해가 필요합니다.")
                .request("함께 노력해 주시길 바랍니다.")
                .build();
    }
}
