package com.againspring.api;

import com.againspring.service.AdminTestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/admin/test")
@Profile("dev")
@RequiredArgsConstructor
public class AdminTestController {

    private final AdminTestService adminTestService;

    @PostMapping("/reset")
    public ResponseEntity<Map<String, Integer>> resetTestData() {
        log.info("Test data reset requested");
        Map<String, Integer> deleted = adminTestService.resetTestUserData();
        log.info("Test data reset complete: {}", deleted);
        return ResponseEntity.ok(deleted);
    }

    @PostMapping("/sessions/{sessionId}/terminate")
    public ResponseEntity<Void> terminateSession(@PathVariable String sessionId) {
        adminTestService.terminateSession(sessionId);
        return ResponseEntity.ok().build();
    }
}
