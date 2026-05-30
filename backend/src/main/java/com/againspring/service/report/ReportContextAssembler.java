package com.againspring.service.report;

import com.againspring.domain.Session;
import com.againspring.domain.Session.HorsemenTurnEntry;
import com.againspring.domain.Session.IssueContext;
import com.againspring.domain.Session.NvcTurnEntry;
import com.againspring.domain.Session.PendingQuestion;
import com.againspring.domain.Session.UserStateEntry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 세션 누적 컬럼(Phase B/C/D)을 읽어 리포트 프롬프트용 {@code <session_context>} 블록으로 조립.
 *
 * 각 섹션은 내용이 없으면 통째로 생략 — null·empty 안전.
 * 절대 금지 규칙(과실비율·처방·낙인어)은 조립 대상 데이터에 포함되지 않으므로 별도 필터 불필요.
 */
@Slf4j
@Component
public class ReportContextAssembler {

    private static final int MAX_FACTS          = 5;
    private static final int MAX_QUESTIONS      = 3;
    private static final double HORSEMEN_THRESHOLD = 0.3;

    /** Solo 모드 컨텍스트 조립 */
    public String assemble(Session session) {
        return build(session);
    }

    /** Duo 모드 컨텍스트 조립 (Solo와 동일 — 데이터 자체가 이미 양측 통합) */
    public String assemble(Session session, Object... ignored) {
        return build(session);
    }

    // -------------------------------------------------------------------------

    private String build(Session session) {
        if (session == null) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("<session_context>\n");

        appendIssueSummary(sb, session.getIssueContext());
        appendStateTraj(sb, session.getUserStateHistory());
        appendHorsemenPattern(sb, session.getHorsemenHistory());
        appendNvcProgress(sb, session.getNvcCompletionHistory());
        appendOpenQuestions(sb, session.getQuestionQueueA(), session.getQuestionQueueB());
        appendCrisisSignals(sb, session.getCrisisDetections());

        sb.append("</session_context>");

        String result = sb.toString();
        // 태그 사이에 아무 내용이 없으면 빈 문자열 반환
        if (result.equals("<session_context>\n</session_context>")) return "";
        return result;
    }

    // ── 이슈 요약 ────────────────────────────────────────────────────────────

    private void appendIssueSummary(StringBuilder sb, IssueContext ctx) {
        if (ctx == null) return;

        if (notBlank(ctx.headline)) {
            sb.append("  <issue_summary>").append(esc(ctx.headline)).append("</issue_summary>\n");
        }

        if (ctx.facts != null && !ctx.facts.isEmpty()) {
            sb.append("  <confirmed_facts>\n");
            ctx.facts.stream().limit(MAX_FACTS).forEach(f ->
                sb.append("    <fact>").append(esc(f.text)).append("</fact>\n"));
            sb.append("  </confirmed_facts>\n");
        }

        if (ctx.namedNeeds != null && !ctx.namedNeeds.isEmpty()) {
            sb.append("  <named_needs>\n");
            ctx.namedNeeds.forEach(n ->
                sb.append("    <need owner=\"").append(esc(n.owner)).append("\">")
                  .append(esc(n.text)).append("</need>\n"));
            sb.append("  </named_needs>\n");
        }

        List<Session.UnresolvedThread> unresolved = ctx.threads == null ? List.of()
                : ctx.threads.stream()
                      .filter(t -> !Boolean.TRUE.equals(t.addressedByQueue))
                      .toList();
        if (!unresolved.isEmpty()) {
            sb.append("  <unresolved_threads>\n");
            unresolved.forEach(t ->
                sb.append("    <thread>").append(esc(t.text)).append("</thread>\n"));
            sb.append("  </unresolved_threads>\n");
        }
    }

    // ── 감정 궤적 ────────────────────────────────────────────────────────────

    private void appendStateTraj(StringBuilder sb, List<UserStateEntry> history) {
        if (history == null || history.isEmpty()) return;
        String traj = history.stream()
                .map(e -> e.state != null ? e.state.name() : "?")
                .collect(Collectors.joining(" → "));
        sb.append("  <state_trajectory>").append(esc(traj)).append("</state_trajectory>\n");
    }

