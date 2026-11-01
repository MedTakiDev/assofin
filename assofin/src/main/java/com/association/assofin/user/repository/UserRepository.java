package com.association.assofin.user.repository;

import com.association.assofin.user.entity.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Loads the user together with its role and the role's permissions in one query.
     * {@code User.getAuthorities()} reads all three, and it is called outside any transaction
     * (by the login authentication provider and by the JWT filter), so they must not be lazy here.
     */
    @EntityGraph(attributePaths = {"role", "role.rolePermissions", "role.rolePermissions.permission"})
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /** True when at least one user holds the role with this name (e.g. {@code "ADMIN"}). */
    boolean existsByRole_Name(String roleName);

    Optional<User> findByUsernameIgnoreCase(String username);

}
