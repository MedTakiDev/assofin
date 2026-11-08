package com.association.assofin.mail;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Application mail settings. Validated at startup, so a missing or malformed sender stops the
 * application instead of failing at the first email.
 *
 * <p>The SMTP connection itself (host, port, credentials) is Spring Boot's {@code spring.mail.*}.
 *
 * @param from sender address of every email (env {@code MAIL_FROM}). It must belong to a domain the
 *             SMTP server may send for, or receiving servers reject the mail (SPF, DKIM).
 */
@Validated
@ConfigurationProperties(prefix = "application.mail")
public record MailProperties(
        @NotBlank(message = "is required (env MAIL_FROM)") @Email(message = "must be an email address (env MAIL_FROM)") String from
) {
}
