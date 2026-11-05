package com.association.assofin.security;

import com.association.assofin.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The servlet ERROR dispatch, on a real Tomcat. MockMvc can't test this: it never performs the
 * internal forward to /error, so only a real HTTP request exercises the rule in SecurityConfig.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ErrorDispatchSecurityTest {

    @Value("${local.server.port}")
    private int port;

    @Test
    void anErrorRaisedOutsideMvcKeepsItsRealStatusForAnAnonymousUser() throws IOException, InterruptedException {
        // StrictHttpFirewall rejects ";" before MVC, so GlobalExceptionHandler never sees this request.
        // The container then forwards to /error. If that forward were not permitted, this would be a 401.
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/role;x=1")).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(400);
    }
}
