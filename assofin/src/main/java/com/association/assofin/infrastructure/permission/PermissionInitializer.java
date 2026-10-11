package com.association.assofin.infrastructure.permission;

import com.association.assofin.permission.entity.Permission;
import com.association.assofin.permission.entity.PermissionDefinition;
import com.association.assofin.permission.repository.PermissionRepository;
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

            if (!permissionRepository.existsByName(permission.value())) {
                permissionRepository.save(
                        Permission.builder()
                                .name(permission.value())
                                .build()
                );
            }
        }
    }
}