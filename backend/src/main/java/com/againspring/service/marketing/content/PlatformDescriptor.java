package com.againspring.service.marketing.content;

import lombok.Builder;
import lombok.Value;

/**
 * Immutable metadata for a single social platform.
 * Loaded from marketing/platform-descriptors.yml at startup.
 */
@Value
@Builder
public class PlatformDescriptor {

    public enum RenderType {
        TEXT, CARD, MARKDOWN
    }

    String code;
    String displayName;
    int maxCharsPerUnit;
    int maxUnits;
    int hashtagCount;
    RenderType renderType;
    boolean enabled;
}
