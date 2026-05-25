package com.againspring.api.admin.marketing;

import com.againspring.api.dto.request.HashtagRequest;
import com.againspring.api.dto.response.HashtagResponse;
import com.againspring.service.marketing.HashtagLibraryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/marketing/hashtags")
@ConditionalOnProperty(name = "app.features.marketing.enabled", havingValue = "true")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Admin Marketing Hashtags", description = "Marketing hashtag library management")
@SecurityRequirement(name = "bearerAuth")
public class HashtagController {

    private final HashtagLibraryService hashtagLibraryService;

    @GetMapping
    @Operation(summary = "List hashtags")
    public ResponseEntity<List<HashtagResponse>> list(
            @RequestParam(required = false) String platform) {
        return ResponseEntity.ok(hashtagLibraryService.findAll(platform));
    }

    @PostMapping
    @Operation(summary = "Create hashtag")
    public ResponseEntity<HashtagResponse> create(@Valid @RequestBody HashtagRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(hashtagLibraryService.create(request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete hashtag")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        hashtagLibraryService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
