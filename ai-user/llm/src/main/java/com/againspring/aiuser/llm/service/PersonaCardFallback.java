package com.againspring.aiuser.llm.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * persona-diversity-v4 계약 4 — WP1의 {@code PersonaCard}(orchestrator)가 아직 요청에 실려오지
 * 않을 때 쓰는 임시 축약기. age/gender/job/general_style/signature_phrases 5개만, 300자 이내.
 * WP3가 orchestrator 쪽에서 {@code personaCard} 문자열을 채워 보내기 시작하면 이 클래스는
 * 자연히 호출되지 않게 된다 — 하위 호환용으로 남겨둔다(삭제 금지).
 */
public final class PersonaCardFallback {
    private static final int MAX_LEN = 300;

    private PersonaCardFallback() {}

    /**
     * {@code raw} = orchestrator {@code PlanPersonaMapper.toPersonaMap} 형태
     * ({@code {personaId, nickname, voiceProfile:{...}, formality, ...}}).
     */
    @SuppressWarnings("unchecked")
    public static String render(Map<String, Object> raw) {
        if (raw == null || raw.isEmpty()) return "";
        Object vp = raw.get("voiceProfile");
        Map<String, Object> voiceProfile = vp instanceof Map ? (Map<String, Object>) vp : Map.of();
        return renderFromVoiceProfile(voiceProfile);
    }

    /** {@code voiceProfile}(persona.voice_profile 원본 맵)만 있을 때. */
    @SuppressWarnings("unchecked")
    public static String renderFromVoiceProfile(Map<String, Object> voiceProfile) {
        if (voiceProfile == null || voiceProfile.isEmpty()) return "";
        String age = str(voiceProfile.get("age"));
        String gender = str(voiceProfile.get("gender"));
        String job = str(voiceProfile.get("job"));
        String generalStyle = str(voiceProfile.get("general_style"));

        String signaturePhrases = "";
        Object lexicon = voiceProfile.get("lexicon");
        if (lexicon instanceof Map<?, ?> lex) {
            signaturePhrases = joinPhrases(lex.get("signature_phrases"));
        }
        if (signaturePhrases.isBlank()) {
            signaturePhrases = joinPhrases(voiceProfile.get("signature_phrases"));
        }

        List<String> demoParts = new ArrayList<>();
        String ageGender = String.join(" ", nonBlank(age), nonBlank(gender)).trim();
        if (!ageGender.isBlank()) demoParts.add(ageGender);
        if (!job.isBlank()) demoParts.add(job);
        if (!generalStyle.isBlank()) demoParts.add(generalStyle);

        if (demoParts.isEmpty() && signaturePhrases.isBlank()) return "";

        StringBuilder sb = new StringBuilder("[페르소나] ").append(String.join(" · ", demoParts));
        if (!signaturePhrases.isBlank()) {
            if (sb.length() > "[페르소나] ".length()) sb.append(" ");
            sb.append("[버릇] ").append(signaturePhrases);
        }
        String out = sb.toString().trim();
        return out.length() <= MAX_LEN ? out : out.substring(0, MAX_LEN);
    }

    private static String nonBlank(String s) { return s == null ? "" : s; }
    private static String str(Object o) { return o == null ? "" : String.valueOf(o).trim(); }

    private static String joinPhrases(Object o) {
        if (o instanceof List<?> list) {
            List<String> strs = new ArrayList<>();
            for (Object item : list) {
                if (item != null && !String.valueOf(item).isBlank()) strs.add(String.valueOf(item).trim());
            }
            return String.join(", ", strs);
        }
        return o == null ? "" : String.valueOf(o).trim();
    }
}
