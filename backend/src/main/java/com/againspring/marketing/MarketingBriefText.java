package com.againspring.marketing;

import java.util.regex.Pattern;

/**
 * Repairs legacy comment strings that were JSON-escaped before they were stored.
 *
 * <p>Only strings containing a JSON Unicode escape are repaired. This keeps ordinary
 * user-entered backslashes untouched while ensuring the accompanying escaped line
 * breaks are not sent to ASM as literal backslashes.
 */
public final class MarketingBriefText {

    private static final Pattern UNICODE_ESCAPE = Pattern.compile("\\\\+u[0-9a-fA-F]{4}");

    private MarketingBriefText() {
    }

    public static String normalize(String value) {
        if (value == null || !UNICODE_ESCAPE.matcher(value).find()) {
            return value;
        }

        StringBuilder normalized = new StringBuilder(value.length());
        for (int index = 0; index < value.length();) {
            char character = value.charAt(index);
            if (character != '\\') {
                normalized.append(character);
                index++;
                continue;
            }

            int slashEnd = index;
            while (slashEnd < value.length() && value.charAt(slashEnd) == '\\') {
                slashEnd++;
            }
            if (slashEnd >= value.length()) {
                normalized.append(value, index, slashEnd);
                break;
            }

            char escaped = value.charAt(slashEnd);
            if (escaped == 'u' && slashEnd + 4 < value.length()
                && isHex(value, slashEnd + 1, slashEnd + 5)) {
                normalized.append((char) Integer.parseInt(value.substring(slashEnd + 1, slashEnd + 5), 16));
                index = slashEnd + 5;
            } else if (escaped == 'n') {
                normalized.append('\n');
                index = slashEnd + 1;
            } else if (escaped == 'r') {
                normalized.append('\r');
                index = slashEnd + 1;
            } else if (escaped == '\n' || escaped == '\r') {
                normalized.append(escaped);
                index = slashEnd + 1;
            } else {
                normalized.append(value, index, slashEnd);
                index = slashEnd;
            }
        }
        return normalized.toString();
    }

    private static boolean isHex(String value, int startInclusive, int endExclusive) {
        for (int index = startInclusive; index < endExclusive; index++) {
            if (Character.digit(value.charAt(index), 16) < 0) {
                return false;
            }
        }
        return true;
    }
}
