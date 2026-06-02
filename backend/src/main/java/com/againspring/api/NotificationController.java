package com.againspring.api;

import com.againspring.service.notification.NotificationDto;
import com.againspring.service.notification.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 알림 API 컨트롤러
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearer-jwt")
@Tag(name = "Notification", description = "Notification endpoints for C3 plaza")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @Operation(summary = "Get user notifications", description = "Get top 50 latest notifications for the current user")
    public ResponseEntity<List<NotificationDto>> getNotifications(
        @AuthenticationPrincipal UserDetails userDetails
    ) {
        String userId = userDetails.getUsername();
        List<NotificationDto> notifications = notificationService.getNotifications(userId);
        return ResponseEntity.ok(notifications);
    }

    @PostMapping("/read-all")
    @Operation(summary = "Mark all notifications as read", description = "Mark all notifications as read for the current user")
    public ResponseEntity<Void> markAllRead(
        @AuthenticationPrincipal UserDetails userDetails
    ) {
        String userId = userDetails.getUsername();
        notificationService.markAllRead(userId);
        return ResponseEntity.ok().build();
    }
}
