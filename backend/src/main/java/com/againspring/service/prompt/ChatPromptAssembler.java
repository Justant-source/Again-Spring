package com.againspring.service.prompt;

import com.againspring.domain.Message;
import com.againspring.domain.Session;
import com.againspring.domain.User;
import com.againspring.domain.enums.MessageSender;
import com.againspring.llm.prompt.PromptLoader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * ChatPromptAssembler (V1.5 카톡식)
 * Solo/Duo 모드별로 프롬프트를 조립
 */
@Service
@RequiredArgsConstructor
public class ChatPromptAssembler {

    private final PromptLoader loader;
    private final UserProfileFragment profileFragment;
    private final PsychologyFeedbackFormatter psychologyFeedback;
    private final DuoBalanceFormatter duoBalance;

    /**
     * Solo 모드 — 본인 컨텍스트만 사용.
     */
    public String assembleSoloTurn(Session session, User user, String currentMessage, List<Message> recentMessages) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append(safeLoad("system.md")).append("\n\n");
        sb.append(safeLoad("gottman/four_horsemen.md")).append("\n\n");
        sb.append(safeLoad("nvc/four_steps.md")).append("\n\n");
        String profile = profileFragment.render(user);
        if (!profile.isEmpty()) {
            sb.append(profile).append("\n");
        }
        String feedback = psychologyFeedback.render(session);
        if (!feedback.isEmpty()) {
            sb.append(feedback).append("\n");
        }
        sb.append(safeLoad("relations/" + session.getRelationType().getValue() + ".md")).append("\n\n");
        sb.append(safeLoad("chat/solo_chat.md")).append("\n\n");

        sb.append("<conversation_history>\n");
        for (var msg : recentMessages) {
            sb.append("[").append(formatSender(msg.getSender())).append("] ")
              .append(msg.getContent()).append("\n");
        }
        sb.append("</conversation_history>\n\n");

        sb.append("<current_user_message>\n").append(currentMessage).append("\n</current_user_message>\n\n");
        sb.append(safeLoad("chat/_response_instructions.md"));

        return sb.toString();
    }

    /**
     * Duo 모드 — 양쪽 컨텍스트 모두 사용. 단, 응답은 currentUserSender에게만 보내짐.
     */
    public String assembleDuoTurn(Session session, User userA, User userB, MessageSender currentUserSender,
                                   String currentMessage, List<Message> allRecentMessages) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append(safeLoad("system.md")).append("\n\n");
        sb.append(safeLoad("gottman/four_horsemen.md")).append("\n\n");
        sb.append(safeLoad("nvc/four_steps.md")).append("\n\n");
        String profileA = profileFragment.render(userA, MessageSender.USER_A);
        String profileB = profileFragment.render(userB, MessageSender.USER_B);
        if (!profileA.isEmpty()) sb.append(profileA);
        if (!profileB.isEmpty()) sb.append(profileB);
        if (!profileA.isEmpty() || !profileB.isEmpty()) sb.append("\n");
        String feedback = psychologyFeedback.render(session);
        if (!feedback.isEmpty()) {
            sb.append(feedback).append("\n");
        }
        sb.append(safeLoad("relations/" + session.getRelationType().getValue() + ".md")).append("\n\n");
        sb.append(safeLoad("chat/duo_chat.md")).append("\n\n");

        // 양쪽 메시지를 시간순으로, 명확히 라벨링
        sb.append("<conversation_history note=\"두 사람 모두의 대화. 응답할 때는 ")
          .append(formatSender(currentUserSender)).append("에게만 답하세요.\">\n");
        for (var msg : allRecentMessages) {
            sb.append("[").append(formatSender(msg.getSender())).append("] ")
              .append(msg.getContent()).append("\n");
        }
        sb.append("</conversation_history>\n\n");

        sb.append("<current_user_message sender=\"").append(formatSender(currentUserSender)).append("\">\n");
        sb.append(currentMessage);
        sb.append("\n</current_user_message>\n\n");

        sb.append(safeLoad("chat/_response_instructions.md")).append("\n\n");

        // B 초기 진입 시 온보딩 지시 주입 (B의 누적 메시지가 2개 이하일 때)
        if (currentUserSender == MessageSender.USER_B) {
            int bCount = session.getUserBMessageCount() == null ? 0 : session.getUserBMessageCount();
            if (bCount <= 2) {
                sb.append("<partner_onboarding>\n")
                  .append("사용자 B가 막 진입했습니다. 위의 대화 이력에서 A가 공유한 상황의 핵심 맥락을 파악하고, ")
                  .append("B에게 왜 이 자리에 초대됐는지 구체적으로 안내하세요. ")
                  .append("A의 발화를 직접 인용하지 않되, 두 사람 사이의 상황 유형(예: '약속 관련 일', '갈등 상황')은 언급 가능합니다. ")
                  .append("B가 거칠게 반응해도 대화를 포기하지 않습니다. ")
                  .append("'여기 있을 필요 없어요' 같은 포기성 발화는 절대 하지 않습니다.\n")
                  .append("</partner_onboarding>\n\n");
            }
        }

        sb.append("<duo_specific_rules>\n")
          .append("- 응답은 오직 ").append(formatSender(currentUserSender)).append("에게만.\n")
          .append("- 상대방의 발화 내용을 인용하거나 누설 금지.\n")
          .append("- 단, 양쪽 컨텍스트를 종합한 통찰은 가설형으로 가능: \"혹시 상대분도 ~~로 느끼셨을 수 있어요\"\n")
          .append("- 한쪽 편들지 않음. 균형 유지.\n")
          .append("</duo_specific_rules>");

        String balance = duoBalance.render(session);
        if (!balance.isEmpty()) {
            sb.append("\n\n").append(balance);
        }

        return sb.toString();
    }

    /**
     * A에게 보내는 파트너 합류 전환 메시지 생성용 프롬프트.
     * Solo 대화 이력에서 핵심 맥락을 요약하도록 LLM에게 지시.
     */
    public String assemblePartnerJoinedSummaryPrompt(List<Message> soloMessages) {
        StringBuilder sb = new StringBuilder();
        sb.append(safeLoad("chat/partner_joined_summary.md")).append("\n\n");

        sb.append("<solo_conversation_history>\n");
        for (var msg : soloMessages) {
            sb.append("[").append(formatSender(msg.getSender())).append("] ")
              .append(msg.getContent()).append("\n");
        }
        sb.append("</solo_conversation_history>\n");

        return sb.toString();
    }

    private String safeLoad(String path) {
        try {
            return loader.get(path);
        } catch (Exception e) {
            // 프롬프트 파일이 없으면 기본값 반환
            return ""; // 또는 기본 프롬프트 텍스트
        }
    }

    private String formatSender(MessageSender s) {
        return switch (s) {
            case USER_A -> "사용자 A";
            case USER_B -> "사용자 B";
            case MEDIATOR_TO_A -> "중재자→A";
            case MEDIATOR_TO_B -> "중재자→B";
        };
    }
}
