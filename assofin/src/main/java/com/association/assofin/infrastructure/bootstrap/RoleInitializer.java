package com.association.assofin.infrastructure.bootstrap;

import com.association.assofin.infrastructure.permission.PermissionRegistry;
import com.association.assofin.permission.entity.Permission;
import com.association.assofin.permission.entity.PermissionDefinition;
import com.association.assofin.permission.repository.PermissionRepository;
import com.association.assofin.role.SystemRole;
import com.association.assofin.role.entity.Role;
import com.association.assofin.role.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Bootstrap step 2 of 3: makes sure every {@link SystemRole} exists.
 * <ul>
 *   <li>{@code ADMIN} is granted <b>every</b> permission declared in the {@link PermissionRegistry}.
 *       This is <i>add-missing-only</i>: a permission added in code later is granted on the next
 *       start, but no grant is ever removed, so manual changes made through the role API survive
 *       a restart.</li>
 *   <li>{@code USER} is created with no permissions.</li>
 * </ul>
 * Idempotent: existing roles are reused, nothing is duplicated on restart.
 * <p>
 * Concurrency: this assumes a single application instance starts at a time. The unique constraint
 * on {@code role.name} makes a concurrent second instance fail fast instead of creating duplicates.
 * Moving the seed to Flyway (PER-01) is the long-term answer for multi-instance deployments.
 */
@Slf4j
@Component
@Order(BootstrapOrder.ROLES)
@RequiredArgsConstructor
public class RoleInitializer implements ApplicationRunner {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final PermissionRegistry permissionRegistry;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (SystemRole systemRole : SystemRole.values()) {
            Role role = findOrCreate(systemRole);
            if (systemRole == SystemRole.ADMIN) {
                grantAllDeclaredPermissions(role);
            }
        }
    }

    private Role findOrCreate(SystemRole systemRole) {
        return roleRepository.findByName(systemRole.name())
                .orElseGet(() -> {
                    log.info("Creating system role {}", systemRole.name());
                    return roleRepository.save(Role.builder().name(systemRole.name()).build());
                });
    }

    private void grantAllDeclaredPermissions(Role admin) {
        Set<String> declaredNames = permissionRegistry.all().stream()
                .map(PermissionDefinition::value)
                .collect(Collectors.toSet());

        Set<Permission> declaredPermissions = permissionRepository.findByNameIn(declaredNames);
        if (declaredPermissions.size() != declaredNames.size()) {
            // PermissionInitializer runs first, so this means the order or the registry is broken.
            throw new IllegalStateException("Some declared permissions are missing from the database; "
                    + "PermissionInitializer must run before RoleInitializer");
        }

        int granted = admin.grantPermissions(declaredPermissions);
        log.info("Role {}: {} new permission(s) granted, {} declared in code",
                admin.getName(), granted, declaredNames.size());
    }
}
