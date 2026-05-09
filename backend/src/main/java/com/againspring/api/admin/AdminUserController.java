package com.againspring.api.admin;

import com.againspring.domain.User;
import com.againspring.repository.UserRepository;
import com.againspring.service.retention.UserDeletionService;
import lombok.RequiredArgsConstructor;
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

    @GetMapping("/search")
    public ResponseEntity<List<User>> search(@RequestParam String q) {
        List<User> users = userRepository
                .findByNicknameContainingIgnoreCaseOrEmailContainingIgnoreCaseAndDeletedAtIsNull(q, q);
        return ResponseEntity.ok(users);
    }

    @DeleteMapping("/{id}/data")
    public ResponseEntity<Map<String, String>> deleteUserData(@PathVariable String id) {
        userDeletionService.scheduleAnonymization(id);
        return ResponseEntity.ok(Map.of("status", "scheduled", "userId", id));
    }
}
