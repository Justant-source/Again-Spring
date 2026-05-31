package com.againspring.service.community;

import com.againspring.domain.community.ThreeWayMessage;
import com.againspring.domain.community.ThreeWaySession;
import com.againspring.domain.enums.ThreeWayRole;
import com.againspring.domain.enums.ThreeWayStatus;
import com.againspring.llm.LLMProvider;
import com.againspring.llm.bridge.PromptSanitizer;
import com.againspring.llm.prompt.PromptLoader;
import com.againspring.repository.community.ThreeWayMessageRepository;
import com.againspring.repository.community.ThreeWaySessionRepository;
import com.againspring.safety.KeywordGuard;
import com.againspring.service.crisis.CrisisDetector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.NoSuchFileException;
import java.util.List;

/**
 * Service for managing 3-way mediation chat (V17 Phase 6).
 * Handles user message storage and asynchronous AI mediator responses.
 *
 * Unlike Duo mode which enforces isolation, THREE_WAY allows full group visibility.
 * Mediator intervenes after 2+ turns to help both parties understand each other.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ThreeWayChatService {

    private final ThreeWaySessionRepository twsRepo;
    private final ThreeWayMessageRepository twmRepo;
    private final PromptLoader promptLoader;
    private final PromptSanitizer promptSanitizer;
    private final KeywordGuard keywordGuard;
    private final CrisisDetector crisisDetector;

    @Qualifier("juryLlmProvider")
    private final LLMProvider moderatorProvider;

    @Value("${llm.jury.model:claude-haiku-4-5-20251001}")
    private String moderatorModel;

    /**
     * Accepts and stores a user message in the 3-way session.
     * Response is immediate (<100ms) — LLM mediator response happens asynchronously.
     *
     * @param twsId Session ID
     * @param authorRoleStr Role as string "PARTY_A" or "PARTY_B"
     * @param content User message content
     * @param userId User ID (for access control verification)
     * @return Stored ThreeWayMessage
     * @throws RuntimeException if session not found, crisis detected, or user not authorized
     */
    public ThreeWayMessage sendUserMessage(
            String twsId,
            String authorRoleStr,
            String content,
            String userId) {

        ThreeWaySession tws = twsRepo.findById(twsId)
            .orElseThrow(() -> new RuntimeException("THREE_WAY_SESSION_NOT_FOUND"));

        if (tws.getStatus() != ThreeWayStatus.ACTIVE) {
            throw new IllegalStateException("SESSION_NOT_ACTIVE");
        }

        ThreeWayRole role = parseRole(authorRoleStr);
        validateParticipant(tws, userId, role);

        // Crisis detection — level 1 = immediate rejection
        var crisis = crisisDetector.detect(content);
        if (crisis.level() == 1) {
            log.warn("Crisis detected in 3-way message: twsId={}, level=1, keyword={}", twsId, crisis.matchedKeyword());
            throw new RuntimeException("CRISIS_DETECTED:" + crisis.matchedKeyword());
        }

        // Sanitize input
        String sanitized = promptSanitizer.sanitize(content, twsId);

        // Store message
        ThreeWayMessage msg = ThreeWayMessage.builder()
            .twsId(twsId)
            .authorRole(role)
            .content(sanitized)
            .status("complete")
            .build();

        ThreeWayMessage saved = twmRepo.save(msg);
        log.info("Three-way message stored: twsId={}, authorRole={}, msgId={}", twsId, role, saved.getId());

        // Trigger async mediator response
        generateModeratorAsync(tws, twsId);

        return saved;
    }

    /**
     * Generates an asynchronous AI mediator response.
     * Triggered after user message but doesn't block response.
     *
     * Mediator only intervenes after 2+ user turns to avoid early interruption.
     *
     * @param tws ThreeWaySession
     * @param twsId Session ID
     */
    @Async
    public void generateModeratorAsync(ThreeWaySession tws, String twsId) {
        try {
            List<ThreeWayMessage> history = twmRepo.findByTwsIdOrderByCreatedAtAsc(twsId);

            // Count user messages (non-MEDIATOR)
            long userMsgCount = history.stream()
                .filter(m -> m.getAuthorRole() != ThreeWayRole.MEDIATOR)
                .count();

            // Don't intervene before 2 user turns
            if (userMsgCount < 2) {
                log.debug("Three-way session has < 2 user turns, skipping mediator: twsId={}", twsId);
                return;
            }

            // Load prompt
            String systemPrompt = safeLoadPrompt("chat/three_way_moderator.md");
            if (systemPrompt.isEmpty()) {
                log.warn("Three-way moderator prompt not found, skipping: twsId={}", twsId);
                return;
            }

            // Build history block
            StringBuilder historyBlock = new StringBuilder();
            for (ThreeWayMessage msg : history) {
                String label = switch (msg.getAuthorRole()) {
                    case PARTY_A -> "A님";
                    case PARTY_B -> "B님";
                    case MEDIATOR -> "[중재자]";
                };
                historyBlock.append(label).append(": ").append(msg.getContent()).append("\n");
            }

            // Compose full prompt
            String fullPrompt = systemPrompt
                + "\n\n## 현재 대화 내역\n"
                + historyBlock
                + "\n## 지시\n현재 상황에서 중재자로서 짧게 개입해주세요.";

            // LLM call
            String response = moderatorProvider.invoke(fullPrompt, moderatorModel);

            // Apply community reframe (public-facing safety filter)
            String filtered = keywordGuard.applyCommunityPublicReframe(response);

            // Store mediator message
            ThreeWayMessage mediatorMsg = ThreeWayMessage.builder()
                .twsId(twsId)
                .authorRole(ThreeWayRole.MEDIATOR)
                .content(filtered)
                .status("complete")
                .llmModel(moderatorModel)
                .build();

            twmRepo.save(mediatorMsg);
            log.info("Mediator response generated and stored: twsId={}, length={}", twsId, filtered.length());

        } catch (Exception e) {
            log.warn("Failed to generate mediator response: twsId={}", twsId, e);
        }
    }

    /**
     * Retrieves conversation history for a 3-way session.
     *
     * @param twsId Session ID
     * @param userId User ID (for access control)
     * @return List of ThreeWayMessages in chronological order
     * @throws RuntimeException if session not found or user not authorized
     */
    @Transactional(readOnly = true)
    public List<ThreeWayMessage> getHistory(String twsId, String userId) {
        ThreeWaySession tws = twsRepo.findById(twsId)
            .orElseThrow(() -> new RuntimeException("THREE_WAY_SESSION_NOT_FOUND"));

        if (!isParticipant(tws, userId)) {
            throw new org.springframework.security.access.AccessDeniedException("NOT_PARTICIPANT");
        }

        return twmRepo.findByTwsIdOrderByCreatedAtAsc(twsId);
    }

    /**
     * Validates that a user is allowed to send a message with a given role.
     *
     * @param tws ThreeWaySession
     * @param userId User ID
     * @param role Intended role (PARTY_A or PARTY_B)
     * @throws IllegalAccessException if user doesn't match role
     * @throws IllegalArgumentException if trying to send as MEDIATOR
     */
    private void validateParticipant(ThreeWaySession tws, String userId, ThreeWayRole role) {
        if (role == ThreeWayRole.MEDIATOR) {
            throw new IllegalArgumentException("MEDIATOR_ROLE_FORBIDDEN");
        }

        boolean valid = switch (role) {
            case PARTY_A -> userId.equals(tws.getPartyAUserId());
            case PARTY_B -> userId.equals(tws.getPartyBUserId());
            default -> false;
        };

        if (!valid) {
            throw new org.springframework.security.access.AccessDeniedException("NOT_AUTHORIZED_FOR_ROLE");
        }
    }

    /**
     * Checks if a user is a participant in the session.
     *
     * @param tws ThreeWaySession
     * @param userId User ID
     * @return true if user is Party A or Party B
     */
    private boolean isParticipant(ThreeWaySession tws, String userId) {
        return userId.equals(tws.getPartyAUserId()) || userId.equals(tws.getPartyBUserId());
    }

    /**
     * Parses role string to enum.
     *
     * @param roleStr "PARTY_A", "PARTY_B", or "MEDIATOR"
     * @return ThreeWayRole
     * @throws IllegalArgumentException if invalid role
     */
    private ThreeWayRole parseRole(String roleStr) {
        try {
            return ThreeWayRole.valueOf(roleStr);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("INVALID_ROLE: " + roleStr);
        }
    }

    /**
     * Safely loads a prompt file, returning empty string on failure.
     *
     * @param path Relative path (e.g., "chat/three_way_moderator.md")
     * @return Prompt content or empty string if not found
     */
    private String safeLoadPrompt(String path) {
        try {
            return promptLoader.get(path);
        } catch (NoSuchFileException e) {
            log.warn("Prompt file not found: path={}", path);
            return "";
        } catch (Exception e) {
            log.warn("Error loading prompt: path={}", path, e);
            return "";
        }
    }
}
