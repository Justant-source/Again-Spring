package com.againspring.api.admin;

import com.againspring.annotation.Auditable;
import com.againspring.api.dto.request.SuspendUserRequest;
import com.againspring.api.dto.response.AdminUserListResponse;
import com.againspring.common.exception.BusinessException;
import com.againspring.domain.User;
import com.againspring.repository.UserRepository;
import com.againspring.service.admin.UserAnonymizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.io.PrintWriter;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 관리자 사용자 관리 컨트롤러
 * 사용자 정지, 강제 로그아웃, 익명화, CSV 내보내기
 */
@RestController
@RequestMapping("/api/admin/users/manage")
@RequiredArgsConstructor
@Tag(name = "Admin — User Management", description = "사용자 정지·강제로그아웃·익명화 (ADMIN 전용)")
@SecurityRequirement(name = "bearer-jwt")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserManagementController {

    private final UserRepository userRepository;
    private final UserAnonymizationService userAnonymizationService;

    /**
     * POST /api/admin/users/manage/{userId}/suspend
     * 사용자 정지
     */
    @PostMapping("/{userId}/suspend")
    @Operation(summary = "사용자 정지", description = "사용자를 정지한다. suspendedUntil이 null이면 무기한 정지.")
    @ApiResponse(responseCode = "200", description = "정지 완료")
    @ApiResponse(responseCode = "404", description = "사용자 없음")
    @Auditable(action = "USER_SUSPEND", targetType = "USER", targetId = "#userId")
    public ResponseEntity<Map<String, Object>> suspendUser(
            @PathVariable String userId,
            @RequestBody SuspendUserRequest req) {

        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "사용자를 찾을 수 없습니다.", 404));

        user.setStatus("SUSPENDED");
        user.setSuspendedReason(req.getReason());

        if (req.getSuspendedUntil() != null && !req.getSuspendedUntil().isEmpty()) {
            user.setSuspendedUntil(Instant.parse(req.getSuspendedUntil()));
        } else {
            user.setSuspendedUntil(null);
        }

        userRepository.save(user);

        return ResponseEntity.ok(Map.of(
                "userId", userId,
                "status", "SUSPENDED",
                "suspendedUntil", user.getSuspendedUntil(),
                "suspendedReason", user.getSuspendedReason()
        ));
    }

    /**
     * POST /api/admin/users/manage/{userId}/unsuspend
     * 사용자 정지 해제
     */
    @PostMapping("/{userId}/unsuspend")
    @Operation(summary = "사용자 정지 해제", description = "정지된 사용자를 활성화한다.")
    @ApiResponse(responseCode = "200", description = "해제 완료")
    @ApiResponse(responseCode = "404", description = "사용자 없음")
    @Auditable(action = "USER_UNSUSPEND", targetType = "USER", targetId = "#userId")
    public ResponseEntity<Map<String, Object>> unsuspendUser(@PathVariable String userId) {

        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "사용자를 찾을 수 없습니다.", 404));

        user.setStatus("ACTIVE");
        user.setSuspendedUntil(null);
        user.setSuspendedReason(null);

        userRepository.save(user);

        return ResponseEntity.ok(Map.of(
                "userId", userId,
                "status", "ACTIVE"
        ));
    }

    /**
     * POST /api/admin/users/manage/{userId}/force-logout
     * 사용자 강제 로그아웃 (토큰 무효화)
     */
    @PostMapping("/{userId}/force-logout")
    @Operation(summary = "사용자 강제 로그아웃", description = "사용자의 발급된 모든 토큰을 무효화한다.")
    @ApiResponse(responseCode = "200", description = "강제 로그아웃 완료")
    @ApiResponse(responseCode = "404", description = "사용자 없음")
    @Auditable(action = "USER_FORCE_LOGOUT", targetType = "USER", targetId = "#userId")
    public ResponseEntity<Map<String, Object>> forceLogout(@PathVariable String userId) {

        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "사용자를 찾을 수 없습니다.", 404));

        user.setTokensInvalidatedAt(Instant.now());
        userRepository.save(user);

        return ResponseEntity.ok(Map.of(
                "userId", userId,
                "tokensInvalidatedAt", user.getTokensInvalidatedAt()
        ));
    }

    /**
     * PATCH /api/admin/users/manage/{userId}/nickname
     * 닉네임 강제 변경
     */
    @PatchMapping("/{userId}/nickname")
    @Operation(summary = "닉네임 강제 변경", description = "관리자가 사용자 닉네임을 강제 변경한다.")
    @ApiResponse(responseCode = "200", description = "변경 완료")
    @ApiResponse(responseCode = "400", description = "닉네임 중복 또는 형식 오류")
    @ApiResponse(responseCode = "404", description = "사용자 없음")
    @Auditable(action = "USER_NICKNAME_CHANGE", targetType = "USER", targetId = "#userId")
    public ResponseEntity<Map<String, Object>> changeNickname(
            @PathVariable String userId,
            @RequestBody Map<String, String> body) {

        String newNickname = body.get("nickname");
        if (newNickname == null || newNickname.trim().isEmpty()) {
            throw new BusinessException("INVALID_NICKNAME", "닉네임을 입력해주세요.", 400);
        }
        newNickname = newNickname.trim();

        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "사용자를 찾을 수 없습니다.", 404));

        if (userRepository.existsByNicknameAndDeletedAtIsNull(newNickname)) {
            throw new BusinessException("NICKNAME_DUPLICATE", "이미 사용 중인 닉네임입니다.", 400);
        }

        String oldNickname = user.getNickname();
        user.setNickname(newNickname);
        userRepository.save(user);

        return ResponseEntity.ok(Map.of(
                "userId", userId,
                "oldNickname", oldNickname != null ? oldNickname : "",
                "newNickname", newNickname
        ));
    }

    /**
     * GET /api/admin/users/export
     * 모든 사용자를 CSV로 내보내기
     */
    @GetMapping("/export")
    @Operation(summary = "사용자 CSV 내보내기", description = "모든 사용자(탈퇴 제외)를 CSV로 스트림한다.")
    @ApiResponse(responseCode = "200", description = "CSV 스트림")
    public void exportUsersAsCSV(HttpServletResponse response) {
        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=\"users.csv\"");
        response.setCharacterEncoding("UTF-8");

        try (PrintWriter writer = response.getWriter()) {
            // CSV 헤더
            writer.println("ID,이메일,닉네임,역할,상태,게스트,가입일");

            // 모든 사용자 조회 (탈퇴 제외)
            int page = 0;
            int pageSize = 100;

            while (true) {
                Pageable pageable = PageRequest.of(page, pageSize);
                Page<User> users = userRepository.findByDeletedAtIsNullOrderByCreatedAtDesc(pageable);

                for (User user : users.getContent()) {
                    String roles = user.getRoles() != null ? String.join("|", user.getRoles()) : "";
                    String csvLine = String.format("%s,%s,%s,%s,%s,%s,%s",
                            escapeCsvField(user.getId()),
                            escapeCsvField(user.getEmail()),
                            escapeCsvField(user.getNickname()),
                            escapeCsvField(roles),
                            escapeCsvField(user.getStatus()),
                            user.isGuest(),
                            user.getCreatedAt() != null ? user.getCreatedAt().toString() : ""
                    );
                    writer.println(csvLine);
                }

                if (users.isLast()) {
                    break;
                }
                page++;
            }

        } catch (Exception e) {
            response.setStatus(500);
        }
    }

    /**
     * CSV 필드 이스케이프 (큰따옴표로 감싸기, 내부 큰따옴표 이중화)
     */
    private String escapeCsvField(String field) {
        if (field == null) {
            return "";
        }
        if (field.contains(",") || field.contains("\"") || field.contains("\n")) {
            return "\"" + field.replace("\"", "\"\"") + "\"";
        }
        return field;
    }
}
