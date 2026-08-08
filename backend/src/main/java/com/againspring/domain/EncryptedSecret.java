package com.againspring.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * AES-256-GCM encrypted application secret (non-marketing).
 * Plaintext never stored; master key is {@code AS_SECRET_MASTER_KEY} env only.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "encrypted_secret")
public class EncryptedSecret {

    @Id
    @Column(name = "secret_key", length = 128, nullable = false)
    private String secretKey;

    /** Base64(iv || ciphertext || tag) — see {@link com.againspring.security.crypto.AesGcmCipher}. */
    @Column(name = "enc_blob", nullable = false, columnDefinition = "TEXT")
    private String encBlob;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
