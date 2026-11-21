package com.againspring.marketing.experiment;

import com.againspring.domain.marketing.MarketingFormatArmEntity;

/** 영상 포맷 arm = 영상 매개변수 묶음 (설계 §2.1). */
public record FormatArm(
    String key,
    String renderProfile,
    String scriptStyle,
    int commentCount,
    String outroMode,
    Integer maxDurationSec,
    boolean enabled,
    String description
) {
    public static FormatArm from(MarketingFormatArmEntity e) {
        return new FormatArm(e.getArmKey(), e.getRenderProfile(), e.getScriptStyle(), e.getCommentCount(),
            e.getOutroMode(), e.getMaxDurationSec(), e.isEnabled(), e.getDescription());
    }
}
