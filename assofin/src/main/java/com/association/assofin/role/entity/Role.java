package com.association.assofin.role.entity;

import com.association.assofin.permission.entity.Permission;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Builder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "role")
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String name;

    @Builder.Default
    @Setter(AccessLevel.NONE)   // force callers through the helper methods
    @OneToMany(mappedBy = "role", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<RolePermission> rolePermissions = new HashSet<>();

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDate createdDate;

    @LastModifiedDate
    @Column(insertable = false)
    private LocalDate lastModifiedDate;

    /** Makes the role's permissions equal to {@code target}, touching only the rows that changed. */
    public void replacePermissions(Set<Permission> target) {
        // 1. Remove links whose permission is no longer wanted
        rolePermissions.removeIf(currentRolePermission-> !target.contains(currentRolePermission.getPermission()));

        // 2. Permissions still linked after the removal
        Set<Permission> existing = rolePermissions.stream()
                .map(RolePermission::getPermission)
                .collect(Collectors.toSet());

        // 3. Add links only for the missing ones
        target.stream()
                .filter(p -> !existing.contains(p))
                .map(p -> RolePermission.builder().role(this).permission(p).build())
                .forEach(rolePermissions::add);
    }
}
