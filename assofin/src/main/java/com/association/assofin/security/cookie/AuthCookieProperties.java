package com.association.assofin.security.cookie;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.StringUtils;

import java.util.Set;

/**
 * Attributes of the HttpOnly cookie that carries the JWT.
 *
 * <p>The defaults are the production-safe values: an environment that sets nothing gets a
 * {@code Secure} cookie, and only {@code application-dev.yml} opts out (local HTTP has no TLS).
 *
 * <p>The lifetime is deliberately not configurable here: it always comes from
 * {@code application.security.jwt.expiration}, see {@link AuthCookieService}.
 *
 * <p>An invalid combination stops the application at startup. A browser rejects such a cookie
 * silently: login answers 2xx, no cookie is stored, and the failure is very hard to diagnose.
 */
@ConfigurationProperties(prefix = "application.security.cookie")
public record AuthCookieProperties(
        @DefaultValue("access_token") String name,
        @DefaultValue("true") boolean secure,
        @DefaultValue("Strict") String sameSite,
        @DefaultValue("/") String path,
        @Nullable String domain
) {

    private static final String HOST_PREFIX = "__Host-";
    private static final Set<String> SAME_SITE_VALUES = Set.of("Strict", "Lax", "None");

    public AuthCookieProperties {
        if (!StringUtils.hasText(name)) {
            throw new IllegalArgumentException("application.security.cookie.name must not be blank");
        }
        if (!SAME_SITE_VALUES.contains(sameSite)) {
            throw new IllegalArgumentException(
                    "application.security.cookie.same-site must be one of " + SAME_SITE_VALUES + ", was: " + sameSite);
        }
        if ("None".equals(sameSite) && !secure) {
            throw new IllegalArgumentException("application.security.cookie: SameSite=None requires secure=true");
        }

        // An empty env var must mean "no Domain attribute", not an empty one.
        domain = StringUtils.hasText(domain) ? domain : null;

        if (name.startsWith(HOST_PREFIX) && (!secure || !"/".equals(path) || domain != null)) {
            throw new IllegalArgumentException(
                    "application.security.cookie: the " + HOST_PREFIX + " prefix requires secure=true, path=/ and no domain");
        }
    }
}
