package com.association.assofin.security.cookie;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Rules checked at startup. Browsers reject an invalid cookie silently, so each combination below
 * must stop the application instead of producing a login that stores no cookie.
 * Plain unit test: no Spring context, no database.
 */
class AuthCookiePropertiesTest {

    @ParameterizedTest(name = "{0}")
    @CsvSource(nullValues = "null", value = {
            // case,                     name,     secure, sameSite, path, domain,      expected message
            "blank name,                 '  ',     true,   Strict,   /,    null,        must not be blank",
            "unknown SameSite,           x,        true,   Medium,   /,    null,        must be one of",
            "SameSite is case sensitive, x,        true,   strict,   /,    null,        must be one of",
            "SameSite=None over HTTP,    x,        false,  None,     /,    null,        SameSite=None requires secure=true",
            "__Host- without Secure,     __Host-x, false,  Strict,   /,    null,        __Host- prefix requires",
            "__Host- with a path,        __Host-x, true,   Strict,   /api, null,        __Host- prefix requires",
            "__Host- with a domain,      __Host-x, true,   Strict,   /,    example.com, __Host- prefix requires",
    })
    void invalidCombinationIsRejected(String description, String name, boolean secure, String sameSite,
                                      String path, String domain, String expectedMessage) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new AuthCookieProperties(name, secure, sameSite, path, domain))
                .withMessageContaining(expectedMessage);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(nullValues = "null", value = {
            "plain development cookie,   access_token,        false, Strict, /,    null",
            "cookie restricted to /api,  access_token,        true,  Lax,    /api, example.com",
            "SameSite=None over HTTPS,   access_token,        true,  None,   /,    null",
            "production __Host- cookie,  __Host-access_token, true,  Strict, /,    null",
    })
    void validCombinationIsAccepted(String description, String name, boolean secure, String sameSite,
                                    String path, String domain) {
        assertThatCode(() -> new AuthCookieProperties(name, secure, sameSite, path, domain))
                .doesNotThrowAnyException();
    }

    @Test
    void blankDomainMeansNoDomainAttribute() {
        // e.g. COOKIE_DOMAIN= left empty in an .env file
        assertThat(new AuthCookieProperties("x", true, "Strict", "/", "   ").domain()).isNull();
    }

    @Test
    void blankDomainIsAllowedWithTheHostPrefix() {
        assertThatCode(() -> new AuthCookieProperties("__Host-x", true, "Strict", "/", ""))
                .doesNotThrowAnyException();
    }
}
