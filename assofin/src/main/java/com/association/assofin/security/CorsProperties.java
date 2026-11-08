package com.association.assofin.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * CORS settings. The origins have no default: every environment must name the front end it trusts
 * (env {@code CORS_ALLOWED_ORIGINS}, comma-separated).
 *
 * <p>Least privilege: only the headers the API really uses. The JWT travels in an HttpOnly cookie,
 * so no {@code Authorization} header is allowed or exposed. Exposed headers stay empty until
 * LOG-01 adds {@code X-Request-Id}.
 */
@Validated
@ConfigurationProperties(prefix = "application.cors")
public record CorsProperties(
        @NotEmpty(message = "is required (env CORS_ALLOWED_ORIGINS)") List<@NotBlank String> allowedOrigins,
        @DefaultValue({"GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"}) List<String> allowedMethods,
        @DefaultValue({"Content-Type", "Accept", "X-Requested-With"}) List<String> allowedHeaders,
        @DefaultValue List<String> exposedHeaders,
        @DefaultValue("3600") long maxAge
) {

    public CorsProperties {
        // Cookies are sent cross-origin (allowCredentials), so a wildcard would trust every website.
        if (allowedOrigins != null && allowedOrigins.stream().anyMatch(origin -> origin.contains("*"))) {
            throw new IllegalArgumentException("application.cors.allowed-origins must list exact origins, not wildcards");
        }
    }
}
