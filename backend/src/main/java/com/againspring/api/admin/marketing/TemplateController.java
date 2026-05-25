package com.againspring.api.admin.marketing;

import com.againspring.api.dto.request.TemplateRequest;
import com.againspring.api.dto.response.TemplateResponse;
import com.againspring.service.marketing.TemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/marketing/templates")
@ConditionalOnProperty(name = "app.features.marketing.enabled", havingValue = "true")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Admin Marketing Templates", description = "Marketing content template management")
@SecurityRequirement(name = "bearerAuth")
public class TemplateController {

    private final TemplateService templateService;

    @GetMapping
    @Operation(summary = "List templates")
    public ResponseEntity<List<TemplateResponse>> list(
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) Boolean activeOnly) {
        return ResponseEntity.ok(templateService.findAll(platform, activeOnly));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get template by ID")
    public ResponseEntity<TemplateResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(templateService.findById(id));
    }

    @PostMapping
    @Operation(summary = "Create template")
    public ResponseEntity<TemplateResponse> create(
            @Valid @RequestBody TemplateRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long adminId = null;
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(templateService.create(request, adminId));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update template")
    public ResponseEntity<TemplateResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody TemplateRequest request) {
        return ResponseEntity.ok(templateService.update(id, request));
    }

    @PostMapping("/{id}/toggle")
    @Operation(summary = "Toggle template active status")
    public ResponseEntity<TemplateResponse> toggle(@PathVariable Long id) {
        return ResponseEntity.ok(templateService.toggleActive(id));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete template")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        templateService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
