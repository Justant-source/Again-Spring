package com.againspring.aiuser.orchestrator.domain.enums;

public enum PersonaTier {
    HEAVY, REGULAR, LIGHT, DORMANT;

    public int weight() {
        return switch(this) {
            case HEAVY -> 3;
            case REGULAR -> 2;
            case LIGHT -> 1;
            case DORMANT -> 0;
        };
    }
}
