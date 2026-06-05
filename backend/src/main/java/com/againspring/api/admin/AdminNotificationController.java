package com.againspring.api.admin;

import com.againspring.annotation.Auditable;
import com.againspring.api.dto.request.BroadcastNotificationRequest;
import com.againspring.domain.User;
import com.againspring.domain.enums.NotificationType;
import com.againspring.repository.UserRepository;
import com.againspring.service.NotificationWriteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 관리자 알림 브로드캐스트 API
 */
@RestController
@RequestMapping("/api/admin/notifications")
@RequiredArgsConstructor
@Tag(name = "Admin — Notifications", description = "알림 브로드캐스트 (ADMIN 전용)")
@SecurityRequirement(name = "bearer-jwt")
@PreAuthorize("hasRole('ADMIN')")
public class AdminNotificationController {

    private final NotificationWriteService notificationWriteService;
    private final UserRepository userRepository;

    /**
     * POST /api/admin/notifications/broadcast
     * 알림 브로드캐스트
     * target: ALL (모든 활성 사용자), MEMBERS (게스트 제외), CUSTOM (지정된 사용자만)
     */
    @PostMapping("/broadcast")
    @Operation(
        summary = "알림 브로드캐스트",
        description = "지정된 대상에게 알림을 발송 (ALL/MEMBERS/CUSTOM)"
    )
    @ApiResponse(responseCode = "204", description = "알림 발송 완료")
    @ApiResponse(responseCode = "400", description = "요청 데이터 오류")
    @ApiResponse(responseCode = "401", description = "인증 필요")
    @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음")
    @Auditable(action = "NOTIFICATION_BROADCAST")
    public ResponseEntity<Void> broadcastNotification(
            @RequestBody BroadcastNotificationRequest request) {

        List<User> targetUsers = null;

        if ("ALL".equals(request.getTarget())) {
            // 모든 활성 비게스트 사용자
            targetUsers = userRepository.findAll().stream()
                .filter(u -> !u.isGuest() && u.getDeletedAt() == null && "ACTIVE".equals(u.getStatus()))
                .collect(Collectors.toList());
        } else if ("MEMBERS".equals(request.getTarget())) {
            // 비게스트 회원만
            targetUsers = userRepository.findAll().stream()
                .filter(u -> !u.isGuest() && u.getDeletedAt() == null && "ACTIVE".equals(u.getStatus()))
                .collect(Collectors.toList());
        } else if ("CUSTOM".equals(request.getTarget())) {
            // 지정된 사용자 ID 목록
            if (request.getUserIds() == null || request.getUserIds().isEmpty()) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "CUSTOM 대상 선택 시 userIds를 입력하세요");
            }
            targetUsers = request.getUserIds().stream()
                .map(userId -> userRepository.findById(userId).orElse(null))
                .filter(u -> u != null && u.getDeletedAt() == null && "ACTIVE".equals(u.getStatus()))
                .collect(Collectors.toList());
        } else {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "target은 ALL, MEMBERS, CUSTOM 중 하나여야 합니다");
        }

        // 각 사용자에게 알림 발송
        for (User user : targetUsers) {
            notificationWriteService.send(
                user.getId(),
                NotificationType.ADMIN_BROADCAST,
                request.getTitle(),
                request.getSubtitle(),
                null
            );
        }

        return ResponseEntity.noContent().build();
    }
}
