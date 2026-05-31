package com.againspring.service.prompt;

import com.againspring.domain.Session;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Translates accumulated 4 Horsemen / NVC completion history into a short
 * natural-language directive injected into the next-turn prompt. Returns an
 * empty string when no signal is strong enough — keeping prompt noise low.
 *
 * <p><b>V16 — 반복 방지 개선</b>: 전체 히스토리 평균 대신 최근 {@link #RECENT_WINDOW}턴 윈도우만
 * 사용합니다. 동일 지시가 윈도우 전체에서 연속 발동 조건이면 "같은 표현 반복 금지 + 전환" 에스컬레이션
 * 메시지로 교체합니다. 이렇게 하면 criticism/need-miss가 높게 유지되더라도 매 턴 동일한
 * "숨은 욕구를 비춰주세요" 지시가 반복 주입되어 사용자가 앵무새처럼 느끼는 현상을 방지합니다.</p>
 */
@Component
public class PsychologyFeedbackFormatter {

    private static final double CRITICISM_AVG_THRESHOLD = 0.4;
    private static final double DEFENSIVENESS_AVG_THRESHOLD = 0.4;
    private static final double STONEWALLING_AVG_THRESHOLD = 0.4;
    private static final double NVC_INCOMPLETE_RATIO_THRESHOLD = 0.5;

    /**
     * 최근 몇 턴만 평가할지 결정하는 윈도우 크기.
     * 전체 히스토리 평균을 쓰면 초반 고비난이 중후반까지 매 턴 동일 지시를 유발함.
     */
    static final int RECENT_WINDOW = 3;

    public String render(Session session) {
        if (session == null) return "";

        List<Session.HorsemenTurnEntry> horsemen = session.getHorsemenHistory();
        List<Session.NvcTurnEntry> nvc = session.getNvcCompletionHistory();

        boolean haveAny = (horsemen != null && !horsemen.isEmpty())
            || (nvc != null && !nvc.isEmpty());
        if (!haveAny) return "";

        // 최근 RECENT_WINDOW턴으로 제한 — 오래된 과거 점수가 매 턴 동일 지시를 유발하지 않도록
        List<Session.HorsemenTurnEntry> recentHorsemen = tail(horsemen, RECENT_WINDOW);
        List<Session.NvcTurnEntry> recentNvc = tail(nvc, RECENT_WINDOW);

        StringBuilder directives = new StringBuilder();

        if (recentHorsemen != null && !recentHorsemen.isEmpty()) {
            double criticismAvg = averageNonNull(recentHorsemen, e -> e.criticism);
            double defensivenessAvg = averageNonNull(recentHorsemen, e -> e.defensiveness);
            double stonewallingAvg = averageNonNull(recentHorsemen, e -> e.stonewalling);
            boolean contemptSeen = recentHorsemen.stream()
                .anyMatch(e -> e.contempt != null && e.contempt > 0);

            if (criticismAvg >= CRITICISM_AVG_THRESHOLD) {
                // 윈도우 전체에서 계속 높으면 같은 NVC 재구성을 반복하지 말고 전환 지시
                boolean persistent = recentHorsemen.size() >= RECENT_WINDOW;
                if (persistent) {
                    directives.append("- 비난(criticism) 패턴이 최근 " + RECENT_WINDOW
                        + "턴 연속으로 높아요. NVC 재구성을 이미 여러 번 시도했을 수 있으니,"
                        + " 같은 감정·욕구 반영을 반복하지 말고 새 사실 질문이나"
                        + " 3단계 조언 동의로 전환하세요.\n");
                } else {
                    directives.append("- 비난(criticism) 패턴이 최근 턴에서 감지됐어요."
                        + " 이번 턴에는 비판을 욕구로 재구성해 들려주는 NVC 4단계 중"
                        + " *느낌·욕구* 표현을 우선해 주세요.\n");
                }
            }
            if (contemptSeen) {
                directives.append("- 경멸(contempt) 신호가 최근 감지됐어요."
                    + " 세션당 1회 EFT 환기 시도 가능 (위기 키워드 없을 때만, 가설형으로).\n");
            }
            if (defensivenessAvg >= DEFENSIVENESS_AVG_THRESHOLD) {
                directives.append("- 방어(defensiveness) 패턴이 보여요."
                    + " 책임 분담 한 조각 인정하기를 부드럽게 모델링해 주세요.\n");
            }
            if (stonewallingAvg >= STONEWALLING_AVG_THRESHOLD) {
                directives.append("- 담쌓기(stonewalling) 신호가 보여요."
                    + " 호흡·잠시 쉼을 권하는 한 줄을 응답 끝에 둘 수 있어요.\n");
            }
        }

        if (recentNvc != null && !recentNvc.isEmpty()) {
            double observationMiss = missingRatio(recentNvc, e -> e.observation);
            double needMiss = missingRatio(recentNvc, e -> e.need);

            if (observationMiss >= NVC_INCOMPLETE_RATIO_THRESHOLD) {
                directives.append("- 사실 관찰 단계가 최근 자주 빠졌어요."
                    + " 이번 턴은 \"~~한 일이 있었어요\" 식 관찰 문장을 한 번 짚어주세요.\n");
            }

            if (needMiss >= NVC_INCOMPLETE_RATIO_THRESHOLD) {
                // 윈도우 전체에서 need가 계속 false → 반복 추측 대신 전환 지시
                boolean needMissPersistent = recentNvc.size() >= RECENT_WINDOW
                    && missingRatio(recentNvc, e -> e.need) >= 1.0;

                if (needMissPersistent) {
                    directives.append("- 욕구(need) 비추기를 최근 " + RECENT_WINDOW
                        + "턴 동안 계속 시도했지만 진전이 없어요."
                        + " 같은 '숨은 마음' 추측을 다시 반복하지 말고,"
                        + " (a) 새 사실 질문 (b) 3단계 묵시적 조언 동의 구하기"
                        + " (c) 돌봄 권유 중 하나로 전환하세요.\n");
                } else {
                    directives.append("- 욕구(need) 명시가 최근 부족했어요."
                        + " 사용자 발화 뒤에 숨은 욕구를 가설형으로 한 줄 비춰주세요."
                        + " 이미 비슷한 표현을 썼다면 반복 금지 — 다른 각도에서 짚어주세요.\n");
                }
            }
        }

        if (directives.length() == 0) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("<psychology_feedback note=\"최근 " + RECENT_WINDOW + "턴 기반, 톤 조정에만 사용\">\n");
        sb.append(directives);
        sb.append("</psychology_feedback>\n");
        return sb.toString();
    }

    /** List의 마지막 n개만 반환. null/empty 시 빈 리스트. */
    private <T> List<T> tail(List<T> list, int n) {
        if (list == null || list.isEmpty()) return List.of();
        int from = Math.max(0, list.size() - n);
        return list.subList(from, list.size());
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
