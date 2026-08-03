package com.againspring.domain.enums;

/**
 * 포스트 발행 모드 (C3 광장형)
 * PUBLISH_NOW: 즉시 발행
 * WAIT_FOR_PARTNER: API 호환용 — 동작은 PUBLISH_NOW와 동일(즉시 PUBLIC). private-until-partner는 폐기됨.
 */
public enum PublishMode {
    PUBLISH_NOW,
    WAIT_FOR_PARTNER
}
