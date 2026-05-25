package com.againspring.service.marketing;

import com.againspring.api.dto.request.TemplateRequest;
import com.againspring.api.dto.response.TemplateResponse;
import com.againspring.domain.marketing.MarketingContent;
import com.againspring.domain.marketing.MarketingContentTemplate;
import com.againspring.repository.marketing.MarketingContentTemplateRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(name = "app.features.marketing.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class TemplateService {

    private final MarketingContentTemplateRepository templateRepo;

    public List<TemplateResponse> findAll(String platformStr, Boolean activeOnly) {
        List<MarketingContentTemplate> all;
        if (platformStr != null && !platformStr.isBlank()) {
            MarketingContent.Platform platform = MarketingContent.Platform.valueOf(platformStr.toUpperCase());
            all = Boolean.TRUE.equals(activeOnly)
                    ? templateRepo.findByPlatformAndIsActiveTrue(platform)
                    : templateRepo.findByPlatform(platform);
        } else {
            all = Boolean.TRUE.equals(activeOnly)
                    ? templateRepo.findByIsActiveTrue()
                    : templateRepo.findAll();
        }
        return all.stream().map(TemplateResponse::from).collect(Collectors.toList());
    }

    public TemplateResponse findById(Long id) {
        return TemplateResponse.from(loadOrThrow(id));
    }

    @Transactional
    public TemplateResponse create(TemplateRequest req, Long adminId) {
        MarketingContent.Platform platform = MarketingContent.Platform.valueOf(req.getPlatform().toUpperCase());
        MarketingContentTemplate template = MarketingContentTemplate.builder()
                .platform(platform)
                .name(req.getName())
                .bodyTemplate(req.getBodyTemplate())
                .variablesJson(req.getVariablesJson())
                .isActive(req.getIsActive() != null ? req.getIsActive() : true)
                .createdBy(adminId)
                .build();
        MarketingContentTemplate saved = templateRepo.save(template);
        log.info("Created template: id={}, platform={}", saved.getId(), saved.getPlatform());
        return TemplateResponse.from(saved);
    }

    @Transactional
    public TemplateResponse update(Long id, TemplateRequest req) {
        MarketingContentTemplate template = loadOrThrow(id);
        if (req.getPlatform() != null) {
            template.setPlatform(MarketingContent.Platform.valueOf(req.getPlatform().toUpperCase()));
        }
        if (req.getName() != null) template.setName(req.getName());
        if (req.getBodyTemplate() != null) template.setBodyTemplate(req.getBodyTemplate());
        if (req.getVariablesJson() != null) template.setVariablesJson(req.getVariablesJson());
        if (req.getIsActive() != null) template.setIsActive(req.getIsActive());
        return TemplateResponse.from(templateRepo.save(template));
    }

    @Transactional
    public TemplateResponse toggleActive(Long id) {
        MarketingContentTemplate template = loadOrThrow(id);
        template.setIsActive(!Boolean.TRUE.equals(template.getIsActive()));
        return TemplateResponse.from(templateRepo.save(template));
    }

    @Transactional
    public void delete(Long id) {
        if (!templateRepo.existsById(id)) throw new EntityNotFoundException("Template not found: " + id);
        templateRepo.deleteById(id);
        log.info("Deleted template: id={}", id);
    }

    public String applyVariables(String bodyTemplate, Map<String, String> variables) {
        if (variables == null || variables.isEmpty()) {
            validateNoResidualPlaceholders(bodyTemplate);
            return bodyTemplate;
        }
        String result = bodyTemplate;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            result = result.replace("${" + entry.getKey() + "}", entry.getValue());
        }
        validateNoResidualPlaceholders(result);
        return result;
    }

    private void validateNoResidualPlaceholders(String text) {
        if (text.contains("${") || text.contains("{{")) {
            throw new IllegalStateException(
                    "Template has unresolved placeholders. Provide all required variable values.");
        }
    }

    private MarketingContentTemplate loadOrThrow(Long id) {
        return templateRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Template not found: " + id));
    }
}
