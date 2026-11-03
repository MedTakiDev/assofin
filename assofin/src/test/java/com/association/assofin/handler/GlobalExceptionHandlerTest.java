package com.association.assofin.handler;

import com.association.assofin.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import jakarta.mail.MessagingException;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contract test for the API error format: status, catalog code and body of every kind of
 * failure, and proof that no internal detail (SQL, SMTP, parser, exception text) reaches the client.
 *
 * <p>{@link ThrowingController} is a test-only controller that throws each exception type on demand.
 * It is registered with {@code @Import}: Spring Boot's TestTypeExcludeFilter deliberately keeps
 * classes nested in test classes out of component scanning.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, GlobalExceptionHandlerTest.ThrowingController.class})
@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionHandlerTest {

    private static final String SECRET = "SECRET-DETAIL";

    @Autowired
    private MockMvc mockMvc;

    // ---------------------------------------------------------------------------------
    // Catch-all and infrastructure errors
    // ---------------------------------------------------------------------------------

    @Test
    @WithMockUser
    void unexpectedExceptionReturnsGeneric500WithErrorIdThatIsLogged(CapturedOutput output) throws Exception {
        MvcResult result = mockMvc.perform(get("/test-errors/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.businessCode").value(BusinessErrorCodes.SERVER_ERROR.getCode()))
                .andExpect(content().string(not(containsString(SECRET))))
                .andReturn();

        String errorId = JsonPath.read(result.getResponse().getContentAsString(), "$.errorId");
        assertThatCode(() -> UUID.fromString(errorId)).doesNotThrowAnyException();
        assertThat(output).contains("Unhandled exception [errorId=" + errorId + "]");
    }

    @Test
    @WithMockUser
    void mailFailureReturns503WithoutSmtpDetails() throws Exception {
        mockMvc.perform(get("/test-errors/mail"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.businessCode").value(BusinessErrorCodes.MAIL_SERVICE_UNAVAILABLE.getCode()))
                .andExpect(jsonPath("$.errorId").exists())
                .andExpect(content().string(not(containsString(SECRET))));
    }

    @Test
    @WithMockUser
    void dataIntegrityViolationReturns409WithoutDatabaseDetails() throws Exception {
        mockMvc.perform(get("/test-errors/data-integrity"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.businessCode").value(BusinessErrorCodes.DATA_CONFLICT.getCode()))
                .andExpect(content().string(not(containsString(SECRET))));
    }

    // ---------------------------------------------------------------------------------
    // Spring MVC framework exceptions
    // ---------------------------------------------------------------------------------

    @Test
    @WithMockUser
    void unknownUrlReturns404() throws Exception {
        mockMvc.perform(get("/test-errors/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.businessCode").value(BusinessErrorCodes.ENDPOINT_NOT_FOUND.getCode()));
    }

    @Test
    void unknownUrlForAnonymousUserReturns401SoUrlsAreNotDisclosed() throws Exception {
        mockMvc.perform(get("/test-errors/does-not-exist"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void wrongHttpMethodReturns405WithAllowHeader() throws Exception {
        mockMvc.perform(get("/test-errors/body"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string(HttpHeaders.ALLOW, containsString("POST")))
                .andExpect(jsonPath("$.businessCode").value(BusinessErrorCodes.HTTP_METHOD_NOT_ALLOWED.getCode()));
    }

    @Test
    @WithMockUser
    void malformedJsonReturns400WithoutParserDetails() throws Exception {
        mockMvc.perform(post("/test-errors/body").contentType(MediaType.APPLICATION_JSON).content("{\"name\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.businessCode").value(BusinessErrorCodes.MALFORMED_REQUEST.getCode()))
                .andExpect(content().string(not(containsString("JSON parse error"))))
                .andExpect(content().string(not(containsString("Source:"))));
    }

    @Test
    @WithMockUser
    void unsupportedContentTypeReturns415() throws Exception {
        mockMvc.perform(post("/test-errors/body").contentType(MediaType.TEXT_PLAIN).content("hello"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.businessCode").value(BusinessErrorCodes.MEDIA_TYPE_NOT_SUPPORTED.getCode()));
    }

    @Test
    @WithMockUser
    void typeMismatchReturns400() throws Exception {
        mockMvc.perform(get("/test-errors/number/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.businessCode").value(BusinessErrorCodes.MALFORMED_REQUEST.getCode()));
    }

    // ---------------------------------------------------------------------------------
    // Validation
    // ---------------------------------------------------------------------------------

    @Test
    @WithMockUser
    void invalidBodyReturns400WithFieldErrorsAndNeverTheRejectedValue() throws Exception {
        mockMvc.perform(post("/test-errors/body").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"\", \"email\": \"" + SECRET + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.businessCode").value(BusinessErrorCodes.VALIDATION_FAILED.getCode()))
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.validationErrors").isArray())
                .andExpect(content().string(not(containsString(SECRET))));
    }

    @Test
    @WithMockUser
    void invalidRequestParamReturns400WithParameterName() throws Exception {
        mockMvc.perform(get("/test-errors/param").param("page", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.businessCode").value(BusinessErrorCodes.VALIDATION_FAILED.getCode()))
                .andExpect(jsonPath("$.errors.page").exists());
    }

    @Test
    @WithMockUser
    void constraintViolationReturns400WithFieldErrors() throws Exception {
        mockMvc.perform(get("/test-errors/constraint"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.businessCode").value(BusinessErrorCodes.VALIDATION_FAILED.getCode()))
                .andExpect(jsonPath("$.errors.email").exists());
    }

    // ---------------------------------------------------------------------------------
    // Security
    // ---------------------------------------------------------------------------------

    @Test
    @WithMockUser // authenticated, but without role::read
    void missingPermissionReturns403FromAccessDeniedHandler() throws Exception {
        mockMvc.perform(get("/role").param("page", "0").param("size", "20"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.businessCode").value(BusinessErrorCodes.ACCESS_DENIED.getCode()));
    }

    @Test
    void anonymousUserReturns401FromEntryPoint() throws Exception {
        mockMvc.perform(get("/role").param("page", "0").param("size", "20"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.businessCode").value(BusinessErrorCodes.AUTHENTICATION_REQUIRED.getCode()));
    }

    @Test
    @WithMockUser
    void lockedDisabledAndWrongPasswordGiveIdenticalResponses() throws Exception {
        String badCredentials = bodyOf("/test-errors/bad-credentials");
        assertThat(bodyOf("/test-errors/locked")).isEqualTo(badCredentials);
        assertThat(bodyOf("/test-errors/disabled")).isEqualTo(badCredentials);
        assertThat(badCredentials).contains("\"businessCode\":" + BusinessErrorCodes.BAD_CREDENTIALS.getCode());
    }

    private String bodyOf(String url) throws Exception {
        return mockMvc.perform(get(url))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
    }

    // ---------------------------------------------------------------------------------
    // Test-only controller
    // ---------------------------------------------------------------------------------

    record Payload(@NotBlank String name, @Email String email) {
    }

    @RestController
    @RequestMapping("/test-errors")
    static class ThrowingController {

        private final Validator validator;

        ThrowingController(Validator validator) {
            this.validator = validator;
        }

        @GetMapping("/unexpected")
        void unexpected() {
            throw new IllegalStateException(SECRET + ": select * from users where email = ?");
        }

        @GetMapping("/mail")
        void mail() throws MessagingException {
            throw new MessagingException(SECRET + ": 535 Authentication failed for smtp.internal.local");
        }

        @GetMapping("/data-integrity")
        void dataIntegrity() {
            throw new DataIntegrityViolationException(SECRET + ": duplicate key violates constraint uk_user_email");
        }

        @PostMapping("/body")
        void body(@Valid @RequestBody Payload payload) {
        }

        @GetMapping("/number/{id}")
        void number(@PathVariable Long id) {
        }

        @GetMapping("/param")
        void param(@RequestParam @Min(1) int page) {
        }

        @GetMapping("/constraint")
        void constraint() {
            throw new ConstraintViolationException(validator.validate(new Payload("name", "not-an-email")));
        }

        @GetMapping("/bad-credentials")
        void badCredentials() {
            throw new BadCredentialsException("Bad credentials");
        }

        @GetMapping("/locked")
        void locked() {
            throw new LockedException("User account is locked");
        }

        @GetMapping("/disabled")
        void disabled() {
            throw new DisabledException("User is disabled");
        }
    }
}
