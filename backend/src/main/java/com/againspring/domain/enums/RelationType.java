package com.againspring.domain.enums;

/**
 * 관계 유형 (CATEGORIES.md 기준)
 * couple | marriage | friend | family | parent_child | korean_specific | work
 */
public enum RelationType {
    COUPLE("couple"),
    MARRIAGE("marriage"),
    FRIEND("friend"),
    FAMILY("family"),
    PARENT_CHILD("parent_child"),
    KOREAN_SPECIFIC("korean_specific"), // 한국 고유 갈등 카테고리
    WORK("work"); // 직장 관계

    private final String value;

    RelationType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static RelationType fromValue(String value) {
        for (RelationType type : RelationType.values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown RelationType: " + value);
    }
}
