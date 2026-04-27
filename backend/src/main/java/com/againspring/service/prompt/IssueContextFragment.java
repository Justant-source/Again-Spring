package com.againspring.service.prompt;

import com.againspring.domain.Session;
import org.springframework.stereotype.Component;

/**
 * Phase D — IssueContext를 프롬프트에 주입할 XML 블록으로 렌더.
 * 양쪽 데이터 격리 유지를 위해 USER_A/USER_B 라벨을 note에 명시.
 *
 * 권위본: shared/docs/policies/context-algorithm.md §4.2, §5.1(a)
 * psychology-model.md §"출력 절대 금지" 준수 — 사용자에게 직접 노출 금지
 */
@Component
public class IssueContextFragment {

    private static final int FACTS_LIMIT = 5;
    private static final int NEEDS_LIMIT = 4;
    private static final int THREADS_LIMIT = 4;

    public String render(Session session) {
        if (session == null) return "";
        Session.IssueContext ctx = session.getIssueContext();
        if (ctx == null || isEffectivelyEmpty(ctx)) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("<issue_context note=\"누적된 이슈 컨텍스트. ")
          .append("USER_A/USER_B 라벨을 본문에 인용 금지. 양쪽 데이터 격리 유지.\">\n");

        if (ctx.headline != null && !ctx.headline.isBlank()) {
            sb.append("- 핵심: ").append(ctx.headline).append("\n");
        }
        if (ctx.facts != null && !ctx.facts.isEmpty()) {
            sb.append("- 확인된 사실:\n");
            ctx.facts.stream().limit(FACTS_LIMIT).forEach(f ->
                sb.append("  • ").append(f.text)
                  .append(Boolean.TRUE.equals(f.confirmedByOther) ? " [양쪽 인정]" : "")
                  .append("\n"));
        }
        if (ctx.namedNeeds != null && !ctx.namedNeeds.isEmpty()) {
            sb.append("- 명시된 욕구:\n");
            ctx.namedNeeds.stream().limit(NEEDS_LIMIT).forEach(n ->
                sb.append("  • ").append(n.text).append(" (").append(n.owner).append(")\n"));
        }
        if (ctx.threads != null && !ctx.threads.isEmpty()) {
            long unaddressed = ctx.threads.stream()
                .filter(t -> !Boolean.TRUE.equals(t.addressedByQueue)).count();
            if (unaddressed > 0) {
                sb.append("- 미해결 갈래:\n");
                ctx.threads.stream()
                    .filter(t -> !Boolean.TRUE.equals(t.addressedByQueue))
                    .limit(THREADS_LIMIT)
                    .forEach(t -> sb.append("  • ").append(t.text).append("\n"));
            }
        }
        sb.append("</issue_context>\n");
        return sb.toString();
    }

    private boolean isEffectivelyEmpty(Session.IssueContext ctx) {
        return (ctx.headline == null || ctx.headline.isBlank())
            && (ctx.facts == null || ctx.facts.isEmpty())
            && (ctx.namedNeeds == null || ctx.namedNeeds.isEmpty())
            && (ctx.threads == null || ctx.threads.isEmpty());
    }
}
