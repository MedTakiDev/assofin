package com.association.assofin.security;

import com.association.assofin.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The API docs switched on, which is what the dev profile runs. Nothing else may become public. */
@SpringBootTest(properties = "application.security.expose-api-docs=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ApiDocsExposedTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void openApiDocumentIsPublic() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/v3/api-docs/swagger-config",
            "/swagger-ui/index.html",
            "/swagger-ui/swagger-ui.css",        // proves the UI loads its assets without /webjars/**
            "/swagger-ui/swagger-ui-bundle.js"
    })
    void swaggerUiAndItsAssetsArePublic(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/role", "/users/me", "/ldap/anything", "/v2/api-docs", "/webjars/anything"})
    void everythingElseStaysProtected(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isUnauthorized());
    }
}
