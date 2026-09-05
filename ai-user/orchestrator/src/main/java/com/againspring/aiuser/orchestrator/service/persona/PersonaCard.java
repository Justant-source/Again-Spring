package com.againspring.aiuser.orchestrator.service.persona;

import com.againspring.aiuser.orchestrator.domain.Persona;

import java.util.Map;

/**
 * WP3 임시 구현 — 병합 시 WP1 버전(orchestrator/.../persona/PersonaCard.java)으로 대체.
 *
 * <p>00-shared.md 계약 4의 400자 이내 텍스트 카드. WP1이 실제 닉네임 조회·시그니처 문구·
 * 관심사·지뢰 데이터를 채워 넣는다. 여기서는 {@code PlanPersonaMapper}가 컴파일/테스트되도록
 * 계약 형태 그대로의 최소 렌더러만 제공한다(닉네임은 personaId로 대체).</p>
 */
public final class PersonaCard {

    private PersonaCard() {}

    public static String render(Persona p) {
        if (p == null) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("[페르소나] 닉네임=").append(p.getId())
                .append(" · ").append(p.getAgeYears()).append("세 ")
                .append(genderLabel(p.getGender()))
                .append(" · ").append(maritalLabel(p));
        if (p.getJobTitle() != null && !p.getJobTitle().isBlank()) {
            sb.append(" · ").append(p.getJobTitle());
        } else if (p.getJobType() != null) {
            sb.append(" · ").append(p.getJobType());
        }
        sb.append('\n');
        sb.append("[말투] ").append(styleAxesLabel(p.getStyleAxes()));
        String rendered = sb.toString();
        return rendered.length() > 400 ? rendered.substring(0, 400) : rendered;
    }

    private static String genderLabel(String gender) {
        return "M".equalsIgnoreCase(gender) ? "남" : "여";
    }

    private static String maritalLabel(Persona p) {
        String marital = p.getMarital() == null ? "SINGLE" : p.getMarital();
        if (!"MARRIED".equalsIgnoreCase(marital)) {
            return switch (marital.toUpperCase(java.util.Locale.ROOT)) {
                case "DATING" -> "연애중";
                case "ENGAGED" -> "약혼";
                default -> "미혼";
            };
        }
        StringBuilder sb = new StringBuilder("기혼");
        if (p.getMarriedYears() != null) sb.append(' ').append(p.getMarriedYears()).append("년차");
        if (p.isHasKids()) sb.append(", 아이 있음");
        return sb.toString();
    }

    private static String styleAxesLabel(Map<String, Object> axes) {
        if (axes == null || axes.isEmpty()) return "정보 없음";
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, Object> e : axes.entrySet()) {
            if (!first) sb.append(" · ");
            sb.append(e.getValue());
            first = false;
        }
        return sb.toString();
    }
}
