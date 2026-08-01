package com.againspring.aiuser.orchestrator.service.match;

import com.againspring.aiuser.orchestrator.domain.Persona;
import com.againspring.aiuser.orchestrator.domain.PersonaFactAssertion;
import com.againspring.aiuser.orchestrator.domain.StoryProfile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Hard filter over evaluable axes only (plan §6.5 / §2.7).
 * Evaluable: active, register, age, gender, job, region (when both sides present).
 * Always UNEVALUATED (never reject): marriage, parenting, cannot_claim.
 */
public final class PersonaHardFilter {

    private static final Set<String> REGISTERS = Set.of("NATEPAN", "BLIND");

    /** Axes that stay UNEVALUATED until fact corpus covers them. */
    public static final List<String> ALWAYS_UNEVALUATED = List.of(
            "marriage", "parenting", "cannot_claim");

    private PersonaHardFilter() {}

    public static FilterResult filter(
            Persona persona,
            List<PersonaFactAssertion> facts,
            StoryProfile profile) {
        List<String> reasons = new ArrayList<>();
        boolean passed = true;

        if (persona == null) {
            reasons.add("FAIL:active");
            appendAlwaysUnevaluated(reasons);
            return new FilterResult(false, reasons);
        }

        if (!persona.isActive()) {
            reasons.add("FAIL:active");
            passed = false;
        } else {
            reasons.add("PASS:active");
        }

        Map<String, String> factMap = toFactMap(facts);
        Map<String, Object> vp = persona.getVoiceProfile() != null
                ? persona.getVoiceProfile() : Map.of();

        // register / voice_type
        String storyReg = normalizeRegister(profile != null ? profile.sourceRegister() : null);
        if (storyReg != null) {
            String personaReg = firstNonBlank(
                    str(vp.get("voice_type")),
                    factMap.get("voice_type"));
            if (personaReg == null) {
                reasons.add("UNEVALUATED:register");
            } else {
                String norm = normalizeRegister(personaReg);
                if (norm == null || !storyReg.equals(norm)) {
                    reasons.add("FAIL:register");
                    passed = false;
                } else {
                    reasons.add("PASS:register");
                }
            }
        }

        Map<String, String> identity = profile != null && profile.explicitIdentity() != null
                ? profile.explicitIdentity() : Map.of();

        // age (analyzer may emit age_band)
        String storyAge = firstNonBlank(identity.get("age"), identity.get("age_band"));
        if (storyAge != null) {
            String personaAge = firstNonBlank(str(vp.get("age")), factMap.get("age"));
            AxisOutcome ageOut = compareAge(storyAge, personaAge);
            reasons.add(ageOut.reason("age"));
            if (ageOut == AxisOutcome.FAIL) passed = false;
        }

        // gender
        String storyGender = firstNonBlank(identity.get("gender"));
        if (storyGender != null) {
            String personaGender = firstNonBlank(str(vp.get("gender")), factMap.get("gender"));
            AxisOutcome genderOut = compareGender(storyGender, personaGender);
            reasons.add(genderOut.reason("gender"));
            if (genderOut == AxisOutcome.FAIL) passed = false;
        }

        // job / occupation
        String storyJob = firstNonBlank(identity.get("occupation"), identity.get("job"));
        if (storyJob != null) {
            String personaJob = firstNonBlank(str(vp.get("job")), factMap.get("job"), factMap.get("occupation"));
            AxisOutcome jobOut = compareLoose(storyJob, personaJob);
            reasons.add(jobOut.reason("job"));
            if (jobOut == AxisOutcome.FAIL) passed = false;
        }

        // region — only when both present (story + persona)
        String storyRegion = firstNonBlank(identity.get("region"));
        if (storyRegion != null) {
            String personaRegion = firstNonBlank(str(vp.get("region")), factMap.get("region"));
            if (personaRegion == null) {
                reasons.add("UNEVALUATED:region");
            } else {
                AxisOutcome regionOut = compareLoose(storyRegion, personaRegion);
                // compareLoose with both non-null never returns UNEVALUATED
                reasons.add(regionOut.reason("region"));
                if (regionOut == AxisOutcome.FAIL) passed = false;
            }
        }

        appendAlwaysUnevaluated(reasons);
        return new FilterResult(passed, reasons);
    }

