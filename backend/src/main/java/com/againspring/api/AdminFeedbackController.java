package com.againspring.api;

import com.againspring.api.dto.request.UpdateFeedbackStatusRequest;
import com.againspring.domain.Feedback;
import com.againspring.service.FeedbackService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/feedbacks")
@RequiredArgsConstructor
public class AdminFeedbackController {

    private final FeedbackService feedbackService;

    @GetMapping
    public ResponseEntity<Page<Feedback>> list(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<Feedback> result;
        if (category != null && !category.isBlank()) {
            result = feedbackService.listByCategory(category, pageable);
        } else if (status != null && !status.isBlank()) {
            result = feedbackService.listByStatus(status, pageable);
        } else {
            result = feedbackService.listAll(pageable);
        }
        return ResponseEntity.ok(result);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Feedback> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateFeedbackStatusRequest req) {

        Feedback updated = feedbackService.updateStatus(id, req.getStatus(), req.getAdminNote());
        return ResponseEntity.ok(updated);
    }
}
