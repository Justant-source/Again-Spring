package com.againspring.security.crypto;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Loads {@code encrypted_secret} into the Environment before bean binding.
 *
 * <p>Requires bootstrap env: {@code DB_URL}/{@code spring.datasource.*} + {@link
 * SecretMasterKey#ENV_NAME}. If the master key or table is missing, skips silently so first-boot
 * Flyway + seed can run with plaintext env still present.
 */
@Order(Ordered.LOWEST_PRECEDENCE)
public class EncryptedSecretEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final Logger log =
            LoggerFactory.getLogger(EncryptedSecretEnvironmentPostProcessor.class);
    public static final String PROPERTY_SOURCE_NAME = "encryptedSecretVault";

    @Override
    public void postProcessEnvironment(
            ConfigurableEnvironment environment, SpringApplication application) {
        String masterRaw = firstNonBlank(environment, SecretMasterKey.ENV_NAME);
        if (masterRaw == null) {
            log.debug("Skip encrypted secret load: {} unset", SecretMasterKey.ENV_NAME);
            return;
        }

        String url =
                firstNonBlank(
                        environment,
                        "DB_URL",
                        "spring.datasource.url");
        String user =
                firstNonBlank(
                        environment,
                        "DB_USER",
                        "spring.datasource.username");
        String password =
                firstNonBlank(
                        environment,
                        "DB_PASSWORD",
                        "spring.datasource.password");
        if (url == null || user == null) {
            log.warn("Skip encrypted secret load: datasource URL/user missing");
            return;
        }

        SecretKey key;
        try {
            key = SecretMasterKey.fromBase64(masterRaw);
        } catch (RuntimeException e) {
            log.error("Invalid {}: {}", SecretMasterKey.ENV_NAME, e.getMessage());
            return;
        }

        Map<String, Object> props = new HashMap<>();
        try {
            Class.forName("org.mariadb.jdbc.Driver");
            try (Connection conn = DriverManager.getConnection(url, user, password == null ? "" : password);
                    PreparedStatement ps =
                            conn.prepareStatement("SELECT secret_key, enc_blob FROM encrypted_secret");
                    ResultSet rs = ps.executeQuery()) {
                int n = 0;
                while (rs.next()) {
                    String secretKey = rs.getString(1);
                    String encBlob = rs.getString(2);
                    String plain =
                            new String(AesGcmCipher.decrypt(encBlob, key), StandardCharsets.UTF_8);
                    applySecret(props, secretKey, plain);
                    n++;
                }
                if (n > 0) {
                    environment
                            .getPropertySources()
                            .addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, props));
                    // EPP runs before logging is fully configured — stderr is intentional.
                    System.err.println(
                            "[encryptedSecretVault] Loaded " + n + " secrets into Environment");
                    log.info("Loaded {} encrypted secrets into Environment", n);
                } else {
                    System.err.println("[encryptedSecretVault] table empty — using env/defaults");
                    log.info("encrypted_secret table empty — using env/defaults");
                }
            }
        } catch (Exception e) {
            // Table may not exist yet (pre-Flyway). Keep bootable.
            System.err.println(
                    "[encryptedSecretVault] load skipped: " + e.getClass().getSimpleName() + ": "
                            + e.getMessage());
            log.warn(
                    "Could not load encrypted_secret (ok on first migrate): {}",
                    e.getMessage());
        }
    }

    private static void applySecret(Map<String, Object> props, String secretKey, String plain) {
        String[] aliases = SecretVaultKeys.propertyAliases().get(secretKey);
        if (aliases != null) {
            for (String a : aliases) {
                props.put(a, plain);
            }
            return;
        }
        if (secretKey.startsWith(SecretVaultKeys.GITHUB_PAT_PREFIX)) {
            props.put(secretKey, plain);
            return;
        }
        props.put(secretKey, plain);
    }

    private static String firstNonBlank(ConfigurableEnvironment env, String... names) {
        for (String name : names) {
            String v = env.getProperty(name);
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }
}
