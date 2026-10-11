package com.association.assofin.role.repository;

import com.association.assofin.role.entity.RolePermission;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface RolePermissionRepository extends JpaRepository<RolePermission,Integer> {

    @EntityGraph(attributePaths = "permission")
    List<RolePermission> findAllByRole_IdIn(
            Collection<Long> roleIds
    );
}
