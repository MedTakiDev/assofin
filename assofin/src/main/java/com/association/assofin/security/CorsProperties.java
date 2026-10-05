package com.association.assofin.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Setter
@Getter
@Configuration
@ConfigurationProperties(prefix = "application.cors")
public class CorsProperties {
    // the assigned values are just a fallback values
    private List<String> allowedOrigins = List.of("http://localhost:4200");
    private List<String> allowedMethods = List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH");
    private List<String> allowedHeaders = List.of("Authorization", "Content-Type", "X-Requested-With", "Accept");
    private List<String> exposedHeaders = List.of("Authorization");
    private Long maxAge = 3600L;
}