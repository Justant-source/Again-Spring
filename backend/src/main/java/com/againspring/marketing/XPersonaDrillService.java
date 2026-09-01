package com.againspring.marketing;

import com.againspring.domain.ai.SystemSetting;
import com.againspring.domain.marketing.XOpsAction;
import com.againspring.domain.marketing.XPersonaExample;
import com.againspring.notification.TelegramNotifier;
import com.againspring.repository.ai.SystemSettingRepository;
import com.againspring.repository.marketing.XPersonaExampleRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;

/**
 * Telegram situation drills: show a live outbound candidate, capture the operator's
 * one-line reply, store it as a DRILL gold pair. Never publishes to X.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class XPersonaDrillService {

    public static final String KEY_PENDING = "marketing.x.persona_drill_pending";
    public static final String KEY_SESSION = "marketing.x.persona_drill_session";
    public static final String KEY_SENT_DAY = "marketing.x.persona_drill_sent_json";
    public static final int DEFAULT_DAILY_CAP = 10;
    public static final Duration PENDING_TTL = Duration.ofHours(2);

    private final SystemSettingRepository systemSettingRepository;
    private final XPersonaExampleRepository exampleRepository;
    private final MarketingXOpsSettingsService xOpsSettingsService;
    private final AsmClient asmClient;
    private final TelegramNotifier telegramNotifier;
    private final XPersonaLearnService xPersonaLearnService;
    private final XOpsActionLedger ledger;
    private final ObjectMapper objectMapper;

    @Value("${marketing.x.drill-daily-cap:10}")
    private int dailyCap;

    @Transactional
    public synchronized void handleUpdate(JsonNode update) {
        if (!telegramNotifier.isConfigured()) {
            return;
        }
        TelegramDrillCommands.Parsed parsed = TelegramDrillCommands.parse(update);
        if (parsed.kind() == TelegramDrillCommands.Kind.IGNORE) {
            return;
        }
        String expectedChat = telegramNotifier.configuredChatId();
        if (expectedChat == null || !expectedChat.equals(Long.toString(parsed.chatId()))) {
            log.debug("[x-drill] ignore chatId={}", parsed.chatId());
            return;
        }
        expirePendingIfNeeded(Instant.now());
        switch (parsed.kind()) {
            case DRILL -> startSession(parsed.drillCount());
            case SKIP -> skipCurrent();
            case REPLY -> captureReply(parsed);
            default -> { }
        }
    }

    public boolean isBlockedTweet(String tweetId) {
        if (tweetId == null || tweetId.isBlank()) {
            return false;
        }
        expirePendingIfNeeded(Instant.now());
        Pending pending = readPending();
        return pending != null && tweetId.equals(pending.tweetId());
    }

    @Transactional(readOnly = true)
    public boolean isLabeledTweet(String tweetId) {
        return tweetId != null && !tweetId.isBlank() && exampleRepository.existsByTweetId(tweetId);
    }

    private void startSession(int count) {
        Pending existing = readPending();
        if (existing != null) {
            telegramNotifier.send("이미 대기 중인 드릴이 있어요. 그 메시지에 답장하거나 /skip.");
            return;
        }
        writeRemaining(Math.max(0, count - 1));
        sendNextCard();
    }

    private void skipCurrent() {
        Pending pending = readPending();
        if (pending == null) {
            telegramNotifier.send("대기 중인 드릴이 없어요. /drill 로 시작.");
            return;
        }
        clearPending();
        telegramNotifier.send("넘김. 이 글은 학습 안 함.");
        sendNextIfRemaining();
    }

    private void captureReply(TelegramDrillCommands.Parsed parsed) {
        Pending pending = readPending();
        if (pending == null || parsed.replyToMessageId() == null
            || pending.telegramMessageId() != parsed.replyToMessageId()) {
            return;
        }
        String body = parsed.text() == null ? "" : parsed.text().trim();
        if (body.isBlank() || body.length() > 280) {
            telegramNotifier.send("한 줄로 다시. 너무 길면 잘라서.");
            return;
        }
        if (exampleRepository.existsByTweetId(pending.tweetId())) {
            clearPending();
            telegramNotifier.send("이미 학습한 글이에요.");
            sendNextIfRemaining();
            return;
        }
        Instant now = Instant.now();
        exampleRepository.save(XPersonaExample.builder()
            .source(XPersonaExample.Source.DRILL)
            .tweetId(pending.tweetId())
            .postText(pending.postText())
            .hasPhoto(pending.hasPhoto())
            .operatorBody(body)
            .createdAt(now)
            .build());
        ledger.recordSkipped(XOpsAction.Kind.OUTBOUND, pending.tweetId(), "DRILL", now);
        String status = xPersonaLearnService.ingestDrillIntoProfile("telegram");
        int today = xPersonaLearnService.drillsToday(now);
        clearPending();
        telegramNotifier.send("저장됨 " + today + "/" + cap() + " (" + status + "). 게시 안 함.");
        sendNextIfRemaining();
    }

    private void sendNextIfRemaining() {
        int left = readRemaining();
        if (left <= 0) {
            writeRemaining(0);
            return;
        }
        writeRemaining(left - 1);
        sendNextCard();
    }

    private void sendNextCard() {
        Instant now = Instant.now();
        if (sentToday(now) >= cap()) {
            telegramNotifier.send("오늘 드릴 상한(" + cap() + ")이에요.");
            writeRemaining(0);
            return;
        }
        telegramNotifier.send("후보 찾는 중… (최대 1분)");
        AsmClient.XOutboundCandidate picked;
        try {
            picked = pickCandidate();
        } catch (Exception e) {
            log.warn("[x-drill] candidate fetch failed: {}", e.getMessage());
            telegramNotifier.send("후보 조회 실패. 잠시 후 /drill.");
            writeRemaining(0);
            return;
        }
        if (picked == null) {
            telegramNotifier.send("후보 없음 — 영상·이미 댓글/드릴한 글은 건너뜀.");
            writeRemaining(0);
            return;
        }
        String caption = buildCaption(picked);
        Long messageId = sendCard(picked, caption);
        if (messageId == null) {
            telegramNotifier.send("Telegram 전송 실패.");
            writeRemaining(0);
            return;
        }
        incrementSent(now);
        savePending(new Pending(
            messageId,
            picked.tweetId(),
            picked.text(),
            picked.hasPhoto(),
            now.plus(PENDING_TTL)));
    }

    private Long sendCard(AsmClient.XOutboundCandidate picked, String caption) {
        byte[] jpeg = decodeJpeg(picked.photoJpegBase64());
        if (picked.hasPhoto() && jpeg != null) {
            return telegramNotifier.sendPhoto(jpeg, caption).orElse(null);
        }
        return telegramNotifier.sendAndGetMessageId(caption).orElse(null);
    }

    AsmClient.XOutboundCandidate pickCandidate() {
        var settings = xOpsSettingsService.get();
        List<AsmClient.XOutboundCandidate> candidates = asmClient.listXOutboundCandidates(
            settings.hotMinReplies(), settings.hotMaxAgeHours());
        if (candidates == null) {
            return null;
        }
        for (AsmClient.XOutboundCandidate c : candidates) {
            if (c == null || c.tweetId() == null || c.tweetId().isBlank()) {
                continue;
            }
            if (c.hasVideo()) {
                continue;
            }
            if (c.replyCount() < settings.hotMinReplies() || c.ageHours() > settings.hotMaxAgeHours()) {
                continue;
            }
            String replyTo = XOutboundService.replyTarget(c);
            if (replyTo == null) {
                continue;
            }
            if (ledger.alreadyHandled(c.tweetId()) || ledger.alreadyHandled(replyTo)) {
                continue;
            }
            if (exampleRepository.existsByTweetId(c.tweetId()) || exampleRepository.existsByTweetId(replyTo)) {
                continue;
            }
            return c;
        }
        return null;
    }

    static String buildCaption(AsmClient.XOutboundCandidate c) {
        StringBuilder sb = new StringBuilder();
        sb.append("[Justant-Bot 드릴]\n");
        if (c.authorHandle() != null && !c.authorHandle().isBlank()) {
            sb.append('@').append(c.authorHandle().replace("@", "")).append('\n');
        }
        if (c.text() != null && !c.text().isBlank()) {
            sb.append(c.text().strip()).append("\n\n");
        }
        List<String> peers = c.peerReplies();
        if (peers != null && !peers.isEmpty()) {
            sb.append("힌트:\n");
            int n = 0;
            for (String p : peers) {
                if (p == null || p.isBlank()) {
                    continue;
                }
                sb.append("- ").append(p.strip()).append('\n');
                n++;
                if (n >= 3) {
                    break;
                }
            }
            sb.append('\n');
        }
        sb.append("이 메시지에 답장으로 한 줄. 넘기려면 /skip. 게시 안 함.");
        return TelegramNotifier.truncateCaption(sb.toString());
    }

    static byte[] decodeJpeg(String b64) {
        if (b64 == null || b64.isBlank()) {
            return null;
        }
        try {
            return Base64.getDecoder().decode(b64);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void expirePendingIfNeeded(Instant now) {
        Pending pending = readPending();
        if (pending != null && pending.expiresAt() != null && now.isAfter(pending.expiresAt())) {
            clearPending();
            log.info("[x-drill] pending expired tweetId={}", pending.tweetId());
        }
    }

    private int cap() {
        return dailyCap > 0 ? dailyCap : DEFAULT_DAILY_CAP;
    }

    record Pending(long telegramMessageId, String tweetId, String postText, boolean hasPhoto, Instant expiresAt) {}

    private Pending readPending() {
        try {
            String raw = readRaw(KEY_PENDING, null);
            if (raw == null || raw.isBlank()) {
                return null;
            }
            JsonNode n = objectMapper.readTree(raw);
            long mid = n.path("telegramMessageId").asLong(0);
            String tweetId = n.path("tweetId").asText(null);
            if (mid <= 0 || tweetId == null || tweetId.isBlank()) {
                return null;
            }
            Instant exp = Instant.parse(n.path("expiresAt").asText());
            return new Pending(mid, tweetId, n.path("postText").asText(null),
                n.path("hasPhoto").asBoolean(false), exp);
        } catch (Exception e) {
            return null;
        }
    }

    private void savePending(Pending pending) {
        ObjectNode n = objectMapper.createObjectNode();
        n.put("telegramMessageId", pending.telegramMessageId());
        n.put("tweetId", pending.tweetId());
        n.put("postText", pending.postText());
        n.put("hasPhoto", pending.hasPhoto());
        n.put("expiresAt", pending.expiresAt().toString());
        saveSetting(KEY_PENDING, n.toString(), Instant.now(), "telegram");
    }

    private void clearPending() {
        saveSetting(KEY_PENDING, "", Instant.now(), "telegram");
    }

    private int readRemaining() {
        try {
            JsonNode n = objectMapper.readTree(readRaw(KEY_SESSION, "{}"));
            return n.path("remaining").asInt(0);
        } catch (Exception e) {
            return 0;
        }
    }

    private void writeRemaining(int remaining) {
        ObjectNode n = objectMapper.createObjectNode();
        n.put("remaining", Math.max(0, remaining));
        saveSetting(KEY_SESSION, n.toString(), Instant.now(), "telegram");
    }

    private int sentToday(Instant now) {
        try {
            JsonNode n = objectMapper.readTree(readRaw(KEY_SENT_DAY, "{}"));
            String day = n.path("day").asText("");
            String today = LocalDate.now(XPersonaLearnService.KST).toString();
            if (!today.equals(day)) {
                return 0;
            }
            return n.path("count").asInt(0);
        } catch (Exception e) {
            return 0;
        }
    }

    private void incrementSent(Instant now) {
        String today = LocalDate.now(XPersonaLearnService.KST).toString();
        int count = sentToday(now) + 1;
        ObjectNode n = objectMapper.createObjectNode();
        n.put("day", today);
        n.put("count", count);
        saveSetting(KEY_SENT_DAY, n.toString(), now, "telegram");
    }

    private String readRaw(String key, String dflt) {
        return systemSettingRepository.findById(key)
            .map(SystemSetting::getSettingValue)
            .filter(v -> v != null && !v.isBlank())
            .orElse(dflt);
    }

    private void saveSetting(String key, String value, Instant now, String updatedBy) {
        SystemSetting setting = systemSettingRepository.findById(key).orElseGet(() ->
            SystemSetting.builder().settingKey(key).build());
        setting.setSettingValue(value);
        setting.setUpdatedAt(now);
        setting.setUpdatedBy(updatedBy != null ? updatedBy : "system");
        systemSettingRepository.save(setting);
    }
}
