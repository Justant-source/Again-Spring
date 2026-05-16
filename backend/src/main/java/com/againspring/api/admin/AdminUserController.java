package com.againspring.api.admin;

import com.againspring.api.dto.response.AdminUserDetailResponse;
import com.againspring.domain.User;
import com.againspring.repository.UserRepository;
import com.againspring.service.admin.AdminUserDetailService;
import com.againspring.service.retention.UserDeletionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.againspring.common.exception.BusinessException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserRepository userRepository;
    private final UserDeletionService userDeletionService;
    private final AdminUserDetailService adminUserDetailService;

    @GetMapping("/search")
    public ResponseEntity<List<User>> search(@RequestParam String q) {
        List<User> users = userRepository
                .findByNicknameContainingIgnoreCaseOrEmailContainingIgnoreCaseAndDeletedAtIsNull(q, q);
        return ResponseEntity.ok(users);
    }

    /** 전체 사용자 페이지네이션 조회 (admin 대시보드 사용자 관리) */
    @GetMapping
    public ResponseEntity<Page<User>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "false") boolean includeGuest) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        Pageable pageable = PageRequest.of(Math.max(page, 0), safeSize);
        Page<User> result = includeGuest
                ? userRepository.findByDeletedAtIsNullOrderByCreatedAtDesc(pageable)
                : userRepository.findByIsGuestFalseAndDeletedAtIsNullOrderByCreatedAtDesc(pageable);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AdminUserDetailResponse> getDetail(@PathVariable String id) {
        return ResponseEntity.ok(adminUserDetailService.getUserDetail(id));
    }

    @DeleteMapping("/{id}/data")
    public ResponseEntity<Map<String, String>> deleteUserData(@PathVariable String id) {
        userDeletionService.scheduleAnonymization(id);
        return ResponseEntity.ok(Map.of("status", "scheduled", "userId", id));
    }

    /**
     * V13 Phase 2 — TESTER 역할 부여/해제.
     * ADMIN이 일반 사용자의 roles 목록을 직접 지정한다.
     * 허용 역할: USER, TESTER (ADMIN은 AdminRoleAssigner가 관리하므로 여기서 변경 불가).
     */
    @PatchMapping("/{id}/roles")
    public ResponseEntity<Map<String, Object>> updateRoles(
            @PathVariable String id,
            @RequestBody Map<String, List<String>> body) {

        List<String> requested = body.getOrDefault("roles", List.of());
        Set<String> ALLOWED = Set.of("USER", "TESTER");

        if (!ALLOWED.containsAll(requested)) {
            throw new BusinessException("INVALID_ROLE",
                    "변경 가능한 역할은 USER, TESTER입니다.", 400);
        }

        User user = userRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "사용자를 찾을 수 없습니다."));

        // ADMIN 역할은 이 API로 변경 불가 — 기존 ADMIN 상태 보존
        List<String> updatedRoles = new ArrayList<>(requested);
        if (user.getRoles() != null && user.getRoles().contains("ADMIN")) {
            if (!updatedRoles.contains("ADMIN")) {
                updatedRoles.add("ADMIN");
            }
        }

        user.setRoles(updatedRoles);
        userRepository.save(user);

        return ResponseEntity.ok(Map.of("userId", id, "roles", updatedRoles));
    }
}
