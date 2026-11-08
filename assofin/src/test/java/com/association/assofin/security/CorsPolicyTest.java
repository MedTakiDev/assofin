package com.association.assofin.security;

import com.association.assofin.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The CORS policy the browser sees from the front end's origin. Least privilege: the JWT travels in
 * an HttpOnly cookie, so no Authorization header is allowed or exposed. Uses the same configuration
 * as the other security tests, so Spring reuses their context.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class CorsPolicyTest {

    private static final String FRONT_END = "http://localhost:4200";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void preflightForTheHeadersTheFrontEndSendsIsAllowed() throws Exception {
        mockMvc.perform(preflight("Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRONT_END))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, containsStringIgnoringCase("Content-Type")))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    @Test
    void preflightAskingForAnAuthorizationHeaderIsRefused() throws Exception {
        mockMvc.perform(preflight("Authorization"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void aCrossOriginResponseExposesNoHeaders() throws Exception {
        mockMvc.perform(post("/auth/logout").header(HttpHeaders.ORIGIN, FRONT_END))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRONT_END))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS));
    }

    private static MockHttpServletRequestBuilder preflight(String requestedHeader) {
        return options("/auth/login")
                .header(HttpHeaders.ORIGIN, FRONT_END)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, requestedHeader);
    }
}
