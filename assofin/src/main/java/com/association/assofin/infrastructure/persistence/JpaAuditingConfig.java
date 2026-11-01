package com.association.assofin.infrastructure.persistence;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Turns on Spring Data JPA auditing so {@code @CreatedDate} / {@code @LastModifiedDate} fields
 * (Role, RolePermission, User) are filled by {@code AuditingEntityListener}.
 * <p>
 * Without it, every insert into a table with a {@code NOT NULL created_date} column fails,
 * which blocks the startup bootstrap of the ADMIN role (BUG-01).
 * <p>
 * Kept in its own {@code @Configuration} rather than on {@code AssofinApplication} so that
 * slice tests such as {@code @WebMvcTest} do not try to start JPA auditing.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
