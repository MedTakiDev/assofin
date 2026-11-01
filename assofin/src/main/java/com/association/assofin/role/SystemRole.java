package com.association.assofin.role;

/**
 * Roles the application needs to exist on every database. They are created at startup by
 * {@code RoleInitializer}.
 * <p>
 * The enum name is stored as-is in {@code role.name} ({@code ADMIN}, {@code USER}), <b>without</b>
 * the {@code ROLE_} prefix. {@code User.getAuthorities()} adds the prefix exactly once, so Spring
 * Security sees {@code ROLE_ADMIN} and {@code hasRole("ADMIN")} works (fixes BUG-03).
 */
public enum SystemRole {
    /** Holds every permission declared in the {@code PermissionRegistry}. */
    ADMIN,
    /** Default role for regular members; starts with no permissions. */
    USER
}
