package com.againspring.marketing;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;

/**
 * Lift a real quality-gate code out of nested ASM/Waggle diagnostics when the
 * wire {@code failure_code} is a generic renderer bucket such as {@code RENDER_UNKNOWN}.
 */
final class MarketingRemoteFailureCodes {

    private MarketingRemoteFailureCodes() {}

    static boolean isGenericRendererFailure(String code) {
        if (code == null || code.isBlank()) return true;
        String u = code.trim().toUpperCase(Locale.ROOT);
        return u.equals("RENDER_UNKNOWN")
            || u.equals("INFRA_WAGGLE_RENDER_FAILED")
            || u.equals("PIPELINE_ERROR");
    }

    static boolean isQualityFailure(String code) {
        if (code == null || code.isBlank()) return false;
        String u = code.trim().toUpperCase(Locale.ROOT);
        return u.startsWith("SIBOM_")
            || u.startsWith("VARIANT_")
            || u.startsWith("DURATION_")
            || u.startsWith("LAYOUT_")
            || u.startsWith("SCRIPT_");
    }

    static String resolve(String reported, Map<String, ?> diagnostics) {
        String nested = firstQualityFailureCode(diagnostics);
        if (isGenericRendererFailure(reported) && nested != null) {
            return nested;
        }
        if (reported != null && !reported.isBlank()) {
            return reported.trim();
        }
        return nested;
    }

    static boolean looksLikeRawDump(String summary) {
        if (summary == null) return false;
        String t = summary.stripLeading();
        return t.startsWith("{") || t.startsWith("'ok'") || t.startsWith("{'ok'");
    }

    static String firstQualityFailureCode(Object node) {
        if (node == null) return null;
        if (node instanceof Map<?, ?> map) {
            Object direct = map.get("failure_code");
            if (direct == null) direct = map.get("failureCode");
            if (direct instanceof String s && isQualityFailure(s)) {
                return s.trim().toUpperCase(Locale.ROOT);
            }
            for (Object value : map.values()) {
                String nested = firstQualityFailureCode(value);
                if (nested != null) return nested;
            }
            return null;
        }
        if (node instanceof Collection<?> items) {
            for (Object item : items) {
                String nested = firstQualityFailureCode(item);
                if (nested != null) return nested;
            }
        }
        return null;
    }
}
