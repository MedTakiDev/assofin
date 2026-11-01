package com.association.assofin.infrastructure.bootstrap;

import com.association.assofin.role.entity.Role;
import com.association.assofin.role.repository.RoleRepository;
import com.association.assofin.user.entity.User;
import com.association.assofin.user.repository.UserRepository;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Unit tests for the decision rules of {@link AdminUserInitializer}; no Spring context, no database. */
class AdminUserInitializerTest {

    private static final String PASSWORD = "a-strong-admin-password";

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    private final UserRepository userRepository = mock(UserRepository.class);
    private final RoleRepository roleRepository = mock(RoleRepository.class);
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4); // low cost: fast tests

    @BeforeAll
    static void createValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    private AdminUserInitializer initializer(BootstrapAdminProperties properties) {
        return new AdminUserInitializer(userRepository, roleRepository, passwordEncoder, properties, validator);
    }

    private static BootstrapAdminProperties properties(String email, String password) {
        return new BootstrapAdminProperties(email, password, "System", "Administrator");
    }

    @Test
    void skipsWhenAnAdminAlreadyExists() {
        when(userRepository.existsByRole_Name("ADMIN")).thenReturn(true);

        // Even an invalid configuration is ignored: env vars may be removed once an admin exists.
        initializer(properties("", "")).run(new DefaultApplicationArguments());

        verify(userRepository, never()).save(any());
    }

    @Test
    void failsWithTheEnvVarNamesWhenConfigurationIsMissing() {
        when(userRepository.existsByRole_Name("ADMIN")).thenReturn(false);

        assertThatThrownBy(() -> initializer(properties(null, "short")).run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADMIN_EMAIL")
                .hasMessageContaining("ADMIN_PASSWORD")
                .message().doesNotContain("short"); // the submitted password never appears

        verify(userRepository, never()).save(any());
    }

    @Test
    void refusesToPromoteAnExistingNonAdminUser() {
        when(userRepository.existsByRole_Name("ADMIN")).thenReturn(false);
        when(userRepository.existsByEmail("taken@assofin.local")).thenReturn(true);

        assertThatThrownBy(() -> initializer(properties("taken@assofin.local", PASSWORD)).run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("taken@assofin.local");

        verify(userRepository, never()).save(any());
    }

    @Test
    void createsAnEnabledAdminWithNormalizedEmailAndHashedPassword() {
        Role adminRole = Role.builder().name("ADMIN").build();
        when(userRepository.existsByRole_Name("ADMIN")).thenReturn(false);
        when(userRepository.existsByEmail("admin@assofin.local")).thenReturn(false);
        when(roleRepository.findByName("ADMIN")).thenReturn(Optional.of(adminRole));

        initializer(properties("  Admin@Assofin.Local ", PASSWORD)).run(new DefaultApplicationArguments());

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        User admin = saved.getValue();
        assertThat(admin.getEmail()).isEqualTo("admin@assofin.local");
        assertThat(admin.getRole()).isSameAs(adminRole);
        assertThat(admin.isEnabled()).isTrue();
        assertThat(admin.isAccountLocked()).isFalse();
        assertThat(admin.getPassword()).isNotEqualTo(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, admin.getPassword())).isTrue();
    }

    @Test
    void toStringNeverExposesThePassword() {
        assertThat(properties("admin@assofin.local", PASSWORD).toString()).doesNotContain(PASSWORD);
    }
}
