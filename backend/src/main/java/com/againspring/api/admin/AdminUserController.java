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

import java.util.List;
import java.util.Map;

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
}
