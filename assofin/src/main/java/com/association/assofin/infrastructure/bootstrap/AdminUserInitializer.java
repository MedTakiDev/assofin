package com.association.assofin.infrastructure.bootstrap;

import com.association.assofin.role.SystemRole;
import com.association.assofin.role.entity.Role;
import com.association.assofin.role.repository.RoleRepository;
import com.association.assofin.user.entity.User;
import com.association.assofin.user.repository.UserRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Bootstrap step 3 of 3: creates the default administrator when <b>no user holds the ADMIN role</b>.
 * <ul>
 *   <li>An ADMIN already exists → nothing happens (the configured credentials are ignored, so
 *       changing {@code ADMIN_PASSWORD} later does <b>not</b> reset the admin's password).</li>
 *   <li>No ADMIN, and the configuration is missing or invalid → startup fails with a message naming
 *       the env vars to set. There are no hard-coded fallback credentials.</li>
 *   <li>No ADMIN, but a non-admin user already uses the configured email → startup fails. An
 *       existing account is never promoted silently.</li>
 * </ul>
 * The password is hashed with the application {@link PasswordEncoder} and is never logged.
 */
@Slf4j
@Component
@Order(BootstrapOrder.ADMIN_USER)
@RequiredArgsConstructor
public class AdminUserInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final BootstrapAdminProperties adminProperties;
    private final Validator validator;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String adminRoleName = SystemRole.ADMIN.name();

        if (userRepository.existsByRole_Name(adminRoleName)) {
            log.info("Admin account present, skipping bootstrap admin creation");
            return;
        }

        validateConfiguration();
        String email = adminProperties.email(); // already trimmed + lower-cased by BootstrapAdminProperties

        if (userRepository.existsByEmail(email)) {
            throw new IllegalStateException("Cannot create the bootstrap admin: a non-admin user already uses "
                    + email + ". Set ADMIN_EMAIL to another address or grant that user the ADMIN role manually.");
        }

        Role adminRole = roleRepository.findByName(adminRoleName)
                .orElseThrow(() -> new IllegalStateException(
                        "Role " + adminRoleName + " not found; RoleInitializer must run before AdminUserInitializer"));

        String firstname = adminProperties.firstname();
        String lastname = adminProperties.lastname();
        User admin = User.builder()
                .firstname(firstname)
                .lastname(lastname)
                .username(firstname + " " + lastname) // same convention as UserService
                .email(email)
                .password(passwordEncoder.encode(adminProperties.password()))
                .role(adminRole)
                .enabled(true)
                .accountLocked(false)
                .build();
        userRepository.save(admin);

        log.info("Bootstrap admin created: {}", email);
    }

    private void validateConfiguration() {
        Set<ConstraintViolation<BootstrapAdminProperties>> violations = validator.validate(adminProperties);
        if (violations.isEmpty()) {
            return;
        }
        // Messages only name the field and the env var; they never contain the submitted value.
        String details = violations.stream()
                .map(violation -> "application.bootstrap.admin." + violation.getPropertyPath() + " " + violation.getMessage())
                .sorted()
                .collect(Collectors.joining("; "));
        throw new IllegalStateException("No user holds the ADMIN role and the bootstrap admin configuration is invalid: "
                + details);
    }
}
