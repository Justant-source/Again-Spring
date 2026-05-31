package com.againspring.service;

import com.againspring.domain.Message;
import com.againspring.domain.Session;
import com.againspring.domain.enums.MessageSender;
import com.againspring.repository.MessageRepository;
import com.againspring.repository.SessionRepository;
import com.againspring.safety.IsolationLintFilter;
import com.againspring.service.context.IssueContextMerger;
import com.againspring.service.context.PhaseDMetrics;
import com.againspring.service.context.QuestionQueueUpdater;
import com.againspring.service.context.UserStateAppender;
import com.againspring.service.parser.ChatTurnMetaParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * LLM 응답 후처리 공통 파이프라인. ChatService / CancelableChatService 공유.
 * turn_meta 파싱 → 심리 이력 · Phase-D 컨텍스트 영속 → 청크 분할 · 격리 검사 · 저장 → 정리 권유 게이트
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatTurnProcessor {

    private final MessageRepository messageRepo;
    private final SessionRepository sessionRepo;
    private final SessionStateMachine stateMachine;
    private final ChatTurnMetaParser turnMetaParser;
    private final UserStateAppender userStateAppender;
    private final IssueContextMerger issueContextMerger;
    private final QuestionQueueUpdater questionQueueUpdater;
    private final IsolationLintFilter isolationLintFilter;
    private final PhaseDMetrics phaseDMetrics;

    public record TurnProcessResult(
        List<Message> mediatorMessages,
        ChatTurnMetaParser.Result parsed,
        boolean finalizeSuggested
    ) {}

    /**
     * @param latencyMs 첫 청크에 기록할 LLM 레이턴시. null이면 기록 생략.
     */
    public TurnProcessResult process(
            Session session, MessageSender sender, String rawLlmResponse, Long latencyMs) {

        String sessionId = session.getId();
        MessageSender mediatorSender = sender.mediatorCounterpart();

        int turnIndex = (session.getHorsemenHistory() == null
                ? 0 : session.getHorsemenHistory().size()) + 1;

        ChatTurnMetaParser.Result parsed = turnMetaParser.parse(rawLlmResponse, turnIndex, sender.name());
        // parser가 본문을 분리하지 못한 경우(본문 없이 메타만 온 비정상 응답 등) raw를 그대로 쓰면
        // turn_meta JSON이 사용자에게 노출된다. raw 대신 안전한 fallback 문구를 사용한다.
        String mediatorResponse = (parsed.mediatorMessage() == null || parsed.mediatorMessage().isBlank())
                ? "잠깐 정리할 시간이 필요해요. 다시 들려주실 수 있을까요?"
                : parsed.mediatorMessage();

        appendPsychologyHistory(session, parsed);
        userStateAppender.append(session, parsed.userState());
        issueContextMerger.merge(session, parsed.issueDelta(), turnIndex);
        questionQueueUpdater.update(session, parsed.queueDelta(), turnIndex);
        // V47 신규: 키워드·제목·koreanTag 추론값 세션에 반영 (예외가 채팅 플로우를 차단하지 않도록 보호)
        try {
            applyInferredMeta(session, parsed);
        } catch (Exception e) {
            log.warn("applyInferredMeta failed for session {}: {}", sessionId, e.getMessage());
        }

        if (parsed.userState() != null) phaseDMetrics.recordUserState(parsed.userState().state);
        if (parsed.userState() != null || parsed.issueDelta() != null) phaseDMetrics.recordMetaPopulated();
        if (parsed.queueDelta() != null && parsed.queueDelta().asked != null) {
            phaseDMetrics.recordQueueAsked(parsed.queueDelta().asked.size());
        }

        String[] rawChunks = mediatorResponse.split("(?m)^---\\s*$");
        List<String> chunks = new ArrayList<>();
        for (String c : rawChunks) {
            String trimmed = c.strip();
            if (!trimmed.isEmpty()) chunks.addAll(splitLongChunk(trimmed));
        }
        if (chunks.isEmpty()) chunks.add(mediatorResponse);

        List<Message> mediatorMessages = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            if (isolationLintFilter.violatesIsolation(chunk)) {
                log.warn("Isolation violation in chunk {} of session {}", i, sessionId);
                chunk = "잠깐 정리할 시간이 필요해요. 다시 들려주실 수 있을까요?";
            }
            Message msg = messageRepo.save(Message.builder()
                    .sessionId(sessionId)
                    .sender(mediatorSender)
                    .content(chunk)
                    .charCount(chunk.length())
                    .llmModel(ChatService.MODEL_HAIKU)
                    .llmLatencyMs(i == 0 ? latencyMs : null)
                    .build());
            mediatorMessages.add(msg);
        }

        boolean suggested = checkAndTriggerFinalizationSuggestion(session);
        return new TurnProcessResult(mediatorMessages, parsed, suggested);
    }

    public boolean checkAndTriggerFinalizationSuggestion(Session session) {
        if (session.getFinalizeSuggestedAt() != null) return false;

        int aCount = session.getUserAMessageCount() == null ? 0 : session.getUserAMessageCount();
        int bCount = session.getUserBMessageCount() == null ? 0 : session.getUserBMessageCount();
        boolean isDuo = stateMachine.isDuo(session.getStatus());

        boolean countThreshold = isDuo
                ? (aCount + bCount) >= ChatService.FINALIZE_SUGGEST_DUO_TOTAL_MIN
                    && aCount >= ChatService.FINALIZE_SUGGEST_DUO_PER_USER_MIN
                    && bCount >= ChatService.FINALIZE_SUGGEST_DUO_PER_USER_MIN
                : aCount >= ChatService.FINALIZE_SUGGEST_SOLO_MIN;

        if (countThreshold && ChatService.hasReachedResolvingState(session)) {
            triggerFinalizationSuggestion(session, isDuo);
            return true;
        }
        return false;
    }

    public void triggerFinalizationSuggestion(Session session, boolean isDuo) {
        String suggestion = "이만큼 이야기 나눠주셔서 고마워요. 지금까지 정리해보면 어떨까요?";
        messageRepo.save(Message.builder()
                .sessionId(session.getId())
                .sender(MessageSender.MEDIATOR_TO_A)
                .content(suggestion)
                .charCount(suggestion.length())
                .isFinalizeSuggestion(true)
                .llmModel(ChatService.MODEL_HAIKU)
                .build());
        if (isDuo) {
            messageRepo.save(Message.builder()
                    .sessionId(session.getId())
                    .sender(MessageSender.MEDIATOR_TO_B)
                    .content(suggestion)
                    .charCount(suggestion.length())
                    .isFinalizeSuggestion(true)
                    .llmModel(ChatService.MODEL_HAIKU)
                    .build());
        }
        session.setFinalizeSuggestedAt(Instant.now());
        sessionRepo.save(session);
    }

    // ── private helpers ───────────────────────────────────────────────────────

    /**
     * V47: turn_meta에서 추론된 키워드·제목·koreanTag를 세션에 반영.
     * - keywords: 아직 없을 때만 저장 (초반 5턴 이내 LLM이 추론)
     * - title: 사용자가 직접 편집하지 않았을 때만 저장
     * - koreanTag: 아직 없을 때만 저장
     */
    private void applyInferredMeta(Session session, ChatTurnMetaParser.Result parsed) {
        boolean dirty = false;

        if (parsed.inferredKeywords() != null && !parsed.inferredKeywords().isEmpty()
                && (session.getKeywords() == null || session.getKeywords().isEmpty())) {
            session.setKeywords(parsed.inferredKeywords());
            dirty = true;
        }

        if (parsed.inferredTitle() != null && !parsed.inferredTitle().isBlank()
                && !Boolean.TRUE.equals(session.getTitleEditedByUser())
                && (session.getTitle() == null || session.getTitle().isBlank())) {
            session.setTitle(parsed.inferredTitle());
            dirty = true;
        }

        if (parsed.inferredKoreanTag() != null && !parsed.inferredKoreanTag().isBlank()
                && session.getKoreanTag() == null) {
            session.setKoreanTag(parsed.inferredKoreanTag());
            dirty = true;
        }

        if (dirty) sessionRepo.save(session);
    }

    private void appendPsychologyHistory(Session session, ChatTurnMetaParser.Result parsed) {
        if (parsed == null) return;
        boolean dirty = false;
        if (parsed.horsemen() != null) {
            List<Session.HorsemenTurnEntry> hist = session.getHorsemenHistory();
            if (hist == null) hist = new ArrayList<>();
            hist.add(parsed.horsemen());
            session.setHorsemenHistory(hist);
            updateEmotionIntensity(session, parsed.horsemen());
            dirty = true;
        }
        if (parsed.nvc() != null) {
            List<Session.NvcTurnEntry> hist = session.getNvcCompletionHistory();
            if (hist == null) hist = new ArrayList<>();
            hist.add(parsed.nvc());
            session.setNvcCompletionHistory(hist);
            dirty = true;
        }
        if (dirty) sessionRepo.save(session);
    }

    private void updateEmotionIntensity(Session session, Session.HorsemenTurnEntry entry) {
        if (entry == null || entry.sender == null) return;
        double turnIntensity = avgNonNull(
                entry.criticism, entry.contempt, entry.defensiveness, entry.stonewalling);
        boolean isA = MessageSender.USER_A.name().equals(entry.sender);
        BigDecimal current = isA
                ? session.getUserAEmotionIntensity() : session.getUserBEmotionIntensity();
        Integer count = isA
                ? session.getUserAMessageCount() : session.getUserBMessageCount();
        int n = count == null ? 1 : Math.max(1, count);
        double prevAvg = current == null ? 0.0 : current.doubleValue();
        double nextAvg = ((prevAvg * (n - 1)) + turnIntensity) / n;
        BigDecimal value = BigDecimal.valueOf(Math.round(nextAvg * 100.0) / 100.0)
                .setScale(2, RoundingMode.HALF_UP);
        if (isA) session.setUserAEmotionIntensity(value);
        else session.setUserBEmotionIntensity(value);
    }

    private double avgNonNull(Double... values) {
        double sum = 0;
        int n = 0;
        for (Double v : values) {
            if (v != null) { sum += v; n++; }
        }
        return n == 0 ? 0 : sum / n;
    }

    // ── chunk splitting ────────────────────────────────────────────────────────

    static final int MAX_CHUNK_LEN = 200;

    public static List<String> splitLongChunk(String chunk) {
        if (chunk.length() <= MAX_CHUNK_LEN) return List.of(chunk);
        List<String> parts = new ArrayList<>();
        String remaining = chunk;
        while (remaining.length() > MAX_CHUNK_LEN) {
            int splitAt = -1;
            for (int i = Math.min(MAX_CHUNK_LEN, remaining.length()) - 1; i >= MAX_CHUNK_LEN / 2; i--) {
                char c = remaining.charAt(i);
                if (c == '.' || c == '?' || c == '!' || c == '~' || c == '\n') {
                    splitAt = i + 1;
                    break;
                }
            }
            if (splitAt <= 0) splitAt = MAX_CHUNK_LEN;
            parts.add(remaining.substring(0, splitAt).trim());
            remaining = remaining.substring(splitAt).trim();
        }
        if (!remaining.isEmpty()) parts.add(remaining);
        return parts;
    }
}
