package com.againspring.api.internal;

import com.againspring.service.ai.SyntheticUserService;
import com.againspring.service.ai.SyntheticUserService.PersonaUpsertRequest;
import com.againspring.service.ai.SyntheticUserService.PersonaUpsertResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * orchestrator 전용 내부 API. JWT 미사용, AI_USER_INTERNAL_TOKEN Bearer.
 * Doc-Sync: docs/shared/50-api/rest-spec.md §내부
 */
@RestController
@RequestMapping("/api/internal/ai-user")
@RequiredArgsConstructor
public class AiUserInternalController {
    private final AiUserInternalTokenGuard guard;
    private final SyntheticUserService syntheticUserService;

    @PostMapping("/personas/upsert")
    public ResponseEntity<PersonaUpsertResponse> upsert(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth,
            @RequestBody PersonaUpsertRequest req) {
        if (!guard.isAuthorized(auth)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(syntheticUserService.upsert(req));
    }

    public record RotateRequest(String password) {}

    @PostMapping("/personas/rotate-password")
    public ResponseEntity<Map<String, Integer>> rotate(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth,
            @RequestBody RotateRequest req) {
        if (!guard.isAuthorized(auth)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(Map.of("updated", syntheticUserService.rotatePassword(req.password())));
    }
}
