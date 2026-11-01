package com.association.assofin.infrastructure.bootstrap;

/**
 * Execution order of the startup {@code ApplicationRunner}s. Each step depends on the previous one:
 * permissions must exist before they are granted to ADMIN, and the ADMIN role must exist before
 * the admin user is created.
 */
final class BootstrapOrder {

    static final int PERMISSIONS = 1;
    static final int ROLES = 2;
    static final int ADMIN_USER = 3;

    private BootstrapOrder() {
    }
}
