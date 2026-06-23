package com.againspring.aiuser.orchestrator.service;

import com.againspring.aiuser.orchestrator.domain.PersonaHistoryEntry;
import com.againspring.aiuser.orchestrator.domain.PersonaLifeState;
import com.againspring.aiuser.orchestrator.repository.PersonaHistoryEntryRepository;
import com.againspring.aiuser.orchestrator.repository.PersonaLifeStateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PersonaHistoryStore {

    private final PersonaHistoryEntryRepository historyRepository;
    private final PersonaLifeStateRepository lifeStateRepository;

    @Transactional
    public void appendPost(String personaId, String content, String postId, String category) {
        appendEntry(personaId, "POST", content, postId, category, Instant.now());
    }

    @Transactional
    public void appendComment(String personaId, String content, String postId) {
        appendEntry(personaId, "COMMENT", content, postId, "", Instant.now());
    }

    @Transactional
    public void appendImportedEntry(
        String personaId,
        String entryType,
        String content,
        String postId,
        String category,
        Instant createdAt
    ) {
        appendEntry(personaId, normalizeEntryType(entryType), content, postId, category, createdAt);
    }

    public List<String> loadRecentPosts(String personaId, int limit) {
        return loadRecentBodies(personaId, "POST", limit);
    }

    public List<String> loadRecentComments(String personaId, int limit) {
        return loadRecentBodies(personaId, "COMMENT", limit);
    }

    public int loadCasualStreak(String personaId) {
        return lifeStateRepository.findById(personaId)
            .map(PersonaLifeState::getCasualStreak)
            .orElse(0);
    }

    public String loadOngoingSituation(String personaId) {
        return lifeStateRepository.findById(personaId)
            .map(PersonaLifeState::getOngoingSituation)
            .map(String::trim)
            .filter(s -> !s.isBlank())
            .orElse(null);
    }

    @Transactional
    public void updateLifeState(String personaId, boolean wasCasual, String ongoingSituation) {
        PersonaLifeState state = lifeStateRepository.findById(personaId)
            .orElseGet(() -> PersonaLifeState.builder().personaId(personaId).build());
        int casualStreak = wasCasual ? state.getCasualStreak() + 1 : 0;
        String situation = wasCasual ? "" : normalizeSituation(ongoingSituation);
        state.setCasualStreak(casualStreak);
        state.setOngoingSituation(situation);
        state.setUpdatedAt(Instant.now());
        lifeStateRepository.save(state);
    }

    @Transactional
    public void importLifeState(String personaId, int casualStreak, String ongoingSituation, Instant updatedAt) {
        PersonaLifeState state = lifeStateRepository.findById(personaId)
            .orElseGet(() -> PersonaLifeState.builder().personaId(personaId).build());
        state.setCasualStreak(Math.max(casualStreak, 0));
        state.setOngoingSituation(normalizeSituation(ongoingSituation));
        state.setUpdatedAt(updatedAt != null ? updatedAt : Instant.now());
        lifeStateRepository.save(state);
    }

    private List<String> loadRecentBodies(String personaId, String entryType, int limit) {
        if (personaId == null || personaId.isBlank() || limit <= 0) {
            return List.of();
        }
        List<PersonaHistoryEntry> rows = historyRepository.findByPersonaIdAndEntryTypeOrderByCreatedAtDescIdDesc(
            personaId, normalizeEntryType(entryType), PageRequest.of(0, limit)
        );
        if (rows.isEmpty()) {
            return List.of();
        }
        List<String> bodies = new ArrayList<>(rows.size());
        for (PersonaHistoryEntry row : rows) {
            if (row.getContent() != null && !row.getContent().isBlank()) {
                bodies.add(row.getContent());
            }
        }
        Collections.reverse(bodies);
        return bodies;
    }

    private void appendEntry(
        String personaId,
        String entryType,
        String content,
        String postId,
        String category,
        Instant createdAt
    ) {
        if (personaId == null || personaId.isBlank() || content == null || content.isBlank()) {
            return;
        }
        PersonaHistoryEntry entry = PersonaHistoryEntry.builder()
            .personaId(personaId)
            .entryType(normalizeEntryType(entryType))
            .targetPostId(normalizeField(postId))
            .category(normalizeField(category))
            .contentHash(sha256(normalizeEntryType(entryType) + "\n" + normalizeField(postId) + "\n" + content))
            .content(content)
            .createdAt(createdAt != null ? createdAt : Instant.now())
            .build();
        try {
            historyRepository.save(entry);
        } catch (DataIntegrityViolationException e) {
            log.debug("Skipping duplicate persona history entry persona={} type={} post={}",
                personaId, entry.getEntryType(), entry.getTargetPostId());
        }
    }

    private static String normalizeField(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalizeEntryType(String value) {
        if (value == null || value.isBlank()) {
            return "COMMENT";
        }
        return switch (value.trim().toUpperCase()) {
            case "POST", "POSTS" -> "POST";
            case "COMMENT", "COMMENTS" -> "COMMENT";
            default -> value.trim().toUpperCase();
        };
    }

    private static String normalizeSituation(String ongoingSituation) {
        if (ongoingSituation == null || ongoingSituation.isBlank()) {
            return "";
        }
        String normalized = ongoingSituation.replace("\"", "'").replace("\n", " ").trim();
        return normalized.length() > 80 ? normalized.substring(0, 80) : normalized;
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to hash persona history", e);
        }
    }
}
