package com.againspring.service;

import com.againspring.api.dto.response.TurnResponse;
import com.againspring.api.dto.response.CurrentTurnResponse;
import com.againspring.domain.enums.ConflictType;
import com.againspring.domain.enums.SessionStatus;
import com.againspring.domain.enums.TurnRole;
import com.againspring.domain.Session;
import com.againspring.domain.Turn;
import com.againspring.domain.User;
import com.againspring.llm.LLMProvider;
import com.againspring.llm.LLMRequest;
import com.againspring.llm.LLMResponse;
import com.againspring.llm.fallback.FallbackResponses;
import com.againspring.llm.prompt.PromptAssembler;
import com.againspring.repository.SessionRepository;
import com.againspring.repository.UserRepository;
import com.againspring.safety.CrisisDetector;
import com.againspring.safety.CrisisResponse;
import com.againspring.safety.KeywordGuard;
import com.againspring.service.event.SessionCompletedEvent;
import com.againspring.service.event.TurnCompletedEvent;
import com.againspring.service.parser.TurnResponseParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Orchestrates mediation turn progression.
 * Handles LLM invocation, safety scanning, turn state management, and event publishing.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class MediationService {

    private final SessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final LLMProvider llmProvider;
    private final PromptAssembler promptAssembler;
    private final KeywordGuard keywordGuard;
    private final CrisisDetector crisisDetector;
    private final FallbackResponses fallbackResponses;
    private final TurnResponseParser turnResponseParser;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    /**
     * Progresses a turn in the session.
     *
     * @param sessionId Session ID
     * @param currentUserId User making the move
     * @param userInput User's input text
     * @return TurnResponse with mediator message and next turn info
     */
    public TurnResponse progressTurn(String sessionId, String currentUserId, String userInput) {
        // 1. Load and validate session
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found: " + sessionId));

        if (session.getStatus() != SessionStatus.IN_MEDIATION) {
            throw new RuntimeException("Session is not in mediation: " + session.getStatus());
        }

        // Determine current user's role
        TurnRole myRole = determineRole(session, currentUserId);
        if (!myRole.name().equals(session.getCurrentRole())) {
            throw new RuntimeException("Not your turn. Current role: " + session.getCurrentRole());
        }

        // 2. Safety scan input
        com.againspring.safety.ScanResult scanResult = keywordGuard.scanUserInput(userInput, currentUserId);
        if (scanResult.isCrisis()) {
            CrisisResponse crisisResponse = crisisDetector.detect(userInput, sessionId, currentUserId);
            session.setStatus(SessionStatus.TERMINATED);
            sessionRepository.save(session);
            return TurnResponse.builder()
                    .sessionId(sessionId)
                    .turnNumber(session.getCurrentTurn())
                    .role(myRole)
                    .crisis(crisisResponse)
                    .isComplete(true)
                    .createdAt(Instant.now(clock))
                    .build();
        }

        // 3. Build LLM request
        Map<String, Object> metadata = new HashMap<>();
        if (Boolean.TRUE.equals(session.getSoloMode())) {
            metadata.put("soloMode", true);
        }

        LLMRequest request = promptAssembler.assemble(
                session.getCurrentTurn(),
                myRole,
                session.getRelationType().name().toLowerCase(),
                session.getConflictType(),
                userInput,
                UUID.randomUUID().toString(),
                metadata
        );

        // 4. Invoke LLM with fallback
        LLMResponse llmResponse;
        boolean isFallback = false;
        try {
            long startTime = System.currentTimeMillis();
            llmResponse = llmProvider.invoke(request);
            long latency = System.currentTimeMillis() - startTime;
            log.info("LLM invocation succeeded: latency={}ms, tokens={}", latency, llmResponse.getTokensUsed());
        } catch (Exception e) {
            log.warn("LLM invocation failed, using fallback: {}", e.getMessage());
            llmResponse = fallbackResponses.forTurn(session.getCurrentTurn(), myRole, session.getConflictType());
            isFallback = true;
        }

        // 5. Parse LLM response
        TurnResponseParser.ParsedTurn parsedTurn = turnResponseParser.parse(llmResponse.getRawText(), session.getCurrentTurn());

        // 6. Apply output filter
        String mediatorMessage = keywordGuard.applyOutputFilter(parsedTurn.getMediatorMessage());
        String neutralSummary = keywordGuard.applyOutputFilter(parsedTurn.getNeutralSummary());

        // 7. Build Turn entity
        Turn turn = Turn.builder()
                .turnNumber(session.getCurrentTurn())
                .role(myRole)
                .userId(currentUserId)
                .content(userInput)
                .mediatorMessage(mediatorMessage)
                .mediatorSummaryForOpponent(neutralSummary)
                .isPerspectiveTaking(session.getCurrentTurn() >= 5)
                .skipped(false)
                .tokensUsed(llmResponse.getTokensUsed())
                .llmLatencyMs(llmResponse.getLatencyMs())
                .createdAt(Instant.now(clock))
                .build();

        // Handle turn 2 conflict classification
        if (session.getCurrentTurn() == 2 && parsedTurn.getConflictType() != null) {
            session.setConflictType(parsedTurn.getConflictType());
            log.info("Conflict type classified: {}", parsedTurn.getConflictType());
        }

        // 8. Append turn and advance
        session.getTurns().add(turn);
        int nextTurn = session.getCurrentTurn() + 1;
        boolean isComplete = false;

        // Determine if session is complete
        int maxTurns = Boolean.TRUE.equals(session.getSoloMode()) ? 3 : 6;
        if (nextTurn > maxTurns) {
            session.setStatus(SessionStatus.COMPLETED);
            session.setCompletedAt(Instant.now(clock));
            isComplete = true;
            log.info("Session completed: {}", sessionId);
            eventPublisher.publishEvent(new SessionCompletedEvent(this, sessionId, Instant.now(clock)));
        } else {
            // Switch role for next turn
            TurnRole nextRole = myRole == TurnRole.A ? TurnRole.B : TurnRole.A;
            session.setCurrentTurn(nextTurn);
            session.setCurrentRole(nextRole.name());
        }

        // 9. Persist session
        sessionRepository.save(session);

        // 10. Publish turn completed event
        eventPublisher.publishEvent(new TurnCompletedEvent(
                this,
                sessionId,
                session.getCurrentTurn(),
                myRole,
                Instant.now(clock)
        ));

        // 11. Build response
        TurnResponse.TurnResponseBuilder responseBuilder = TurnResponse.builder()
                .sessionId(sessionId)
                .turnNumber(session.getCurrentTurn() - (isComplete ? 1 : 0))
                .role(myRole)
                .mediatorMessage(mediatorMessage)
                .neutralSummaryForOpponent(neutralSummary)
                .questions(parsedTurn.getQuestions())
                .isComplete(isComplete)
                .isFallback(isFallback || parsedTurn.isFallback())
                .createdAt(Instant.now(clock));

        if (!isComplete) {
            TurnRole nextRole = myRole == TurnRole.A ? TurnRole.B : TurnRole.A;
            responseBuilder.nextTurnNumber(nextTurn)
                    .nextRole(nextRole);
        }

        return responseBuilder.build();
    }

    /**
     * Get current turn state for a user.
     */
    public CurrentTurnResponse getCurrentTurn(String sessionId, String currentUserId) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found: " + sessionId));

        TurnRole myRole = determineRole(session, currentUserId);
        boolean isMyTurn = myRole.name().equals(session.getCurrentRole());

        // Build transcript visible to current user
        List<CurrentTurnResponse.TranscriptItem> transcript = session.getTurns().stream()
                .map(turn -> CurrentTurnResponse.TranscriptItem.builder()
                        .turnNumber(turn.getTurnNumber())
                        .role(turn.getRole())
                        .userInput(turn.getContent())
                        .mediatorMessage(turn.getMediatorMessage())
                        .createdAt(turn.getCreatedAt())
                        .build())
                .toList();

        return CurrentTurnResponse.builder()
                .currentTurn(session.getCurrentTurn())
                .currentRole(TurnRole.valueOf(session.getCurrentRole()))
                .myRole(myRole)
                .isMyTurn(isMyTurn)
                .transcript(transcript)
                .build();
    }

    /**
     * Determine which role a user has in this session.
     */
    private TurnRole determineRole(Session session, String userId) {
        if (session.getCreatedByUserId().equals(userId)) {
            return TurnRole.A;
        } else if (session.getInviteeUserId() != null && session.getInviteeUserId().equals(userId)) {
            return TurnRole.B;
        }
        throw new RuntimeException("User is not a participant in this session: " + userId);
    }
}
