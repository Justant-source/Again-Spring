package com.againspring.service.marketing;

import com.againspring.domain.marketing.MarketingContent;
import com.againspring.repository.marketing.MarketingContentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 마케팅 콘텐츠 내보내기 서비스
 * 콘텐츠를 ZIP 형식으로 내보내기 (content.txt, metadata.json, README.txt)
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "app.features.marketing.enabled", havingValue = "true")
@Transactional(readOnly = true)
public class ContentExportService {

    private final MarketingContentRepository contentRepository;
    private final ObjectMapper objectMapper;

    public ContentExportService(
            MarketingContentRepository contentRepository,
            ObjectMapper objectMapper) {
        this.contentRepository = contentRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 콘텐츠를 ZIP 형식으로 내보내기 (메모리 기반)
     * 반환 바이트 배열은 HTTP 응답 body로 전송
     */
    public byte[] exportAsZip(Long contentId) throws IOException {
        MarketingContent content = contentRepository.findById(contentId)
                .orElseThrow(() -> new EntityNotFoundException("콘텐츠를 찾을 수 없습니다: " + contentId));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // 1. content.txt
            ZipEntry contentEntry = new ZipEntry("content.txt");
            zos.putNextEntry(contentEntry);
            zos.write(content.getBodyText().getBytes("UTF-8"));
            zos.closeEntry();

            // 2. metadata.json
            ZipEntry metadataEntry = new ZipEntry("metadata.json");
            zos.putNextEntry(metadataEntry);
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("id", content.getId());
            metadata.put("simulationId", content.getSimulationId());
            metadata.put("platform", content.getPlatform().toString());
            metadata.put("status", content.getStatus().toString());
            metadata.put("createdAt", content.getCreatedAt().toString());
            String metadataJson = objectMapper.writeValueAsString(metadata);
            zos.write(metadataJson.getBytes("UTF-8"));
            zos.closeEntry();

            // 3. README.txt
            ZipEntry readmeEntry = new ZipEntry("README.txt");
            zos.putNextEntry(readmeEntry);
            String readme = "다시봄 마케팅 콘텐츠 내보내기\n" +
                    "================================\n" +
                    "\n" +
                    "발행 전에 반드시 내용을 검토한 후 사용하세요.\n" +
                    "이 콘텐츠는 AI 생성 결과입니다.\n";
            zos.write(readme.getBytes("UTF-8"));
            zos.closeEntry();
        }

        log.info("마케팅 콘텐츠 내보내기: contentId={}, size={} bytes", contentId, baos.size());
        return baos.toByteArray();
    }
}
