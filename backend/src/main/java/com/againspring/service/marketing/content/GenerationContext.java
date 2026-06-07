package com.againspring.service.marketing.content;

import java.util.Map;

/**
 * Immutable context passed to every ContentGenerator.
 * sourceContent: 커뮤니티 게시글 요약 문자열 (작성자/상대방 입장 + 공감비율 + 링크 포함).
 * templateBody and templateVariables are null unless generating from a template.
 */
public record GenerationContext(
        String sourceContent,
        String relationType,
        PlatformDescriptor descriptor,
        String templateBody,
        Map<String, String> templateVariables
) {

    public static GenerationContext of(String sourceContent, String relationType, PlatformDescriptor descriptor) {
        return new GenerationContext(sourceContent, relationType, descriptor, null, null);
    }

    public boolean hasTemplate() {
        return templateBody != null && !templateBody.isBlank();
    }
}
