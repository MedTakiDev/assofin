package com.association.assofin.config;

import com.association.assofin.TestcontainersConfiguration;
import com.association.assofin.mail.MailProperties;
import com.association.assofin.security.ApiDocsProperties;
import com.association.assofin.security.CorsProperties;
import com.association.assofin.security.cookie.AuthCookieProperties;
import com.association.assofin.security.jwt.JwtProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Starts the real prod profile with exactly the required variables listed in the root README, and
 * nothing else. If a new required setting appears without being documented, this fails.
 *
 * <p>Keep {@code properties} in sync with the README table "Production environment variables".
 * The database values are placeholders: Testcontainers provides the real connection.
 */
@SpringBootTest(properties = {
        "POSTGRES_HOST=replaced-by-testcontainers",
        "POSTGRES_DB=assofin",
        "POSTGRES_USER=assofin",
        "POSTGRES_PASSWORD=not-a-real-secret",
        "JWT_SECRET_KEY=dGVzdC1vbmx5LWp3dC1zZWNyZXQta2V5LTMyLWJ5dGVzISE=",
        "JWT_EXPIRATION=15m",
        "CORS_ALLOWED_ORIGINS=https://app.example.org",
        "MAIL_HOST=smtp.example.org",
        "MAIL_PORT=587",
        "MAIL_FROM=no-reply@example.org",
        "ADMIN_EMAIL=admin@example.org",
        "ADMIN_PASSWORD=prod-test-password"
})
@ActiveProfiles("prod")
@Import(TestcontainersConfiguration.class)
class ProductionProfileStartupTest {

    @Autowired private JwtProperties jwtProperties;
    @Autowired private CorsProperties corsProperties;
    @Autowired private AuthCookieProperties cookieProperties;
    @Autowired private ApiDocsProperties apiDocsProperties;
    @Autowired private MailProperties mailProperties;

    @Value("${spring.mail.properties.mail.smtp.auth}") private boolean smtpAuth;
    @Value("${spring.mail.properties.mail.smtp.starttls.enable}") private boolean smtpStartTls;

    @Test
    void startsWithOnlyTheDocumentedVariablesAndProductionSettings() {
        assertThat(jwtProperties.expiration()).isEqualTo(Duration.ofMinutes(15));
        assertThat(corsProperties.allowedOrigins()).containsExactly("https://app.example.org");

        assertThat(cookieProperties.secure()).isTrue();
        assertThat(cookieProperties.name()).isEqualTo("__Host-access_token");
        assertThat(apiDocsProperties.exposeApiDocs()).isFalse();

        assertThat(smtpAuth).as("SMTP auth defaults to on").isTrue();
        assertThat(smtpStartTls).as("SMTP STARTTLS defaults to on").isTrue();
        assertThat(mailProperties.from()).isEqualTo("no-reply@example.org");
        // show-sql and ddl-auto in production are handled by PER-01.
    }
}
