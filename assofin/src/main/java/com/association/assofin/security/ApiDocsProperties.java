package com.association.assofin.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Controls whether the API documentation (OpenAPI JSON and Swagger UI) is available.
 *
 * <p>The default is {@code false}, so an environment that sets nothing exposes nothing. Only
 * {@code application-dev.yml} turns it on.
 *
 * <p>The same property also drives {@code springdoc.api-docs.enabled} and
 * {@code springdoc.swagger-ui.enabled} in {@code application.yml}. When it is off, the endpoints do
 * not exist at all, so there is one switch and the security rules can never disagree with springdoc.
 *
 * @param exposeApiDocs {@code true} to make the documentation endpoints public
 */
@ConfigurationProperties(prefix = "application.security")
public record ApiDocsProperties(@DefaultValue("false") boolean exposeApiDocs) {
}
