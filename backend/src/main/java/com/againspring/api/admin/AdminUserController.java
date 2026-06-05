package com.againspring.api.admin;

import com.againspring.api.dto.response.AdminUserDetailResponse;
import com.againspring.annotation.Auditable;
import com.againspring.common.exception.BusinessException;
import com.againspring.domain.User;
import com.againspring.repository.UserRepository;
import com.againspring.service.admin.AdminUserDetailService;
import com.againspring.service.admin.UserAnonymizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@Tag(name = "Admin — Users", description = "사용자 조회·삭제·역할 관리 (ADMIN 전용)")
@SecurityRequirement(name = "bearer-jwt")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final UserRepository userRepository;
    private final AdminUserDetailService adminUserDetailService;
    private final UserAnonymizationService userAnonymizationService;

    @GetMapping("/search")
    @Operation(summary = "사용자 검색", description = "닉네임·이메일 contains 검색 (삭제된 계정 제외)")
    @ApiResponse(responseCode = "200", description = "검색 결과 목록")
    @ApiResponse(responseCode = "401", description = "인증 필요")
    @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음")
    public ResponseEntity<List<User>> search(@RequestParam String q) {
        List<User> users = userRepository
                .findByNicknameContainingIgnoreCaseOrEmailContainingIgnoreCaseAndDeletedAtIsNull(q, q);
        return ResponseEntity.ok(users);
    }

    @GetMapping
    @Operation(summary = "사용자 목록 (페이지네이션)", description = "전체 사용자를 페이지 단위로 반환. size는 1~100으로 클램프.")
    @ApiResponse(responseCode = "200", description = "사용자 페이지")
    @ApiResponse(responseCode = "401", description = "인증 필요")
    @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음")
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
    @Operation(summary = "사용자 상세 조회", description = "세션 이력·역할·온보딩 상태 등 상세 정보 반환")
    @ApiResponse(responseCode = "200", description = "사용자 상세")
    @ApiResponse(responseCode = "401", description = "인증 필요")
    @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음")
    @ApiResponse(responseCode = "404", description = "사용자 없음")
    public ResponseEntity<AdminUserDetailResponse> getDetail(@PathVariable String id) {
        return ResponseEntity.ok(adminUserDetailService.getUserDetail(id));
    }

    @DeleteMapping("/{id}/data")
    @Operation(summary = "사용자 데이터 익명화", description = "사용자의 PII(이메일, 비밀번호, OAuth 정보)를 삭제한다.")
    @ApiResponse(responseCode = "200", description = "익명화 완료")
    @ApiResponse(responseCode = "401", description = "인증 필요")
    @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음")
    @ApiResponse(responseCode = "404", description = "사용자 없음")
    @Auditable(action = "USER_ANONYMIZE", targetType = "USER", targetId = "#id")
    public ResponseEntity<Map<String, String>> deleteUserData(@PathVariable String id) {
        userAnonymizationService.anonymize(id);
        return ResponseEntity.ok(Map.of("status", "completed", "userId", id));
    }

    /**
     * V13 Phase 2 — TESTER 역할 부여/해제.
     * ADMIN이 일반 사용자의 roles 목록을 직접 지정한다.
     * 허용 역할: USER, TESTER (ADMIN은 AdminRoleAssigner가 관리하므로 여기서 변경 불가).
     */
    @PatchMapping("/{id}/roles")
    @Operation(summary = "사용자 역할 변경 (V13)", description = "USER·TESTER 역할을 지정한다. ADMIN 역할은 변경 불가(보존됨). 잘못된 역할 지정 시 400 반환.")
    @ApiResponse(responseCode = "200", description = "변경된 역할 목록 반환")
    @ApiResponse(responseCode = "400", description = "허용되지 않는 역할 (INVALID_ROLE)")
    @ApiResponse(responseCode = "401", description = "인증 필요")
    @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음")
    @ApiResponse(responseCode = "404", description = "사용자 없음 (USER_NOT_FOUND)")
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
