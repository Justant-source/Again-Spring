package com.againspring.api.admin;

import com.againspring.security.crypto.SecretVaultService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Write-only access to the {@code encrypted_secret} vault. Never returns plaintext — only
 * presence booleans. ADMIN 전용.
 */
@RestController
@RequestMapping("/api/admin/secrets")
@RequiredArgsConstructor
@Tag(name = "Admin — Secrets", description = "encrypted_secret vault 쓰기 전용 (ADMIN 전용, 평문 조회 불가)")
public class AdminSecretsController {

    private final SecretVaultService secretVaultService;

    @GetMapping
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(summary = "vault 키 존재 여부 목록", description = "저장된 시크릿 키와 존재 여부만 반환 (평문 없음).")
    public ResponseEntity<Map<String, Boolean>> listPresence() {
        return ResponseEntity.ok(secretVaultService.listPresence());
    }

    public record PutSecretRequest(String value) {}

    @PostMapping("/{key}")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(summary = "vault 시크릿 저장/갱신", description = "AES-GCM으로 암호화해 저장한다. 응답에 평문을 포함하지 않는다.")
    public ResponseEntity<Map<String, Object>> putSecret(
            @PathVariable String key, @RequestBody PutSecretRequest request) {
        secretVaultService.putPlain(key, request.value());
        return ResponseEntity.ok(Map.of("key", key, "status", "saved"));
    }
}
