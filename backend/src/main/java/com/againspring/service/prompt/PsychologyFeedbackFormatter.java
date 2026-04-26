package com.againspring.service.prompt;

import com.againspring.domain.Session;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Translates accumulated 4 Horsemen / NVC completion history into a short
 * natural-language directive injected into the next-turn prompt. Returns an
 * empty string when no signal is strong enough — keeping prompt noise low.
 */
@Component
public class PsychologyFeedbackFormatter {

    private static final double CRITICISM_AVG_THRESHOLD = 0.4;
    private static final double DEFENSIVENESS_AVG_THRESHOLD = 0.4;
    private static final double STONEWALLING_AVG_THRESHOLD = 0.4;
    private static final double NVC_INCOMPLETE_RATIO_THRESHOLD = 0.5;

    public String render(Session session) {
        if (session == null) return "";

        List<Session.HorsemenTurnEntry> horsemen = session.getHorsemenHistory();
        List<Session.NvcTurnEntry> nvc = session.getNvcCompletionHistory();

        boolean haveAny = (horsemen != null && !horsemen.isEmpty())
            || (nvc != null && !nvc.isEmpty());
        if (!haveAny) return "";

        StringBuilder directives = new StringBuilder();

        if (horsemen != null && !horsemen.isEmpty()) {
            double criticismAvg = averageNonNull(horsemen, e -> e.criticism);
            double defensivenessAvg = averageNonNull(horsemen, e -> e.defensiveness);
            double stonewallingAvg = averageNonNull(horsemen, e -> e.stonewalling);
            boolean contemptSeen = horsemen.stream()
                .anyMatch(e -> e.contempt != null && e.contempt > 0);

            if (criticismAvg >= CRITICISM_AVG_THRESHOLD) {
                directives.append("- 비난(criticism) 패턴이 누적되고 있어요. 이번 턴에는 비판을 욕구로 재구성해 들려주는 NVC 4단계 중 *느낌·욕구* 표현을 우선해 주세요.\n");
            }
            if (contemptSeen) {
                directives.append("- 경멸(contempt) 신호가 감지된 적 있어요. 세션당 1회 EFT 환기 시도 가능 (위기 키워드 없을 때만, 가설형으로).\n");
            }
            if (defensivenessAvg >= DEFENSIVENESS_AVG_THRESHOLD) {
                directives.append("- 방어(defensiveness) 패턴이 보여요. 책임 분담 한 조각 인정하기를 부드럽게 모델링해 주세요.\n");
            }
            if (stonewallingAvg >= STONEWALLING_AVG_THRESHOLD) {
                directives.append("- 담쌓기(stonewalling) 신호가 보여요. 호흡·잠시 쉼을 권하는 한 줄을 응답 끝에 둘 수 있어요.\n");
            }
        }

        if (nvc != null && !nvc.isEmpty()) {
            double observationMiss = missingRatio(nvc, e -> e.observation);
            double needMiss = missingRatio(nvc, e -> e.need);
            if (observationMiss >= NVC_INCOMPLETE_RATIO_THRESHOLD) {
                directives.append("- 사실 관찰 단계가 자주 빠졌어요. 이번 턴은 \"~~한 일이 있었어요\" 식 관찰 문장을 한 번 짚어주세요.\n");
            }
            if (needMiss >= NVC_INCOMPLETE_RATIO_THRESHOLD) {
                directives.append("- 욕구(need) 명시가 부족했어요. 사용자 발화 뒤에 숨은 욕구를 가설형으로 한 줄 비춰주세요.\n");
            }
        }

        if (directives.length() == 0) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("<psychology_feedback note=\"누적 분석 기반, 톤 조정에만 사용\">\n");
        sb.append(directives);
        sb.append("</psychology_feedback>\n");
        return sb.toString();
    }

    private double averageNonNull(List<Session.HorsemenTurnEntry> list, DoubleField extractor) {
        double sum = 0;
        int n = 0;
        for (Session.HorsemenTurnEntry e : list) {
            Double v = extractor.extract(e);
            if (v != null) { sum += v; n++; }
        }
        return n == 0 ? 0 : sum / n;
    }

    private double missingRatio(List<Session.NvcTurnEntry> list, BoolField extractor) {
        int missing = 0;
        int n = 0;
        for (Session.NvcTurnEntry e : list) {
            Boolean v = extractor.extract(e);
            if (v != null) {
                n++;
                if (!v) missing++;
            }
        }
        return n == 0 ? 0 : (double) missing / n;
    }

    @FunctionalInterface
    private interface DoubleField {
        Double extract(Session.HorsemenTurnEntry e);
    }

    @FunctionalInterface
    private interface BoolField {
        Boolean extract(Session.NvcTurnEntry e);
    }
}
