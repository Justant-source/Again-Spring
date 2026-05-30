package com.againspring.service.prompt;

import com.againspring.domain.Message;
import com.againspring.domain.Session;
import com.againspring.domain.User;
import com.againspring.domain.enums.MessageSender;
import com.againspring.llm.prompt.CacheTier;
import com.againspring.llm.prompt.PromptLoader;
import com.againspring.llm.prompt.PromptSegment;
import com.againspring.llm.prompt.SegmentRole;
import com.againspring.llm.prompt.StructuredPrompt;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
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
    // Phase D fragments — PR-1 골격 (빈 반환). PR-2/3/4에서 실제 로직 채워짐.
    private final IssueContextFragment issueContextFragment;
    private final UserStateFragment userStateFragment;
    private final QuestionQueueFragment questionQueueFragment;
    private final CategoryContextFragment categoryContextFragment;

    /**
     * Solo 모드 — 본인 컨텍스트만 사용.
     */
    public String assembleSoloTurn(Session session, User user, String currentMessage, List<Message> recentMessages) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append(safeLoad("system.md")).append("\n\n");

        // 중재자 성향 주입 (2D 슬라이더)
        String mediatorStyle = buildMediatorStyleFragment(session.getMediatorStyleX(), session.getMediatorStyleY());
        sb.append(mediatorStyle).append("\n\n");

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
        // Phase D: issue → state → queue (PR-1 빈 반환, PR-3/2/4에서 활성화)
        String issue = issueContextFragment.render(session);
        if (!issue.isEmpty()) sb.append(issue).append("\n");
        String state = userStateFragment.render(session, false);
        if (!state.isEmpty()) sb.append(state).append("\n");
        String queue = questionQueueFragment.render(session, MessageSender.USER_A);
        if (!queue.isEmpty()) sb.append(queue).append("\n");
        sb.append(safeLoad("relations/" + session.getRelationType().getValue() + ".md")).append("\n\n");
        String categoryContext = categoryContextFragment.render(session);
        if (!categoryContext.isEmpty()) sb.append(categoryContext).append("\n");
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

        // 중재자 성향 주입 (2D 슬라이더)
        String mediatorStyle = buildMediatorStyleFragment(session.getMediatorStyleX(), session.getMediatorStyleY());
        sb.append(mediatorStyle).append("\n\n");

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
        // Phase D: issue → state → queue (PR-1 빈 반환, PR-3/2/4에서 활성화)
        String issue = issueContextFragment.render(session);
        if (!issue.isEmpty()) sb.append(issue).append("\n");
        String state = userStateFragment.render(session, true);
        if (!state.isEmpty()) sb.append(state).append("\n");
        String queue = questionQueueFragment.render(session, currentUserSender);
        if (!queue.isEmpty()) sb.append(queue).append("\n");
        sb.append(safeLoad("relations/" + session.getRelationType().getValue() + ".md")).append("\n\n");
        String categoryContextDuo = categoryContextFragment.render(session);
        if (!categoryContextDuo.isEmpty()) sb.append(categoryContextDuo).append("\n");
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

    /**
     * Solo 모드 구조화 프롬프트 — 본인 컨텍스트만 사용.
     *
     * Legacy order (§7.2):
     *   1. system.md [GLOBAL_STATIC]
     *   2. <mediator_style> [SESSION_STATIC]
     *   3. gottman/four_horsemen.md [GLOBAL_STATIC]
     *   4. nvc/four_steps.md [GLOBAL_STATIC]
     *   5. <user_profile> [SESSION_STATIC]
     *   6. <psychology_feedback> [DYNAMIC]
     *   7. Phase-D fragments: issue, state, queue [DYNAMIC]
     *   8. relations/<type>.md [SESSION_STATIC]
     *   9. <category_context> [DYNAMIC]
     *  10. solo_chat.md [GLOBAL_STATIC]
     *  11. <conversation_history> [HISTORY]
     *  12. <current_user_message> [DYNAMIC]
     *  13. _response_instructions.md [DYNAMIC]
     *
     * flatten() guarantees byte-for-byte equivalence with legacy assembleSoloTurn().
     * Segments are added in legacy order, with tiers reflecting prompt caching tier.
     */
    public StructuredPrompt assembleSoloTurnStructured(Session session, User user, String currentMessage, List<Message> recentMessages) throws Exception {
        StructuredPrompt prompt = new StructuredPrompt();

        // 1. system.md
        prompt.add(CacheTier.GLOBAL_STATIC, safeLoad("system.md") + "\n\n", SegmentRole.SYSTEM);

        // 2. <mediator_style>
        String mediatorStyle = buildMediatorStyleFragment(session.getMediatorStyleX(), session.getMediatorStyleY());
        prompt.add(CacheTier.SESSION_STATIC, mediatorStyle + "\n\n", SegmentRole.USER_CONTEXT);

        // 3-4. gottman/four_horsemen.md + nvc/four_steps.md
        prompt.add(CacheTier.GLOBAL_STATIC, safeLoad("gottman/four_horsemen.md") + "\n\n", SegmentRole.FRAMEWORK);
        prompt.add(CacheTier.GLOBAL_STATIC, safeLoad("nvc/four_steps.md") + "\n\n", SegmentRole.FRAMEWORK);

        // 5. <user_profile> (conditional)
        String profile = profileFragment.render(user);
        if (!profile.isEmpty()) {
            prompt.add(CacheTier.SESSION_STATIC, profile + "\n", SegmentRole.USER_CONTEXT);
        }

        // 6. <psychology_feedback> (conditional)
        String feedback = psychologyFeedback.render(session);
        if (!feedback.isEmpty()) {
            prompt.add(CacheTier.DYNAMIC, feedback + "\n", SegmentRole.USER_CONTEXT);
        }

        // 7. Phase D fragments (conditional)
        String issue = issueContextFragment.render(session);
        if (!issue.isEmpty()) {
            prompt.add(CacheTier.DYNAMIC, issue + "\n", SegmentRole.USER_CONTEXT);
        }
        String state = userStateFragment.render(session, false);
        if (!state.isEmpty()) {
            prompt.add(CacheTier.DYNAMIC, state + "\n", SegmentRole.USER_CONTEXT);
        }
        String queue = questionQueueFragment.render(session, MessageSender.USER_A);
        if (!queue.isEmpty()) {
            prompt.add(CacheTier.DYNAMIC, queue + "\n", SegmentRole.USER_CONTEXT);
        }

        // 8. relations/<type>.md
        prompt.add(CacheTier.SESSION_STATIC, safeLoad("relations/" + session.getRelationType().getValue() + ".md") + "\n\n", SegmentRole.USER_CONTEXT);

        // 9. <category_context> (conditional)
        String categoryContext = categoryContextFragment.render(session);
        if (!categoryContext.isEmpty()) {
            prompt.add(CacheTier.DYNAMIC, categoryContext + "\n", SegmentRole.USER_CONTEXT);
        }

        // 10. solo_chat.md
        prompt.add(CacheTier.GLOBAL_STATIC, safeLoad("chat/solo_chat.md") + "\n\n", SegmentRole.FRAMEWORK);

        // 11. <conversation_history> — split into segments
        prompt.add(CacheTier.HISTORY, "<conversation_history>\n", SegmentRole.CONVERSATION_HISTORY);
        for (var msg : recentMessages) {
            prompt.add(CacheTier.HISTORY, "[" + formatSender(msg.getSender()) + "] " + msg.getContent() + "\n", SegmentRole.CONVERSATION_HISTORY);
        }
        prompt.add(CacheTier.HISTORY, "</conversation_history>\n\n", SegmentRole.CONVERSATION_HISTORY);

        // 12. <current_user_message>
        prompt.add(CacheTier.DYNAMIC, "<current_user_message>\n" + currentMessage + "\n</current_user_message>\n\n", SegmentRole.CURRENT_INPUT);

        // 13. _response_instructions.md
        prompt.add(CacheTier.DYNAMIC, safeLoad("chat/_response_instructions.md"), SegmentRole.INSTRUCTIONS);

        return prompt;
    }

    /**
     * Duo 모드 구조화 프롬프트 — 양쪽 컨텍스트 모두 사용. 단, 응답은 currentUserSender에게만 보내짐.
     *
     * Legacy order (§7.2):
     *   1. system.md [GLOBAL_STATIC]
     *   2. <mediator_style> [SESSION_STATIC]
     *   3. gottman/four_horsemen.md [GLOBAL_STATIC]
     *   4. nvc/four_steps.md [GLOBAL_STATIC]
     *   5. <user_profile>(들) [SESSION_STATIC]
     *   6. <psychology_feedback> [DYNAMIC]
     *   7. Phase-D fragments: issue, state, queue [DYNAMIC]
     *   8. relations/<type>.md [SESSION_STATIC]
     *   9. <category_context> [DYNAMIC]
     *  10. duo_chat.md [GLOBAL_STATIC]
     *  11. <conversation_history> [HISTORY]
     *  12. <current_user_message> [DYNAMIC]
     *  13. _response_instructions.md [DYNAMIC]
     *  14. <partner_onboarding> (B early, conditional) [DYNAMIC]
     *  15. <duo_specific_rules> [DYNAMIC]
     *  16. <duo_balance> (conditional) [DYNAMIC]
     *
     * flatten() guarantees byte-for-byte equivalence with legacy assembleDuoTurn().
     */
    public StructuredPrompt assembleDuoTurnStructured(Session session, User userA, User userB, MessageSender currentUserSender,
                                                      String currentMessage, List<Message> allRecentMessages) throws Exception {
        StructuredPrompt prompt = new StructuredPrompt();

        // 1. system.md
        prompt.add(CacheTier.GLOBAL_STATIC, safeLoad("system.md") + "\n\n", SegmentRole.SYSTEM);

        // 2. <mediator_style>
        String mediatorStyle = buildMediatorStyleFragment(session.getMediatorStyleX(), session.getMediatorStyleY());
        prompt.add(CacheTier.SESSION_STATIC, mediatorStyle + "\n\n", SegmentRole.USER_CONTEXT);

        // 3-4. gottman/four_horsemen.md + nvc/four_steps.md
        prompt.add(CacheTier.GLOBAL_STATIC, safeLoad("gottman/four_horsemen.md") + "\n\n", SegmentRole.FRAMEWORK);
        prompt.add(CacheTier.GLOBAL_STATIC, safeLoad("nvc/four_steps.md") + "\n\n", SegmentRole.FRAMEWORK);

        // 5. <user_profile>(들) (conditional)
        String profileA = profileFragment.render(userA, MessageSender.USER_A);
        String profileB = profileFragment.render(userB, MessageSender.USER_B);
        if (!profileA.isEmpty()) prompt.add(CacheTier.SESSION_STATIC, profileA, SegmentRole.USER_CONTEXT);
        if (!profileB.isEmpty()) prompt.add(CacheTier.SESSION_STATIC, profileB, SegmentRole.USER_CONTEXT);
        if (!profileA.isEmpty() || !profileB.isEmpty()) prompt.add(CacheTier.SESSION_STATIC, "\n", SegmentRole.USER_CONTEXT);

        // 6. <psychology_feedback> (conditional)
        String feedback = psychologyFeedback.render(session);
        if (!feedback.isEmpty()) {
            prompt.add(CacheTier.DYNAMIC, feedback + "\n", SegmentRole.USER_CONTEXT);
        }

        // 7. Phase D fragments (conditional)
        String issue = issueContextFragment.render(session);
        if (!issue.isEmpty()) {
            prompt.add(CacheTier.DYNAMIC, issue + "\n", SegmentRole.USER_CONTEXT);
        }
        String state = userStateFragment.render(session, true);
        if (!state.isEmpty()) {
            prompt.add(CacheTier.DYNAMIC, state + "\n", SegmentRole.USER_CONTEXT);
        }
        String queue = questionQueueFragment.render(session, currentUserSender);
        if (!queue.isEmpty()) {
            prompt.add(CacheTier.DYNAMIC, queue + "\n", SegmentRole.USER_CONTEXT);
        }

        // 8. relations/<type>.md
        prompt.add(CacheTier.SESSION_STATIC, safeLoad("relations/" + session.getRelationType().getValue() + ".md") + "\n\n", SegmentRole.USER_CONTEXT);

        // 9. <category_context> (conditional)
        String categoryContextDuo = categoryContextFragment.render(session);
        if (!categoryContextDuo.isEmpty()) {
            prompt.add(CacheTier.DYNAMIC, categoryContextDuo + "\n", SegmentRole.USER_CONTEXT);
        }

        // 10. duo_chat.md
        prompt.add(CacheTier.GLOBAL_STATIC, safeLoad("chat/duo_chat.md") + "\n\n", SegmentRole.FRAMEWORK);

        // 11. <conversation_history> — split into segments
        prompt.add(CacheTier.HISTORY, "<conversation_history note=\"두 사람 모두의 대화. 응답할 때는 " + formatSender(currentUserSender) + "에게만 답하세요.\">\n", SegmentRole.CONVERSATION_HISTORY);
        for (var msg : allRecentMessages) {
            prompt.add(CacheTier.HISTORY, "[" + formatSender(msg.getSender()) + "] " + msg.getContent() + "\n", SegmentRole.CONVERSATION_HISTORY);
        }
        prompt.add(CacheTier.HISTORY, "</conversation_history>\n\n", SegmentRole.CONVERSATION_HISTORY);

        // 12. <current_user_message>
        prompt.add(CacheTier.DYNAMIC, "<current_user_message sender=\"" + formatSender(currentUserSender) + "\">\n" + currentMessage + "\n</current_user_message>\n\n", SegmentRole.CURRENT_INPUT);

        // 13. _response_instructions.md
        prompt.add(CacheTier.DYNAMIC, safeLoad("chat/_response_instructions.md") + "\n\n", SegmentRole.INSTRUCTIONS);

        // 14. <partner_onboarding> (B early, conditional)
        if (currentUserSender == MessageSender.USER_B) {
            int bCount = session.getUserBMessageCount() == null ? 0 : session.getUserBMessageCount();
            if (bCount <= 2) {
                String partnerOnboarding = "<partner_onboarding>\n" +
                        "사용자 B가 막 진입했습니다. 위의 대화 이력에서 A가 공유한 상황의 핵심 맥락을 파악하고, " +
                        "B에게 왜 이 자리에 초대됐는지 구체적으로 안내하세요. " +
                        "A의 발화를 직접 인용하지 않되, 두 사람 사이의 상황 유형(예: '약속 관련 일', '갈등 상황')은 언급 가능합니다. " +
                        "B가 거칠게 반응해도 대화를 포기하지 않습니다. " +
                        "'여기 있을 필요 없어요' 같은 포기성 발화는 절대 하지 않습니다.\n" +
                        "</partner_onboarding>\n\n";
                prompt.add(CacheTier.DYNAMIC, partnerOnboarding, SegmentRole.INSTRUCTIONS);
            }
        }

        // 15. <duo_specific_rules>
        String duoRules = "<duo_specific_rules>\n" +
                "- 응답은 오직 " + formatSender(currentUserSender) + "에게만.\n" +
                "- 상대방의 발화 내용을 인용하거나 누설 금지.\n" +
                "- 단, 양쪽 컨텍스트를 종합한 통찰은 가설형으로 가능: \"혹시 상대분도 ~~로 느끼셨을 수 있어요\"\n" +
                "- 한쪽 편들지 않음. 균형 유지.\n" +
                "</duo_specific_rules>";
        prompt.add(CacheTier.DYNAMIC, duoRules, SegmentRole.INSTRUCTIONS);

        // 16. <duo_balance> (conditional)
        String balance = duoBalance.render(session);
        if (!balance.isEmpty()) {
            prompt.add(CacheTier.DYNAMIC, "\n\n" + balance, SegmentRole.USER_CONTEXT);
        }

        return prompt;
    }

    private String buildMediatorStyleFragment(int styleX, int styleY) {
        // X축 설명: 팩트(0) ↔ 공감(100)
        String xDesc = styleX >= 70 ? "논리적 사실과 구체적 상황에 집중하며"
                     : styleX <= 30 ? "상대방의 감정과 내면에 깊이 공감하며"
                     : "사실과 감정을 균형있게 살피며";

        // Y축 설명: 경청(0) ↔ 능동(100)
        String yDesc = styleY >= 70 ? "탐색적 질문으로 상대방이 스스로 통찰을 얻도록 안내하는"
                     : styleY <= 30 ? "대화를 정리하고 반영하여 명확한 이해를 돕는"
                     : "경청과 질문을 균형있게 사용하는";

        return String.format("""
                <mediator_style>
                당신은 %s %s 중재자입니다.
                팩트/논리 성향: %d/100 (0=완전공감, 100=완전팩트)
                경청/능동 성향: %d/100 (0=경청형, 100=능동형)
                </mediator_style>""", xDesc, yDesc, styleX, styleY);
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
