package com.againspring.aiuser.orchestrator.service.threadplan;

import com.againspring.aiuser.orchestrator.domain.Persona;

import java.util.Locale;

/**
 * persona-diversity-v4 계약1 — {@code personas.marital}(Flyway V22, WP1) 읽기 어댑터.
 *
 * <p><b>이 브랜치의 한계</b>: WP1의 V22 마이그레이션과 {@code Persona.getMarital()} getter가
 * 아직 이 브랜치에 없다(worktree 분리 작업). 따라서 여기서는 {@code voice_profile.marital}
 * JSON fallback만 구현한다. WP1 병합 후에는 {@link #read(Persona)}를
 * "신규 컬럼 우선 → 컬럼이 비어있는 레거시 행만 voice_profile fallback" 순서로 갱신해야 한다
 * (리플렉션 없이 컴파일 타임에 {@code getMarital()}을 호출하려면 병합이 먼저 필요하다).</p>
 */
public final class PersonaMaritalReader {

    public static final String SINGLE = "SINGLE";
    public static final String DATING = "DATING";
    public static final String ENGAGED = "ENGAGED";
    public static final String MARRIED = "MARRIED";

    private PersonaMaritalReader() {}

    /** {@code voice_profile.marital} fallback. 없거나 알 수 없는 값 → SINGLE(계약1 컬럼 기본값과 동일). */
    public static String read(Persona p) {
        if (p == null || p.getVoiceProfile() == null) return SINGLE;
        Object v = p.getVoiceProfile().get("marital");
        if (v == null) return SINGLE;
        String s = String.valueOf(v).trim().toUpperCase(Locale.ROOT);
        return switch (s) {
            case DATING, ENGAGED, MARRIED -> s;
            default -> SINGLE;
        };
    }

    public static boolean isMarried(Persona p) {
        return MARRIED.equals(read(p));
    }
}
