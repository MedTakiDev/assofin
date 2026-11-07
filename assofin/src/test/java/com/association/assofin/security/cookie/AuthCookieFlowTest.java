package com.association.assofin.security.cookie;

import com.association.assofin.TestcontainersConfiguration;
import com.association.assofin.security.jwt.JwtProperties;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Checks the real Set-Cookie headers at the three places that send them (login, logout, the
 * authentication entry point) and the filter that reads the cookie back, through the whole
 * application. Uses the same configuration as BootstrapInitializersTest, so Spring reuses its context.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AuthCookieFlowTest {

    private static final String ADMIN_EMAIL = "admin@test.local";
    private static final String ADMIN_PASSWORD = "test-admin-password";

    @Autowired private MockMvc mockMvc;
    @Autowired private AuthCookieProperties authCookieProperties;
    @Autowired private JwtProperties jwtProperties;

    // ---------------------------------------------------------------------------------
    // Written at login
    // ---------------------------------------------------------------------------------

    @Test
    void loginSetsAHardenedCookieThatLivesAsLongAsTheJwt() throws Exception {
        String setCookie = login().getHeader(HttpHeaders.SET_COOKIE);

        long jwtLifetimeInSeconds = jwtProperties.expiration().toSeconds();
        assertThat(setCookie)
                .startsWith(authCookieProperties.name() + "=")
                .contains("Max-Age=" + jwtLifetimeInSeconds)
                .contains("Path=/")
                .contains("Secure")   // the test profile sets nothing, so this is the default
                .contains("HttpOnly")
                .contains("SameSite=Strict");
    }

    @Test
    void loginDoesNotExposeTheJwtInTheResponseBody() throws Exception {
        MockHttpServletResponse response = login();

        String jwt = response.getCookie(authCookieProperties.name()).getValue();
        assertThat(jwt).isNotBlank();
        assertThat(response.getContentAsString()).doesNotContain(jwt);
    }

    // ---------------------------------------------------------------------------------
    // Read by JwtAuthenticationFilter
    // ---------------------------------------------------------------------------------

    @Test
    void theLoginCookieAuthenticatesTheNextRequest() throws Exception {
        Cookie cookie = login().getCookie(authCookieProperties.name());

        mockMvc.perform(get("/role").param("page", "0").param("size", "20").cookie(cookie))
                .andExpect(status().isOk());
    }

    @Test
    void aValidJwtUnderTheFormerCookieNameIsIgnored() throws Exception {
        String jwt = login().getCookie(authCookieProperties.name()).getValue();

        mockMvc.perform(get("/role").param("page", "0").param("size", "20").cookie(new Cookie("token", jwt)))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------------------------
    // Cleared at logout and by the entry point
    // ---------------------------------------------------------------------------------

    @Test
    void logoutClearsTheCookie() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(post("/auth/logout"))
                .andExpect(status().isOk())
                .andReturn().getResponse();

        assertIsClearingCookie(response.getHeader(HttpHeaders.SET_COOKIE));
    }

    @Test
    void entryPointClearsTheCookieWhenTheJwtIsInvalid() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(get("/role")
                        .param("page", "0").param("size", "20")
                        .cookie(new Cookie(authCookieProperties.name(), "not-a-valid-jwt")))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse();

        assertIsClearingCookie(response.getHeader(HttpHeaders.SET_COOKIE));
    }

    /** Same attributes as the login cookie: a browser would otherwise keep the original. */
    private void assertIsClearingCookie(String setCookie) {
        assertThat(setCookie)
                .startsWith(authCookieProperties.name() + "=;")
                .contains("Max-Age=0")
                .contains("Path=/")
                .contains("Secure")
                .contains("HttpOnly")
                .contains("SameSite=Strict");
    }

    private MockHttpServletResponse login() throws Exception {
        return mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s"}
                                """.formatted(ADMIN_EMAIL, ADMIN_PASSWORD)))
                .andExpect(status().isAccepted())
                .andReturn().getResponse();
    }
}
