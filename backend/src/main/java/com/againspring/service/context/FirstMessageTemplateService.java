package com.againspring.service.context;

import com.againspring.domain.Message;
import com.againspring.domain.Session;
import com.againspring.domain.enums.MessageSender;
import com.againspring.repository.MessageRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * V47 재구현 — 대분류별 predefined 첫마디 서비스.
 *
 * shared/docs/templates/first_message/{relationType}.json 에서
 * templates 배열의 5개 항목 중 랜덤 1개를 골라 MEDIATOR_TO_A 메시지로 저장.
 * 세션 생성 응답을 블로킹하지 않도록 @Async 비차단 처리.
 *
 * 기존 FirstMessageService의 대분류별 단순화 버전.
 * 중·소분류 의존성 없음 — relationType(대분류)만 사용.
 */
@Slf4j
@Service
public class FirstMessageTemplateService {

    private static final String UNIVERSAL_FALLBACK =
            "어떤 일이 있었는지 편하게 말씀해 주세요. 무엇이 가장 마음에 걸리시나요?";

    private final MessageRepository messageRepository;
    /** 대분류별 첫마디 JSON 파일들이 있는 디렉토리 경로 */
    private final String templatesDir;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Random random = new Random();

    /** relationType → 템플릿 리스트 메모리 캐시 */
    private final Map<String, List<String>> templateCache = new ConcurrentHashMap<>();

    public FirstMessageTemplateService(
            MessageRepository messageRepository,
            @Value("${app.templates.path:./shared/docs/templates/first_message}") String templatesDir) {
        this.messageRepository = messageRepository;
        this.templatesDir = templatesDir;
    }

    /**
     * 세션 생성 응답을 블로킹하지 않도록 비동기로 첫마디 생성·저장.
     *
     * @param session 방금 생성된 세션 (relationType이 설정된 상태)
     */
    @Async
    public void generateAndSaveAsync(Session session) {
        try {
            String content = pickTemplate(session);
            messageRepository.save(Message.builder()
                    .sessionId(session.getId())
                    .sender(MessageSender.MEDIATOR_TO_A)
                    .content(content)
                    .charCount(content.length())
                    .llmModel("predefined-template")
                    .build());
            log.debug("[FirstMsg] Saved predefined first message for session={}, relationType={}",
                    session.getId(),
                    session.getRelationType() != null ? session.getRelationType().getValue() : "null");
        } catch (Exception e) {
            log.warn("[FirstMsg] Failed to save first message for session={}: {}",
                    session.getId(), e.getMessage());
        }
    }

    private String pickTemplate(Session session) {
        if (session.getRelationType() == null) return UNIVERSAL_FALLBACK;
        String key = session.getRelationType().getValue();
        List<String> templates = getTemplates(key);
        if (templates.isEmpty()) return UNIVERSAL_FALLBACK;
        return templates.get(random.nextInt(templates.size()));
    }

    private List<String> getTemplates(String relationType) {
        return templateCache.computeIfAbsent(relationType, key -> loadTemplates(key));
    }

    private List<String> loadTemplates(String relationType) {
        try {
            // {templatesDir}/{relationType}.json
            Path templateFile = Paths.get(templatesDir, relationType + ".json");

            if (!Files.exists(templateFile)) {
                log.warn("[FirstMsg] Template file not found: {}", templateFile);
                return List.of();
            }

            String json = Files.readString(templateFile);
            JsonNode root = objectMapper.readTree(json);
            JsonNode arr = root.get("templates");
            if (arr == null || !arr.isArray()) return List.of();

            List<String> result = new ArrayList<>();
            for (JsonNode node : arr) {
                if (node.isTextual() && !node.asText().isBlank()) {
                    result.add(node.asText());
                }
            }
            log.debug("[FirstMsg] Loaded {} templates for relationType={}", result.size(), relationType);
            return result;
        } catch (IOException e) {
            log.warn("[FirstMsg] Failed to load templates for relationType={}: {}", relationType, e.getMessage());
            return List.of();
        }
    }
}
