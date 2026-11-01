package com.association.assofin.infrastructure.bootstrap;

import com.association.assofin.infrastructure.permission.PermissionRegistry;
import com.association.assofin.permission.entity.Permission;
import com.association.assofin.permission.entity.PermissionDefinition;
import com.association.assofin.permission.repository.PermissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Bootstrap step 1 of 3: copies every {@link PermissionDefinition} declared in code
 * ({@link PermissionRegistry}) into the {@code permission} table.
 * <p>
 * Idempotent: only missing rows are inserted, so restarting the application is safe.
 * Runs before {@link RoleInitializer}, which needs these rows to grant them to ADMIN.
 */
@Slf4j
@Component
@Order(BootstrapOrder.PERMISSIONS)
@RequiredArgsConstructor
public class PermissionInitializer implements ApplicationRunner {

    private final PermissionRepository permissionRepository;
    private final PermissionRegistry permissionRegistry;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<PermissionDefinition> declared = permissionRegistry.all();
        int created = 0;
        for (PermissionDefinition permission : declared) {
            if (!permissionRepository.existsByName(permission.value())) {
                permissionRepository.save(
                        Permission.builder()
                                .name(permission.value())
                                .build()
                );
                created++;
            }
        }
        log.info("Permission bootstrap: {} declared in code, {} created", declared.size(), created);
    }
}
