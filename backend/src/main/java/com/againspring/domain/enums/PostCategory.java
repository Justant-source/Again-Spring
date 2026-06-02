package com.againspring.domain.enums;

/**
 * 관계 카테고리 (C3 광장형)
 * COUPLE: 연인
 * MARRIED: 부부
 * FRIEND: 친구
 * FAMILY: 가족
 * WORK: 직장
 * OTHER: 기타
 */
public enum PostCategory {
    COUPLE("연인"),
    MARRIED("부부"),
    FRIEND("친구"),
    FAMILY("가족"),
    WORK("직장"),
    OTHER("기타");

    private final String displayName;

    PostCategory(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
