package com.association.assofin.infrastructure.bootstrap;

import com.association.assofin.TestcontainersConfiguration;
import com.association.assofin.infrastructure.permission.PermissionRegistry;
import com.association.assofin.permission.entity.PermissionDefinition;
import com.association.assofin.permission.repository.PermissionRepository;
import com.association.assofin.role.SystemRole;
import com.association.assofin.role.entity.Role;
import com.association.assofin.role.repository.RoleRepository;
import com.association.assofin.user.entity.User;
import com.association.assofin.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Starts the whole application on an empty PostgreSQL and checks what the three bootstrap runners
 * left in the database. The admin credentials come from {@code application-test.yml}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class BootstrapInitializersTest {

    private static final String ADMIN_EMAIL = "admin@test.local"; // configured as "Admin@Test.Local"
    private static final String ADMIN_PASSWORD = "test-admin-password";

    @Autowired private PermissionRegistry permissionRegistry;
    @Autowired private PermissionRepository permissionRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private MockMvc mockMvc;

    @Autowired private PermissionInitializer permissionInitializer;
    @Autowired private RoleInitializer roleInitializer;
    @Autowired private AdminUserInitializer adminUserInitializer;

    @Test
    void everyDeclaredPermissionIsPersisted() {
        Set<String> persisted = permissionRepository.findAll().stream()
                .map(permission -> permission.getName())
                .collect(Collectors.toSet());

        assertThat(persisted).containsAll(declaredPermissionNames());
    }

    @Test
    void adminRoleHoldsEveryDeclaredPermissionAndUserRoleHoldsNone() {
        assertThat(permissionNamesOf(SystemRole.ADMIN)).containsExactlyInAnyOrderElementsOf(declaredPermissionNames());
        assertThat(permissionNamesOf(SystemRole.USER)).isEmpty();
    }

    @Test
    void exactlyOneEnabledAdminWithHashedPasswordIsCreated() {
        User admin = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow();

        assertThat(admin.isEnabled()).isTrue();
        assertThat(admin.isAccountNonLocked()).isTrue();
        assertThat(admin.getPassword()).isNotEqualTo(ADMIN_PASSWORD).startsWith("$2");
        assertThat(passwordEncoder.matches(ADMIN_PASSWORD, admin.getPassword())).isTrue();
        assertThat(admin.getCreatedDate()).as("JPA auditing fills createdDate").isNotNull();
        assertThat(admin.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .contains("ROLE_ADMIN", "user::create", "role::read")
                .doesNotContain("ROLE_ROLE_ADMIN");
        assertThat(countAdmins()).isEqualTo(1);
    }

    @Test
    void runningTheBootstrapAgainCreatesNoDuplicates() throws Exception {
        long permissions = permissionRepository.count();
        long roles = roleRepository.count();
        long users = userRepository.count();
        int adminGrants = permissionNamesOf(SystemRole.ADMIN).size();

        var args = new DefaultApplicationArguments();
        permissionInitializer.run(args);
        roleInitializer.run(args);
        adminUserInitializer.run(args);

        assertThat(permissionRepository.count()).isEqualTo(permissions);
        assertThat(roleRepository.count()).isEqualTo(roles);
        assertThat(userRepository.count()).isEqualTo(users);
        assertThat(permissionNamesOf(SystemRole.ADMIN)).hasSize(adminGrants);
        assertThat(countAdmins()).isEqualTo(1);
    }

    @Test
    void bootstrapAdminCanLogInAndReadRoles() throws Exception {
        Cookie token = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s"}
                                """.formatted(ADMIN_EMAIL, ADMIN_PASSWORD)))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getCookie("token");

        assertThat(token).isNotNull();

        mockMvc.perform(get("/role").param("page", "0").param("size", "20").cookie(token))
                .andExpect(status().isOk());
    }

    private Set<String> declaredPermissionNames() {
        return permissionRegistry.all().stream()
                .map(PermissionDefinition::value)
                .collect(Collectors.toSet());
    }

    private Set<String> permissionNamesOf(SystemRole systemRole) {
        return transactionTemplate.execute(status -> {
            Role role = roleRepository.findByName(systemRole.name()).orElseThrow();
            return role.getRolePermissions().stream()
                    .map(rolePermission -> rolePermission.getPermission().getName())
                    .collect(Collectors.toSet());
        });
    }

    private boolean isAdmin(User user) {
        return SystemRole.ADMIN.name().equals(user.getRole().getName());
    }

    private long countAdmins() {
        return transactionTemplate.execute(status ->
                userRepository.findAll().stream().filter(this::isAdmin).count());
    }
}
