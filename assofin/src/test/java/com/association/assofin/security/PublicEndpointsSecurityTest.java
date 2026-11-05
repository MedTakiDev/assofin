package com.association.assofin.security;

import com.association.assofin.TestcontainersConfiguration;
import com.association.assofin.handler.BusinessErrorCodes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The public surface with the API docs switched off, which is what production runs. Uses the same
 * configuration as BootstrapInitializersTest, so Spring reuses its context.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class PublicEndpointsSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest
    @ValueSource(strings = {
            // API documentation, off by default
            "/v3/api-docs", "/v3/api-docs/swagger-config", "/swagger-ui.html", "/swagger-ui/index.html",
            // removed leftovers: they must not be public, even though nothing is mapped there
            "/ldap/anything", "/v2/api-docs", "/swagger-resources", "/swagger-resources/configuration/ui",
            "/configuration/ui", "/configuration/security", "/webjars/anything",
            // only the internal error dispatch is permitted, not a direct request
            "/error"
    })
    void anonymousRequestIsRejected(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.businessCode").value(BusinessErrorCodes.AUTHENTICATION_REQUIRED.getCode()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/v3/api-docs", "/swagger-ui/index.html"})
    @WithMockUser
    void theDocsDoNotExistEvenForAnAuthenticatedUser(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isNotFound());
    }

    @Test
    void loginStaysPublic() throws Exception {
        // BAD_CREDENTIALS comes from the login itself: the request got past the security rules.
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "nobody@test.local", "password": "a-wrong-password"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.businessCode").value(BusinessErrorCodes.BAD_CREDENTIALS.getCode()));
    }

    @Test
    void logoutStaysPublic() throws Exception {
        mockMvc.perform(post("/auth/logout"))
                .andExpect(status().isOk());
    }
}