    /** Count of PASS-evaluated demographic axes (age/gender/job/region). */
    public static int countEvaluatedFactPasses(List<String> reasons) {
        int n = 0;
        for (String r : reasons) {
            if (r != null && (r.equals("PASS:age") || r.equals("PASS:gender")
                    || r.equals("PASS:job") || r.equals("PASS:region"))) {
                n++;
            }
        }
        return n;
    }

    /** Count of demographic axes that were actually compared (PASS or FAIL). */
    public static int countEvaluatedFactAxes(List<String> reasons) {
        int n = 0;
        for (String r : reasons) {
            if (r == null) continue;
            if (r.startsWith("PASS:age") || r.startsWith("FAIL:age")
                    || r.startsWith("PASS:gender") || r.startsWith("FAIL:gender")
                    || r.startsWith("PASS:job") || r.startsWith("FAIL:job")
                    || r.startsWith("PASS:region") || r.startsWith("FAIL:region")) {
                n++;
            }
        }
        return n;
    }

    static String normalizeRegister(String register) {
        if (register == null || register.isBlank()) return null;
        String r = register.trim().toUpperCase(Locale.ROOT);
        return REGISTERS.contains(r) ? r : null;
    }

    private static void appendAlwaysUnevaluated(List<String> reasons) {
        for (String axis : ALWAYS_UNEVALUATED) {
            reasons.add("UNEVALUATED:" + axis);
        }
    }

    private static Map<String, String> toFactMap(List<PersonaFactAssertion> facts) {
        Map<String, String> map = new HashMap<>();
        if (facts == null) return map;
        for (PersonaFactAssertion f : facts) {
            if (f == null || f.getFactKey() == null || f.getFactValue() == null) continue;
            String key = f.getFactKey().trim().toLowerCase(Locale.ROOT);
            String val = f.getFactValue().trim();
            if (!key.isEmpty() && !val.isEmpty()) {
                map.putIfAbsent(key, val);
            }
        }
        return map;
    }

    private enum AxisOutcome {
        PASS, FAIL, UNEVALUATED;

        String reason(String axis) {
            return name() + ":" + axis;
        }
    }

    private static AxisOutcome compareAge(String story, String persona) {
        if (persona == null) return AxisOutcome.UNEVALUATED;
        String s = story.trim().toLowerCase(Locale.ROOT);
        String p = persona.trim().toLowerCase(Locale.ROOT);
        if (s.equals(p)) return AxisOutcome.PASS;
        // band compatibility: "30s" ↔ "30s_early" / "30s_late"
        String sBand = ageBand(s);
        String pBand = ageBand(p);
        if (sBand != null && sBand.equals(pBand)) return AxisOutcome.PASS;
        return AxisOutcome.FAIL;
    }

    private static String ageBand(String age) {
        if (age == null) return null;
        if (age.startsWith("10")) return "10s";
        if (age.startsWith("20")) return "20s";
        if (age.startsWith("30")) return "30s";
        if (age.startsWith("40")) return "40s";
        if (age.startsWith("50")) return "50s";
        if (age.startsWith("60")) return "60s";
        return null;
    }

    private static AxisOutcome compareGender(String story, String persona) {
        if (persona == null) return AxisOutcome.UNEVALUATED;
        String s = normalizeGender(story);
        String p = normalizeGender(persona);
        if (s == null || p == null) return AxisOutcome.UNEVALUATED;
        return s.equals(p) ? AxisOutcome.PASS : AxisOutcome.FAIL;
    }

    private static String normalizeGender(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String g = raw.trim().toLowerCase(Locale.ROOT);
        if (g.equals("m") || g.equals("male") || g.equals("남") || g.equals("남성") || g.equals("남자")) {
            return "M";
        }
        if (g.equals("f") || g.equals("female") || g.equals("여") || g.equals("여성") || g.equals("여자")) {
            return "F";
        }
        return g.toUpperCase(Locale.ROOT);
    }

    private static AxisOutcome compareLoose(String story, String persona) {
        if (persona == null) return AxisOutcome.UNEVALUATED;
        String s = story.trim().toLowerCase(Locale.ROOT);
        String p = persona.trim().toLowerCase(Locale.ROOT);
        if (s.equals(p) || s.contains(p) || p.contains(s)) return AxisOutcome.PASS;
        return AxisOutcome.FAIL;
    }

    private static String str(Object o) {
        if (o == null) return null;
        String s = Objects.toString(o, "").trim();
        return s.isEmpty() ? null : s;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String v : values) {
            if (v != null && !v.isBlank()) return v.trim();
        }
        return null;
    }
}
