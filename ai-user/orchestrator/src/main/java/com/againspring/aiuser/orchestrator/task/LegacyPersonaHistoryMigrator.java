package com.againspring.aiuser.orchestrator.task;

import com.againspring.aiuser.orchestrator.config.OrchestratorProperties;
import com.againspring.aiuser.orchestrator.service.PersonaHistoryStore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class LegacyPersonaHistoryMigrator {

    private static final DateTimeFormatter LEGACY_TS =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final OrchestratorProperties props;
    private final PersonaHistoryStore personaHistoryStore;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Value("${ai-user.legacy-history-import.enabled:true}")
    private boolean importEnabled;

    @PostConstruct
    public void importLegacyHistory() {
        if (!importEnabled) {
            return;
        }
        try {
            File profilesDir = new File(props.getPersonasDir(), "profiles");
            File[] personaDirs = profilesDir.listFiles(File::isDirectory);
            if (personaDirs == null || personaDirs.length == 0) {
                return;
            }
            int importedEntries = 0;
            int importedStates = 0;
            for (File personaDir : personaDirs) {
                String personaId = resolvePersonaId(personaDir);
                if (personaId == null) {
                    continue;
                }
                importedEntries += importHistoryFile(personaId, personaDir.toPath().resolve("history/posts.md"), "posts");
                importedEntries += importHistoryFile(personaId, personaDir.toPath().resolve("history/comments.md"), "comments");
                if (importLifeState(personaId, personaDir.toPath().resolve("life_state.json"))) {
                    importedStates++;
                }
            }
            if (importedEntries > 0 || importedStates > 0) {
                log.info("Legacy persona file history imported: entries={} lifeStates={}", importedEntries, importedStates);
            }
        } catch (Exception e) {
            log.warn("Legacy persona history import skipped: {}", e.getMessage());
        }
    }

    private String resolvePersonaId(File personaDir) {
        String syntheticEmail = resolveSyntheticEmail(personaDir);
        List<String> ids = jdbcTemplate.queryForList(
            "SELECT p.id FROM personas p " +
                "JOIN users u ON u.id = p.id " +
                "WHERE u.email = ? LIMIT 1",
            String.class,
            syntheticEmail
        );
        if (!ids.isEmpty()) {
            return ids.get(0);
        }
        log.debug("Skipping legacy history import for {}: no persona mapping", personaDir.getName());
        return null;
    }

    private String resolveSyntheticEmail(File personaDir) {
        return personaDir.getName() + "@againspring.internal";
    }

    private int importHistoryFile(String personaId, Path file, String type) {
        try {
            if (!Files.exists(file)) {
                return 0;
            }
            String raw = Files.readString(file);
            Instant fallbackCreatedAt = Instant.ofEpochMilli(Files.getLastModifiedTime(file).toMillis());
            int imported = 0;
            for (String block : raw.split("\\n---")) {
                String body = ActionExecutor.extractHistoryBody(block, type);
                if (body == null || body.isBlank()) {
                    continue;
                }
                List<String> cells = headerCells(block);
                Instant createdAt = parseLegacyTimestamp(cells, fallbackCreatedAt);
                String category = "posts".equals(type) && cells.size() > 1 ? cells.get(1) : "";
                String postId = cells.size() > 2 ? cells.get(2) : "";
                personaHistoryStore.appendImportedEntry(personaId, type, body, postId, category, createdAt);
                imported++;
            }
            return imported;
        } catch (Exception e) {
            log.debug("Failed to import legacy history {} for {}: {}", type, personaId, e.getMessage());
            return 0;
        }
    }

    private boolean importLifeState(String personaId, Path file) {
        try {
            if (!Files.exists(file)) {
                return false;
            }
            JsonNode node = objectMapper.readTree(Files.readString(file));
            int casualStreak = node.path("casualStreak").asInt(0);
            String ongoingSituation = node.path("ongoingSituation").asText("");
            Instant updatedAt = parseIsoInstant(node.path("updatedAt").asText(null));
            personaHistoryStore.importLifeState(personaId, casualStreak, ongoingSituation, updatedAt);
            return true;
        } catch (Exception e) {
            log.debug("Failed to import legacy life_state for {}: {}", personaId, e.getMessage());
            return false;
        }
    }

    private static List<String> headerCells(String block) {
        if (block == null || block.isBlank()) {
            return List.of();
        }
        String header = null;
        for (String line : block.split("\\R")) {
            if (line.contains("|")) {
                header = line;
                break;
            }
        }
        if (header == null) {
            return List.of();
        }
        if (!header.contains("|")) {
            return List.of();
        }
        return java.util.Arrays.stream(header.split("\\|"))
            .map(String::trim)
            .filter(s -> !s.isBlank())
            .toList();
    }

    private static Instant parseLegacyTimestamp(List<String> cells, Instant fallbackCreatedAt) {
        if (cells.isEmpty()) {
            return fallbackCreatedAt;
        }
        try {
            LocalDateTime ldt = LocalDateTime.parse(cells.get(0), LEGACY_TS);
            return ldt.atZone(ZoneId.of("Asia/Seoul")).toInstant();
        } catch (Exception e) {
            return fallbackCreatedAt;
        }
    }

    private static Instant parseIsoInstant(String raw) {
        if (raw == null || raw.isBlank()) {
            return Instant.now();
        }
        try {
            return Instant.parse(raw);
        } catch (Exception e) {
            return Instant.now();
        }
    }
}
