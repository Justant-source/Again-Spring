package com.againspring.domain.enums;

/**
 * Turn 역할 (DATABASE_SCHEMA.md: turns.role)
 * A | B | MEDIATOR
 */
public enum TurnRole {
    A("A"),
    B("B"),
    MEDIATOR("MEDIATOR");

    private final String value;

    TurnRole(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static TurnRole fromValue(String value) {
        for (TurnRole role : TurnRole.values()) {
            if (role.value.equals(value)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Unknown TurnRole: " + value);
    }
}
