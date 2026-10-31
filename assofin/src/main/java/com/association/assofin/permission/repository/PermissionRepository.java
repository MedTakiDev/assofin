package com.association.assofin.permission.repository;

import com.association.assofin.permission.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.Set;

public interface PermissionRepository extends JpaRepository<Permission, Long> {
    Optional<Permission> findByName(String name);
    Optional<Permission> findById(Long id);

    Boolean existsByName(String name);

    Set<Permission> findByNameIn(Set<String> permissions);
}
