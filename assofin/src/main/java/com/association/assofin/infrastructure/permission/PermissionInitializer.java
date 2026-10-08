package com.association.assofin.infrastructure.permission;

import com.association.assofin.permission.Permission;
import com.association.assofin.permission.PermissionDefinition;
import com.association.assofin.permission.PermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class PermissionInitializer implements ApplicationRunner {

    private final PermissionRepository permissionRepository;
    private final PermissionRegistry permissionRegistry;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {

        for (PermissionDefinition permission : permissionRegistry.all()) {

            if (!permissionRepository.existsByName(permission.name())) {
                permissionRepository.save(
                        Permission.builder()
                                .name(permission.name())
                                .build()
                );
            }
        }
    }
}