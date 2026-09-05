package com.againspring.aiuser.llm.service;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * persona-diversity-v4 계약 4 — WP1의 {@code PersonaCard}(orchestrator)가 아직 요청에 실려오지
 * 않을 때 쓰는 임시 축약기. age/gender/job/general_style/signature_phrases 5개만, 300자 이내.
 * WP3가 orchestrator 쪽에서 {@code personaCard} 문자열을 채워 보내기 시작하면 이 클래스는
 * 자연히 호출되지 않게 된다 — 하위 호환용으로 남겨둔다(삭제 금지).
 *
 * <p><b>이 폴백은 신원축(marital/job_type/style_axes)을 복원하지 못한다</b> — legacy
 * voiceProfile(age/gender/job/general_style)에는 애초에 그 값들이 없기 때문이다. 그래서 이
 * 폴백을 개선하는 대신, 호출될 때마다 경고 로그만 남긴다: 이 로그가 보이면 orchestrator 쪽
 * 어딘가가 아직 personaCard를 채워 보내지 않고 있다는 뜻이다(본질적 수정 대상은 orchestrator).
 */
@Slf4j
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
        return renderFromVoiceProfile(voiceProfile, raw.get("personaId"));
    }

    /** {@code voiceProfile}(persona.voice_profile 원본 맵)만 있을 때. */
    public static String renderFromVoiceProfile(Map<String, Object> voiceProfile) {
        return renderFromVoiceProfile(voiceProfile, null);
    }

    @SuppressWarnings("unchecked")
    private static String renderFromVoiceProfile(Map<String, Object> voiceProfile, Object personaId) {
        if (voiceProfile == null || voiceProfile.isEmpty()) return "";
        log.warn("personaCard 미수신 — legacy voiceProfile 축약 폴백 사용 (신원축 marital/job_type/"
                + "style_axes 복원 불가, orchestrator가 이 페르소나에 personaCard를 채워 보내야 함) personaId={}",
                personaId == null ? "unknown" : personaId);
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
