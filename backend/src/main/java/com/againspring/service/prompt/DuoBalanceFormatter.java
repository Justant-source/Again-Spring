package com.againspring.service.prompt;

import com.againspring.domain.Session;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * Renders a balance directive for the Duo turn prompt when one side has been
 * carrying noticeably more emotional weight or speaking volume than the other.
 * Returns empty when balance is reasonable so the prompt stays quiet by default.
 *
 * The directive is phrased as "attention distribution" rather than picking a
 * side — the goal is even depth of exploration, never advocacy.
 */
@Component
public class DuoBalanceFormatter {

    private static final double INTENSITY_RATIO_TRIGGER = 1.5;
    private static final int VOLUME_RATIO_TRIGGER = 2;

    public String render(Session session) {
        if (session == null) return "";

        double aIntensity = doubleValue(session.getUserAEmotionIntensity());
        double bIntensity = doubleValue(session.getUserBEmotionIntensity());
        int aCount = nullSafeInt(session.getUserAMessageCount());
        int bCount = nullSafeInt(session.getUserBMessageCount());

        boolean enoughData = (aCount + bCount) >= 4;
        if (!enoughData) return "";

        String directive = pickDirective(aIntensity, bIntensity, aCount, bCount);
        if (directive == null) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("<duo_balance note=\"관심 분배 보정 — 편들기 금지, 양쪽 동등한 깊이로 탐색\">\n");
        sb.append(String.format("- A 발화 %d회, 누적 강도 %.2f / B 발화 %d회, 누적 강도 %.2f\n",
            aCount, aIntensity, bCount, bIntensity));
        sb.append("- ").append(directive).append("\n");
        sb.append("</duo_balance>\n");
        return sb.toString();
    }

    private String pickDirective(double aI, double bI, int aCount, int bCount) {
        boolean aDominant = (aI > 0 && bI > 0 && aI / Math.max(bI, 0.01) >= INTENSITY_RATIO_TRIGGER)
            || (aCount >= VOLUME_RATIO_TRIGGER * Math.max(bCount, 1));
        boolean bDominant = (bI > 0 && aI > 0 && bI / Math.max(aI, 0.01) >= INTENSITY_RATIO_TRIGGER)
            || (bCount >= VOLUME_RATIO_TRIGGER * Math.max(aCount, 1));

        if (aDominant && !bDominant) {
            return "B에게는 입장을 차분히 더 풀어낼 수 있도록 부드러운 질문을 한 번 보장해 주세요. A에게는 충분히 들어드렸음을 짧게 인정하고 호흡 한 번 권할 수 있어요.";
        }
        if (bDominant && !aDominant) {
            return "A에게는 자기 입장을 더 구체적으로 풀어낼 여유를 한 번 보장해 주세요. B에게는 들어드렸음을 짧게 인정하고 호흡을 권할 수 있어요.";
        }
        return null;
    }

    private double doubleValue(BigDecimal v) {
        return v == null ? 0.0 : v.doubleValue();
    }

    private int nullSafeInt(Integer v) {
        return v == null ? 0 : v;
    }
}
