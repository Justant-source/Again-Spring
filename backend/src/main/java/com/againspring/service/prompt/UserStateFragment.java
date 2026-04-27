package com.againspring.service.prompt;

import com.againspring.domain.Session;
import com.againspring.domain.enums.MessageSender;
import org.springframework.stereotype.Component;
import java.util.List;

/**
 * Phase D — 가장 최근 UserStateEntry를 프롬프트에 주입.
 * 사용자에게는 절대 노출 금지 — 내부 톤 조정용.
 *
 * 권위본: shared/docs/policies/context-algorithm.md §4.3, §5.1(b)
 * psychology-model.md §"출력 절대 금지" 준수
 */
@Component
public class UserStateFragment {

    public String render(Session session, boolean isDuo) {
        if (session == null) return "";
        List<Session.UserStateEntry> hist = session.getUserStateHistory();
        if (hist == null || hist.isEmpty()) return "";

        Session.UserStateEntry latestA = latestFor(hist, MessageSender.USER_A.name());
        Session.UserStateEntry latestB = isDuo ? latestFor(hist, MessageSender.USER_B.name()) : null;
        if (latestA == null && latestB == null) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("<user_states note=\"현재 사용자(들)의 대화 상태. 톤 조정에만 사용. ")
          .append("본문에 이 라벨을 절대 노출하지 마세요.\">\n");
        if (latestA != null) {
            sb.append("- USER_A: ").append(latestA.state.name())
              .append(" (turn ").append(latestA.turn).append(")\n");
        }
        if (latestB != null) {
            sb.append("- USER_B: ").append(latestB.state.name())
              .append(" (turn ").append(latestB.turn).append(")\n");
        }
        sb.append("</user_states>\n");
        return sb.toString();
    }

    private Session.UserStateEntry latestFor(List<Session.UserStateEntry> hist, String senderName) {
        Session.UserStateEntry latest = null;
        for (Session.UserStateEntry e : hist) {
            if (senderName.equals(e.sender)) latest = e;
        }
        return latest;
    }
}

