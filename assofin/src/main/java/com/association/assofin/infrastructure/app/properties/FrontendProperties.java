package com.association.assofin.infrastructure.app.properties;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "application.frontend")
public record FrontendProperties(
        String activationUrl
) {
}