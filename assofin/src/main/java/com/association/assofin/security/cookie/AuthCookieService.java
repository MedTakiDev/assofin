package com.association.assofin.security.cookie;

import com.association.assofin.security.jwt.JwtProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.web.util.WebUtils;

import java.time.Duration;

/**
 * The only place that knows how the authentication cookie is written and read.
 * Nothing else in the application should mention its name or its attributes.
 */
@Service
@RequiredArgsConstructor
public class AuthCookieService {

    private final AuthCookieProperties properties;
    private final JwtProperties jwtProperties;

    /** Cookie that stores the JWT. It expires together with the token it carries. */
    public ResponseCookie createAccessTokenCookie(String jwt) {
        return baseCookie(jwt)
                .maxAge(Duration.ofMillis(jwtProperties.getExpiration()))
                .build();
    }

    /** Cookie that makes the browser delete the authentication cookie immediately. */
    public ResponseCookie createClearingCookie() {
        return baseCookie("")
                .maxAge(Duration.ZERO)
                .build();
    }

    /** The JWT sent by the browser, or {@code null} when the request has no authentication cookie. */
    public @Nullable String extractAccessToken(HttpServletRequest request) {
        Cookie cookie = WebUtils.getCookie(request, properties.name());
        return cookie != null ? cookie.getValue() : null;
    }

    /**
     * Attributes shared by both cookies. A browser identifies a cookie by name, domain and path, so a
     * clearing cookie only works if it matches the original: building both here guarantees that.
     */
    private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
        return ResponseCookie.from(properties.name(), value)
                .httpOnly(true)                  // not readable from JavaScript (XSS cannot steal it)
                .secure(properties.secure())     // only sent over HTTPS
                .sameSite(properties.sameSite()) // not sent on cross-site requests (CSRF mitigation)
                .path(properties.path())
                .domain(properties.domain());    // null = host-only cookie
    }
}
