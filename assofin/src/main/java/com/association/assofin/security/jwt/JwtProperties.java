package com.association.assofin.security.jwt;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "application.security.jwt") // Adjusted to match your nested YAML structure
@Getter
@Setter
public class JwtProperties {
    private String secretKey;
    private long expiration;
}