package com.againspring.domain.enums;

/**
 * 갈등 유형 (RATIO_CALCULATION.md 기준)
 * factual | difference | mixed
 */
public enum ConflictType {
    FACTUAL("factual"),
    DIFFERENCE("difference"),
    MIXED("mixed");

    private final String value;

    ConflictType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static ConflictType fromValue(String value) {
        for (ConflictType type : ConflictType.values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown ConflictType: " + value);
    }
}
