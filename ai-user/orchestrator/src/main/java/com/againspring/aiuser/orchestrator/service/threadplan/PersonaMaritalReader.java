package com.againspring.aiuser.orchestrator.service.threadplan;

import com.againspring.aiuser.orchestrator.domain.Persona;

import java.util.Locale;

/**
 * persona-diversity-v4 계약1 — {@code personas.marital}(Flyway V22) 읽기 어댑터.
 *
 * <p>SSOT는 컬럼 하나다. V22가 {@code NOT NULL DEFAULT 'SINGLE'}로 만들었고
 * {@code PersonaProfileRegenerator}도 컬럼에만 쓴다. {@code voice_profile.marital}은
 * 어떤 코드도 채우지 않으므로 폴백을 두지 않는다 — 폴백을 두면 컬럼이 절대 비지 않아
 * 실행되지 않는 죽은 분기가 되고, 어느 쪽이 권위본인지 흐려진다.</p>
 */
public final class PersonaMaritalReader {

    public static final String SINGLE = "SINGLE";
    public static final String DATING = "DATING";
    public static final String ENGAGED = "ENGAGED";
    public static final String MARRIED = "MARRIED";

    private PersonaMaritalReader() {}

    /** 알 수 없는 값·null은 SINGLE(V22 컬럼 기본값). */
    public static String read(Persona p) {
        if (p == null || p.getMarital() == null) return SINGLE;
        String s = p.getMarital().trim().toUpperCase(Locale.ROOT);
        return switch (s) {
            case DATING, ENGAGED, MARRIED -> s;
            default -> SINGLE;
        };
    }

    public static boolean isMarried(Persona p) {
        return MARRIED.equals(read(p));
    }
}
