package com.association.assofin.infrastructure.bootstrap;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.Locale;

/**
 * Credentials of the default administrator created on an empty database
 * ({@code application.bootstrap.admin.*}, bound from {@code ADMIN_EMAIL}, {@code ADMIN_PASSWORD},
 * {@code ADMIN_FIRSTNAME}, {@code ADMIN_LASTNAME}).
 * <p>
 * The class is deliberately <b>not</b> {@code @Validated}: the values are only required while no
 * ADMIN user exists. {@link AdminUserInitializer} validates them at that moment, so the env vars can
 * be removed from an environment once its admin account exists.
 * <p>
 * {@link #toString()} is overridden so the password can never end up in a log line.
 */
@ConfigurationProperties(prefix = "application.bootstrap.admin")
public record BootstrapAdminProperties(

        @NotBlank(message = "is required (env ADMIN_EMAIL)")
        @Email(message = "must be a valid email address (env ADMIN_EMAIL)")
        String email,

        @NotBlank(message = "is required (env ADMIN_PASSWORD)")
        @Size(min = 12, max = 128, message = "must be between 12 and 128 characters (env ADMIN_PASSWORD)")
        String password,

        @DefaultValue("System")
        @NotBlank(message = "must not be blank (env ADMIN_FIRSTNAME)")
        String firstname,

        @DefaultValue("Administrator")
        @NotBlank(message = "must not be blank (env ADMIN_LASTNAME)")
        String lastname
) {

    /**
     * Trims surrounding whitespace (a common copy/paste or .env mistake) and lower-cases the email,
     * so validation and the stored account use the same value. The password is kept as typed.
     */
    public BootstrapAdminProperties {
        email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
        firstname = firstname == null ? null : firstname.trim();
        lastname = lastname == null ? null : lastname.trim();
    }

    @Override
    public String toString() {
        return "BootstrapAdminProperties[email=" + email + ", password=***, firstname=" + firstname
                + ", lastname=" + lastname + "]";
    }
}
