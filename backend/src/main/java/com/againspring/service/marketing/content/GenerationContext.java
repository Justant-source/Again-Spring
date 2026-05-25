package com.againspring.service.marketing.content;

import java.util.Map;

/**
 * Immutable context passed to every ContentGenerator.
 * templateBody and templateVariables are null unless generating from a template (PR3).
 */
public record GenerationContext(
        String simulationSummary,
        String relationType,
        PlatformDescriptor descriptor,
        String templateBody,
        Map<String, String> templateVariables
) {

    public static GenerationContext of(String simulationSummary, String relationType, PlatformDescriptor descriptor) {
        return new GenerationContext(simulationSummary, relationType, descriptor, null, null);
    }

    public boolean hasTemplate() {
        return templateBody != null && !templateBody.isBlank();
    }
}
