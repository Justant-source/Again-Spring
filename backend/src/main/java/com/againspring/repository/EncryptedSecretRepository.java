package com.againspring.repository;

import com.againspring.domain.EncryptedSecret;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EncryptedSecretRepository extends JpaRepository<EncryptedSecret, String> {}
