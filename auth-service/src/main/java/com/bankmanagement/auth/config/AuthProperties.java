package com.bankmanagement.auth.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(Jwt jwt, Lockout lockout) {

    /**
     * @param privateKey PKCS#8 RSA private key (PEM, or base64 of the PEM). Required in production.
     * @param allowEphemeralKey dev only: generate a throw-away key when none is configured
     */
    public record Jwt(@NotBlank String issuer,
                      Duration accessTokenTtl,
                      Duration refreshTokenTtl,
                      Duration impersonationTtl,
                      String privateKey,
                      boolean allowEphemeralKey) {
    }

    public record Lockout(@Min(3) int maxAttempts, Duration baseLock, Duration maxLock) {
    }
}
