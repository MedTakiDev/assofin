package com.association.assofin.role.repository;

import com.association.assofin.role.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByName(String name);

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Long id);

    @Query("select r from Role r left join fetch r.rolePermissions where r.id = :id")
    Optional<Role> findByIdWithRolePermissions(@Param("id") Long id);
}
