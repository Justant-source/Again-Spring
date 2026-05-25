package com.againspring.api.admin.marketing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.nio.file.Paths;

/**
 * 마케팅 콘텐츠 채팅 스크린샷 이미지 서빙.
 * GET /api/admin/marketing/images/{filename}
 */
@RestController
@RequestMapping("/api/admin/marketing/images")
@ConditionalOnProperty(name = "app.features.marketing.enabled", havingValue = "true")
@PreAuthorize("hasRole('ADMIN')")
public class MarketingImageController {

    @Value("${app.features.marketing.image-dir:/tmp/marketing-images}")
    private String imageDir;

    @GetMapping("/{filename}")
    public ResponseEntity<Resource> getImage(@PathVariable String filename) {
        if (filename.contains("..") || filename.contains("/")) {
            return ResponseEntity.badRequest().build();
        }
        File file = Paths.get(imageDir, filename).toFile();
        if (!file.exists() || !file.isFile()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(new FileSystemResource(file));
    }
}
