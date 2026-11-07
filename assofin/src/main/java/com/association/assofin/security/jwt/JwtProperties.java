package com.association.assofin.security.jwt;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * JWT settings. Validated at startup, so a missing secret stops the application immediately
 * instead of failing at the first login.
 *
 * @param secretKey  Base64 HMAC key (env {@code JWT_SECRET_KEY})
 * @param expiration token lifetime, e.g. {@code 24h} or {@code 15m} (env {@code JWT_EXPIRATION});
 *                   a plain number is read as milliseconds
 */
@Validated
@ConfigurationProperties(prefix = "application.security.jwt")
public record JwtProperties(
        @NotBlank(message = "is required (env JWT_SECRET_KEY)") String secretKey,
        @NotNull(message = "is required (env JWT_EXPIRATION)") Duration expiration
) {

    public JwtProperties {
        if (expiration != null && (expiration.isZero() || expiration.isNegative())) {
            throw new IllegalArgumentException("application.security.jwt.expiration must be positive, was " + expiration);
        }
    }

    /** Never print the key, e.g. in a startup log or an error report. */
    @Override
    public String toString() {
        return "JwtProperties[secretKey=***, expiration=" + expiration + "]";
    }
}