    // ── 4기사 패턴 ───────────────────────────────────────────────────────────

    private void appendHorsemenPattern(StringBuilder sb, List<HorsemenTurnEntry> history) {
        if (history == null || history.isEmpty()) return;

        double sumCrit = 0, sumContempt = 0, sumDef = 0, sumStone = 0;
        for (HorsemenTurnEntry e : history) {
            sumCrit     += safe(e.criticism);
            sumContempt += safe(e.contempt);
            sumDef      += safe(e.defensiveness);
            sumStone    += safe(e.stonewalling);
        }
        int n = history.size();
        StringBuilder patterns = new StringBuilder();
        if (sumCrit / n     >= HORSEMEN_THRESHOLD) patterns.append("비난(criticism) ");
        if (sumContempt / n >= HORSEMEN_THRESHOLD) patterns.append("경멸(contempt) ");
        if (sumDef / n      >= HORSEMEN_THRESHOLD) patterns.append("방어(defensiveness) ");
        if (sumStone / n    >= HORSEMEN_THRESHOLD) patterns.append("담쌓기(stonewalling) ");

        if (patterns.length() > 0) {
            sb.append("  <horsemen_pattern>")
              .append(esc(patterns.toString().trim()))
              .append(" 패턴이 반복적으로 감지됨</horsemen_pattern>\n");
        }
    }

    // ── NVC 진행도 ───────────────────────────────────────────────────────────

    private void appendNvcProgress(StringBuilder sb, List<NvcTurnEntry> history) {
        if (history == null || history.isEmpty()) return;

        long obs  = history.stream().filter(e -> Boolean.TRUE.equals(e.observation)).count();
        long feel = history.stream().filter(e -> Boolean.TRUE.equals(e.feeling)).count();
        long need = history.stream().filter(e -> Boolean.TRUE.equals(e.need)).count();
        long req  = history.stream().filter(e -> Boolean.TRUE.equals(e.request)).count();
        int  n    = history.size();

        StringBuilder nvc = new StringBuilder();
        if (obs  > n / 2.0) nvc.append("관찰 ");
        if (feel > n / 2.0) nvc.append("느낌 ");
        if (need > n / 2.0) nvc.append("욕구 ");
        if (req  > n / 2.0) nvc.append("요청 ");

        String reached = nvc.toString().trim();
        if (!reached.isEmpty()) {
            sb.append("  <nvc_progress>")
              .append(esc(reached + " 단계에 반복적으로 도달함"))
              .append("</nvc_progress>\n");
        }
    }

    // ── 미해결 질문 ──────────────────────────────────────────────────────────

    private void appendOpenQuestions(StringBuilder sb,
                                     List<PendingQuestion> queueA,
                                     List<PendingQuestion> queueB) {
        List<PendingQuestion> open = new java.util.ArrayList<>();
        if (queueA != null) queueA.stream()
                .filter(q -> !Boolean.TRUE.equals(q.asked)).forEach(open::add);
        if (queueB != null) queueB.stream()
                .filter(q -> !Boolean.TRUE.equals(q.asked)).forEach(open::add);

        if (open.isEmpty()) return;

        sb.append("  <open_questions>\n");
        open.stream().limit(MAX_QUESTIONS).forEach(q ->
            sb.append("    <question>").append(esc(q.text)).append("</question>\n"));
        sb.append("  </open_questions>\n");
    }

    // ── 위기 신호 ────────────────────────────────────────────────────────────

    private void appendCrisisSignals(StringBuilder sb, List<String> crisisDetections) {
        if (crisisDetections != null && !crisisDetections.isEmpty()) {
            sb.append("  <crisis_signals>위기 신호 감지됨 — 외부자원 안내 강화 필요"
                    + " (1393 자살예방, 1366 가정폭력, 132 범죄피해)</crisis_signals>\n");
        }
    }

    // ── 유틸 ─────────────────────────────────────────────────────────────────

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private double safe(Double d) {
        return d != null ? d : 0.0;
    }

    /** XML 특수문자 이스케이프 */
    private String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
