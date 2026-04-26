package com.againspring.service.prompt;

import com.againspring.domain.User;
import com.againspring.domain.enums.MessageSender;
import com.againspring.service.StyleCalculator.CommunicationStyle;
import org.springframework.stereotype.Component;

@Component
public class UserProfileFragment {

    public String render(User user) {
        return render(user, null);
    }

    public String render(User user, MessageSender senderTag) {
        if (user == null || user.getCommunicationStyle() == null || user.getCommunicationStyle().isBlank()) {
            return "";
        }
        CommunicationStyle style;
        try {
            style = CommunicationStyle.valueOf(user.getCommunicationStyle().toUpperCase());
        } catch (IllegalArgumentException e) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<user_profile note=\"참고용 — 단독 결정 변수 아님, 사용자 발화 우선\"");
        if (senderTag != null) {
            sb.append(" sender=\"").append(senderTag.name()).append("\"");
        }
        sb.append(">\n");
        sb.append("- 커뮤니케이션 스타일: ").append(style.getLabel()).append(style.getEmoji()).append("\n");
        sb.append("  ").append(style.getDescription()).append(".\n");
        if (style.getStrengths() != null && !style.getStrengths().isEmpty()) {
            sb.append("- 강점: ").append(String.join(", ", style.getStrengths())).append("\n");
        }
        if (style.getCaution() != null && !style.getCaution().isEmpty()) {
            sb.append("- 주의: ").append(String.join(", ", style.getCaution())).append("\n");
        }
        sb.append("</user_profile>\n");
        return sb.toString();
    }
}
