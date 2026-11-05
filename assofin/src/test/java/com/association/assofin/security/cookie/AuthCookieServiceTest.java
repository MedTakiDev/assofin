package com.association.assofin.security.cookie;

import com.association.assofin.security.jwt.JwtProperties;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.web.MockHttpServletRequest;

import java.io.IOException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks the cookie each profile really produces. The attributes are read from the real
 * {@code application-dev.yml} and {@code application-prod.yml}, so removing or changing a setting in
 * those files fails here. Plain unit test: only the cookie settings are bound, nothing else starts.
 */
class AuthCookieServiceTest {

    private static final long ONE_DAY_MS = 86_400_000L;
    private static final String JWT = "header.payload.signature";

    // ---------------------------------------------------------------------------------
    // Secure by default: an environment with no cookie configuration
    // ---------------------------------------------------------------------------------

    @Test
    void withoutConfigurationTheCookieIsSecure() throws IOException {
        ResponseCookie cookie = serviceFor(null, ONE_DAY_MS).createAccessTokenCookie(JWT);

        assertThat(cookie.getName()).isEqualTo("access_token");
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Strict");
        assertThat(cookie.getPath()).isEqualTo("/");
        assertThat(cookie.getDomain()).isNull();
    }

    // ---------------------------------------------------------------------------------
    // Development profile
    // ---------------------------------------------------------------------------------

    @Test
    void devProfileDisablesOnlyTheSecureFlag() throws IOException {
        ResponseCookie cookie = serviceFor("application-dev.yml", ONE_DAY_MS).createAccessTokenCookie(JWT);

        assertThat(cookie.getName()).isEqualTo("access_token");
        assertThat(cookie.isSecure()).isFalse();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Strict");
    }

    // ---------------------------------------------------------------------------------
    // Production profile
    // ---------------------------------------------------------------------------------

    @Test
    void prodProfileUsesASecureHostPrefixedCookie() throws IOException {
        ResponseCookie cookie = serviceFor("application-prod.yml", ONE_DAY_MS).createAccessTokenCookie(JWT);

        assertThat(cookie.getName()).isEqualTo("__Host-access_token");
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Strict");
        assertThat(cookie.getPath()).isEqualTo("/");
        assertThat(cookie.getDomain()).isNull();
    }

    // ---------------------------------------------------------------------------------
    // Lifetime follows the JWT
    // ---------------------------------------------------------------------------------

    @Test
    void cookieLifetimeIsTheJwtLifetime() throws IOException {
        ResponseCookie oneHour = serviceFor(null, 3_600_000L).createAccessTokenCookie(JWT);
        ResponseCookie oneDay = serviceFor(null, ONE_DAY_MS).createAccessTokenCookie(JWT);

        assertThat(oneHour.getMaxAge()).isEqualTo(Duration.ofHours(1));
        assertThat(oneDay.getMaxAge()).isEqualTo(Duration.ofDays(1));
    }

    @Test
    void accessTokenCookieCarriesTheJwt() throws IOException {
        assertThat(serviceFor(null, ONE_DAY_MS).createAccessTokenCookie(JWT).getValue()).isEqualTo(JWT);
    }

    // ---------------------------------------------------------------------------------
    // Clearing cookie: must match the original, or the browser keeps it
    // ---------------------------------------------------------------------------------

    @Test
    void clearingCookieExpiresImmediatelyAndHasNoValue() throws IOException {
        ResponseCookie clearing = serviceFor(null, ONE_DAY_MS).createClearingCookie();

        assertThat(clearing.getMaxAge()).isEqualTo(Duration.ZERO);
        assertThat(clearing.getValue()).isEmpty();
    }

    @Test
    void clearingCookieMatchesTheAccessCookieInDev() throws IOException {
        assertClearingMatchesAccessCookie(serviceFor("application-dev.yml", ONE_DAY_MS));
    }

    @Test
    void clearingCookieMatchesTheAccessCookieInProd() throws IOException {
        assertClearingMatchesAccessCookie(serviceFor("application-prod.yml", ONE_DAY_MS));
    }

    @Test
    void clearingCookieMatchesAConfiguredDomainAndPath() {
        AuthCookieProperties properties = new AuthCookieProperties("x", true, "Lax", "/api", "example.com");

        assertClearingMatchesAccessCookie(new AuthCookieService(properties, jwtProperties(ONE_DAY_MS)));
    }

    private static void assertClearingMatchesAccessCookie(AuthCookieService service) {
        ResponseCookie access = service.createAccessTokenCookie(JWT);
        ResponseCookie clearing = service.createClearingCookie();

        assertThat(clearing.getName()).isEqualTo(access.getName());
        assertThat(clearing.getDomain()).isEqualTo(access.getDomain());
        assertThat(clearing.getPath()).isEqualTo(access.getPath());
        assertThat(clearing.isSecure()).isEqualTo(access.isSecure());
        assertThat(clearing.isHttpOnly()).isEqualTo(access.isHttpOnly());
        assertThat(clearing.getSameSite()).isEqualTo(access.getSameSite());
    }

    // ---------------------------------------------------------------------------------
    // Reading the cookie back
    // ---------------------------------------------------------------------------------

    @Test
    void extractsTheTokenFromTheConfiguredCookie() throws IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("other", "x"), new Cookie("__Host-access_token", JWT));

        assertThat(serviceFor("application-prod.yml", ONE_DAY_MS).extractAccessToken(request)).isEqualTo(JWT);
    }

    @Test
    void ignoresACookieWithAnotherName() throws IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("token", JWT)); // the former cookie name

        assertThat(serviceFor(null, ONE_DAY_MS).extractAccessToken(request)).isNull();
    }

    @Test
    void returnsNullWhenTheRequestHasNoCookies() throws IOException {
        assertThat(serviceFor(null, ONE_DAY_MS).extractAccessToken(new MockHttpServletRequest())).isNull();
    }

    // ---------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------

    /** Builds the service from a real profile file, the same way Spring binds it at startup. */
    private static AuthCookieService serviceFor(String profileFile, long jwtExpirationMs) throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        if (profileFile != null) {
            new YamlPropertySourceLoader()
                    .load(profileFile, new ClassPathResource(profileFile))
                    .forEach(environment.getPropertySources()::addFirst);
        }
        ConfigurationPropertySources.attach(environment);

        AuthCookieProperties properties = Binder.get(environment)
                .bindOrCreate("application.security.cookie", AuthCookieProperties.class);

        return new AuthCookieService(properties, jwtProperties(jwtExpirationMs));
    }

    private static JwtProperties jwtProperties(long expirationMs) {
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setExpiration(expirationMs);
        return jwtProperties;
    }
}
